# ADR 0009 · Aggregate 영속 계약은 Domain Repository가 소유한다

- 상태: **Accepted**
- 결정일: 2026-10-16. 사용자는 전체 모듈 전환을 검토하며 Aggregate 조회·저장까지 Application Port로 옮기는 방식보다 Domain의 추상 Repository를 직접 사용하는 기준을 선택했다.
- [ADR 0007](0007-purchasewaiting-ports-and-adapters.md)의 Aggregate 영속 계약 위치를 대체한다. 입력 Adapter·Port·UseCase와 Application 조합 책임은 [ADR 0008](0008-application-entry-orchestration.md)을 유지한다.

## 문제와 대안

Repository의 모든 메서드를 Application Query/Command Port로 옮기면 Aggregate 영속성을 사용하는 기본 업무에도 저장소 계약이 Application으로 이동한다. Domain이 소유하는 Aggregate 수명 주기와 Repository 경계가 흐려지고 동일한 저장 계약을 위한 중간 추상화가 늘어난다.

모든 외부 호출을 Application Port로 통일하는 대안은 이름을 통일하기 쉽지만 Aggregate 영속성과 외부 기술 호출의 성격을 구분하지 못한다. 반대로 검색·기술 호출까지 모두 Domain Repository에 넣으면 정렬·페이지·외부 시스템 계약이 Domain에 섞인다. 이번에는 Aggregate 영속성과 Application 조회·외부 기능을 구분한다.

## 결정

- Aggregate 조회·저장과 해당 상태의 조건부 영속 전이는 Domain의 추상 Repository interface를 직접 사용한다. infrastructure의 PersistenceAdapter가 구현하며 Application Repository Port나 중계 Adapter를 추가하지 않는다.
- InventoryRepository의 원자 재고 reserve/confirm/release, PurchaseRepository·PaymentRepository의 조건부 상태 저장, PurchaseWaitingEntryRepository의 행 잠금·claim·복구 조회도 Aggregate 정합성 계약으로 포함한다. 멱등 저장 결과도 Domain Repository 계약에 둔다.
- SaleProductRepository는 Aggregate save/findById를 소유한다. 상품 목록의 필터·정렬·페이지 조회는 Application의 SaleProductSearchPort와 출력 DTO를 사용한다. 같은 DB 구현체가 두 계약을 구현해도 중간 중계 계층을 추가하지 않는다.
- 다른 모듈·Redis·이벤트 발행·작업 실행·순번 발급은 Application Output Port를 유지한다. 다른 모듈의 Domain Repository를 직접 호출하지 않는다.
- Domain Repository는 Application·Spring·JPA에 의존하지 않는다. 기존 transaction·DB 조건부 UPDATE·행 잠금·멱등성·오류를 보존한다.

## 결과와 비용

Aggregate 영속성 경계가 Domain에 남고 외부 기술·검색 계약과 구분된다. Repository와 Output Port를 함께 사용하므로 모든 의존성을 하나의 Port 명명 규칙으로 통일하지는 않는다. 잠금·원자 전이의 의도는 Repository 메서드가 표현하고 DB 기술의 구현은 infrastructure가 맡는다. 위치 변경 자체를 정합성·성능 개선으로 주장하지 않는다.

## 근거

- [현재 모듈·영속성 경계](../architecture/module-boundaries.md#aggregate-영속성과-application-출력-계약)
- [구매 대기 계약](../architecture/purchase-waiting.md#portadapter-구조)
- [업무·아키텍처 검증 기준](../development/testing.md#아키텍처-검사-정책)
- [적용 상태와 다음 행동](../plans/roadmap.md)
