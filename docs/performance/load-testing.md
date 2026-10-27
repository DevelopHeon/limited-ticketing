# 부하 테스트 실행

상품 상세·목록의 cache 비활성/cold/warm 비교는 [상품 조회 캐시 실험](catalog-read-cache.md)을 따른다. 아래 구매 접수·완료 부하와 조건·지표를 섞지 않는다.

실행 스크립트와 SQL은 [load-test/k6](../../load-test/k6)에 두고, 절차는 이 문서에서 관리한다. 로컬 실행 및 인증 설정은 [개발 및 검증](../development/README.md)을 참고한다. k6, psql과 jq가 필요하다.

아래 명령은 모두 프로젝트 루트에서 실행한다. 현재 위치와 관계없이 다음 명령으로 루트로 이동할 수 있다.

```bash
cd "$(git rev-parse --show-toplevel)"
```

## 자동 기준선 · 현재 평가 도구

`run-baseline.py`는 새 fixture·키 선발급·warmup·k6 실행·최종 SQL 검증·원본 보존·실험 소유 데이터 정리를 묶는다. 직접/대기 예약을 같은 유입 모델로 비교한다. [1단계 실측](../evaluations/load-testing/2026-10-15-stage-1-baseline.md)은 이 도구로 수집했다. 아래의 기존 개별 k6 시나리오와 서로 다른 유입 조건을 섞어 비교하지 않는다.

### 기존 로컬 환경 준비

기존 `limited-ticketing` checkout의 `master`에서 작업하고 기존 앱·PostgreSQL·Redis를 사용한다. 이 부하 도구 실행을 위해 별도 worktree·Compose project·앱 프로세스·DB·Redis를 만들지 않는다. 기본 연결은 앱 8081·PostgreSQL 5432·Redis 6379이며, 이미 실행 중인 서비스는 그대로 사용한다. 아래 기동 명령은 앱이 실행 중이지 않을 때 사용한다.

