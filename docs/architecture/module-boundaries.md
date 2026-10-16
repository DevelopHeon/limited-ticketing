# 모듈 경계와 내부 설계

전체 실행 구성은 [C4 개요](README.md), 로컬 배치는 [배포 구조](deployment.md)를 참고한다. Gradle 모듈은 하나의 Spring Boot 프로세스 안에 있는 코드 경계다.

### 핵심 원칙

- PostgreSQL은 구매, 재고, 대기열 상태의 최종 정합성 기준이다.
- Redis는 대기 순서와 처리 슬롯을 제어하는 보조 저장소다. 재고 수량과 구매 확정 상태를 Redis에 두지 않는다.
- 각 도메인은 자기 모델과 Repository를 소유한다. 다른 도메인은 공개 Facade와 DTO만 사용한다.
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

다른 모듈을 호출할 때는 대상 모듈의 `application.port.in` Facade와 `application.port.in.dto`만 참조한다. 대상 모듈의 Domain 모델, JPA Entity, Spring Data Repository, 내부 Command/Query Service는 직접 참조하지 않는다.

purchasewaiting 이외 모듈의 Scheduler는 presentation에서 내부 입력 Facade를 호출한다. 입력·출력의 배치 기준은 [ADR 0005](../adr/0005-inbound-outbound-adapters.md)를 따른다. 아래 구조는 현재 구현 기준이다.

## 레이어 구조

