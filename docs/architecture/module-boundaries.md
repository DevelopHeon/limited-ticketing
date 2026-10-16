# 모듈 경계와 내부 설계

전체 실행 구성은 [C4 개요](README.md), 로컬 배치는 [배포 구조](deployment.md)를 참고한다. Gradle 모듈은 하나의 Spring Boot 프로세스 안에 있는 코드 경계다.

### 핵심 원칙

- PostgreSQL은 구매, 재고, 대기열 상태의 최종 정합성 기준이다.
- Redis는 대기 순서와 처리 슬롯을 제어하는 보조 저장소다. 재고 수량과 구매 확정 상태를 Redis에 두지 않는다.
- 각 도메인은 자기 Aggregate와 Domain Repository를 소유한다. 다른 모듈은 공개 Input Port와 DTO만 사용한다.
- HTTP 계약, 모듈 공개 계약, 내부 유스케이스 DTO를 분리한다.
- 비동기 처리로 바꿔도 구매 멱등성 및 재고의 조건부 원자 갱신을 최종 보호 장치로 유지한다.

## 비즈니스 규칙

- 활성 회원만 구매할 수 있습니다.
- `OPEN` 상태이며 판매 기간 안에 있는 상품만 예약할 수 있습니다.
- 한 사용자는 같은 판매 상품에 `PENDING_PAYMENT` 구매를 동시에 하나만 가질 수 있습니다.
- `PAID` 구매가 있으면 같은 판매 상품을 다시 구매할 수 없습니다.
- `EXPIRED` 또는 `CANCELLED` 이후에는 다시 예약할 수 있습니다.
- 재고 예약 시 `available`이 감소하고 `reserved`가 증가합니다.
- 결제 승인 시 `reserved`가 감소하고 `sold`가 증가합니다.
- 결제 실패는 하나의 결제 시도 이력으로 남으며 구매 예약 만료 전까지 새 요청 키로 재시도할 수 있습니다.
- 구매 취소 또는 만료 시 예약 재고가 가용 재고로 반환됩니다.

## 프로젝트와 모듈 구성

```text
limited-ticketing
├── app          # Spring Boot 조립, 환경 설정, 전역 HTTP/보안 처리, 통합 테스트
├── shared       # 제한된 공통 값 타입, 공통 예외, 공통 API 응답 계약
├── member       # 회원과 구매 가능 상태
├── sale         # 상품, 판매 기간, 판매 상태
├── inventory    # 가용/예약/판매 재고 전이
├── purchase     # 구매 예약, 취소, 만료, 구매 완료
├── payment      # 결제 시도 이력과 승인/실패
└── purchasewaiting # 구매 대기 접수, 순번, 처리 결과와 복구
```

| 모듈 | 주요 모델 | 책임 | 공개 API를 사용하는 모듈 |
|---|---|---|---|
| `shared` | `Money`, `SalePeriod`, `DomainException`, `ApiResponse` | 전 모듈에서 동일한 의미를 갖는 계약 | 전체 |
| `member` | `Member`, `MemberStatus` | 회원 식별, 구매 가능 여부 | `purchase`, `purchasewaiting` |
| `sale` | `Product`, `SaleProduct`, `SaleStatus` | 판매 상품, 기간, 판매 상태와 구매 가능 여부 | `purchase`, `purchasewaiting` |
| `inventory` | `Inventory` | 가용, 예약, 판매 수량의 원자적 전이 | `sale`, `purchase`, `payment` |
| `purchase` | `Purchase`, `PurchaseStatus` | 구매 예약, 취소, 만료, 완료와 구매 멱등성 | `payment`, `purchasewaiting` |
| `payment` | `Payment`, `PaymentStatus` | 결제 시도 이력과 승인/실패 처리 | HTTP API |
| `purchasewaiting` | `PurchaseWaitingEntry`, `PurchaseWaitingStatus` | 구매 대기 접수, 순번, 처리 결과와 복구 | HTTP API |
| `app` | - | 실행 모듈, DataSource/Flyway/Redis/Security/전역 예외 조립 | 전체 |

모듈 간 Aggregate 참조는 객체 그래프가 아니라 식별자로 표현한다. 예를 들어 `Purchase`는 `memberId`, `saleProductId`만 보관하며 `Member`, `SaleProduct` 엔티티를 직접 참조하지 않는다.

