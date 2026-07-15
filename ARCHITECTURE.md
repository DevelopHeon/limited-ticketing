# Limited Ticketing 아키텍처

## 1. 프로젝트 목적

이 프로젝트는 유료 할인 쿠폰처럼 한정 수량으로 판매되는 상품의 구매 흐름을 모델링한다.

- 회원은 판매 상품별로 1개만 구매할 수 있다.
- 판매 상품에는 판매 기간, 가격, 고정 재고가 있다.
- 결제 전에 재고를 예약하고, 결제 결과에 따라 재고를 확정하거나 복구한다.
- 모듈러 모놀리스로 시작하며, 측정된 요구가 생길 때만 Redis, Kafka, 별도 배포 단위를 도입한다.

## 2. 아키텍처 결정

### 모듈러 모놀리스 우선

프로젝트는 Spring Boot 애플리케이션 하나이자 배포 단위 하나다.

```text
member + sale + inventory + purchase + payment
                         |
                       app.jar
                         |
              컨테이너 또는 Pod 하나로 배포
```

Gradle 하위 프로젝트는 코드와 빌드 경계다. 현재는 독립 서비스나 독립 배포 단위가 아니다.

### Gradle 멀티 프로젝트와 Spring Modulith

Gradle은 물리적인 모듈을 분리하고, Spring Modulith는 논리적인 애플리케이션 모듈 경계를 검증한다.

```text
limited-ticketing
├── app          # Spring Boot 실행과 런타임 의존성
├── shared       # 좁은 범위의 공통 값 타입
├── member
├── sale
├── inventory
├── purchase
└── payment
```

- `settings.gradle`에서 Gradle 하위 프로젝트를 선언한다.
- 루트 `build.gradle`에서 의존성 버전, BOM, 저장소, Java 버전, 테스트 기본 설정을 관리한다.
- 각 하위 모듈은 필요한 의존성만 버전 없이 선언한다.
- `app`만 `@SpringBootApplication`, H2, Spring Boot JPA Starter를 가진다.
- `app`은 Security, DB 연결, HTTP 예외 처리처럼 애플리케이션 전체를 조립하는 런타임 구성을 소유한다.
- `app/src/test/.../ModularityTests.java`가 `ApplicationModules.verify()`를 호출해 잘못된 모듈 의존성과 순환 의존성을 검증한다.

## 3. 모듈 의존 규칙

```text
app -> member, sale, inventory, purchase, payment, shared
sale -> shared
purchase -> shared
payment -> shared
member -> 다른 비즈니스 모듈 없음
inventory -> 다른 비즈니스 모듈 없음
shared -> 다른 비즈니스 모듈 없음
```

현재 모델은 모듈 간 참조에 ID만 사용한다. 예를 들어 `Purchase`는 `memberId`, `saleProductId`를 가지며 `Member`, `SaleProduct` 객체를 직접 참조하지 않는다.

향후 코드 작성 규칙은 다음과 같다.

1. 다른 모듈의 `domain`, `infrastructure` 패키지에 직접 접근하지 않는다.
2. 동기 호출은 대상 모듈의 `application.port.in`에 공개된 Facade를 통해서만 한다.
3. 비동기 후속 처리는 도메인 이벤트 또는 통합 이벤트로 연결한다.
4. `shared`에는 모든 모듈에서 같은 의미를 가지는 개념만 둔다. Aggregate, API DTO, Repository, 기능별 이벤트는 넣지 않는다.
5. Gradle 의존성이 선언돼 있어도 내부 타입 사용 권한까지 생기는 것은 아니다. 공개 API가 실제 계약이다.

## 4. 모듈별 책임