purchasewaiting 이외의 모듈은 아래 구조를 사용한다. purchasewaiting의 새 구조는 [구매 대기 설계](purchase-waiting.md#portadapter-구조)를 따른다. 필요한 코드가 없으면 패키지를 미리 만들지 않는다.

```text
purchase
├── presentation                         # HTTP 어댑터
│   ├── controller
│   ├── request
│   └── response
├── application                          # 유스케이스와 트랜잭션 경계
│   ├── command
│   │   ├── dto
│   │   └── service
│   ├── query
│   │   ├── dto
│   │   └── service
│   ├── support                           # Finder 등 유스케이스 공통 보조 객체
│   └── port
│       ├── in                            # 다른 모듈/Controller에 공개하는 Facade
│       │   └── dto
│       └── out                           # 인프라에 요구하는 기술 중립 계약
├── domain                               # 순수 비즈니스 규칙
│   ├── event
│   ├── exception
│   ├── model
│   └── repository
└── infrastructure                       # JPA, Redis, Spring Event 등의 구현체
    ├── configuration
    ├── messaging
    ├── persistence
    │   ├── entity
    │   └── repository
    └── redis
```

### 레이어 책임

| 레이어 | 책임 | 허용되는 의존성 |
|---|---|---|
| `presentation` | HTTP 바인딩, 인증 주체 추출, HTTP 응답 생성 | 공개 `port.in`과 HTTP DTO |
| `application.command` | 상태 변경 유스케이스, 트랜잭션, 도메인 조율 | Domain, 다른 모듈의 공개 API, `port.out` |
| `application.query` | 상태를 바꾸지 않는 조회 유스케이스 | Domain Repository, 조회용 `port.out` |
| `application.port.in` | 모듈 외부 진입점, 공개 DTO와 내부 DTO 변환 | 내부 Application Service |
| `application.port.out` | DB 외 기술에 대한 요구 계약 | 기술 중립 인터페이스만 선언 |
| `domain` | 상태 전이, 불변식, 도메인 예외, Repository 추상화 | Java와 `shared`의 의미 있는 값 타입 |
| `infrastructure` | JPA, Spring Data, Redis, Spring Event, Executor 구현 | Domain Repository 및 `port.out` 구현 |

Domain에는 Spring, JPA, Hibernate 어노테이션을 두지 않는다. JPA 매핑은 `infrastructure.persistence.entity`의 `*JpaEntity`가 소유하고, `*RepositoryAdapter`가 JPA Entity와 Domain 모델을 변환하면서 Domain Repository를 구현한다.

### 호출 흐름

```text
HTTP 요청
  -> presentation.request
  -> application.port.in.dto
  -> application.port.in.*Facade
  -> application.command/query.dto
  -> application.command/query.service
  -> domain model / domain repository
  -> infrastructure adapter / JPA entity
  -> presentation.response

다른 모듈 호출
  -> 대상 모듈 application.port.in.dto
  -> 대상 모듈 application.port.in.*Facade

외부 기술 호출
  -> application service
  -> application.port.out
  -> infrastructure 구현체
```

`Facade`는 공개 계약을 내부 Command/Query로 변환하고 Service에 위임한다. 비즈니스 규칙과 트랜잭션은 Facade가 아니라 Application Service와 Domain에 둔다. `port.out`은 다른 모듈에서 호출하는 API가 아니라, Application이 인프라 구현에 의존하지 않게 하는 출력 포트다.

## DTO, 예외, 시간 규칙

### DTO 경계

| 위치 | 역할 | 이름 예시 |
|---|---|---|
| `presentation.request` | HTTP 입력 및 Bean Validation | `ReservePurchaseRequest` |
| `presentation.response` | HTTP JSON 응답 | `PurchaseReservationResponse` |
| `shared.api` | 성공 응답 외피 | `ApiResponse<T>` |
| `application.command.dto` | 내부 상태 변경 입력/결과 | `ReservePurchaseCommand`, `PurchaseReservationResult` |
| `application.query.dto` | 내부 조회 입력/결과 | `FindPurchaseQuery`, `PurchaseDetailResult` |
| `application.port.in.dto` | 모듈 공개 Facade 계약 | `ReservePurchaseInput`, `PurchaseReservation` |
| `application.port.out` | 외부 기술 호출 계약 | `PurchaseEventPublisher`, `ReservationQueueStore` |

`presentation`과 `application.port.in.dto`는 Domain Aggregate, Repository, Domain enum, 내부 Command/Query/Support DTO를 직접 import하지 않는다. 공개 상태값은 `String` 또는 공개 계약 enum으로 표현하고, Facade에서 Domain enum으로 변환한다.

단순 DTO 변환은 대상 DTO의 `from(source)` 또는 `of(...)` 정적 팩토리에 둔다. Aggregate를 필수 조회하는 공통 규칙은 `application.support.*Finder`로 분리하며, 각 Service에 같은 `findById(...).orElseThrow(...)`를 반복하지 않는다.

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

`purchasewaiting`은 구매 대기 접수, 순번, 처리 결과와 복구를 소유한다. Worker는 UseCase → Service → PurchaseReservationPort → PurchaseModuleAdapter를 통해 `purchase`의 공개 Facade로 실제 구매 예약을 실행하며, 구매와 재고의 최종 정합성은 PostgreSQL 트랜잭션이 보호한다.

Redis ZSET은 대기 순서와 처리 슬롯을 제공한다. API 계약, DB 상태, Redis 키와 장애 복구 절차는 [구매 대기 설계](purchase-waiting.md)를 따른다. 직접 예약 HTTP는 `baseline` profile에서만 기준선 측정 경로로 제공한다.

## 검증과 관련 문서

- 모듈 내부 패키지 의존 방향: [LayeringArchitectureTests](../../app/src/test/java/com/example/limitedticketing/architecture/LayeringArchitectureTests.java)
- 모듈 간 공개 API와 순환 의존: [ModularityTests](../../app/src/test/java/com/example/limitedticketing/architecture/ModularityTests.java)
- 코드 규칙과 테스트 배치: [개발 및 검증](../development/testing.md#자동-검증)
- 메시징 도입 조건: [로드맵](../plans/roadmap.md#메시징-확장-판단-기준)


## purchasewaiting의 구조 전환

purchasewaiting은 [ADR 0007](../adr/0007-purchasewaiting-ports-and-adapters.md)의 infrastructure 입력·출력 배치를 유지하고 [ADR 0008](../adr/0008-application-entry-orchestration.md)에 따라 Controller·Scheduler가 Input Adapter 역할을 맡는다. Input Port는 Application Facade 또는 진입 Service가 구현하며 내부 업무 실행은 UseCase로 연결한다. 다른 모듈은 기존 Facade 구조다. Input Port와 공개 DTO는 기존 Named Interface `api`로 노출하고, Worker Port·Facade 구현·UseCase·Service·Output Port·Domain·Adapter는 다른 모듈에 공개하지 않는다.

기존 LayeringArchitectureTests는 사용자의 명시적 지시로 그대로 유지하며 새 구조와의 충돌을 과도기 실패로 보고한다. 이는 검사 완료가 아니며, 모든 모듈 전환 후 의존 방향 정책을 함께 개정한다. Modulith와 Checkstyle은 계속 검증한다.
