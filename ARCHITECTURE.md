# Limited Ticketing 아키텍처

## 1. 목표와 배포 단위

Limited Ticketing은 한정 수량 상품의 판매, 재고 예약, 구매, 결제를 다루는 모듈러 모놀리스다. 현재는 Spring Boot 애플리케이션 하나를 하나의 컨테이너 또는 Pod로 배포한다.

```text
member + sale + inventory + purchase + payment
                         |
                       app.jar
                         |
              컨테이너 또는 Pod 하나
```

Gradle 하위 프로젝트와 Spring Modulith 애플리케이션 모듈은 서비스 분리나 배포 분리를 의미하지 않는다. 독립 배포가 필요한 근거가 생기기 전까지는 같은 프로세스 안에서 모듈 경계만 강제한다.

## 2. 프로젝트 구성

```text
limited-ticketing
├── app          # Spring Boot 실행, HTTP, Security, DB 구성, 테스트
├── shared       # 제한된 공통 값 타입과 예외 분류 계약
├── member       # 회원
├── sale         # 상품과 판매
├── inventory    # 재고
├── purchase     # 구매와 예약
└── payment      # 결제 시도와 결과
```

- 루트 `build.gradle`은 Java 버전, BOM, 라이브러리 버전을 관리한다.
- 각 하위 모듈은 필요한 라이브러리와 `project(':module')` 의존성만 선언한다.
- `app`만 Spring Boot 플러그인과 실행 구성을 가진다. 다른 모듈은 기능 코드와 영속성 어댑터를 제공한다.
- `app`의 `configuration`과 `presentation.exception`은 웹·보안·DB 같은 애플리케이션 조립 책임을 가진다. 도메인 규칙을 공통 설정 패키지에 두지 않는다.

## 3. 모듈 경계와 의존 규칙

Gradle 의존성은 **컴파일 가능 여부**를, Spring Modulith의 `@ApplicationModule`은 **허용된 애플리케이션 모듈 방향**을 검증한다. 둘 다 만족해야 다른 모듈 타입을 사용할 수 있다.

```text
shared    -> 없음
member    -> shared::exception
sale      -> shared::vo, shared::exception
inventory -> shared::exception
purchase  -> shared::vo, shared::exception, member::api, sale::api, inventory::api
payment   -> shared::vo, shared::exception, purchase::api, inventory::api
```

`::vo`, `::api`는 Spring Modulith Named Interface다. 모듈 전체를 공개하지 않고, 필요한 하위 패키지만 공개 계약으로 노출한다. `application.port.in`과 `application.port.in.dto`는 각각 `api`로 선언해 Facade와 계약 DTO를 함께 공개한다.

```java
@ApplicationModule(
    allowedDependencies = {
        "shared::vo",
        "member::api",
        "sale::api",
        "inventory::api",
        "payment::api"
    }
)
package com.example.limitedticketing.purchase;
```

```java
@NamedInterface("api")
package com.example.limitedticketing.member.application.port.in;
```

다른 모듈을 호출해야 할 때는 다음 규칙을 지킨다.

1. 대상 모듈의 `application.port.in`에 Facade와 계약 DTO를 둔다.
2. 대상의 `api` Named Interface만 참조한다. `domain`, `infrastructure`, JPA Entity, Spring Data Repository를 직접 참조하지 않는다.
3. 호출 모듈의 Gradle 의존성과 `@ApplicationModule(allowedDependencies = ...)`를 함께 추가한다.
4. 즉시 응답이 필요하지 않은 후속 작업은 동기 호출보다 이벤트를 우선 검토한다.

`shared`에는 모든 모듈에서 같은 의미를 갖는 값 타입과 공통 예외 분류 계약만 둔다. Aggregate, 기능별 API DTO, Repository, 이벤트, 웹/보안 설정은 `shared`에 두지 않는다. 현재 `shared.vo`, `shared.exception`이 각각 Named Interface로 공개된다.

### 예외 처리 규칙

각 모듈은 `PurchaseException`, `PaymentException`, `InventoryException`처럼 도메인별 예외를 던진다. 일반적인 `IllegalArgumentException`, `IllegalStateException`, `NoSuchElementException`으로 HTTP 오류를 표현하지 않는다.

도메인 예외는 `shared.exception.DomainException`을 상속하고 다음 정보를 가진다.

```text
DomainErrorType: INVALID_REQUEST | NOT_FOUND | CONFLICT | FORBIDDEN
code:            PURCHASE_NOT_FOUND, INSUFFICIENT_INVENTORY 등
message:         클라이언트에 반환할 오류 설명
```