`purchasewaiting`은 기술적 범용 큐가 아니라 구매 대기 요청의 수명 주기를 소유한다. 구매 대기 접수·조회·처리·복구와 실제 구매 예약의 책임을 분리하고, 예약 자체는 `purchase`의 공개 Facade에 위임한다. Java 패키지와 Gradle 모듈명은 이 업무를 표현하며 기존 HTTP 경로, DB 테이블, Redis 키와 `limited-ticketing.queue` 설정은 유지한다.

## 모듈 경계와 의존 규칙

Gradle 의존성은 컴파일 가능 여부를, Spring Modulith의 `@ApplicationModule`은 허용된 모듈 참조 방향을 검증한다. 두 규칙을 모두 만족해야 다른 모듈의 타입을 사용할 수 있다.

```text
shared    -> 없음
member    -> shared::exception
sale      -> shared::vo, shared::exception, shared::api, inventory::api
inventory -> shared::exception
purchase  -> shared::vo, shared::exception, shared::api,
             member::api, sale::api, inventory::api
payment   -> shared::vo, shared::exception, shared::api,
             purchase::api, inventory::api
purchasewaiting -> shared::api, shared::exception,
             member::api, sale::api, purchase::api
```

`::api`, `::vo`, `::exception`, `::events`는 Spring Modulith Named Interface다. 모듈 전체가 아니라 필요한 공개 패키지만 노출한다.

```java
@ApplicationModule(allowedDependencies = {
    "shared::vo",
    "member::api",
    "sale::api",
    "inventory::api"
})
package com.example.limitedticketing.purchase;
```

```java
@NamedInterface("api")
package com.example.limitedticketing.member.application.port.in;
```

다른 모듈 호출은 자신의 Application Output Port → infrastructure의 ModuleAdapter → 대상 모듈 Input Port로 연결한다. 상대 Domain·Repository·Entity·내부 UseCase·Service는 참조하지 않는다. ModuleAdapter는 필요한 외부 DTO를 자신의 출력 DTO로 변환한다. 공통 값 타입은 shared의 공개 계약을 사용한다.

## 레이어 구조

모든 업무 모듈은 같은 책임 배치를 사용한다. 기능이 없는 패키지나 Port를 미리 생성하지 않는다. app은 실행 조립·전역 오류·보안·공통 키 발급을, shared는 제한된 공통 값·예외를 소유하며 업무 모듈 모양을 억지로 복제하지 않는다.

```text
업무 모듈/
├── application/
│   ├── port/in/                 공개 Port·외부 Input/Output DTO
│   │   └── worker/              다른 모듈에 공개하지 않는 스케줄 진입 Port
│   ├── port/out/                외부 기능·기술·검색 조회 계약과 출력 DTO
│   ├── usecase/                 내부 업무 interface·command/query/result DTO
│   ├── service/                 UseCase 구현·transaction·단순 Port 진입 연결
│   └── facade/                  여러 UseCase의 순차 조합이 필요한 경우
├── domain/
│   ├── model/                   Aggregate·상태 규칙
│   ├── repository/              Aggregate 조회·저장 추상 계약과 저장 결과
│   ├── event/                   필요한 Domain 이벤트
│   └── exception/
└── infrastructure/adapter/
    ├── in/web/                  Controller·HTTP Request/Response
    ├── in/scheduler/            실행 주기
    ├── out/persistence/         Domain Repository·검색 Port의 DB 구현·JPA
    ├── out/module/              다른 모듈 공개 Port 연결
    └── out/...                  Redis·이벤트·실행기 등 실제 필요한 구현
```

Controller·Scheduler 자체가 Input Adapter이며 Input Port를 호출한다. 일반 진입 EntryService는 Input/Output과 내부 DTO를 변환해 UseCase에 연결한다. 실제 여러 UseCase를 조합하는 PurchaseWaitingCommandFacade만 facade에 둔다. Application 내부 업무 호출은 UseCase를 사용한다. 의존성 필드·생성자·테스트에는 Port·UseCase·Repository·Service·Adapter 등 타입의 역할 접미사를 유지한다.

