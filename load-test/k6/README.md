# 판매 오픈 부하 테스트

이 시나리오는 판매가 이미 `OPEN` 상태이고 재고가 설정된 상황에서, 서로 다른 회원이 동시에 예약을 요청하는 부하를 측정한다. 각 VU는 한 번만 요청하며, 같은 회원 ID를 재사용하지 않는다.

## 사전 조건

애플리케이션은 개발 인증 필터가 활성화된 `local` 프로필로 실행한다.

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew :app:bootRun
```

관리 API에는 `X-Admin-Id: admin` 헤더를 사용한다. 이 헤더는 `local`, `test` 프로필에서만 동작하며 실제 인증 수단이 아니다.

1. `POST /api/admin/sale-products`로 시작 시각이 현재 이전이고 종료 시각이 미래인 DRAFT 판매를 생성한다.
2. `POST /api/admin/sale-products/{saleProductId}/open`으로 판매를 연다.
3. 실행할 VU 수 이상으로 활성 회원을 준비한다. 현재 회원 가입 API는 범위 밖이므로 로컬 DB에만 아래 SQL로 테스트 회원을 만든다.

```sql
INSERT INTO members (external_id, name, status)
SELECT 'k6-member-' || number, 'k6 member ' || number, 'ACTIVE'
FROM generate_series(1, 100) AS number;
```

테스트 회원 ID 목록은 다음처럼 준비한다.

```bash
export MEMBER_IDS="$(psql "$DATABASE_URL" -Atqc "
  select string_agg(id::text, ',' order by id)
  from (
    select id
    from members
    where external_id like 'k6-member-%'
    order by id
    limit 100
  ) members
")"
```

## 실행

`VUS`보다 `MEMBER_IDS`의 회원 수가 적으면 스크립트가 즉시 실패한다. 같은 회원을 중복 사용하면 재고 부족이 아닌 활성 구매 정책 충돌도 함께 측정되므로 허용하지 않는다.

```bash
mkdir -p load-test/k6/results

k6 run load-test/k6/sale-open.js \
  -e BASE_URL=http://localhost:8080 \
  -e SALE_PRODUCT_ID=1 \
  -e MEMBER_IDS="$MEMBER_IDS" \
  -e VUS=100 \
  --summary-export=load-test/k6/results/sale-open-summary.json
```

스크립트의 `setup` 단계는 각 회원으로 `POST /api/idempotency-keys`를 호출해 서버 발급 `PURCHASE` 키를 준비한다. 실제 동시 예약 구간에는 발급된 키만 사용하므로 키 발급 API의 응답 시간은 `reservation_duration`에 포함하지 않는다.

측정 값은 다음과 같이 해석한다.

- `reservation_successes`: 예약 성공 수. 초기 재고와 같아야 한다.
- `sold_out_responses`: 재고 소진으로 인한 409 수. `VUS - 초기 재고`와 같아야 한다.
- `unexpected_responses`: 201, 409 이외의 응답 수. 0이어야 한다.
- `reservation_duration`: 모든 예약 API 응답 시간. p95를 기록한다.
- `reservation_created_duration`: 성공한 201 예약 응답 시간. 재고 확보·구매 생성 경로의 p95를 기록한다.
- `reservation_conflict_duration`: 재고 소진 409 응답 시간. 재고 행 경합 후 실패 경로의 p95를 기록한다.

스크립트는 200(키 발급), 201(예약 성공), 409(재고 소진)을 시나리오상 정상 HTTP 응답으로 지정한다. 따라서 `http_req_failed`는 의도된 409이 아니라 실제 예기치 못한 HTTP 상태만 집계한다.

## DB 락과 낙관적 락 기록

부하 실행 전에 별도 터미널에서 아래 스크립트를 실행하면 PostgreSQL에서 `Lock` 대기 중인 세션 수를 초 단위 CSV로 기록한다.

```bash
DATABASE_URL="$DATABASE_URL" \
  bash load-test/k6/record-postgresql-locks.sh
```

이 프로젝트의 재고 예약은 `UPDATE ... WHERE available_quantity >= :quantity` 조건부 원자 갱신을 사용하므로 재고 경로에는 `@Version` 기반 낙관적 락이 없다. 따라서 이번 기준선에서 낙관적 락 충돌 수는 0이 정상이다. 추후 낙관적 락을 도입하면 애플리케이션 로그의 `OptimisticLockException`, `ObjectOptimisticLockingFailureException` 발생 횟수도 결과 파일에 함께 기록한다.

Redis 대기열이나 분산 락은 다음 조건이 확인된 경우에만 검토한다.

- DB 락 대기가 지속적으로 증가한다.
- 재고 정합성은 유지되지만 p95/p99 응답 시간이 목표를 넘는다.
- 애플리케이션 인스턴스를 늘려도 단일 재고 행 경합이 병목으로 남는다.
