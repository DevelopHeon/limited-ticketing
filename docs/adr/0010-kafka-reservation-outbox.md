# ADR 0010 · 예약 작업은 Transactional Outbox와 Kafka로 전달한다

- 상태: **Accepted**
- 결정일: 2026-10-19. 사용자가 3단계 스펙과 구현을 승인했다.
- ADR 0006의 DB 순번·입구 보호·token·lease는 유지하며, 로컬 Executor 제출을 Kafka 전달로 교체한다.

## 문제와 제약

Redis에서 입장한 작업을 프로세스 내부 Executor에만 제출하면 DB 커밋 후 프로세스가 종료될 때 전달 기록이 남지 않는다. Kafka partition·offset·재소비·복제를 학습하되, Broker 장애나 중복 소비가 구매·재고 정합성을 훼손하지 않아야 한다. Kafka 자체를 기존 지연의 해결 근거로 사용하지 않는다.

## 대안과 결정

| 대안 | 판단 |
|---|---|
| 기존 Executor 유지 | 단순하지만 프로세스 사이의 전달·offset·복제 학습 범위를 충족하지 못함 |
| 상태 저장 후 Kafka 직접 발행 | 상태 커밋과 발행 사이의 유실 구간이 남음 |
| DB/Kafka 분산 transaction | 이번 공유 DB·학습 범위보다 구성과 복구 비용이 큼 |
| DB Outbox + Kafka at-least-once | 상태와 전달 의도를 같은 DB transaction에 저장하고, 중복은 업무 멱등성으로 방어하므로 선택 |

DISPATCHED와 REQUESTED Outbox는 같은 transaction에 저장한다. Publisher는 Kafka ACK 후 PUBLISHED를 기록한다. Consumer는 실행 횟수를 먼저 커밋하고 업무 결과가 내구성 있게 기록된 뒤 offset을 저장한다. 총 3회 기술 실패 후 기존 구매를 조회해 복원하거나 FAILED·DLT Outbox를 저장한다. DB나 DLT가 불가하면 offset을 진행시키지 않는다.

JSON envelope v1에 명시적 version과 식별자·token을 둔다. 사람이 직접 읽으며 계약·partition·offset을 확인하기 쉬운 방식을 선택했다. Avro·Protobuf·Schema Registry는 도입하지 않으며 호환성 검증은 코드·테스트가 책임진다. 작업 요청과 구매 완료 사실 이벤트를 구분한다. 운영 구독자가 없던 Spring 구매 예약 이벤트는 제거한다.

Aggregate 영속성은 Domain Repository를 유지한다. 기술 전달 기록인 Outbox는 Application Output Port를 사용한다. Adapter는 직렬화·topic/key와 계약 변환을, infrastructure의 구체 JdbcRepository는 SQL과 행 매핑을 맡는다. SQL 중심의 polling·잠금·조건부 저장을 표현하기 위해 JdbcTemplate을 선택했으며 JPA보다 빠르다는 측정 주장은 하지 않는다.

## 결과와 비용

- 전달 보장은 at-least-once다. ACK 후 DB 반영 전 장애에서는 같은 eventId가 재발행될 수 있다.
- 상품별 key는 같은 상품을 한 partition에 모으므로 단일 인기 상품이 모든 partition의 처리량을 사용하지 못한다. 입장 순서와 병렬 완료 순서도 구분한다.
- 로컬 3 combined KRaft 노드·RF3·minISR2는 복제 학습 구성이다. 1 Broker 장애와 2 Broker 장애의 가용성 차이를 확인한다. 운영 배포 인증이나 전용 Controller 구성을 대신하지 않는다.
- 최초 구현의 Outbox polling과 잠금 중 유한한 Kafka 전송 대기는 DB 연결을 사용했다. 이 발행 경계는 후속 [ADR 0011](0011-asynchronous-outbox-claim-lease.md)에서 Claim/Lease와 비동기 전송·완료 transaction으로 대체한다. 무한 재시도·자동 poison 재입장은 피하고, 관리자 재처리 시 중복 구매와 이전 token을 검사한다.
- 같은 산출물의 API/Worker 역할 분리는 코드 모듈을 독립 서비스로 분리하는 결정이 아니다. 같은 DB와 공개 모듈 계약을 유지한다.

## 근거

- [처리·실패·offset·인수 계약](../plans/2026-10-19-stage-3-kafka-outbox.md)
- [현재 구매 대기 흐름](../architecture/purchase-waiting.md)
- [Kafka 공식 combined KRaft 예제](https://github.com/apache/kafka/blob/4.2.2/docker/examples/docker-compose-files/cluster/combined/plaintext/docker-compose.yml)
- [진행 상태와 검증 범위](../plans/roadmap.md)