### Aggregate 영속성과 Application 출력 계약

[ADR 0009](../adr/0009-domain-repository-ownership.md)에 따라 Aggregate의 조회·저장·조건부 전이는 Domain Repository를 직접 사용한다. MemberRepository, ProductRepository, SaleProductRepository, InventoryRepository, PurchaseRepository, PaymentRepository, PurchaseWaitingEntryRepository를 infrastructure의 PersistenceAdapter가 구현한다. 그 사이에 동일 기능의 Application Repository Port나 중계 Adapter를 추가하지 않는다.

재고 reserve/confirm/release 조건부 UPDATE, 구매·결제 상태 조건부 저장, 대기 요청 행 잠금·claim·복구 조회도 해당 Aggregate의 영속성 계약이다. Domain Repository는 framework·Application에 의존하지 않는다. Query라는 이름만으로 잠금 조회나 복구 transaction을 read-only로 바꾸지 않는다.

판매 목록의 필터·정렬·페이지 조회는 SaleProductSearchPort, Redis 대기·복구·입구 제한과 외부 모듈 호출·이벤트 발행은 Application Output Port다. 상품별 순번 발급도 Aggregate 저장 계약과 구분해 기존 Sequence Port를 유지한다.

```text
외부 입력 → Input Port → EntryService / Facade → UseCase → 업무 Service
  ├─ Aggregate 영속화 → Domain Repository → PersistenceAdapter
  └─ 검색·외부 기능 → Application Output Port → Driven Adapter
```

## DTO, 예외, 시간 규칙

| 위치 | 역할 |
|---|---|
| infrastructure.adapter.in.web.request/response | HTTP 입력과 응답 |
| application.port.in.dto | 모듈 공개 Input/Output·공개 상태값 |
| application.usecase.dto.command/query/result | 내부 업무 실행·조회·결과 |
| application.port.out.dto | 검색·외부 기능 호출의 내부 출력 계약 |
| domain.repository | Aggregate 저장 결과·멱등 저장 여부 |
| shared.api | 성공 응답 외피 |

공개 DTO는 Domain Aggregate·Domain enum·내부 구현 DTO를 노출하지 않는다. HTTP 응답과 모듈 공개 응답도 구분한다. Finder/Support는 별도 계층으로 두지 않으며 필요한 Aggregate 조회와 기존 오류는 Domain Repository로 처리한다. 반복 조회가 한 Service 내부에 있으면 의미 있는 private 메서드로 묶는다.

### 예외와 HTTP 응답

각 도메인은 `PurchaseException`, `PaymentException`, `InventoryException`처럼 도메인별 예외를 던진다. 이 예외는 `shared.exception.DomainException`을 상속하며 `INVALID_REQUEST`, `NOT_FOUND`, `CONFLICT`, `FORBIDDEN` 분류와 오류 코드를 가진다.

`app`의 `GlobalExceptionHandler`가 이를 HTTP 상태와 `ApiErrorResponse`로 변환한다. 성공 응답은 `ApiResponse.success(data)`를 기본으로 사용한다. 생성 API처럼 `201 Created` 또는 `202 Accepted`와 `Location` 헤더가 필요한 경우에는 `ResponseEntity<ApiResponse<T>>`를 사용한다.

### 시간

기술적으로 동일한 현재 시각은 `app.configuration.time.TimeConfiguration`이 제공하는 JDK `Clock` Bean을 주입받아 사용한다. 테스트는 `Clock.fixed(...)`로 시간을 고정한다. 영업일, 정산 마감처럼 도메인별 시간 정책이 생길 때만 별도 Port를 만든다.

## 영속성, 정합성, 이벤트

### PostgreSQL과 Flyway

PostgreSQL이 최종 영속 저장소다. `app`이 DataSource, Flyway, JPA 설정을 소유하고 모든 도메인 모듈은 같은 DataSource를 공유한다.

- Flyway migration이 스키마 변경의 유일한 이력이다.
- 적용된 migration 파일은 수정하지 않고 변경마다 다음 버전의 SQL 파일을 추가한다.
- Hibernate는 `ddl-auto: validate`로 Entity와 스키마의 불일치만 검증한다.
- H2 대신 Testcontainers PostgreSQL로 통합 테스트를 수행한다.
- Aggregate 간 참조는 Domain 모델에서 식별자로 표현하되, 단일 DB 안의 물리 참조는 FK로 보호한다.
- FK 삭제 정책은 `RESTRICT`를 사용하며 Aggregate를 연쇄 삭제하지 않는다.

