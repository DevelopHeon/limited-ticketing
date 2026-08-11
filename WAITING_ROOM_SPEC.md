# Redis ZSET Waiting Room Specification

## 1. Goal

판매 오픈 시 다수의 회원이 같은 상품의 구매 예약을 동시에 호출해도 모든 요청이 즉시 `Purchase`와 `Inventory` DB 경로에 진입하지 않게 한다. 회원은 상품별 대기열에 진입하고, 제한된 수의 요청만 순번대로 비동기 예약 처리한다.

이번 구현은 좌석 선택이 없는 한정 수량 상품을 기준으로 한다. 따라서 입장 허용된 회원이 추가 입력을 수행하는 대신, 워커가 기존 구매 예약 유스케이스를 자동 실행한다.

## 2. Baseline and Decision

기존 조건부 원자 UPDATE 방식은 재고 정합성을 보장한다. k6 측정에서도 재고 10개에 대해 성공 10건, 재고 소진 409건이 정확히 나왔다. 다만 동시 요청이 증가할수록 재고 소진 409 경로의 지연이 증가했고, 200 VU에서 PostgreSQL 락 대기도 관측됐다.

따라서 Redis를 DB 재고의 최종 저장소로 바꾸지 않는다. Redis는 다음 두 역할만 담당한다.

1. 상품별 대기 순서(ZSET)를 유지한다.
2. 상품별 처리 중 요청 수를 제한해 DB 예약 경로의 동시 진입을 제어한다.

DB의 `Purchase`, `Inventory`는 최종 정합성 기준으로 유지한다. Redis 장애, 워커 중단, 재시작에도 요청을 복구할 수 있도록 대기 요청의 상태는 PostgreSQL에도 영속화한다.

## 3. Scope

### 이번 단계

- `queue` 애플리케이션 모듈 추가
- `reservation_queue_entries` DB 테이블과 상태 전이
- Redis ZSET 기반 대기 순서와 처리 중 요청 집합
- Lua 스크립트로 상품별 제한 인원만 원자적으로 claim
- `POST /api/sale-products/{saleProductId}/queue-entries` 대기열 진입 API
- `GET /api/queue-entries/{queueEntryId}` 상태 조회 API
- 제한된 워커가 기존 `PurchaseCommandFacade.reserve()`를 호출하는 비동기 예약 처리
- Redis 등록 실패와 처리 중 워커 중단에 대비한 DB 기반 복구
- keyset cursor 기반 전체 WAITING 복원과 Redis 고아 항목 정리
- 종료된 판매의 Redis 키 수명 주기 정리

### 후속 단계

