# 부하 테스트

아래 명령은 모두 프로젝트 루트에서 실행한다. 현재 위치와 관계없이 다음 명령으로 루트로 이동할 수 있다.

```bash
cd "$(git rev-parse --show-toplevel)"
```

## 직접 예약 기준선

이 시나리오는 판매가 이미 `OPEN` 상태이고 재고가 설정된 상황에서, 서로 다른 회원이 동시에 예약을 요청하는 부하를 측정한다. 각 VU는 한 번만 요청하며, 같은 회원 ID를 재사용하지 않는다.

## 사전 조건

애플리케이션은 개발 인증 필터가 활성화된 `local` 프로필로 실행한다.

```bash
export DATABASE_URL='postgresql://limited_ticketing:limited_ticketing@localhost:5432/limited_ticketing'
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
  -e BASE_URL=http://localhost:8081 \
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

## Redis ZSET 대기열

`waiting-room.js`는 각 iteration마다 서로 다른 회원을 사용한다. 먼저 서버의 멱등성 키 API를 호출하고, 발급된 키로 대기열 진입 API를 호출한다. `constant-arrival-rate`를 사용하므로 진입 요청 수와 초당 유입률을 별도로 조정할 수 있다.

k6가 시간 구간 마지막 경계에 요청을 하나 더 예약하지 않도록 실제 scenario duration은 `DURATION_SECONDS * 1000 - 1ms`로 계산한다. 목표 진입 요청 수는 정확히 `RATE * DURATION_SECONDS`다.

멱등성 키 발급 시간과 대기열 진입 시간은 각각 `idempotency_key_duration`, `waiting_room_enqueue_duration`으로 분리해 측정한다. 상태 조회 polling은 진입 부하 결과를 오염시키므로 이 시나리오에 포함하지 않는다.

### 0. 인프라와 애플리케이션 실행

터미널 A에서 프로젝트 루트로 이동해 PostgreSQL과 Redis를 실행한다.

```bash
cd "$(git rev-parse --show-toplevel)"
docker compose up -d
docker compose ps
```

같은 터미널에서 애플리케이션을 실행한다. 아래 값은 한 상품에 동시에 최대 10건만 DB 예약 경로로 진입시킨다.

```bash
SPRING_PROFILES_ACTIVE=local \
QUEUE_MAX_CONCURRENT_PROCESSING=10 \
QUEUE_CLAIM_BATCH_SIZE=10 \
QUEUE_RECOVERY_BATCH_SIZE=1000 \
./gradlew :app:bootRun
```

터미널 B도 반드시 프로젝트 루트로 이동한다. 이후 명령은 모두 터미널 B에서 실행한다.

```bash
cd "$(git rev-parse --show-toplevel)"
```

### 1. 테스트 데이터와 환경 변수 준비

준비 스크립트는 PostgreSQL에 새 회원 1,000명, OPEN 판매 상품, 재고를 생성하고 회원 ID JSON 파일을 만든다. 출력된 `export` 명령을 `eval`하므로 현재 셸에 k6 실행용 환경 변수가 바로 설정된다.

```bash
export DATABASE_URL='postgresql://limited_ticketing:limited_ticketing@localhost:5432/limited_ticketing'
export BASE_URL='http://localhost:8081'
eval "$(bash load-test/k6/prepare-waiting-room.sh)"
```

환경 변수와 데이터 파일을 확인한다.

```bash
printf 'run=%s saleProduct=%s members=%s inventory=%s\n' \
  "$TEST_RUN_ID" "$SALE_PRODUCT_ID" "$MEMBER_COUNT" "$INVENTORY_QUANTITY"
test -f "$MEMBER_IDS_FILE"
jq 'length' "$MEMBER_IDS_FILE"
curl -fsS "$BASE_URL/api/sale-products/$SALE_PRODUCT_ID" | jq '.data'
```

기본 SQL에서는 `jq 'length'` 결과가 1,000이어야 한다. `prepare-waiting-room.sh`는 어느 디렉터리에서 호출해도 내부 파일을 절대 경로로 찾지만, 이후 k6 명령의 상대 경로를 위해 프로젝트 루트 실행을 권장한다.

SQL은 실행 시각으로 `TEST_RUN_ID`를 자동 생성한다. DB가 초기화되어 판매 ID가 재사용된 경우를 대비해 준비 스크립트는 새 판매 ID의 `waiting`, `processing`, `sequence` Redis 키도 삭제한다. 이 스크립트는 로컬 전용 PostgreSQL과 Redis에서만 실행해야 한다.

테스트 규모를 바꾸려면 `load-test/k6/sql/prepare-waiting-room.sql` 상단 `params` CTE의 `member_count`, `inventory_quantity` 값을 수정한다. IntelliJ/DataGrip에서는 SQL 파일을 별도 매개변수 없이 그대로 실행할 수 있다.

IDE에서 실행한 경우 결과 행의 `test_run_id`, `sale_product_id`를 각각 `TEST_RUN_ID`, `SALE_PRODUCT_ID` 환경 변수로 사용한다.

### 2. Smoke 테스트

먼저 10건으로 API 계약과 멱등성을 확인한다.

```bash
k6 run load-test/k6/waiting-room.js \
  -e BASE_URL="$BASE_URL" \
  -e SALE_PRODUCT_ID="$SALE_PRODUCT_ID" \
  -e MEMBER_IDS_FILE="$MEMBER_IDS_FILE" \
  -e RATE=10 \
  -e DURATION_SECONDS=1 \
  -e PRE_ALLOCATED_VUS=10 \
  -e MAX_VUS=20 \
  -e P95_THRESHOLD_MS=2000 \
  -e P99_THRESHOLD_MS=3000 \
  -e VERIFY_IDEMPOTENCY=true \
  --summary-export="load-test/k6/results/waiting-room-smoke-${TEST_RUN_ID}.json"