`app`의 `GlobalExceptionHandler`는 이 공통 계약을 HTTP 상태와 `ApiErrorResponse`로 변환한다. DB 유니크 제약 같은 `DataIntegrityViolationException`은 별도로 409로 처리한다. 트랜잭션 없이 이벤트를 발행하는 등 내부 구현 오류는 일반 예외로 남겨 500으로 처리한다.

## 4. 모듈별 책임

| 모듈 | 주요 모델 | 책임 |
|---|---|---|
| `member` | `Member`, `MemberStatus` | 회원 식별과 구매 가능 상태 |
| `sale` | `Product`, `SaleProduct`, `SaleStatus` | 상품 정보, 판매 기간, 판매 조건 |
| `inventory` | `Inventory` | 가용·예약·판매 수량 전이 |
| `purchase` | `Purchase`, `PurchaseStatus` | 구매 예약, 취소, 만료, 완료 상태 전이 |
| `payment` | `Payment`, `PaymentStatus` | 결제 시도 이력과 승인·실패 결과 |
| `shared` | `Money`, `SalePeriod` | 제한된 공통 값 타입 |

모듈 간 관계는 우선 식별자 값으로 표현한다. 예를 들어 `Purchase`는 `memberId`, `saleProductId`를 보관하며 `Member`, `SaleProduct` 객체를 직접 참조하지 않는다.

## 5. 도메인 모듈 패키지 구조

필요한 코드가 생길 때만 패키지를 추가한다. 아래는 `purchase` 기능이 구현된 상태를 가정한 목표 구조다.

```text
purchase
├── presentation                              # HTTP 어댑터
│   ├── controller
│   │   └── PurchaseController
│   ├── request
│   │   ├── CancelPurchaseRequest
│   │   └── ReservePurchaseRequest
│   └── response
│       ├── PurchaseResponse
│       └── PurchaseReservationResponse
│
├── application                               # 유스케이스와 트랜잭션 경계
│   ├── command                               # 상태 변경
│   │   ├── dto
│   │   │   ├── CancelPurchaseCommand
│   │   │   └── ReservePurchaseCommand
│   │   └── service
│   │       └── PurchaseCommandService
│   ├── query                                 # 상태 비변경 조회
│   │   ├── dto
│   │   │   ├── FindPurchaseQuery
│   │   │   └── PurchaseDetailResult
│   │   └── service
│   │       └── PurchaseQueryService
│   └── port
│       ├── in                                # 모듈 외부 공개 계약
│       │   ├── PurchaseCommandFacade
│       │   ├── PurchaseQueryFacade
│       │   └── dto
│       │       ├── FindPurchaseInput
│       │       ├── PurchaseSummary
│       │       └── ReservePurchaseInput
│       └── out                               # 외부 기술 의존 계약
│           ├── Clock
│           └── PurchaseEventPublisher
│
├── domain                                    # 순수 비즈니스 규칙
│   ├── event
│   │   └── PurchaseReserved
│   ├── model
│   │   ├── Purchase
│   │   └── PurchaseStatus
│   └── repository
│       └── PurchaseRepository
│
└── infrastructure                            # 기술 구현체
    ├── messaging
    │   └── SpringPurchaseEventPublisher
    └── persistence
        ├── entity
        │   └── PurchaseJpaEntity
        ├── repository
        │   └── JpaPurchaseRepository
        └── PurchaseRepositoryAdapter
```

### DTO와 호출 경계

| 위치 | 역할 | 이름 규칙 |
|---|---|---|
| `presentation.request` | HTTP 입력과 Bean Validation | `*Request` |
| `presentation.response` | HTTP 응답 JSON | `*Response` |
| `application.command.dto` | 상태 변경 유스케이스 입력/결과 | `*Command`, `*Result` |
| `application.query.dto` | 조회 유스케이스 입력/결과 | `*Query`, `*Result` |
| `application.port.in` | Controller와 다른 모듈이 호출하는 공개 진입점 | `*Facade` |
| `application.port.in.dto` | 공개 Facade 계약 | `*Input`, `*Summary` 등 의미 중심 이름 |
| `application.port.out` | Application Service가 인프라에 요구하는 계약 | 기술 중립적인 Port 이름 |

```text
HTTP 요청
  -> presentation.request
  -> application.port.in.dto
  -> application.port.in.*Facade
  -> application.command/query.dto
  -> application.command/query.service
  -> presentation.response

다른 모듈 호출
  -> 대상 모듈 application.port.in.dto
  -> 대상 모듈 application.port.in.*Facade

외부 기술 호출
  -> application.command/query.service
  -> application.port.out
  -> infrastructure 구현체
```

