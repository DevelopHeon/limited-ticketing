# ADR 0002 · PostgreSQL을 구매·재고의 최종 정합성 기준으로 유지

- 상태: **Accepted · 회고 작성**
- 정리일: 2026-10-15. 최초 결정일 미확인.
- 확인 기준: `b545618252b58b41e2107828409418bc3fb215cb`의 조건부 전이와 DB 제약. 대안 비교는 정리 시점의 설명이다.

## 배경

판매 시작의 동시 요청과 같은 요청의 재시도는 초과 판매·중복 구매를 유발할 수 있다. 동일 구매의 승인·취소·만료가 겹칠 때 재고를 두 번 확정하거나 반환하면 다른 회원의 예약 재고까지 침범한다. 요청을 순서대로 받아도 DB 상태 변경의 원자성이 필요하다.

## 비교한 대안

| 대안 | 이점 | 감수할 비용 |
|---|---|---|
| 조회한 수량·상태를 일반 저장 | 구현이 단순함 | 동시 요청의 상태 덮어쓰기와 중복 부수효과 위험 |
| 비관적 잠금으로 유스케이스 직렬화 | 잠금 구간의 순서가 명확함 | hot row 대기, 긴 트랜잭션의 병목 |
| 조건부 UPDATE + 유니크 제약 | DB가 잔여 수량·허용 상태를 원자 검사 | SQL과 영향 행 수 처리, 같은 행의 경합은 남음 |
| Redis를 재고 원장으로 사용 | 입구에서 빠른 수량 처리 가능 | DB 구매 상태와 이중 원장·보상·복구 필요 |

## 결정

PostgreSQL에서 재고를 조건부 원자 UPDATE하고 DB 제약으로 요청 멱등성과 활성 구매 중복을 방어한다. 구매는 `PENDING_PAYMENT`, 결제 시도는 `READY`인 행만 완료 상태로 전이한다. 전이에 성공한 요청만 같은 트랜잭션에서 연관된 구매·재고를 변경한다. 상세 계약은 [정합성 설계](../architecture/module-boundaries.md#영속성-정합성-이벤트)가 소유한다.

Redis 대기열이 추가되어도 이 보호 장치를 유지한다. Redis 슬롯은 요청 유입을 조절하며 구매·재고의 최종 성공 여부를 결정하지 않는다.

## 결과와 비용

동시 예약과 승인·취소 경합에서 수량 보존과 상태 전이의 단일 성공을 보호한다. DB 트랜잭션·제약·영향 행 수에 의존하므로 Repository 어댑터와 실제 PostgreSQL 테스트가 필요하다. 조건부 UPDATE도 동일 재고 행에서 경합하며 무제한 처리량을 제공하지 않는다. 저장소나 서비스가 분리되면 현재의 단일 트랜잭션 가정을 다시 검토해야 한다.

## 근거와 검증

- [재고 Repository SQL](../../inventory/src/main/java/com/example/limitedticketing/inventory/infrastructure/persistence/repository/JpaInventoryRepository.java): 수량 조건부 전이.
- [구매 Repository SQL](../../purchase/src/main/java/com/example/limitedticketing/purchase/infrastructure/persistence/repository/JpaPurchaseRepository.java), [결제 Repository SQL](../../payment/src/main/java/com/example/limitedticketing/payment/infrastructure/persistence/repository/JpaPaymentRepository.java): 허용 상태에서의 전이.
- [Flyway migrations](../../app/src/main/resources/db/migration): 멱등성·활성 항목·참조 무결성 제약.
- [PurchaseConcurrencyIntegrationTest](../../app/src/test/java/com/example/limitedticketing/integration/purchase/PurchaseConcurrencyIntegrationTest.java): 동시 예약과 재고 보존.
- [PurchaseLifecycleConcurrencyIntegrationTest](../../app/src/test/java/com/example/limitedticketing/integration/purchase/PurchaseLifecycleConcurrencyIntegrationTest.java): 동일 구매 전이 경합과 다른 회원 재고 보호.

기존 k6 진입 지표만으로 이 정합성을 입증하지 않는다. 부하 실행 후 DB 검증은 [실행 가이드](../performance/load-testing.md#5-최종-정합성-검증)를 따른다.