구매와 결제의 멱등성은 DB 유니크 제약과 기존 행 재조회로 보장한다.

```text
purchases(member_id, purchase_request_key)  # 동일 구매 요청 재시도
payments(payment_request_key)                # 동일 결제 시도 재시도
reservation_queue_entries(member_id, purchase_request_key)
                                                # 동일 대기열 진입 재시도
```

활성 구매와 활성 대기열 항목은 별도 제약으로 막는다. 구매는 `active_purchase_key`를 사용하고, 대기열은 `WAITING`, `PROCESSING` 상태에만 적용되는 `(member_id, sale_product_id)` 부분 유니크 인덱스를 사용한다.

재고 예약은 조회 후 저장 방식이 아니라 조건부 원자 UPDATE로 수행한다. `available_quantity >= 요청 수량`일 때만 `available`을 줄이고 `reserved`를 늘린다. 영향 행 수가 0이면 재고 부족으로 처리하므로 동시 요청에서도 음수 재고와 유실 갱신을 방지한다.

구매의 결제 완료·취소·만료는 `PENDING_PAYMENT`인 행만 조건부 UPDATE로 전이한다. 상태 전이에 성공한 요청만 같은 트랜잭션에서 재고를 확정하거나 반환한다. 동일 구매를 동시에 승인·취소해도 다른 회원의 예약 수량을 중복 차감하지 않는다.

결제 시도의 승인·실패도 `READY`인 행만 조건부 UPDATE로 전이한다. 실패와 승인이 동시에 도착해도 완료된 결제 상태를 덮어쓰지 않으며, 승인 요청이 결제 상태 전이에 실패하면 같은 트랜잭션의 구매 완료와 재고 확정도 롤백한다.

### 도메인 이벤트

구매 예약 이벤트는 트랜잭션 커밋 후 Spring ApplicationEventPublisher로 발행한다. `purchase.domain.event`는 `events` Named Interface로 공개된다.

```text
Aggregate 상태 변경
  -> Application Service가 저장
  -> 트랜잭션 커밋 후 Spring 이벤트 발행
  -> 구독자가 등록된 경우 같은 프로세스의 후속 처리
```

프로세스 내부 이벤트는 외부 메시지 브로커가 아니다. 프로세스 종료에도 반드시 전달되어야 하는 이벤트, 소비자 독립 확장, 재시도와 모니터링이 필요해지면 Transactional Outbox와 Kafka를 별도 단계로 도입한다.

현재 운영 코드에는 `PurchaseReserved` 이벤트를 소비하는 후처리 Listener가 없으며, 테스트에서 발행 여부를 검증한다.

## 구매 대기와 실제 구매의 경계

`purchasewaiting`은 구매 대기 접수, 순번, 처리 결과와 복구를 소유한다. Worker는 UseCase → Service → PurchaseReservationPort → PurchaseModuleAdapter를 통해 `purchase`의 공개 Input Port로 실제 구매 예약을 실행하며, 구매와 재고의 최종 정합성은 PostgreSQL 트랜잭션이 보호한다.

Redis ZSET은 대기 순서와 처리 슬롯을 제공한다. API 계약, DB 상태, Redis 키와 장애 복구 절차는 [구매 대기 설계](purchase-waiting.md)를 따른다. 직접 예약 HTTP는 `baseline` profile에서만 기준선 측정 경로로 제공한다.

## 검증과 관련 문서

- 모듈 내부 패키지 의존 방향: [LayeringArchitectureTests](../../app/src/test/java/com/example/limitedticketing/architecture/LayeringArchitectureTests.java)
- 모듈 간 공개 API와 순환 의존: [ModularityTests](../../app/src/test/java/com/example/limitedticketing/architecture/ModularityTests.java)
- 코드 규칙과 테스트 배치: [개발 및 검증](../development/testing.md#자동-검증)
- 메시징 도입 조건: [로드맵](../plans/roadmap.md#메시징-확장-판단-기준)