```

정상이라면 `waiting_room_accepted_entries=10`, `waiting_room_idempotent_retries=10`, `waiting_room_unexpected_responses=0`, `dropped_iterations=0`이어야 한다. Smoke 실행이 끝난 뒤 같은 fixture로 Load 테스트를 이어서 실행하면 이미 처리된 회원과 재고가 결과에 섞이므로 사용하지 않는다.

### 3. 단계별 부하 테스트

각 실행은 **새 `TEST_RUN_ID`와 새 판매 상품**으로 준비해야 한다. 그렇지 않으면 이전 실행의 활성 구매와 대기열 항목이 결과에 섞인다.

Smoke 이후 Load용 fixture와 환경 변수를 새로 준비한다.

```bash
eval "$(bash load-test/k6/prepare-waiting-room.sh)"
printf 'load run=%s saleProduct=%s memberFile=%s\n' \
  "$TEST_RUN_ID" "$SALE_PRODUCT_ID" "$MEMBER_IDS_FILE"
```

| 단계 | 설정 | 총 진입 요청 |
|---|---|---:|
| Load | `RATE=100`, `DURATION_SECONDS=10` | 1,000 |
| Stress | `RATE=500`, `DURATION_SECONDS=10` | 5,000 |
| Spike | `RATE=2000`, `DURATION_SECONDS=5` | 10,000 |

예를 들어 1,000건 Load 테스트는 다음과 같이 실행한다.

```bash
k6 run load-test/k6/waiting-room.js \
  -e BASE_URL="$BASE_URL" \
  -e SALE_PRODUCT_ID="$SALE_PRODUCT_ID" \
  -e MEMBER_IDS_FILE="$MEMBER_IDS_FILE" \
  -e RATE=100 \
  -e DURATION_SECONDS=10 \
  -e PRE_ALLOCATED_VUS=200 \
  -e MAX_VUS=1000 \
  -e P95_THRESHOLD_MS=500 \
  -e P99_THRESHOLD_MS=1000 \
  --summary-export="load-test/k6/results/waiting-room-load-${TEST_RUN_ID}.json"
```

Smoke 테스트는 워밍업되지 않은 첫 요청이 표본 대부분을 차지하므로 완화된 임계값을 사용한다. 성능 비교는 애플리케이션 워밍업 후 Load/Stress/Spike를 각각 3회 수행하고 중앙값과 p95/p99를 기록한다. 목표 요청 수는 `iterations`와 `waiting_room_accepted_entries`가 모두 `RATE * DURATION_SECONDS`인지 확인한다.

Spike 테스트에서 `dropped_iterations`가 발생하면 애플리케이션이 아니라 로컬 k6의 VU 부족일 수 있다. `PRE_ALLOCATED_VUS`와 `MAX_VUS`를 높이되 CPU와 메모리 사용량을 함께 확인한다.

### 4. Redis 처리 슬롯 관찰

부하 테스트 중 별도 터미널에서 실행한다.

```bash
while true; do
  printf '%s waiting=' "$(date '+%H:%M:%S')"
  docker compose exec -T redis redis-cli ZCARD "waiting:sale:$SALE_PRODUCT_ID" | tr -d '\r\n'
  printf ' processing='
  docker compose exec -T redis redis-cli ZCARD "processing:sale:$SALE_PRODUCT_ID" | tr -d '\r\n'
  printf '\n'
  sleep 1
done
```

`processing`은 `QUEUE_MAX_CONCURRENT_PROCESSING`을 초과하지 않아야 한다. `waiting`은 유입 중 증가하고 Worker 처리에 따라 감소해야 한다.

### 5. 최종 정합성 검증

Worker 처리가 끝나 `waiting`과 `processing`이 모두 0이 된 후 검증한다.

```bash
psql "$DATABASE_URL" \
  -v sale_product_id="$SALE_PRODUCT_ID" \
  -f load-test/k6/sql/verify-waiting-room.sql

docker compose exec -T redis redis-cli ZCARD "waiting:sale:$SALE_PRODUCT_ID"
docker compose exec -T redis redis-cli ZCARD "processing:sale:$SALE_PRODUCT_ID"
```

- `reservation_queue_entries` 총 수는 k6의 `waiting_room_accepted_entries`와 같아야 한다.
- `RESERVED` 수는 초기 재고 이하이며, 처리 완료 후에는 초기 재고와 같아야 한다.
- 나머지 정상 요청은 `SOLD_OUT`이어야 하고 `FAILED`는 0이어야 한다.
- `available + reserved + sold`는 항상 초기 재고와 같아야 한다.
- 결제를 수행하지 않았으므로 `reserved`는 `RESERVED` 구매 수, `sold`는 0이어야 한다.
