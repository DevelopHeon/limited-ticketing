# ADR 0011 · Outbox Claim과 Kafka 전송·완료 transaction을 분리한다

- 상태: **Accepted**
- 결정일: 2026-10-19.
- [ADR 0010](0010-kafka-reservation-outbox.md)의 Outbox·at-least-once 계약은 유지하며, 발행 중 DB transaction을 보유하는 경계를 대체한다.

## 문제와 대안

최초 3단계 Publisher는 한 transaction에서 최대 10개를 잠그고 각 Kafka ACK를 순서대로 기다렸다. Broker 지연이 DB 연결·행 잠금 시간과 다음 polling을 늘리는 구조였다. 사용자는 기존 단계 완료 후 별도 리팩토링으로 비동기 전송·Claim/Lease·제한된 콜백 실행을 함께 요청했다. 처리량 향상을 실측했다는 뜻은 아니다.

| 대안 | 판단 |
|---|---|
| 기존 동기 발행 유지 | 구현이 단순하지만 Kafka 대기 동안 DB 자원을 보유한다 |
| Future만 도입 | transaction 종료 후 다른 Publisher가 같은 메시지를 즉시 선택하고, 무제한 완료 대기가 쌓일 수 있다 |
| Claim/Lease + 제한된 Future·콜백 | 짧은 DB 소유권 저장과 전송을 분리하고, 장애 시 재선택할 수 있어 채택한다 |
| `@Async`·`runAsync`로 전송 스레드 추가 | Kafka가 제공하는 Future 외에 전송 스레드·큐를 추가하므로 사용하지 않는다 |

## 결정

- 발행 Service는 `NEVER`로 이미 열린 transaction의 호출을 Claim·전송 전에 거부한다. `NOT_SUPPORTED`는 호출자의 연결·행 잠금을 해제하지 않으므로 사용하지 않는다. 내부 OutboxTransactionUseCase로 DB 동작을 호출하고 입력 Port 구현·Facade의 기존 역할은 유지한다.
- DB Claim·Complete·Failure는 별도 Service의 `REQUIRES_NEW`, timeout 5초다. 자기 호출로 transaction proxy를 우회하지 않는다.
- `FOR UPDATE SKIP LOCKED`와 조건부 UPDATE RETURNING으로 Claim ID·Lease·발행 시도를 커밋한다. Kafka 전송 중 DB 행 잠금은 유지하지 않는다.
- Output Port는 `CompletableFuture<Void>`다. KafkaTemplate의 완료 결과를 연결하고, DB 결과 기록만 유한 큐의 전용 Executor로 넘긴다.
- Semaphore는 Claim 전부터 DB 콜백 종료까지 점유한다. Claim 오류·콜백 거부·DB 오류에서 반환한다. DB 기록이 없으면 Lease 만료가 재전달 경로다.
- 완료·실패는 미발행·동일 Claim ID·미만료 Lease일 때만 기록한다. 오래된 콜백은 새 Claim과 발행 완료를 변경하지 않는다.
- V8을 추가하며 V7·측정 원본·과거 평가는 보존한다. 현재 기본값·운영 경계는 [구매 대기 설계](../architecture/purchase-waiting.md#outbox-비동기-발행-경계)가 소유한다.

## 비용과 한계

Claim 시점·전송 ACK·DB 완료 사이의 상태와 복구 경로가 늘어난다. ACK 후 DB 오류나 콜백 거부는 중복 발행을 만들 수 있으므로 Consumer의 멱등성과 PROCESSING 중복의 nack를 유지한다. Lease보다 오래 지연된 전송은 fencing으로 DB 완료를 거부하고 재전달할 수 있다.

완료 기록까지 In-flight 용량을 차지하므로 DB가 느리면 신규 발행도 제한된다. Semaphore는 인스턴스별이고 DB Claim은 여러 인스턴스의 소유권을 조정한다. 상품별 strict FIFO나 다중 Worker 처리량·장기 안정성·5,000건 성능 개선을 이번 구조만으로 보장하지 않는다. DB transaction timeout과 전체 작업 시간 상한도 구분한다.

## 검증 근거

Kafka 전송 중 열린 transaction을 기존 코드에서 재현하고 호출자 transaction이 있으면 Claim·전송 없이 거부하고, transaction 밖 정상 전송과 별도 완료 commit을 회귀로 검증한다. 동시 Claim·Lease 재선택·이전 콜백 차단·독립 commit은 실제 PostgreSQL로, ACK 뒤 완료 저장 오류와 재발행의 구매·재고 멱등성은 실제 Kafka로 검증한다. Permit 반환·동시 polling·실패 시각 backoff는 제어 가능한 Future와 Clock으로 검증한다. 실행 상태와 남은 항목은 [로드맵](../plans/roadmap.md)이 소유한다.