다음 단계의 순서와 완료 기준은 [11. 단계별 진행 계획](#11-단계별-진행-계획)을 따른다. Redis Stream, Kafka, 분산 락은 측정 결과 또는 서비스 분리 요구가 생기기 전에는 도입하지 않는다.

## 4. API Contract

### 대기열 진입

```text
POST /api/sale-products/{saleProductId}/queue-entries
X-Member-Id: 42
Idempotency-Key: purchase_42_...

202 Accepted
{
  "code": "SUCCESS",
  "data": {
    "queueEntryId": "UUID",
    "status": "WAITING",
    "position": 153
  }
}
```

같은 회원이 같은 `Idempotency-Key`로 재시도하면 기존 Queue Entry를 반환한다. 같은 회원이 같은 상품에 WAITING 또는 PROCESSING 상태로 다른 키를 사용해 중복 진입하면 충돌로 처리한다.

### 상태 조회

```text
GET /api/queue-entries/{queueEntryId}
X-Member-Id: 42
```

응답 상태는 `WAITING`, `PROCESSING`, `RESERVED`, `SOLD_OUT`, `FAILED`다. `WAITING` 상태에서만 Redis `ZRANK` 기반 현재 순번을 반환한다. 순번은 다른 회원이 처리되면서 변하므로 참고용 값이다.

## 5. Data Model

```text
reservation_queue_entries
- id UUID PK
- member_id
- sale_product_id
- purchase_request_key
- queue_sequence
- status
- purchase_id nullable
- failure_code nullable
- created_at
- processing_started_at nullable
- completed_at nullable
```

`id`는 Redis와 DB 저장 전부터 같은 식별자가 필요하므로 UUID를 사용한다. 일반 Aggregate의 DB IDENTITY 전략과 달리, 이 경우에는 영속화 전 식별자가 실제로 필요하다.

`member_id + purchase_request_key`는 멱등성을 보장한다. WAITING/PROCESSING 상태에 대한 `member_id + sale_product_id` partial unique index는 같은 상품의 중복 대기를 막는다. 구매 완료 후의 1인 1개 정책은 기존 `purchases.active_purchase_key` 제약이 최종 방어선이다.

## 6. Redis Keys

```text
waiting:sale:{saleProductId}      # ZSET: score=queue_sequence, member=queueEntryId
processing:sale:{saleProductId}   # ZSET: score=claim timestamp, member=queueEntryId
sequence:sale:{saleProductId}     # String: INCR sequence
```

대기 순번은 서버 시각이 아니라 Redis `INCR` 값으로 결정한다. 같은 밀리초에 들어온 요청도 안정적인 순서를 가진다.

처리 슬롯은 상품별 설정값만큼 동시에 열리므로, 먼저 claim된 묶음 안에서는 실제 DB 예약 완료 순서가 달라질 수 있다. ZSET은 **입장·처리 시작 순서**를 보장하며, 구매 성공의 엄격한 FIFO 순서가 요구되면 상품별 처리 슬롯을 1로 설정해야 한다.

Redis 접근 책임은 순번 발급용 `QueueSequenceGenerator`, 정상 처리용 `ReservationQueueStore`, 복구용 `ReservationQueueRecoveryStore`로 분리한다. 실제 구현체는 `ReservationQueueRedisKeys`를 공유하므로 키 문자열이 여러 클래스에 중복되지 않는다. waiting과 processing 사이의 claim은 Lua 원자 연산이므로 두 개의 독립 Store로 나누지 않는다.

## 7. Processing Flow

```text
대기열 진입
  -> Redis INCR로 sequence 발급
  -> DB QueueEntry(WAITING) 저장
  -> Redis waiting ZSET 등록

스케줄러
  -> Lua로 processing 수를 확인
  -> 여유 슬롯만큼 waiting ZSET의 앞 순번 claim
  -> processing ZSET으로 이동
  -> 제한된 TaskExecutor가 QueueEntry 처리

워커
  -> DB QueueEntry WAITING -> PROCESSING
  -> PurchaseCommandFacade.reserve()
  -> 성공: RESERVED + purchaseId
  -> 재고 부족: SOLD_OUT
  -> Redis processing ZSET에서 제거
```

Redis claim 뒤 워커가 중단될 수 있으므로 PROCESSING 상태가 설정 시간보다 오래 남으면 복구 작업이 WAITING으로 되돌려 Redis ZSET에 재등록한다. Redis 등록 자체가 실패한 WAITING DB 행도 복구 작업이 다시 등록한다.

복구 작업은 다음 순서로 실행한다.

```text
오래된 DB PROCESSING을 배치 단위로 모두 WAITING으로 전환
  -> 트랜잭션 커밋
  -> 상품별 (queue_sequence > cursor) keyset 조회로 전체 WAITING을 Redis에 보충
  -> Redis waiting/processing ID와 DB ID를 비교해 DB에 없는 ID 제거
  -> CLOSED 판매이며 DB에 WAITING/PROCESSING이 없으면 Redis 3개 키 삭제
```

`QUEUE_RECOVERY_BATCH_SIZE`의 기본값은 1,000이다. OFFSET을 사용하면 앞 항목의 상태 변경에 따라 누락이나 중복이 생길 수 있으므로, 상품별로 단조 증가하는 `queue_sequence`를 cursor로 사용한다.

## 8. Consistency Rules

- Redis ZSET은 순서와 동시 처리 제한용 보조 저장소다.
- 구매 생성과 재고 예약의 최종 정합성은 기존 PostgreSQL 트랜잭션이 보장한다.
- Redis 분산 락은 사용하지 않는다. 상품별 hot row의 대기를 Redis 락으로 옮기는 것은 처리량 개선이 아니다.
- Redis 재고 카운터도 이번 단계에서는 사용하지 않는다. DB 재고와 Redis 수량의 보상·복구 문제를 추가하지 않기 위해서다.
- 알려진 비즈니스 실패(재고 부족)는 Queue Entry의 최종 상태로 저장한다. 예상하지 못한 시스템 오류는 PROCESSING 상태를 유지해 복구 대상이 된다.
- Redis에서 claim한 ID가 DB에 없거나 이미 WAITING이 아니면 `claim()`은 `Optional.empty()`를 반환한다. Worker는 이를 오류로 반복하지 않고 Redis processing 항목을 제거한다.
- Redis 고아 항목은 PostgreSQL에 같은 UUID의 QueueEntry가 없는 waiting/processing 구성원이다. 복구 작업은 이를 두 ZSET에서 제거한다.
- 판매가 `CLOSED`이고 PostgreSQL에 WAITING/PROCESSING 항목이 없을 때 `waiting`, `processing`, `sequence` 키를 삭제한다. DB 초기화 등으로 판매 자체가 존재하지 않는 키도 삭제하며, 판매 중인 대기열 키는 삭제하지 않는다.

## 9. 복구 문제와 해결

로컬 부하 테스트 후 PostgreSQL만 초기화하고 Redis를 유지하면 이전 실행의 UUID가 ZSET에 남을 수 있었다. 재시작한 Worker가 이 ID를 claim한 뒤 필수 조회 Finder를 호출하면서 `Queue entry not found` 예외가 반복됐고, 예외 때문에 processing ZSET에서도 제거되지 않았다.

또한 기존 복구 조회는 `LIMIT 1000`만 사용했다. 첫 1,000건이 계속 WAITING이면 매 실행마다 같은 행만 조회하므로 1,001번째 이후 항목은 Redis에 복원되지 않는다. 이는 조회 성능 문제가 아니라 복구 누락을 일으키는 정확성 문제다.

다음과 같이 해결했다.

1. `claim()`은 DB 부재와 WAITING이 아닌 상태를 정상적인 claim 실패로 취급해 `Optional.empty()`를 반환한다.
2. Worker는 빈 결과를 받으면 해당 UUID를 processing ZSET에서 제거한다.
3. Recovery는 상품별 `queue_sequence` keyset cursor로 WAITING 행을 끝까지 순회한다.
4. Redis waiting/processing 구성원을 DB와 비교하고 DB에 없는 UUID를 제거한다.
5. 판매 종료 후 활성 QueueEntry가 없거나 판매 자체가 DB에 없으면 세 Redis 키를 삭제해 다음 실행에 상태가 남지 않게 한다.

환경명과 판매 세대 식별자를 Redis 키에 포함하는 방식은 이번 범위에 포함하지 않았다. 따라서 DB를 초기화해 ID가 재사용될 수 있는 로컬 테스트에서는 fixture 준비 스크립트가 해당 판매 ID의 세 키를 먼저 삭제한다.

## 10. Verification

1. 같은 요청 키 재시도는 Queue Entry 하나만 생성한다.
2. 다른 요청 키의 같은 회원/상품 중복 대기는 거부한다.
3. 제한 인원이 넘는 요청은 WAITING에 남고, 처리 중 수는 설정값을 넘지 않는다.
4. 워커가 예약을 완료하면 Purchase와 Inventory가 기존 정책대로 변경된다.
5. 재고 부족은 Queue Entry를 SOLD_OUT으로 바꾼다.
6. Redis ZSET에 없는 WAITING DB 행은 복구 배치보다 많아도 모두 다시 등록된다.
7. 오래된 PROCESSING DB 행은 WAITING으로 돌아가 다시 처리 가능해진다.
8. DB에 없는 Redis waiting/processing UUID는 복구 작업이 제거한다.
9. CLOSED 판매에 활성 QueueEntry가 없으면 Redis 세 키가 삭제된다.
10. Spring Modulith 검증으로 queue -> purchase/member/sale 공개 API 의존성만 허용한다.

## 11. 단계별 진행 계획

| 단계 | 상태 | 수행 내용 | 선택 이유와 완료 기준 |
|---|---|---|---|
| 0. DB 직접 예약 기준선 | 완료 | 조건부 원자 재고 갱신과 k6 동시 요청 측정 | Redis 도입 전에도 재고 정합성은 DB가 보장함을 확인했다. 성공·재고 소진 건수와 PostgreSQL 락 대기 값을 기준선으로 남긴다. |
| 1. 대기열 최소 수직 슬라이스 | 완료 | DB Queue Entry, Redis ZSET, Lua claim, 단일 프로세스 워커, 상태 조회와 복구 | DB는 최종 상태, Redis는 순서와 슬롯이라는 경계를 확정했다. Testcontainers에서 멱등성, 중복 차단, `RESERVED`/`SOLD_OUT`, 복구를 통과하면 완료다. |
| 2. 대기열 부하 측정 | 다음 | k6로 1,000/5,000/10,000명의 대기열 진입을 실행하고, 상태 조회는 별도 시나리오로 측정 | 직접 예약과 비교해 진입 API p95, 대기열 적재량, 워커 처리량, 최종 `RESERVED`/`SOLD_OUT` 수, PostgreSQL 락 대기를 기록한다. 대기열이 DB 진입을 설정한 슬롯 수로 제한하는지 확인한다. |
| 3. 입구 보호와 운영 지표 | 대기 | 상품별 최대 대기 인원, 회원·IP rate limit, Micrometer 지표와 대시보드 추가 | Redis와 애플리케이션 자체가 무제한 요청의 병목이 되지 않게 한다. 거부율, 대기 시간, 처리 중 수, 복구 횟수, Redis 오류를 관측할 수 있어야 한다. |
| 4. 사용자 대기 경험 | 대기 | SSE 또는 WebSocket 알림, 예상 순번/대기 시간, 재고 소진 시 남은 항목 일괄 `SOLD_OUT` 처리 | polling 비용을 줄이고 사용자에게 최종 상태를 빠르게 전달한다. 실제 클라이언트 요구가 생길 때 구현한다. |
| 5. 다중 인스턴스·서비스 분리 | 보류 | 리더 선출 또는 분산 스케줄링, Outbox, Redis Stream/Kafka 워커 분리 | 단일 프로세스의 처리량·가용성 한계가 측정되거나 독립 배포가 필요할 때만 진행한다. 이 단계 전까지는 DB와 Redis의 이중 상태 복구를 단순하게 유지한다. |

다음 구현 작업은 **2단계 대기열 부하 측정**이다. 기존 직접 예약 k6 스크립트와 별도로 대기열 진입·상태 조회 시나리오를 만들고, 같은 상품·같은 재고·동일한 동시 사용자 수로 결과를 비교한다.