| 모듈 | Aggregate와 모델 | 책임 |
|---|---|---|
| `member` | `Member`, `MemberStatus` | 회원 식별과 구매 가능 여부를 관리한다. |
| `sale` | `Product`, `SaleProduct`, `SaleStatus` | 상품 혜택 정보, 판매 기간, 판매 가격, 구매 제한 정책을 관리한다. |
| `inventory` | `Inventory` | 구매 가능, 예약, 판매 완료 수량의 상태 전이를 관리한다. |
| `purchase` | `Purchase`, `PurchaseStatus` | 1개 구매 예약, 만료, 취소, 구매 완료 상태를 관리한다. |
| `payment` | `Payment`, `PaymentStatus` | 결제 요청 멱등성과 승인 또는 실패 상태를 관리한다. |
| `shared` | `Money`, `SalePeriod` | 모듈 간 공통 값 타입만 제공한다. |

### 애플리케이션 공통 구성

Security, DB 설정, HTTP 공통 예외 처리는 별도 `global` Gradle 모듈이 아니라 실행 모듈인 `app`에 둔다. `global`이라는 이름은 도메인 규칙과 기술 설정이 계속 쌓이는 공용 저장소가 되기 쉽다.

```text
app
└── src/main/java/com/example/limitedticketing
    ├── LimitedTicketingApplication
    ├── configuration
    │   ├── security
    │   │   └── SecurityConfig
    │   └── persistence
    │       └── JpaConfig
    └── presentation
        └── exception
            ├── ApiErrorResponse
            └── GlobalExceptionHandler
```

- `SecurityConfig`: `SecurityFilterChain`, 인증 필터, 인가 규칙을 구성한다. 현재는 인증 기능이 없으므로 모든 요청을 허용한다. 도메인별 권한 판단은 각 모듈의 Application 또는 Domain 규칙에 둔다.
- `JpaConfig`: Spring Data JPA Auditing을 활성화한다. DataSource 연결은 Spring Boot 자동 설정을 사용한다.
- `GlobalExceptionHandler`: 모든 Controller의 예외를 HTTP 오류 응답으로 변환한다. 도메인 예외 자체는 각 도메인 모듈이 소유한다.
- `shared`: HTTP, Spring Security, JPA 설정을 넣지 않는다. 공통 값 타입과 기술 비의존적인 예외 기반 타입만 제한적으로 둔다.

향후 API 애플리케이션과 Batch 애플리케이션처럼 실행 모듈이 둘 이상 생기고 같은 웹/보안 정책을 실제로 공유해야 할 때만, 목적이 드러나는 `support-web`, `support-security`, `support-persistence` 모듈 분리를 검토한다. 이 경우에도 도메인 모듈은 이 지원 모듈에 의존하지 않는다.

### 주요 소유 경계

- `SaleProduct`는 회원당 구매 제한이 1개라는 정책을 가진다.
- `Inventory`는 재고 수량을 소유한다. `SaleProduct`가 재고를 직접 차감하면 안 된다.
- `Purchase`는 회원의 구매 상태 전이를 소유한다.
- `Payment`는 내부 결제 처리 상태를 소유한다. 실제 PG사 연동은 현재 범위에 포함하지 않는다.
- `member_id + sale_product_id` 유니크 제약은 1인 1구매 규칙을 위한 DB 수준의 보호 장치다. 도메인 검증을 대체하지는 않는다.

## 5. 현재 도메인 상태

### 판매 상태

```text
SCHEDULED -> OPEN -> CLOSED
```

### 구매 상태

```text
PENDING_PAYMENT -> PAID
                -> EXPIRED
                -> CANCELLED
```

### 결제 상태

```text
READY -> APPROVED
      -> FAILED
```

### 재고 상태 전이

```text
reserve(quantity)
  availableQuantity -= quantity
  reservedQuantity += quantity

confirmReservation(quantity)
  reservedQuantity -= quantity
  soldQuantity += quantity

releaseReservation(quantity)
  reservedQuantity -= quantity
  availableQuantity += quantity
```

## 6. 도메인 모델과 영속성 모델 분리

