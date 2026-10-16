# ADR 0003 · 구매 대기를 업무 모듈로 분리하고 순서·슬롯만 Redis에서 관리

- 상태: **Accepted · 회고 작성**
- 정리일: 2026-10-15. 최초 결정일 미확인.
- 확인 기준: 기존 구매 대기 스펙, Redis ZSET 구현과 `b545618252b58b41e2107828409418bc3fb215cb`의 모듈 경계. 대안 비교는 정리 시점의 설명이다.

## 배경

기존 문서에는 직접 예약 부하에서 재고 행 경합과 지연을 관찰했다고 기록되어 있다. 구매 예약 전 요청을 제한해서 들여보내는 수명 주기가 필요해졌다. 이 업무는 접수·순번·진행 상태·처리 결과·중단 복구를 포함한다. `queue`라는 이름만으로는 기술 큐인지 구매 대기 업무인지 분명하지 않다.

## 비교한 대안

| 대안 | 이점 | 감수할 비용 |
|---|---|---|
| `purchase` 내부에서 대기까지 처리 | 모듈 간 호출이 줄어듦 | 예약 규칙과 대기 처리·복구 책임이 함께 변경됨 |
| 범용 기술 모듈 `queue` | 구현 기술을 중심으로 재사용 가능 | 구매 대기 상태·정책의 소유 업무가 불명확함 |
| `purchasewaiting` 업무 모듈 | 대기 수명 주기와 실제 예약의 책임이 구분됨 | 접수 상태와 구매 상태를 연결하는 공개 계약 필요 |
| Kafka 기반 독립 Worker | 소비자 확장과 전달 관측 가능 | 브로커·Outbox·전달 중복 처리 등 운영 책임 증가 |

## 결정

`purchasewaiting`이 구매 대기 접수·조회·claim·처리 결과·복구를 소유하고, 실제 예약은 `purchase`의 공개 Facade에 위임한다. `reservation`보다 대기 업무를 구체적으로 드러내는 이름을 사용하며 구매 예약 자체와 구분한다. Java 패키지와 Gradle 모듈은 변경하되 기존 HTTP·DB·Redis·설정 계약은 유지한다.

Redis INCR·ZSET·Lua는 순번과 상품별 처리 슬롯을 담당한다. PostgreSQL에는 Queue Entry 상태와 멱등성 키를 영속화한다. Worker는 같은 애플리케이션에서 실행하며 Redis 등록 누락과 중단된 처리는 DB 기준 복구한다. 현재 계약과 C3는 [구매 대기 설계](../architecture/purchase-waiting.md)가 소유한다.

## 결과와 비용

입구 대기와 실제 구매 예약을 독립적으로 변경할 수 있다. Redis가 유실되어도 DB에 남은 요청을 복구 대상으로 찾을 수 있다. 반면 DB·Redis 사이의 원자적 커밋은 없어 복구가 필수이며, Redis 순번 유실 시 순서 보존의 한계가 남는다. 여러 상품은 같은 TaskExecutor를 공유하므로 상품 간 공정성·전역 처리량도 별도 검증이 필요하다. FIFO 구매 성공이나 전체 장애 복구가 운영에서 입증되었다고 표현하지 않는다.

Kafka·Outbox는 [도입 조건](../plans/roadmap.md#메시징-확장-판단-기준)이 충족될 때 검토한다. 기존 수락 지표만으로 Redis의 성능 개선 효과를 확정하지 않는다.

## 근거와 검증

- [구현 스펙 원문](../archive/specs/2026-10-15-purchase-waiting-implementation.md): 당시 범위·선택 배경·복구 문제 기록.
- [purchasewaiting 메타데이터](../../purchasewaiting/src/main/java/com/example/limitedticketing/purchasewaiting/package-info.java), [공개 Facade](../../purchasewaiting/src/main/java/com/example/limitedticketing/purchasewaiting/application/port/in/ReservationQueueFacade.java): 업무 경계.
- [Redis Store](../../purchasewaiting/src/main/java/com/example/limitedticketing/purchasewaiting/infrastructure/redis/RedisReservationQueueStore.java), [Recovery Service](../../purchasewaiting/src/main/java/com/example/limitedticketing/purchasewaiting/application/command/service/ReservationQueueRecoveryService.java): claim·복구 구현.
- [대기열 통합 테스트](../../app/src/test/java/com/example/limitedticketing/integration/purchasewaiting/ReservationQueueIntegrationTest.java): 실제 DB·Redis의 멱등성·상태 전이·복구 검증.
- [기존 평가 기록](../evaluations/load-testing/2026-10-15-existing-results.md): 원본에서 확인한 수치와 비교 한계.

## 후속 변경 · 2026-10-16

[ADR 0006](0006-durable-queue-admission-and-attempts.md)에서 Redis INCR 순번을 PostgreSQL로 이전하고 시도 토큰·lease·입구/전역 상한을 적용했다. 위 Redis 순번 유실과 전역 실행 제한 설명은 최초 결정 당시의 비용이며 현재 계약은 구매 대기 설계를 따른다.
