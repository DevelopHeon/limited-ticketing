# ZSET·Kafka 질문과 수동 학습 실험

현재 구매 대기 구현을 작은 데이터로 관찰하는 실행 안내다. [주요 시퀀스](../architecture/business-flows.md), [대기 계약](../architecture/purchase-waiting.md), [발행 경계](../architecture/purchase-waiting.md#outbox-비동기-발행-경계)를 함께 읽는다. 부하 성능 비교는 [부하 테스트 안내](../performance/load-testing.md)를 따른다. 여기의 10건 실험은 5,000건 성능이나 운영 안정성을 입증하지 않는다.

학습은 **대기열·예약 전달 흐름이 갖춰진 시점에 먼저 작은 실험을 하고**, 캐시·랭킹을 추가한 뒤 확장하는 것을 권장한다. 이 문서의 순번·복구·ACK·재전달을 자기 말로 설명할 수 있으면 다음 구현을 검토한다. 이후 상품 조회 캐시에서 TTL·무효화·동시 miss, 랭킹에서 `ZINCRBY`·기간 합산·재구성을 배우고, 다중 프로세스 단계에서 rebalance·Broker 장애를 심화한다. 단계별 현재 상태와 다음 구현은 [로드맵](../plans/roadmap.md#단계별-진행)에서만 관리한다. 매 실험에 5,000건 부하를 반복할 필요는 없다.

## 1. 질문에 대한 답

### ZSET

| 질문 | 현재 구현의 답 |
|---|---|
| member·score에 무엇을 저장하는가? | `waiting:sale:{saleId}`의 member는 대기 항목 UUID 문자열, score는 PostgreSQL의 `queue_sequence`다. `processing:sale:{saleId}`는 같은 UUID와 Redis claim 시각의 epoch ms를 저장한다. 시도 token은 별도 HASH에 둔다. |
| 왜 DB 순번을 score로 쓰는가? | Redis가 유실되어도 순서를 복원하기 위해서다. 상품별 DB 순번 발급이 접수 transaction에 참여한다. 정확한 정수 score 범위를 위해 `1..2^53-1`로 제한한다. Redis 도착 시각·회원 ID·구매 성공 시각을 순번으로 쓰지 않는다. |
| 같은 score는 어떻게 정렬되는가? | member의 바이트 사전순이다. UUID 문자열 순서이며 등록 순서가 아니다. 새 접수의 순번은 정상적으로 증가하지만 동점 동작도 이해해야 한다. 복구 DB cursor는 `(queue_sequence, UUID)`다. |
| rank와 순번은 어떻게 다른가? | 순번은 발급된 고정 식별 순서이고 rank는 현재 ZSET 안의 0부터 시작하는 위치다. 앞 항목이 빠지면 뒤 항목의 rank는 줄지만 score는 그대로다. API position은 rank+1이며 WAITING에서만 조회한다. |
| 왜 Lua로 claim하는가? | 슬롯 여유 확인·앞 항목 선택·waiting 제거·processing 등록·token 저장 사이에 다른 claim이 끼어들지 않게 한다. Lua 원자성은 Redis 안의 경합을 막으며 DB와 Redis의 분산 transaction을 제공하지 않는다. DB도 별도로 전역·상품별 입장 상한을 확인한다. |
| Redis가 유실되면 어떻게 복원하는가? | DB WAITING을 순번·ID로 배치 조회하고 행 잠금 아래 현재 WAITING을 다시 확인해 원래 score로 등록한다. 종료 항목의 Redis 슬롯·고아 항목도 정리한다. DB 원장이 있어야 복구할 수 있다. |

Redis는 동점 member를 사전순으로 정렬하고, Lua 실행 중에는 다른 명령이 끼어들지 못한다. Lua가 길면 다른 요청도 기다리므로 배치 크기는 비용과 함께 봐야 한다. [ZADD 공식 설명](https://redis.io/docs/latest/commands/zadd/), [Lua 원자성](https://redis.io/docs/latest/develop/programmability/eval-intro/).

### Kafka

| 질문 | 현재 구현의 답 |
|---|---|
| 같은 상품 key를 쓰는 이유와 한계는? | `saleProductId` 문자열을 key로 써 같은 상품 요청을 같은 partition에 모은다. 현재 partition 수와 partitioner가 유지되는 조건이다. 한 Consumer Group에서 한 partition은 한 Consumer가 맡으므로 특정 인기 상품의 처리량이 partition 수만큼 늘지 않는다. 다른 상품도 hash 충돌로 같은 partition에 배치될 수 있다. |
| Producer ACK와 구매 완료는? | ACK는 Broker가 메시지를 수락했다는 전송 결과다. 그 뒤에도 Consumer 대기·DB 처리 시간이 남는다. DB Outbox의 `published_at`은 전달 확인이고 대기 `RESERVED`는 구매 예약 성공이다. 결제 승인 PAID는 다시 별도다. |
| DB commit 후 offset commit 전에 종료되면? | 같은 메시지가 다시 전달될 수 있다. offset은 다음에 읽을 위치이며, 업무 DB와 Kafka offset은 하나의 transaction으로 묶여 있지 않다. 재전달을 전제로 업무 멱등성을 지킨다. |
| 구매·재고 효과를 한 번만 적용하는 장치는? | DB Outbox eventId·대기 ID·상품 ID·token 대조, 대기 행 잠금·상태 검사, 구매 요청 키 유니크 제약, 구매 생성 여부에 따른 재고 예약, 재고 조건부 갱신이다. 완료·이전 token은 무시하고 실행 중 중복 BUSY는 nack한다. Producer idempotence만으로 DB 업무 중복을 막는 것은 아니다. |
| 두 Lease는 무엇을 보호하는가? | Outbox Claim Lease는 발행 소유권과 완료 콜백의 갱신 권한을 제한한다. 만료 시 같은 eventId 재발행이 가능하다. 실행 Lease는 Consumer가 PROCESSING을 시작한 뒤 해당 예약 시도를 복구할 기준이다. DISPATCHED에는 실행 Lease를 시작하지 않는다. 두 기본값이 30초여도 목적·시작 시점·복구 주체가 다르다. |

Kafka의 partition·Consumer Group·offset 개념은 [공식 소개](https://kafka.apache.org/42/getting-started/introduction/), group·offset 조회는 [운영 명령](https://kafka.apache.org/42/operations/basic-kafka-operations/)을 참고한다. 같은 partition의 로그 순서와 Outbox 병렬 발행·업무 완료의 FIFO를 동일하게 보지 않는다.

## 2. 환경과 관찰 모드

명령은 프로젝트 루트의 **Bash** 기준이다. 터미널 A는 앱, 터미널 B는 명령·조회에 사용한다. Mac 기본 zsh라면 터미널 B에서 `bash`를 먼저 실행한다. Docker Compose·JDK 17·Python 3·curl·jq·ripgrep(`rg`)가 필요하다. psql·redis-cli·Kafka CLI는 기존 서비스 컨테이너 안의 도구를 사용한다.

기존 checkout·Compose project·DB·Redis를 사용한다. 앱은 항상 하나만 실행하며 모드 변경 때 터미널 A에서 기존 앱을 종료한 후 재시작한다. 다른 활성 대기·미발행 Outbox가 있으면 먼저 소유자와 상태를 확인한다. 이 안내를 위해 전체 DB·Redis·offset을 초기화하지 않는다.

```bash
cd /Users/gimhuiheon/Documents/study/limited-ticketing
docker compose --profile kafka up -d postgres redis kafka-1 kafka-2 kafka-3
docker compose ps
```

앱이 실행 중이지 않을 때 JAR를 한 번 빌드한다. 이후 실험 중에는 같은 JAR를 사용하고 재빌드하지 않는다.

```bash
./gradlew :app:bootJar
```

| 모드 | 실행 기능 | 사용하는 단계 |
|---|---|---|
| A · API만 | HTTP 접수·조회, Worker 없음 | DB WAITING·Redis 등록 관찰 |
| B · 복구만 관찰 | 복구 실행, Dispatcher는 첫 실행 1시간 대기, Consumer 비활성 | Redis 누락 복원 |
| C · 발행까지만 | Dispatcher·Publisher 실행, Consumer 비활성 | DISPATCHED·Outbox·Kafka 관찰 |
| D · 전체 처리 | Dispatcher·Publisher·Consumer·복구 | 예약·중복·재시작 |

B~D는 관찰 도중 재고가 만료로 반환되는 것을 피하도록 만료 Scheduler의 첫 실행을 1시간 늦춘다. 구매 `expires_at`의 5분 규칙을 바꾸지는 않는다. 1시간 이상 유지하는 모드가 아니며 종료 후 일반 local 설정으로 복귀한다. 아래 실행 플래그는 해당 학습 실행에만 사용한다.

**터미널 A: 모드 A로 시작한다.**

```bash
SPRING_PROFILES_ACTIVE=local,api java -jar app/build/libs/app-0.0.1-SNAPSHOT.jar
```

**터미널 B: 공통 변수와 명령을 정의한다.** 이 터미널에서 이후 조회·fixture 명령을 이어서 수행한다.

```bash
cd /Users/gimhuiheon/Documents/study/limited-ticketing
export BASE_URL=http://localhost:8081
export TOPIC=limited-ticketing.purchasewaiting.reservation.v1
export GROUP=purchasewaiting-reservation-v1
set -o pipefail
set -o noclobber

study_psql() {
  docker compose exec -T postgres sh -c \
    'exec psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" "$@"' study-psql "$@"
}
study_redis() {
  docker compose exec -T redis redis-cli --raw "$@"
}
study_kafka() {
  local tool="$1"
  shift
  docker compose --profile kafka exec -T kafka-1 "/opt/kafka/bin/$tool" \
    --bootstrap-server kafka-1:9092 "$@"
}

curl -fsS "$BASE_URL/actuator/health" | jq .
study_redis PING
study_psql -c "SELECT count(*) AS active_entries FROM reservation_queue_entries WHERE status IN ('WAITING','DISPATCHED','PROCESSING');"
study_psql -c "SELECT count(*) AS pending_outbox FROM reservation_job_outbox WHERE published_at IS NULL;"
```

새 fixture를 만들기 전 `active_entries`와 `pending_outbox`는 0을 기준으로 한다. 0이 아니면 기존 데이터를 일괄 삭제하지 말고 해당 처리를 완료·복구한 뒤 시작한다. `noclobber`는 결과 파일 덮어쓰기를 막는다. 실패 응답 파일이 남아도 덮어쓰지 말고 원인을 확인한다.

## 3. 10명·재고 3개 데이터 준비

새 SQL 파일을 만들지 않고 [기존 fixture SQL](../../load-test/k6/sql/prepare-flash-sale.sql)을 재사용한다. 이 SQL은 ACTIVE 회원, Product, OPEN SaleProduct, Inventory를 생성하고 생성된 ID를 JSON으로 반환한다. 판매 기간은 한국 시간 기준 현재 1분 전부터 2시간 뒤다. HTTP 업무 로직을 대신하는 초기 fixture 준비 전용 SQL이다.

```bash
new_study_fixture() {
  STUDY_ID="study-$(python3 -c 'import uuid; print(uuid.uuid4().hex)')"
  STUDY_DIR="$(pwd)/load-test/k6/results/$STUDY_ID"
  mkdir -p load-test/k6/results
  mkdir "$STUDY_DIR" || return
  study_psql -qAt -v run_id="$STUDY_ID" -v member_count=10 -v inventory_quantity=3 \
    < load-test/k6/sql/prepare-flash-sale.sql > "$STUDY_DIR/fixture.json" || return
  SALE_ID="$(jq -er '.saleProductId' "$STUDY_DIR/fixture.json")" || return
  export STUDY_ID STUDY_DIR SALE_ID
  jq . "$STUDY_DIR/fixture.json"
}

enqueue_study_members() {
  jq -r '.members[].memberId' "$STUDY_DIR/fixture.json" |
  while IFS= read -r member_id; do
    request_key="$(curl -fsS -X POST "$BASE_URL/api/idempotency-keys" \
      -H "X-Member-Id: $member_id" -H 'Content-Type: application/json' \
      -d '{"operation":"PURCHASE"}' | jq -er '.data.idempotencyKey')" || return
    curl -fsS -X POST "$BASE_URL/api/sale-products/$SALE_ID/queue-entries" \
      -H "X-Member-Id: $member_id" -H "Idempotency-Key: $request_key" \
      > "$STUDY_DIR/entry-$member_id.json" || return
    jq -e '.data.queueEntryId' "$STUDY_DIR/entry-$member_id.json" || return
  done
}

new_study_fixture
curl -fsS "$BASE_URL/api/sale-products/$SALE_ID" | jq '.data'
enqueue_study_members
```

이후 모든 업무 SQL은 `sale_id`를 조건으로 제한한다. 새 실험은 `new_study_fixture`로 새 회원·판매·결과 경로를 만든다. 기존 fixture·측정 원본·manifest·hash를 덮어쓰지 않는다. 명령 실패 시 fixture JSON과 HTTP 응답을 확인한 뒤 진행한다.

**공통 확인 SQL**을 함수로 정의한다. 각 실험 전후에 `show_study_state`를 호출한다.

```bash
show_study_state() {
  study_psql -v sale_id="$SALE_ID" <<'SQL'
SELECT id, member_id, queue_sequence, status, execution_attempts,
       processing_token, lease_until, purchase_id, failure_code
FROM reservation_queue_entries
WHERE sale_product_id = :'sale_id'::bigint
ORDER BY queue_sequence, id;

SELECT status, count(*) FROM reservation_queue_entries
WHERE sale_product_id = :'sale_id'::bigint GROUP BY status ORDER BY status;

SELECT event_id, event_type, message_key, publish_attempts,
       claim_id, claim_until, published_at, failure_code
FROM reservation_job_outbox
WHERE sale_product_id = :'sale_id'::bigint ORDER BY created_at, event_id;

SELECT id, member_id, purchase_request_key, status, quantity, expires_at
FROM purchases WHERE sale_product_id = :'sale_id'::bigint ORDER BY id;

SELECT available_quantity, reserved_quantity, sold_quantity,
       available_quantity + reserved_quantity + sold_quantity AS total
FROM inventories WHERE sale_product_id = :'sale_id'::bigint;
SQL
}
show_study_state
```

모드 A의 기대값: **WAITING 10, Outbox 0, 구매 0, 재고 available=3·reserved=0·sold=0·total=3**. 아래 실험 4까지는 이 fixture를 이어서 사용한다.

## 4. 실험 1 · score·동점·rank와 원자 claim

먼저 실제 대기열을 읽기만 한다.

```bash
study_redis ZRANGE "waiting:sale:$SALE_ID" 0 -1 WITHSCORES
study_redis ZCARD "waiting:sale:$SALE_ID"
study_redis ZCARD "processing:sale:$SALE_ID"
study_redis HGETALL "processing-tokens:sale:$SALE_ID"
MEMBER_ID="$(jq -r '.members[0].memberId' "$STUDY_DIR/fixture.json")"
ENTRY_ID="$(jq -r '.data.queueEntryId' "$STUDY_DIR/entry-$MEMBER_ID.json")"
study_redis ZSCORE "waiting:sale:$SALE_ID" "$ENTRY_ID"
study_redis ZRANK "waiting:sale:$SALE_ID" "$ENTRY_ID"
curl -fsS "$BASE_URL/api/queue-entries/$ENTRY_ID" -H "X-Member-Id: $MEMBER_ID" | jq '.data'
```

모드 A에서 순차 접수했으므로 ZCARD=10, 첫 항목 score=1·rank=0·API position=1을 기대한다. 실제 구매 성공 순서는 이 관찰로 보장하지 않는다.

**수정 실험은 업무 키 대신 고유한 lab 키에서 수행한다.**

```bash
LAB_WAITING="lab:$STUDY_ID:waiting"
LAB_PROCESSING="lab:$STUDY_ID:processing"
LAB_TOKENS="lab:$STUDY_ID:tokens"
study_redis ZADD "$LAB_WAITING" 10 b 10 a 20 c
study_redis ZRANGE "$LAB_WAITING" 0 -1 WITHSCORES
study_redis ZRANK "$LAB_WAITING" c
study_redis ZREM "$LAB_WAITING" a
study_redis ZRANK "$LAB_WAITING" c
study_redis ZSCORE "$LAB_WAITING" c
study_redis OBJECT ENCODING "$LAB_WAITING"
study_redis MEMORY USAGE "$LAB_WAITING"
```

처음 순서는 a,b,c다. a 제거 후 c의 rank는 2→1이지만 score는 20이다. 작은 ZSET이 언제나 skiplist라고 가정하지 말고 실제 encoding을 읽는다. [Redis encoding 설명](https://redis.io/docs/latest/commands/object-encoding/).

실제 [claim Lua](../../purchasewaiting/src/main/resources/redis/queue-claim.lua)를 lab 키에 실행한다. `--eval`의 세 key 뒤 쉼표와 인자는 각각 최대 슬롯·batch·claim 시각·token이다.

```bash
docker compose exec -T redis redis-cli --raw \
  --eval /dev/stdin "$LAB_WAITING" "$LAB_PROCESSING" "$LAB_TOKENS" , 1 1 1000 token-a \
  < purchasewaiting/src/main/resources/redis/queue-claim.lua
study_redis ZRANGE "$LAB_WAITING" 0 -1 WITHSCORES
study_redis ZRANGE "$LAB_PROCESSING" 0 -1 WITHSCORES
study_redis HGETALL "$LAB_TOKENS"
docker compose exec -T redis redis-cli --raw \
  --eval /dev/stdin "$LAB_WAITING" "$LAB_PROCESSING" "$LAB_TOKENS" , 1 1 2000 token-b \
  < purchasewaiting/src/main/resources/redis/queue-claim.lua
```

첫 claim은 b를 선택하고 processing에 score=1000·token-a로 기록한다. 슬롯 1개가 이미 차 있으므로 두 번째 claim은 빈 결과다. 잘못된 token으로 반환해도 슬롯이 유지되는지 확인한다.

```bash
docker compose exec -T redis redis-cli --raw --eval /dev/stdin "$LAB_PROCESSING" "$LAB_TOKENS" , b wrong-token \
  < purchasewaiting/src/main/resources/redis/queue-release.lua
study_redis ZCARD "$LAB_PROCESSING"
docker compose exec -T redis redis-cli --raw --eval /dev/stdin "$LAB_PROCESSING" "$LAB_TOKENS" , b token-a \
  < purchasewaiting/src/main/resources/redis/queue-release.lua
study_redis ZCARD "$LAB_PROCESSING"
study_redis DEL "$LAB_WAITING" "$LAB_PROCESSING" "$LAB_TOKENS"
```

반환 결과는 각각 0·1, processing 수는 각각 1·0을 기대한다. 이 실험은 Redis 연산을 확인하며 DB와 함께하는 전체 입장 원자성을 입증하지 않는다.

## 5. 실험 2 · Redis 대기 키 유실과 DB 복원

모드 A에서 `show_study_state`로 **해당 상품 WAITING 10·Outbox 0**을 먼저 확인한다. 이 조건일 때만 실험 소유 상품의 waiting 키를 지운다.

```bash
study_redis DEL "waiting:sale:$SALE_ID"
study_redis ZCARD "waiting:sale:$SALE_ID"
curl -fsS "$BASE_URL/api/queue-entries/$ENTRY_ID" -H "X-Member-Id: $MEMBER_ID" | jq '.data'
show_study_state
```

DB는 WAITING 10을 유지하고 API 위치는 null이다. **터미널 A의 API 앱을 종료하고 모드 B로 재시작한다.**

```bash
SPRING_PROFILES_ACTIVE=local java -jar app/build/libs/app-0.0.1-SNAPSHOT.jar \
  --spring.kafka.listener.auto-startup=false \
  --limited-ticketing.queue.worker.initial-delay=3600000 \
  --limited-ticketing.purchase.expiration.initial-delay=3600000
```

복구 Scheduler는 기본 주기로 실행되고 Dispatcher는 첫 실행을 1시간 기다린다. 앱 시작·복구가 끝난 뒤 다음을 확인한다.

```bash
study_redis ZCARD "waiting:sale:$SALE_ID"
study_redis ZRANGE "waiting:sale:$SALE_ID" 0 -1 WITHSCORES
study_psql -v sale_id="$SALE_ID" <<'SQL'
SELECT last_sequence FROM reservation_queue_sequences WHERE sale_product_id = :'sale_id'::bigint;
SQL
show_study_state
```

ZCARD=10, 원래 순번 1..10, DB last_sequence=10을 기대한다. Redis 전체 초기화나 processing token 삭제로 대신 실험하지 않는다.

## 6. 실험 3 · 입장·Broker ACK·Consumer 완료 분리

**터미널 A의 앱을 종료하고 모드 C로 시작한다.**

```bash
SPRING_PROFILES_ACTIVE=local java -jar app/build/libs/app-0.0.1-SNAPSHOT.jar \
  --spring.kafka.listener.auto-startup=false \
  --limited-ticketing.purchase.expiration.initial-delay=3600000
```

Dispatcher와 Publisher가 처리할 때까지 기다리며 터미널 B에서 확인한다.

```bash
show_study_state
study_redis ZCARD "waiting:sale:$SALE_ID"
study_redis ZCARD "processing:sale:$SALE_ID"
study_kafka kafka-topics.sh --describe --topic "$TOPIC"
study_kafka kafka-consumer-groups.sh --describe --group "$GROUP"
study_kafka kafka-consumer-groups.sh --describe --group "$GROUP" --members --verbose
```

기대값: **DISPATCHED 10·Outbox published_at 있음·execution_attempts=0·구매 0·재고 available=3**. Redis waiting=0·processing=10이다. Producer ACK 이후에도 구매가 생성되지 않는 것을 확인한다. 최초 실행의 group offset은 `-`이거나 group이 아직 없을 수 있다. 이를 측정된 lag=0으로 바꾸어 해석하지 않는다.

별도 관찰용 Consumer Group으로 로그를 읽는다. 업무 group ID를 콘솔 Consumer에 사용하지 않는다.

```bash
study_kafka kafka-console-consumer.sh --topic "$TOPIC" \
  --group "$STUDY_ID-observer" --from-beginning --timeout-ms 10000 \
  --consumer-property enable.auto.commit=false \
  --property print.partition=true --property print.offset=true --property print.key=true
```

10초 유휴 timeout으로 끝나는 로그는 메시지 처리 실패와 구분한다. 기존 메시지도 출력되므로 현재 `saleProductId`와 fixture의 대기 ID를 대조한다. 같은 상품의 key가 같은 partition으로 모이는지 확인한다.

**모드 C를 종료하고 모드 D로 시작한다.**

```bash
SPRING_PROFILES_ACTIVE=local java -jar app/build/libs/app-0.0.1-SNAPSHOT.jar \
  --limited-ticketing.purchase.expiration.initial-delay=3600000
```

```bash
show_study_state
study_kafka kafka-consumer-groups.sh --describe --group "$GROUP"
study_redis ZCARD "processing:sale:$SALE_ID"
curl -fsS -H 'X-Admin-Id: admin' "$BASE_URL/actuator/prometheus" |
  rg 'reservation_|kafka_consumer|hikaricp_connections'
```

완료 후 **RESERVED 3·SOLD_OUT 7·구매 3·available=0·reserved=3·sold=0·total=3**을 기대한다. Redis 슬롯 정리는 DB 결과보다 늦을 수 있으므로 결국 0이 되는지 확인한다. 사용한 partition에서 committed offset이 log end에 도달하는지 본다. 상태별 API 응답도 확인한다.

## 7. 실험 4 · 같은 메시지 재전송과 업무 멱등성

모드 D에서 앞 실험이 완료된 뒤 수행한다. RESERVED 한 건의 **저장된 원본 payload**를 선택한다. 임의 JSON은 DB Outbox 계약 검증에서 거부되므로 중복 업무 실험으로 사용할 수 없다.

```bash
study_psql -qAt -F '|' -v sale_id="$SALE_ID" > "$STUDY_DIR/duplicate-message.txt" <<'SQL'
SELECT o.message_key, o.payload
FROM reservation_job_outbox o
JOIN reservation_queue_entries e ON e.id = o.waiting_entry_id
WHERE o.sale_product_id = :'sale_id'::bigint
  AND o.event_type = 'PurchaseReservationRequested' AND e.status = 'RESERVED'
ORDER BY e.queue_sequence LIMIT 1;
SQL
test -s "$STUDY_DIR/duplicate-message.txt"
show_study_state
study_kafka kafka-console-producer.sh --topic "$TOPIC" \
  --property parse.key=true --property 'key.separator=|' \
  --producer-property acks=all --producer-property enable.idempotence=true \
  < "$STUDY_DIR/duplicate-message.txt"
study_kafka kafka-consumer-groups.sh --describe --group "$GROUP"
show_study_state
```

처리가 끝나면 offset은 진행하지만 **구매 3·reserved=3·각 항목 execution_attempts=1**이 유지된다. 콘솔 재전송은 Outbox Publisher를 거치지 않으므로 Outbox publish_attempts도 증가하지 않는다. 이 값과 Consumer 실행 횟수를 혼동하지 않는다.

이 실험은 DB commit 이후의 재전달 효과를 확인한다. 정확히 DB commit과 offset commit 사이에서 프로세스를 종료한 증거는 아니다. 그 지점은 실험 6에서 구분한다.

## 8. 실험 5 · Worker 없는 접수 후 재시작·상품별 partition

앞 fixture의 활성 대기가 0인지 확인하고 터미널 A를 모드 A로 바꾼다. 새 fixture를 만들어 접수한다. API만 실행하므로 Worker 역할이 없는 동안 WAITING이 쌓인다.

```bash
new_study_fixture
enqueue_study_members
show_study_state
```

모드 A를 종료하고 모드 D로 바꾸면 DB·Redis의 대기가 입장·발행·예약 처리되는지 확인한다. 이것은 **Worker 부재 중 접수 보존과 시작 후 처리** 실험이며, 실행 중 PROCESSING을 강제 중단한 실험과 구분한다.

다른 상품의 key 분포도 보려면 매번 새 fixture로 같은 절차를 반복한다. `SALE_ID`와 `STUDY_DIR`가 새 값으로 바뀌므로 각 fixture JSON·응답을 따로 보존한다. 상품 2~3개로도 같은 partition에 충돌할 수 있으며 균등 분산을 보장하지 않는다. 관찰용 콘솔 Consumer에서 상품별 key→partition을 기록한다. partition 수를 바꾸는 실험은 기존 key 매핑을 바꾸므로 이 실습에서는 하지 않는다.

## 9. 실험 6 · DB commit과 offset commit 사이 종료

이 경계는 짧아 임의 시점의 `kill`만으로 재현 여부를 알 수 없다. 정확한 중단을 보려면 IDE의 조건부 breakpoint를 사용한다. 다른 활성 요청이 없는 상태에서 모드 A로 **새 fixture를 만들고 회원 한 명만 접수**한다.

```bash
new_study_fixture
MEMBER_ID="$(jq -r '.members[0].memberId' "$STUDY_DIR/fixture.json")"
REQUEST_KEY="$(curl -fsS -X POST "$BASE_URL/api/idempotency-keys" \
  -H "X-Member-Id: $MEMBER_ID" -H 'Content-Type: application/json' \
  -d '{"operation":"PURCHASE"}' | jq -er '.data.idempotencyKey')"
ENTRY_ID="$(curl -fsS -X POST "$BASE_URL/api/sale-products/$SALE_ID/queue-entries" \
  -H "X-Member-Id: $MEMBER_ID" -H "Idempotency-Key: $REQUEST_KEY" | jq -er '.data.queueEntryId')"
printf 'entry=%s member=%s sale=%s\n' "$ENTRY_ID" "$MEMBER_ID" "$SALE_ID"
```

모드 A를 종료하고 터미널 A에서 디버그 가능한 모드 D를 시작한다. Listener 첫 실행을 IDE가 연결될 때까지 보장해 멈추는 기능은 없으므로, **JVM 시작을 suspend=y로 멈춘다.**

```bash
SPRING_PROFILES_ACTIVE=local java \
  -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=127.0.0.1:5005 \
  -jar app/build/libs/app-0.0.1-SNAPSHOT.jar \
  --spring.kafka.listener.concurrency=1 \
  --limited-ticketing.purchase.expiration.initial-delay=3600000
```

IDE에서 localhost:5005에 Remote JVM으로 연결한다. [ReservationKafkaConsumer.receive](../../purchasewaiting/src/main/java/com/example/limitedticketing/purchasewaiting/infrastructure/adapter/in/messaging/ReservationKafkaConsumer.java)의 `acknowledgment.acknowledge()` 호출 **직전**에 breakpoint를 설정한다. 조건은 `record.value().contains("대기 UUID")`로 실제 ENTRY_ID를 넣고 Suspend는 **Thread**로 설정한다. JVM을 resume한다.

breakpoint에 도달하면 터미널 B에서 다음을 확인한다.

```bash
show_study_state
study_kafka kafka-consumer-groups.sh --describe --group "$GROUP"
jcmd -l
```

DB는 RESERVED 1·구매 1·reserved=1이다. 관찰용 콘솔 Consumer로 해당 eventId의 partition·offset을 찾아 group committed offset이 그 레코드 offset보다 크지 않은지 확인한다. committed offset은 다음 위치이므로 레코드 offset+1 이상이면 이미 그 레코드를 지나간 것이다. 비교할 offset·DB snapshot을 먼저 기록하고 breakpoint를 해제하거나 resume하지 않는다.

`jcmd -l`과 앱 로그로 **해당 학습 JVM의 PID를 직접 대조**한 뒤 그 PID에만 강제 종료한다. 아래는 확인한 PID를 입력해야 하는 명령이다.

```bash
read -r -p '확인한 학습 JVM PID: ' STUDY_APP_PID
ps -p "$STUDY_APP_PID" -o pid=,command=
case "$STUDY_APP_PID" in
  ''|*[!0-9]*|0|1) printf '학습 JVM의 양의 PID를 확인해야 합니다.\n' ;;
  *)
    if ps -p "$STUDY_APP_PID" -o command= | rg -q 'app-0\.0\.1-SNAPSHOT\.jar'; then
      kill -KILL "$STUDY_APP_PID"
    else
      printf '대상 JAR가 아니므로 종료하지 않았습니다.\n'
    fi
    ;;
esac
```

다시 일반 모드 D로 시작하고 breakpoint 없이 처리한다. 같은 이벤트가 재전달되어도 **구매 1·reserved=1·execution_attempts=1**이 유지되고 group offset이 진행해야 한다. 중단 전 DB·offset을 확인하지 못했으면 이 경계를 검증했다고 기록하지 않는다. 이 안내 작성 시 이 수동 장애 실험은 실행하지 않았다.

Outbox ACK 후 완료 기록 실패는 다른 경계다. 이 경계와 중복·offset 회귀는 다음 기존 테스트로도 확인할 수 있다. 테스트는 Testcontainers를 사용하며 실제 로컬 Broker 장애 실험과 구분한다.

```bash
./gradlew :app:test \
  --tests '*PurchaseWaitingKafkaIntegrationTest.httpToKafkaReservationAndDuplicateDelivery' \
  --tests '*PurchaseWaitingKafkaIntegrationTest.brokerAckBeforePublisherRollbackAllowsSafeDuplicatePublish' \
  --tests '*PurchaseWaitingKafkaIntegrationTest.failedDltSendDoesNotLoseTheOriginalRecord'
```

## 10. 종료·정리와 학습 기록

각 fixture의 결과를 먼저 보존한다. `fixture.json`은 생성 소유권·초기 수량의 근거이며 같은 디렉터리의 원본을 덮어쓰지 않는다. 실험 3·4·5는 10건, 실험 6은 1건 접수라는 조건도 함께 기록한다.

| 기록 | 확인할 항목 |
|---|---|
| 실행 조건 | commit, 앱 모드·플래그, fixture 경로·판매 ID, 실제 접수 수 |
| Redis | waiting·processing 수, score·rank, token, encoding·메모리 |
| Kafka | 상품 key→partition, 레코드 offset·committed offset·lag, 재전송 횟수 |
| DB | 상태별 대기 수·실행 횟수·구매 수·재고 합, Outbox ACK/Claim·시도 수 |
| 해석 | 예상과 실제 차이, 장애 지점의 확인 여부, 아직 검증하지 못한 조건 |

정리는 **해당 SALE_ID의 활성 대기와 미발행 Outbox가 0일 때** 수행한다. WAITING·DISPATCHED가 남았다면 모드 D로 처리·복구하고 원인을 먼저 확인한다. 실패 원장을 삭제해서 통과시키지 않는다.

```bash
study_psql -v sale_id="$SALE_ID" <<'SQL'
SELECT count(*) AS active_entries FROM reservation_queue_entries
WHERE sale_product_id = :'sale_id'::bigint AND status IN ('WAITING','DISPATCHED','PROCESSING');
SELECT count(*) AS pending_outbox FROM reservation_job_outbox
WHERE sale_product_id = :'sale_id'::bigint AND published_at IS NULL;
SQL
```

접수·구매·Outbox 원장은 남긴다. 해당 fixture의 결제 대기 구매만 정상 취소 API로 재고를 반환하고 판매를 닫는다. 여러 fixture를 썼다면 **각 결과 디렉터리로 STUDY_DIR를 바꾸고**, `SALE_ID="$(jq -er '.saleProductId' "$STUDY_DIR/fixture.json")"`로 판매 ID를 다시 읽어 반복한다. 각 디렉터리의 취소 대상 파일도 한 번만 생성한다.

```bash
study_psql -qAt -F '|' -v sale_id="$SALE_ID" > "$STUDY_DIR/cancel-targets.txt" <<'SQL'
SELECT member_id, id FROM purchases
WHERE sale_product_id = :'sale_id'::bigint AND status = 'PENDING_PAYMENT' ORDER BY id;
SQL
while IFS='|' read -r member_id purchase_id; do
  curl -fsS -X POST "$BASE_URL/api/purchases/$purchase_id/cancel" -H "X-Member-Id: $member_id"
done < "$STUDY_DIR/cancel-targets.txt"
curl -fsS -X POST "$BASE_URL/api/admin/sale-products/$SALE_ID/close" -H 'X-Admin-Id: admin' | jq .
show_study_state
```

학습용 앱을 종료한 뒤 일반 `local` 실행 설정으로 복귀한다. 전체 DB·Redis 초기화, 업무 Consumer Group offset reset·삭제, 기존 측정 원본 변경은 이 실험 절차에 없다.

ZSET 크기·배치 비용을 추가로 측정하려면 별도 lab 키만 쓰는 [기존 claim 도구](../../load-test/k6/zset-claim.py)를 사용한다. Redis EVAL 통계 차이를 재므로 앱·다른 EVAL 실행을 멈춘 상태에서 수행하며, HTTP·DB·네트워크 지연이나 전체 처리량의 측정으로 해석하지 않는다.

```bash
python3 load-test/k6/zset-claim.py --redis-container "$(docker compose ps -q redis)" --samples 20
```

이 문서의 SQL·명령은 코드·migration과 대조하고 문서 링크·Bash 구문을 검사한다. 전체 수동 시나리오의 실제 결과는 실행 후 별도로 기록해야 한다. 작성만으로 실험 성공이나 성능 목표 달성을 주장하지 않는다.