도메인 모델은 순수 Java 코드다. JPA, Spring Data, Spring stereotype을 import하지 않는다.

```text
purchase/domain/model/Purchase.java
purchase/domain/repository/PurchaseRepository.java
```

영속성 세부 사항은 infrastructure에 둔다.

```text
purchase/infrastructure/persistence/
├── entity/PurchaseJpaEntity.java
├── repository/JpaPurchaseRepository.java
└── PurchaseRepositoryAdapter.java
```

현재 모든 도메인 모듈은 같은 패턴을 적용한다.

```text
도메인 Aggregate
  -> 도메인 Repository 인터페이스
  -> Repository Adapter
  -> Spring Data JPA Repository
  -> JPA Entity
```

`*JpaEntity`는 테이블명, 컬럼, JPA 어노테이션, DB 유니크 제약, 생성 ID를 소유한다. `from(domain)`, `toDomain()`으로 변환하며, 도메인 모델은 `reconstitute(...)` 팩터리로 저장 상태를 복원한다.

도메인 모델에 JPA 어노테이션을 다시 추가하지 않는다. `*JpaEntity`는 해당 모듈의 infrastructure 패키지 밖으로 노출하지 않는다.

## 7. 도메인 모듈의 목표 패키지 구조

필요한 코드가 생길 때만 패키지를 추가한다. 빈 패키지 트리를 미리 만들지 않는다. 아래는 `purchase` 모듈의 기능이 구현됐다고 가정한 전체 예시다.

`publicapi`라는 최상위 패키지는 만들지 않는다. 모듈 외부에 공개하는 API를 `application.port.in`, 모듈이 인프라에 요구하는 API를 `application.port.out`에 두어 Port의 방향을 같은 문맥으로 표현한다.

```text
purchase
├── presentation                                  # HTTP 어댑터
│   ├── controller
│   │   └── PurchaseController
│   ├── request
│   │   ├── CancelPurchaseRequest
│   │   └── ReservePurchaseRequest
│   └── response
│       ├── PurchaseResponse
│       └── PurchaseReservationResponse
│
├── application                                   # 유스케이스 실행과 트랜잭션 경계
│   ├── command                                   # 상태 변경 유스케이스
│   │   ├── dto
│   │   │   ├── CancelPurchaseCommand
│   │   │   ├── PurchaseReservationResult
│   │   │   └── ReservePurchaseCommand
│   │   └── service
│   │       └── PurchaseCommandService
│   ├── query                                     # 상태를 바꾸지 않는 조회 유스케이스
│   │   ├── dto
│   │   │   ├── FindMemberPurchasesQuery
│   │   │   ├── FindPurchaseQuery
│   │   │   ├── PurchaseDetailResult
│   │   │   └── PurchaseListResult
│   │   └── service
│   │       └── PurchaseQueryService
│   └── port
│       ├── in                                    # 공개 API, inbound port
│       │   ├── PurchaseCommandFacade
│       │   ├── PurchaseQueryFacade
│       │   └── dto
│       │       ├── CancelPurchaseInput
│       │       ├── FindMemberPurchasesInput
│       │       ├── FindPurchaseInput
│       │       ├── PurchaseReservation
│       │       ├── PurchaseSummary
│       │       └── ReservePurchaseInput
│       └── out                                   # 인프라/외부 시스템 의존성, outbound port
│           ├── Clock
│           └── PurchaseEventPublisher
│
├── domain                                        # 순수 비즈니스 규칙
│   ├── event
│   │   ├── PurchaseCancelled
│   │   ├── PurchaseCompleted
│   │   ├── PurchaseExpired
│   │   └── PurchaseReserved
│   ├── model
│   │   ├── Purchase
│   │   └── PurchaseStatus
│   ├── repository
│   │   └── PurchaseRepository
│   └── service
│       └── PurchaseDomainService
│
└── infrastructure                                # 기술 구현체
    ├── messaging
    │   └── SpringPurchaseEventPublisher
    └── persistence
        ├── entity
        │   └── PurchaseJpaEntity
        ├── repository
        │   └── JpaPurchaseRepository
        └── PurchaseRepositoryAdapter
```

