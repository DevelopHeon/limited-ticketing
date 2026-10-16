# ADR 0005 · 입력 어댑터와 출력 어댑터의 역할에 따른 계층 배치

- 상태: **Superseded · 입력·출력 배치는 ADR 0007·0008로 대체**
- 결정일: 2026-10-15. 사용자와 계층 방향을 논의해 채택했다.
- 2단계에서 구매 대기·구매 만료 Scheduler와 내부 입력 Facade 분리를 적용했다. Kafka Consumer는 3단계 대상이며 아직 없다.

## 배경과 대안

결정 당시 `ReservationQueueWorker`는 infrastructure에서 스케줄 실행, DB 조회, Redis claim, 구매 처리와 슬롯 반환을 조율한다. Kafka와 캐시를 추가하면서 기술 이름만으로 모든 코드를 infrastructure에 두면 업무를 시작하는 입력과 외부 저장소를 사용하는 출력이 섞인다.

대안은 모든 기술 어댑터를 infrastructure에 두거나, 입출력 역할로 구분하는 것이다. Redis 관련 코드를 전부 presentation으로 옮기는 방식은 저장소 구현이 입력 계층에 섞이므로 채택하지 않는다.

## 결정

- presentation: HTTP Controller, Kafka Consumer, Scheduler. 요청/메시지 변환·실행 트리거와 입력 Facade 호출을 맡는다.
- application: 처리 대상·claim·예약·집계·복구의 업무 조율과 트랜잭션을 맡는다.
- infrastructure: Redis ZSET/캐시, Kafka Producer, JPA 등의 출력 Port/Repository 구현과 연결·Executor·Listener Container 설정을 맡는다.
- Consumer의 offset·ack·기술 retry는 어댑터/설정에, 업무 멱등성·상태 전이는 application/domain/DB에 둔다.
- 입력 어댑터는 Domain·Repository·출력 Port를 직접 호출하지 않는다. 필요한 입력 계약을 추가하며, 내부 실행 계약과 모듈 간 공개 Named Interface를 구분한다.

## 결과와 비용

HTTP·메시지·스케줄 입력이 동일한 업무 유스케이스를 사용한다. 패키지 이동만으로 해결되지 않아 현재 Worker의 책임을 분리하고 입력 Facade와 테스트를 보완해야 한다. ArchUnit 규칙을 느슨하게 해서 이동을 통과시키지 않는다. Modulith는 기존처럼 모듈 간 공개 계약을 검증한다.

현재 흐름은 [구매 대기 계약](../architecture/purchase-waiting.md)에 반영했다. 필요한 코드가 없으면 consumer/scheduler 패키지나 빈 Port를 선행 생성하지 않는다.

## 근거

- [현재 Scheduler](https://github.com/DevelopHeon/limited-ticketing/blob/a04cc26a1e4243246e31f3343f1a7190016c8630/purchasewaiting/src/main/java/com/example/limitedticketing/purchasewaiting/presentation/scheduler/ReservationQueueScheduler.java)
- [현재 ArchUnit 검사](../../app/src/test/java/com/example/limitedticketing/architecture/LayeringArchitectureTests.java)
- [구현 단계](../plans/2026-10-15-flash-sale-implementation-plan.md#5-단계별-산출물과-검토-지점)

## 후속 결정 · 2026-10-16

[ADR 0007](0007-purchasewaiting-ports-and-adapters.md)에 따라 purchasewaiting은 입력 Adapter도 infrastructure/adapter/in에 배치하고 Input Port → Driving Adapter → UseCase → Service 흐름으로 전환한다. 다른 모듈의 presentation 배치는 아직 유지한다. 위 내용은 최초 결정과 2단계 적용 당시의 기록이다.

전체 업무 모듈은 ADR 0008의 입력·출력 infrastructure 배치를 따른다. 이 문서의 presentation 배치는 최초 결정 당시의 기록으로 보존한다. 현재 구현 범위와 진행 상태는 [로드맵](../plans/roadmap.md)이 소유한다.