관측 비밀번호 준비와 Prometheus·Grafana 기동은 [개발 안내](../development/README.md#관측과-자동-기준선)를 먼저 따른다. 비밀번호는 Git 제외 비밀 파일로 관리한다. `.env`가 없으면 `.env.example`을 복사하며 기존 `.env`는 덮어쓰지 않는다.

```bash
test -f .env || cp .env.example .env
docker compose --profile kafka up -d postgres redis kafka-1 kafka-2 kafka-3
./gradlew :app:bootJar
SPRING_PROFILES_ACTIVE=local,baseline \
java -Xms256m -Xmx768m -jar app/build/libs/app-0.0.1-SNAPSHOT.jar
```

앱을 이미 다른 JVM 설정으로 실행했다면 실제 값을 `BASELINE_JVM_OPTIONS`에 기록한다. Worker 설정도 앱과 평가 도구에 같은 값을 전달한다. manifest의 JVM/Worker 값은 선언 출처를 표시하며 원격 앱 설정을 자동 조회한 값이 아니다. 아래 DB 연결은 기본 개발 계정 예이며 기존 설정이 다르면 실제 연결 값으로 바꾼다.

```bash
export DATABASE_URL='postgresql://limited_ticketing:limited_ticketing@localhost:5432/limited_ticketing'
export SPRING_PROFILES_ACTIVE='local,baseline'
export BASELINE_JVM_OPTIONS='-Xms256m -Xmx768m'
export COMPOSE_PROJECT_NAME='limited-ticketing'
export PROMETHEUS_URL='http://localhost:9090'
python3 load-test/k6/run-baseline.py --flow direct --scenario smoke --base-url http://localhost:8081
python3 load-test/k6/run-baseline.py --flow waiting --scenario smoke --base-url http://localhost:8081
```

요구 도구는 Python 3(Mac/Linux), k6, psql, Docker CLI와 Node.js(계약 테스트용)다. Prometheus는 `http://localhost:9090`, Grafana는 `http://localhost:3000`이며 Grafana 사용자는 admin, 최초 암호는 개발 안내의 로컬 비밀 파일에서 확인한다. 지표는 local/test 관리자 header로만 scrape한다. 관측 판정은 base URL의 로컬 포트를 `host.docker.internal:<port>`와 대조하고, 해당 flow URI의 HTTP counter 증가를 확인한다. warmup 뒤 5초 scrape 대기와 측정 전 5초 표본을 포함한다.

기존 DB의 활성 대기·결제 대기 구매가 있으면 측정을 거부한다. 해당 요청의 소유권과 상태를 확인해 정상 처리한 뒤 실행하며, 측정을 위해 기존 데이터를 일괄 삭제하지 않는다. 기존 환경에서 얻은 새 결과는 별도 환경에서 수집한 과거 1단계 결과와 조건을 구분한다.

대기 flow는 Kafka를 사용한다. 활성 요청 판정에는 WAITING·DISPATCHED·PROCESSING을 모두 포함하며, fixture 정리는 해당 구매 취소·판매 종료까지만 수행하고 대기 행·Outbox는 보존한다. API/Worker 분리 시 자동 도구는 API 대상 지표를 수집하므로 Worker metrics·기동 설정은 별도로 기록한다. 실행 중인 JAR를 재빌드하지 않는 절차는 [개발 안내](../development/README.md#api와-worker-역할-분리)를 따른다.

직접 flow는 `baseline` profile이 필요하다. 대기 flow만 실행하면 기본 `local`을 사용한다. 평가 도구의 profile·Worker·풀 환경 변수는 실제 앱 기동 설정과 일치시킨다.

### 시나리오와 판정

| scenario | 회원 수 / 재고 | rate / nominal duration | 목적 |
|---|---|---|---|
| smoke | 10 / 3 | 5/s · 2초 | 실제 연결·응답·수량 확인 |
| baseline | 1,000 / 100 | 100/s · 10초 | 두 flow를 각 3회 교차 비교 |
| observe-5000 | 5,000 / 500 | 500/s · 10초 | 목표 규모의 현재 처리 한계 관찰 |

`--flow`는 direct/waiting, `--scenario`는 위 값이다. 같은 DB에 동시 평가 도구를 실행하면 배타 lock으로 거부한다. 기존 활성 대기·미정리 구매가 있으면 새 실행을 거부한다. 각 run은 새 회원·판매·키·출력 디렉터리를 사용하며 API 상품 이름과 DB fixture가 일치하는지 먼저 확인한다.

```bash
for repetition in 1 2 3; do
  for flow in direct waiting; do
    python3 load-test/k6/run-baseline.py --flow "$flow" --scenario baseline --base-url http://localhost:8081
  done
done
```

종료 코드는 전체 PASS=0, 완결된 평가의 FAIL=1, 불완전/준비 실패=2다. 비 0 종료에서도 원본과 가능한 최종 검증을 보존한다. 각 실행의 `workload`, `integrity`, `observability`, `latency`를 따로 읽고, 지연 미달과 데이터 불일치를 합치지 않는다. 반복 명령은 각 run 결과를 따로 기록하며 한 실행의 실패를 다음 실행의 성공으로 덮지 않는다.

HTTP p95 목표안은 500ms, waiting 최종 결과 p95 목표안은 60초다. 실제 iterations·업무 응답 수·drop과 SQL 결과를 대조한다. 종료 경계는 마지막 목표 시작과 다음 시작의 중간에 두며, raw iterations가 목표와 다르면 FAIL이다. 빈 추가 iteration을 빼서 통과시키지 않는다.

### Git 관리와 원본 보존

| 대상 | Git 관리 | 역할 |
|---|---|---|
| JS 시나리오·응답 계약 | 포함 | 유입 조건과 업무 응답 판정을 재현 |
| Python 실행·판정 도구와 도구 테스트 | 포함 | fixture 준비, 결과 검증, 실패 원본 보존 |
| SQL | 포함 | 테스트 데이터와 정합성 검증 조건을 버전 관리 |
| 평가 보고서·실행 문서·관측 설정 | 포함 | 결과 해석과 실행 조건을 공유 |
| `results/`의 JSON·CSV·로그 | 제외 | 실행마다 생성되는 측정 원본을 로컬 보존 |
| `.env`·`.local/observability/secrets/` | 제외 | 로컬 환경 설정·Grafana 비밀번호 |

도구는 서버의 실행 코드에 포함되지 않지만, 같은 commit의 업무 코드와 측정 방법을 함께 재현하기 위해 버전 관리한다. 보고서만 남기면 부하·정확성 판정 조건을 복구할 수 없다.

`load-test/k6/results/<runId>/`의 manifest·fixture·summary·verification·samples·Prometheus 원본·stdout/stderr를 보존한다. manifest 자신을 제외한 artifact hash를 manifest에 기록한다. 원본은 Git 제외 대상이며 보고서에 manifest hash·실행 조건·보존 위치를 연결한다.

검증 snapshot은 **정리 전 상태**다. 원본 수집 후 실험 소유 회원의 PENDING_PAYMENT 구매만 취소하고 판매를 닫는다. 활성 대기가 남은 실패는 조사할 수 있도록 정리를 건너뛰고 다음 실행을 막는다. 이때 manifest·fixture와 API 상태를 확인해 해당 실행의 요청만 복구한다. 전체 DB/Redis 초기화나 다른 실행 데이터의 일괄 삭제로 우회하지 않는다.

도구 검사:

```bash
python3 -m unittest discover -s load-test/k6 -p test_run_baseline.py
node --test load-test/k6/baseline-contract.test.mjs
./gradlew check
```

아래 수동 시나리오는 특정 API·DB 락·Redis 슬롯을 따로 관찰할 때 사용한다. 전후 성능 비교와 최종 정합성 판정은 위 자동 기준선을 사용하고, 같은 실행을 수동·자동 결과로 중복 기록하지 않는다.

## 직접 예약 기준선

이 시나리오는 판매가 이미 `OPEN` 상태이고 재고가 설정된 상황에서, 서로 다른 회원이 동시에 예약을 요청하는 부하를 측정한다. 각 VU는 한 번만 요청하며, 같은 회원 ID를 재사용하지 않는다.

### 사전 조건

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

### 실행

`VUS`보다 `MEMBER_IDS`의 회원 수가 적으면 스크립트가 즉시 실패한다. 같은 회원을 중복 사용하면 재고 부족이 아닌 활성 구매 정책 충돌도 함께 측정되므로 허용하지 않는다.

```bash
mkdir -p load-test/k6/results

k6 run --summary-trend-stats="avg,min,med,max,p(90),p(95),p(99)" load-test/k6/sale-open.js \
  -e BASE_URL=http://localhost:8081 \
  -e SALE_PRODUCT_ID=1 \
  -e MEMBER_IDS="$MEMBER_IDS" \
  -e VUS=100 \
  --summary-export=load-test/k6/results/sale-open-summary.json
```

스크립트의 `setup` 단계는 각 회원으로 `POST /api/idempotency-keys`를 호출해 서버 발급 `PURCHASE` 키를 준비한다. 실제 동시 예약 구간에는 발급된 키만 사용하므로 키 발급 API의 응답 시간은 `reservation_duration`에 포함하지 않는다.

[summary-trend-stats](https://grafana.com/docs/k6/latest/using-k6/k6-options/reference/#summary-trend-stats)에 p99를 명시해 기본 summary에서 누락되는 백분위도 저장한다. 측정 값은 다음과 같이 해석한다.

- `reservation_successes`: 예약 성공 수. 초기 재고 이하이며, 유효한 요청 수가 재고 이상이면 초기 재고와 같아야 한다.
- `sold_out_responses`: 재고 소진으로 인한 409 수. 다른 업무 충돌이 없다면 `max(VUS - 초기 재고, 0)`와 같아야 한다.
- `unexpected_responses`: 201, 409 이외의 응답 수. 0이어야 한다.
- `reservation_duration`: 모든 예약 API 응답 시간. p95를 기록한다.
- `reservation_created_duration`: 성공한 201 예약 응답 시간. 재고 확보·구매 생성 경로의 p95를 기록한다.
- `reservation_conflict_duration`: 재고 소진 409 응답 시간. 재고 행 경합 후 실패 경로의 p95를 기록한다.

스크립트는 200(키 발급), 201(예약 성공), 409(재고 소진)을 시나리오상 정상 HTTP 응답으로 지정한다. 따라서 `http_req_failed`는 의도된 409이 아니라 실제 예기치 못한 HTTP 상태만 집계한다.

### DB 락과 낙관적 락 기록

부하 실행 전에 별도 터미널에서 PostgreSQL의 `Lock` 대기 세션 수를 초 단위 CSV로 기록한다. 애플리케이션과 측정 세션이 같은 로컬 DB를 사용해야 한다.

```bash
mkdir -p load-test/k6/results
{
  printf 'recorded_at,lock_waiters\n'
  while true; do
    lock_waiters="$(psql "$DATABASE_URL" -X -qAt -v ON_ERROR_STOP=1 -c "
      select count(*) from pg_stat_activity
      where datname = current_database() and wait_event_type = 'Lock'
    ")"
    printf '%s,%s\n' "$(date '+%Y-%m-%dT%H:%M:%S%z')" "$lock_waiters"
    sleep 1
  done
} | tee load-test/k6/results/postgresql-locks.csv
```

측정 종료 후 `Ctrl+C`로 기록을 중단하고 실행별 이름으로 CSV를 보관한다.

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

[준비 SQL](../../load-test/k6/sql/prepare-waiting-room.sql)은 새 회원, OPEN 판매 상품과 재고를 생성한다. 기본값은 회원 1,000명과 재고 100개다. SQL 결과로 환경 변수와 회원 ID 파일을 준비한다.

```bash
export DATABASE_URL='postgresql://limited_ticketing:limited_ticketing@localhost:5432/limited_ticketing'
export BASE_URL='http://localhost:8081'
mkdir -p load-test/k6/results

fixture_row="$(psql "$DATABASE_URL" -X -qAt -F '|' -v ON_ERROR_STOP=1 \
  -f load-test/k6/sql/prepare-waiting-room.sql)"
IFS='|' read -r TEST_RUN_ID MEMBER_COUNT INVENTORY_QUANTITY PRODUCT_ID SALE_PRODUCT_ID <<< "$fixture_row"
export TEST_RUN_ID MEMBER_COUNT INVENTORY_QUANTITY PRODUCT_ID SALE_PRODUCT_ID
export MEMBER_IDS_FILE="$(pwd)/load-test/k6/results/waiting-room-members-${TEST_RUN_ID}.json"

psql "$DATABASE_URL" -X -qAt -v ON_ERROR_STOP=1 -v test_run_id="$TEST_RUN_ID" \
  > "$MEMBER_IDS_FILE" <<'SQL'
SELECT JSON_AGG(id ORDER BY id)
FROM members
WHERE external_id LIKE ('waiting-room-' || :'test_run_id' || '-%');
SQL

docker compose exec -T redis redis-cli DEL \
  "waiting:sale:$SALE_PRODUCT_ID" \
  "processing:sale:$SALE_PRODUCT_ID" \
  "sequence:sale:$SALE_PRODUCT_ID"
```

DB를 초기화해 판매 ID가 재사용될 수 있으므로 해당 판매 ID의 이전 Redis 키를 정리한다. 이 준비 절차는 로컬 전용 DB와 Redis에서만 실행한다.

```bash
printf 'run=%s saleProduct=%s members=%s inventory=%s\n' \
  "$TEST_RUN_ID" "$SALE_PRODUCT_ID" "$MEMBER_COUNT" "$INVENTORY_QUANTITY"
test -f "$MEMBER_IDS_FILE"
jq 'length' "$MEMBER_IDS_FILE"
curl -fsS "$BASE_URL/api/sale-products/$SALE_PRODUCT_ID" | jq '.data'
```

기본 SQL에서는 회원 ID 파일 길이가 1,000이어야 한다. `MEMBER_IDS_FILE`은 k6의 파일 해석 위치와 무관하게 읽을 수 있도록 절대 경로를 사용한다.

규모를 바꿀 때는 준비 SQL 상단 `params` CTE의 `member_count`, `inventory_quantity`를 수정한다. IntelliJ/DataGrip에서 SQL을 실행했다면 결과 행의 `test_run_id`, `sale_product_id` 등을 위 환경 변수에 설정하고 회원 ID 파일 생성 및 Redis 키 정리부터 진행한다.

### 2. Smoke 테스트

먼저 10건으로 API 계약과 멱등성을 확인한다.

```bash
k6 run --summary-trend-stats="avg,min,med,max,p(90),p(95),p(99)" load-test/k6/waiting-room.js \
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

Smoke 이후에는 [테스트 데이터와 환경 변수 준비](#1-테스트-데이터와-환경-변수-준비)를 다시 수행해 Load용 fixture를 만든다. Stress·Spike는 필요한 회원 수 이상으로 SQL의 `member_count`를 바꾸고 매번 새 fixture를 준비한다.

| 단계 | 설정 | 총 진입 요청 |
|---|---|---:|
| Load | `RATE=100`, `DURATION_SECONDS=10` | 1,000 |
| Stress | `RATE=500`, `DURATION_SECONDS=10` | 5,000 |
| Spike | `RATE=2000`, `DURATION_SECONDS=5` | 10,000 |

예를 들어 1,000건 Load 테스트는 다음과 같이 실행한다.

```bash
k6 run --summary-trend-stats="avg,min,med,max,p(90),p(95),p(99)" load-test/k6/waiting-room.js \
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

Smoke는 소수 요청의 기능 확인이 목적이라 완화된 임계값을 사용하며, 성능 기준선으로 사용하지 않는다. 성능 비교는 애플리케이션 워밍업 후 Load/Stress/Spike를 각각 3회 수행하고 중앙값과 p95/p99를 기록한다. 목표 요청 수는 `iterations`와 `waiting_room_accepted_entries`가 모두 `RATE * DURATION_SECONDS`인지 확인한다.

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
- `RESERVED` 수는 초기 재고 이하이며, 수락된 유효 요청이 재고 이상이고 모든 처리가 완료되면 초기 재고와 같아야 한다.
- 나머지 정상 요청은 `SOLD_OUT`이어야 하고 `FAILED`는 0이어야 한다.
- `available + reserved + sold`는 항상 초기 재고와 같아야 한다.
- 결제를 수행하지 않았으므로 `reserved`는 `RESERVED` 구매 수, `sold`는 0이어야 한다.


## 측정 결과 관리

실행별 결과는 [평가 기록](../evaluations/load-testing/2026-10-15-existing-results.md)에 조건·원본 식별자·한계와 함께 남긴다. 기존 결과를 덮어쓰지 않는다. 다음 측정과 완료 기준은 [로드맵](../plans/roadmap.md)을 따른다.


## 단계별 평가 크기와 ZSET 단독 실험

매 단계 5,000건 요청을 반복하지 않는다. 기능·장애 회귀와 10건 Smoke를 기본으로 한다. 처리 경로 성능을 비교할 때 1,000건 반복 또는 5,000건을 선택하고, 최종 종합 평가에서는 계획한 집중·분산·조회 부하를 수행한다. 작은 Smoke는 5,000명 목표 달성 근거가 아니다.

실제 운영 claim Lua의 크기·batch 비용은 별도 실험한다. 앱을 중지한 시간에 기존 Redis를 사용하며 다른 EVAL이 겹치면 표본을 거부한다. 5,000 ZSET member는 5,000 HTTP 요청을 뜻하지 않는다.

```bash
REDIS_CONTAINER_ID=$(docker compose ps -q redis)
python3 load-test/k6/zset-claim.py --redis-container "$REDIS_CONTAINER_ID" --samples 20
```

도구는 `lab:<runId>:*` 키만 생성·정리하고 실제 [claim Lua](../../purchasewaiting/src/main/resources/redis/queue-claim.lua)를 읽는다. 1,000/5,000/10,000 member와 batch 1/10/50/100에서 각 5회 warmup 후 20회 측정한다. `INFO commandstats`의 EVAL usec 증가분으로 서버 명령 비용을 기록한다. TIME은 Lua 내부에서 고정되므로 두 TIME 호출의 차이는 사용하지 않는다. reset·Docker CLI·네트워크는 timed EVAL 밖이다. 매 표본의 이동 수·원래 수 복원·실험 키 제거를 assert로 확인한다.

이는 warm Redis 단독 실험이며 DB·예약·HTTP 완료 시간과 다르다. 서버 명령 비용은 호스트 경합에도 영향을 받으며 작은 표본으로 성능 보장을 하지 않는다. 실행별 CSV·메모리·script SHA와 manifest를 Git 제외 `results/`에 보존한다. [2단계 평가](../evaluations/load-testing/2026-10-16-stage-2-queue-consistency.md)에 실제 값과 잘못된 초기 계측을 함께 기록한다.