### 패키지별 역할과 DTO 경계

| 패키지 | 역할 | 외부에 노출되는가 | DTO 규칙 |
|---|---|---|---|
| `presentation.request` | HTTP 요청 본문, 경로 변수 등을 표현하고 Bean Validation을 적용한다. | HTTP 클라이언트 | `*Request` |
| `presentation.response` | HTTP 상태와 JSON 응답 형태를 표현한다. | HTTP 클라이언트 | `*Response` |
| `application.command.dto` | 상태 변경 유스케이스의 입력과 내부 실행 결과를 둔다. | 모듈 내부 | 입력은 `*Command`, 결과는 `*Result` |
| `application.command.service` | 상태 변경 유스케이스와 트랜잭션 경계를 담당한다. | 모듈 내부 | `*CommandService` |
| `application.query.dto` | 조회 유스케이스의 입력과 내부 실행 결과를 둔다. | 모듈 내부 | 입력은 `*Query`, 결과는 `*Result` |
| `application.query.service` | 상태를 변경하지 않는 조회 유스케이스를 담당한다. | 모듈 내부 | `*QueryService` |
| `application.port.in` | Controller와 다른 모듈이 호출하는 공개 유스케이스 진입점이다. Facade는 inbound adapter 역할을 하며 공개 DTO와 내부 DTO를 변환한다. | Controller, 다른 비즈니스 모듈 | `*Facade` |
| `application.port.in.dto` | Facade 호출에 사용하는 공개 계약 DTO다. 다른 모듈뿐 아니라 Controller도 이 타입으로 변환해 호출한다. HTTP DTO와 Application DTO를 재사용하지 않는다. | Controller, 다른 비즈니스 모듈 | `*Input`, `*Summary`, `*Reservation` 등 의미 중심 이름 |
| `application.port.out` | Command/Query 서비스가 인프라 또는 외부 시스템에 요구하는 계약이다. Infrastructure Adapter가 구현한다. | 모듈 내부 | Port 전용 타입이 필요한 경우에만 추가한다. |

DTO 변환은 경계에서만 수행한다.

```text
HTTP Request
  -> presentation.request.ReservePurchaseRequest
  -> application.port.in.dto.ReservePurchaseInput
  -> application.port.in.PurchaseCommandFacade
  -> application.command.dto.ReservePurchaseCommand
  -> application.command.dto.PurchaseReservationResult
  -> presentation.response.PurchaseReservationResponse

다른 모듈의 조회 호출
  -> application.port.in.dto.FindPurchaseInput
  -> application.port.in.PurchaseQueryFacade
  -> application.query.dto.FindPurchaseQuery
  -> application.query.dto.PurchaseDetailResult
  -> application.port.in.dto.PurchaseSummary

외부 기술 호출
  -> application.command.service.PurchaseCommandService
  -> application.port.out.PurchaseEventPublisher
  -> infrastructure.messaging.SpringPurchaseEventPublisher
```

`PurchaseCommandFacade`와 `PurchaseQueryFacade`는 공개 진입점이다. 각 Facade는 `port.in.dto`를 `command.dto` 또는 `query.dto`로 변환한 뒤 해당 Service에 위임하고, Service의 `*Result`를 다시 공개 DTO로 변환한다. Facade에는 트랜잭션과 비즈니스 규칙을 두지 않는다. 이 책임은 `PurchaseCommandService`, `PurchaseQueryService`가 가진다.