Facade는 공개 DTO와 내부 Command/Query DTO를 변환하고 Service에 위임한다. 트랜잭션과 비즈니스 규칙은 `*CommandService`, `*QueryService`, Domain에 둔다. `port.out`은 다른 모듈이 호출하는 API가 아니라, Infrastructure가 구현하는 의존성 역전 지점이다.

### 조회와 DTO 생성 규칙

- Aggregate를 식별자로 조회하고 없으면 도메인 예외를 던지는 규칙은 `application.support.*Finder`에 둔다. Command/Query Service는 `Finder`를 주입받아 사용하며, 각 Service에 `getXxx`, `findRequiredXxx` private 메서드로 중복하지 않는다.
- 한 객체를 다른 DTO/Result로 단순 변환하는 생성 책임은 대상 DTO/Result에 둔다. 원본 객체가 있으면 `from(source)`, 필요한 값을 직접 조합하면 `of(...)` 정적 팩토리를 사용한다.
- Facade와 Service에는 `toXxx`, `resultOf`처럼 DTO를 단순 생성하는 private 변환 메서드를 두지 않는다. 검증, 여러 소스의 조합, 호출 순서 제어는 해당 계층에 남긴다.

## 6. 도메인과 영속성 분리

도메인 모델은 순수 Java다. JPA, Spring Data, Spring stereotype을 import하지 않는다.

```text
purchase/domain/model/Purchase.java
purchase/domain/repository/PurchaseRepository.java

purchase/infrastructure/persistence/
├── entity/PurchaseJpaEntity.java
├── repository/JpaPurchaseRepository.java
└── PurchaseRepositoryAdapter.java
```

`*JpaEntity`는 테이블/컬럼/인덱스/유니크 제약/JPA 생성 전략을 소유한다. `RepositoryAdapter`가 도메인 Repository를 구현하고, Entity와 Domain의 변환을 담당한다. JPA Entity와 Spring Data Repository는 해당 모듈의 `infrastructure` 밖으로 노출하지 않는다.

## 7. PostgreSQL과 스키마 관리

실행 DB는 PostgreSQL 하나를 사용한다. `app`이 DataSource, Flyway, JPA 설정을 소유하고, 도메인 모듈은 같은 DataSource를 공유한다.

```text
app/src/main/resources/
├── application.yml                 # 환경 변수 기반 PostgreSQL 연결 설정
└── db/migration/
    └── V1__initial_schema.sql       # 초기 스키마
```

- Flyway migration이 스키마의 유일한 변경 이력이다. 적용된 migration 파일을 수정하지 않고, 변경마다 다음 버전의 새 파일을 추가한다.
- Hibernate는 `ddl-auto: validate`로 Entity와 실제 스키마의 불일치만 검증한다. `create`, `create-drop`, `update`는 사용하지 않는다.
- 신규 테이블, 컬럼, 인덱스, 유니크 제약은 해당 JPA Entity 변경과 같은 작업에서 Flyway migration으로 추가한다.
- `purchases.active_purchase_key`의 유니크 제약은 활성 구매 상태만 중복을 막기 위한 DB 보호 장치다. PostgreSQL의 UNIQUE 제약은 NULL을 여러 개 허용하므로 만료·취소된 구매는 같은 회원과 판매 상품으로 다시 생성할 수 있다.

로컬 PostgreSQL은 다음 명령으로 실행한다.

```bash
cp .env.example .env
docker compose up -d
```

테스트에서 DB가 필요한 경우에는 H2를 사용하지 않고 Testcontainers PostgreSQL을 사용한다. 이 방식은 Flyway migration과 JPA 매핑을 실제 PostgreSQL에서 검증한다.

## 8. 이벤트 사용 기준

도메인 이벤트는 상태 변경이 완료됐다는 사실을 표현한다. Aggregate가 이벤트를 만들고, Application Service가 저장과 발행 시점을 조율한다. 외부 모듈이 구독할 이벤트는 `domain.event`의 `events` Named Interface로 공개한다.

```text
Purchase.reserve()
  -> Purchase 상태 변경
  -> PurchaseReserved 등록

PurchaseCommandService
  -> Purchase 저장
  -> 저장된 Purchase가 PurchaseReserved 생성
  -> PurchaseEventPublisher에 전달
  -> 트랜잭션 커밋 뒤 Spring 이벤트 발행
```

현재 `PurchaseReserved`는 커밋 뒤에만 발행된다. 트랜잭션이 롤백되면 이벤트는 소비되지 않는다. 후속 비즈니스 요구가 생긴 모듈만 `purchase::events` 의존성을 선언해 구독한다.

처음에는 프로세스 내부 이벤트를 사용한다. 전달 보장, 재시도, 소비자 독립 확장, 별도 서비스 추출이 실제로 필요해질 때 Outbox와 Kafka 도입을 검토한다.