```java
public class PurchaseCommandFacade {

    private final PurchaseCommandService purchaseCommandService;

    public PurchaseCommandFacade(PurchaseCommandService purchaseCommandService) {
        this.purchaseCommandService = purchaseCommandService;
    }

    public PurchaseReservation reserve(ReservePurchaseInput input) {
        PurchaseReservationResult result = purchaseCommandService.reserve(
            new ReservePurchaseCommand(input.memberId(), input.saleProductId())
        );

        return new PurchaseReservation(result.purchaseId());
    }
}

public class PurchaseQueryFacade {

    private final PurchaseQueryService purchaseQueryService;

    public PurchaseQueryFacade(PurchaseQueryService purchaseQueryService) {
        this.purchaseQueryService = purchaseQueryService;
    }

    public PurchaseSummary find(FindPurchaseInput input) {
        PurchaseDetailResult result = purchaseQueryService.find(
            new FindPurchaseQuery(input.purchaseId())
        );

        return new PurchaseSummary(result.purchaseId(), result.status());
    }
}
```

`application.port.out`의 `PurchaseEventPublisher`, `Clock`은 다른 모듈이 호출하기 위한 API가 아니다. Infrastructure가 구현하는 내부 의존성 역전 지점이다. DB 저장소처럼 도메인 의미가 강한 `PurchaseRepository`는 `domain.repository`에 둔다. 실제 PG사 연동 계획이 없으므로 현재 `PaymentGateway` Port는 추가하지 않는다.

## 8. 이벤트 전략

도메인 이벤트는 이미 발생한 도메인 사실을 표현한다.

```text
Purchase.reserve()
  -> Purchase 상태 변경
  -> PurchaseReserved 이벤트 등록

PurchaseCommandService
  -> Purchase 저장
  -> 저장 성공 뒤 등록된 이벤트 발행
```

상태 변화의 의미를 아는 도메인이 이벤트를 만든다. Application Service는 트랜잭션과 발행 시점을 조율한다. 모든 모듈 이벤트를 `if`나 `switch`로 분기해 처리하는 중앙 관리자가 되면 안 된다.

초기 이벤트 후보는 다음과 같다.

- `PurchaseReserved`
- `PurchaseExpired`
- `PaymentApproved`
- `PaymentFailed`
- `InventoryReleased`

처음에는 프로세스 내부의 트랜잭션 이벤트 처리를 사용한다. 전달 보장, 재시도, 소비자 독립 확장, 서비스 추출 요구가 생길 때 Outbox와 Kafka를 추가한다. Kafka를 쓴다는 사실만으로 MSA가 되는 것은 아니다.

## 9. 다음 구현 순서

1. `purchase` 흐름에 필요한 `member`, `sale`, `inventory`의 공개 API를 정의한다.
2. `PurchaseCommandService`, `PurchaseQueryService`와 구매 예약 Command/Result를 추가한다.
3. 회원 검증, 판매 검증, 재고 예약, 구매 저장을 동기 흐름으로 먼저 구현한다.
4. 외부 PG 연동 없이 결제 승인·실패 상태 전이와 테스트용 결제 유스케이스를 추가한다.
5. 구매 만료와 재고 복구를 구현한다.
6. 도메인 이벤트 발행과 프로세스 내부 Listener를 추가한다.
7. k6로 경합과 처리량을 측정한 뒤 Redis 또는 Kafka 도입 여부를 결정한다.

## 10. 검증 방법

전체 빌드와 아키텍처 검증은 다음 명령어로 실행한다.

```bash
./gradlew test
```

모듈 간 의존성을 추가하기 전에 아래 질문에 답한다.

1. 이 규칙 또는 상태를 실제로 소유하는 모듈은 어디인가?
2. 현재 유스케이스에 반드시 필요한 동기 호출인가, 이후 처리해도 되는 후속 작업인가?
3. 호출자는 공개 API를 사용해야 하는가, 이벤트를 발행해야 하는가?
4. 이 타입은 현재 모듈에 있어야 하는가, 아니면 잘못 `shared`에 들어간 것인가?