## 9. 아키텍처 검증과 시각화

`app` 모듈의 테스트는 두 가지를 수행한다.

1. `ModularityTests`는 `ApplicationModules.verify()`로 금지된 모듈 참조, Named Interface 외부 접근, 순환 의존을 검증한다.
2. `ModularityDocumentationTests`는 Spring Modulith `Documenter`로 현재 코드에서 모듈 문서를 생성한다.

```bash
./gradlew test
```

`configuration`, `presentation`은 애플리케이션 조립과 HTTP 어댑터이며 비즈니스 애플리케이션 모듈이 아니다. 따라서 모듈 검증과 문서 생성 대상에서 제외한다.

문서 생성 결과는 Git 추적 대상이 아닌 다음 경로에 생성된다.

```text
app/build/spring-modulith-docs/
├── components.puml                 # 전체 모듈 관계 PlantUML
├── module-*.puml                   # 개별 모듈 의존 PlantUML
├── module-*.adoc                   # 모듈 캔버스
└── all-docs.adoc                    # 전체 모듈 문서
```

PlantUML 플러그인 또는 렌더러에서 `*.puml`을 열면 관계도를 볼 수 있다. 새 모듈, Named Interface, 모듈 간 참조를 추가한 뒤에는 반드시 `./gradlew test`를 실행해 검증과 문서 생성을 함께 갱신한다.

## 10. 첫 수직 슬라이스: 구매 예약

첫 구현 범위는 `POST /api/sale-products/{saleProductId}/purchases`다. `local`, `test` 프로필에서는 `X-Member-Id` 헤더를 임시 인증 수단으로 사용한다. OAuth 또는 회원가입은 이 흐름의 범위에 포함하지 않는다.

```text
X-Member-Id
  -> DevMemberAuthenticationFilter
  -> MemberPrincipal
  -> PurchaseController
  -> PurchaseCommandFacade
  -> MemberPurchaseEligibilityFacade(member::api)
  -> SaleProductPurchaseFacade(sale::api)
  -> InventoryCommandFacade(inventory::api)
  -> Purchase 저장
```

`PurchaseCommandService`가 하나의 트랜잭션 안에서 회원 상태 확인, 판매 가능 여부 확인, 재고 예약, 구매 저장을 조율한다. 같은 회원과 판매 상품으로 활성 구매를 한 번 더 저장하면 `active_purchase_key` 유니크 제약으로 실패하고, 트랜잭션 전체가 롤백되어 재고 예약도 남지 않는다.

## 11. 두 번째 수직 슬라이스: 결제 시도와 예약 재고 전이

실제 PG 연동 전까지는 결제 성공·실패를 API로 시뮬레이션한다. `Payment`는 구매당 하나가 아니라 결제 시도 이력이며, 재시도마다 새로운 `paymentRequestKey`로 `READY` Payment를 생성한다.

```text
POST /api/purchases/{purchaseId}/payments        # 결제 시도 생성
POST /api/payments/{paymentId}/fail              # 결제 실패 시뮬레이션
POST /api/payments/{paymentId}/approve           # 결제 승인 시뮬레이션
POST /api/purchases/{purchaseId}/cancel          # 예약 취소
```

```text
결제 실패
  -> Payment: READY -> FAILED
  -> Purchase: PENDING_PAYMENT 유지
  -> Inventory: reserved 유지
  -> 새 결제 시도 가능

결제 승인
  -> Payment: READY -> APPROVED
  -> Purchase: PENDING_PAYMENT -> PAID
  -> Inventory: reserved -> sold

예약 취소 또는 만료
  -> Purchase: PENDING_PAYMENT -> CANCELLED 또는 EXPIRED
  -> Inventory: reserved -> available
```

예약 만료는 30초 주기의 단일 프로세스 스케줄러가 처리한다. 다중 인스턴스 배포가 필요한 시점에는 중복 실행 방지와 분산 스케줄링 방식을 별도로 결정한다.

`payment` 모듈은 `purchase::api`, `inventory::api`의 공개 Facade와 DTO만 사용한다. `Purchase`, `Inventory`의 JPA Entity나 Repository에는 직접 접근하지 않는다.

## 12. 테스트 범위

- `purchase`, `inventory`, `payment` 모듈의 Domain 단위 테스트는 상태 전이 규칙을 검증한다.
- `PaymentLifecycleIntegrationTest`는 결제 실패 후 재시도, 승인 시 구매 완료와 재고 판매 전환, 예약 취소 후 재예약을 PostgreSQL Testcontainer에서 검증한다.
- `ModularityTests`는 `payment -> purchase/inventory` 의존이 각 모듈의 `api` Named Interface로만 향하는지 검증한다.
