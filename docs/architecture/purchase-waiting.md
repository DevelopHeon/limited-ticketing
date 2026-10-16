# 구매 대기 계약과 처리 흐름

`purchasewaiting`은 구매 진입 요청의 수명 주기를 소유한다. PostgreSQL은 요청·순번·처리 권한의 원장, Redis ZSET은 입장 선택과 위치 조회를 위한 재구성 가능한 저장소다. 실제 구매·재고 예약은 PurchaseReservationPort와 PurchaseModuleAdapter를 거쳐 `purchase` 공개 Input Port에 위임한다. 선택 이유와 비용은 [ADR 0006](../adr/0006-durable-queue-admission-and-attempts.md)에 기록한다.

## 접수와 조회

- 상품별 `WAITING + PROCESSING` 최대 10,000건이다. 초과는 429 `QUEUE_CAPACITY_EXCEEDED`다.
- 회원·상품별 새 요청은 1초당 1회다. 초과는 429 `QUEUE_RATE_LIMITED`다. 이미 수락한 같은 요청 키의 재전송은 기존 원장을 반환한다. 동시 재전송도 상품 잠금 아래 재확인한다.
- 동일 회원·상품의 활성 대기는 DB partial unique index로 하나만 허용한다. 속도 제한 시간이 지나도 다른 키로 중복 대기하면 409다.
- 수락 전 Redis 불가는 503 `QUEUE_UNAVAILABLE`이며 원장을 생성하지 않는다. 429/503은 `Retry-After: 1`을 보낸다.
- DB 커밋 뒤 Redis 등록 실패는 202를 유지하고 복구가 등록한다. 상태 조회는 소유권 확인 후 DB 기준으로 응답한다. Redis 장애 시 WAITING의 position은 null이다.
- 직접 예약 HTTP는 `baseline` profile에서만 제공한다. 기본 실행의 구매 진입은 대기 API를 사용한다. 내부 예약 Facade·결제·취소는 그대로 사용한다.

## 원장과 Redis 키

`reservation_queue_entries`는 UUID, 회원·판매·요청 키, 순번, 상태, 구매 ID·실패 코드와 생성/처리/완료 시각을 저장한다. PROCESSING은 `processing_token`과 `lease_until`도 저장한다. `reservation_queue_sequences`는 상품별 마지막 순번을 보존한다.

```text
waiting:sale:{saleId}             ZSET: score=DB queue_sequence, member=entry UUID
processing:sale:{saleId}          ZSET: score=claim timestamp, member=entry UUID
processing-tokens:sale:{saleId}   HASH: entry UUID → attempt token
admission:sale:{saleId}:member:{memberId}  SET NX, TTL 1초
```

순번은 PostgreSQL upsert로 발급하며 `1..2^53-1` 범위다. Redis `sequence:sale:*`는 더 이상 발급에 쓰지 않는 이전 키이며 정리 대상으로만 남긴다. 기존 동점도 복구하기 위해 DB cursor는 `(queue_sequence, UUID)`다. ZSET은 score와 UUID 문자열 순으로 선택한다. 병렬 완료의 FIFO나 Redis 복원 전에 이미 입장한 작업의 취소는 보장하지 않는다.

## C3 · 구매 대기 컴포넌트

```mermaid
flowchart TB
    client["HTTP Client"]
    subgraph app["Spring Boot 애플리케이션 · 하나의 프로세스"]
        subgraph waiting["purchasewaiting 모듈"]
            http["Input Adapter<br/>HTTP Controller"]
            entry["Application 진입<br/>Input Port → Facade / Service"]
            command["UseCase → Service<br/>접수·멱등성·상태 조회"]
            worker["Input Adapter<br/>Scheduler"]
            workerEntry["Application 진입<br/>Worker Port → Worker Service"]
            process["Claim / Process UseCase<br/>Service와 Domain 규칙"]
            recovery["Recovery Service"]
            adapters["Domain Repository / Output Port<br/>Persistence / Redis Adapter"]
            http --> entry
            entry --> command
            command --> adapters
            worker --> workerEntry
            workerEntry -->|"claim과 예약 처리 위임"| process
            workerEntry -->|"주기적 복구"| recovery
            process --> adapters
            recovery --> adapters
            purchaseAdapter["PurchaseReservationPort<br/>PurchaseModuleAdapter"]
            process -->|"실제 구매 예약"| purchaseAdapter
        end
        purchase["purchase 모듈 공개 계약"]
        purchaseAdapter --> purchase
    end
    client -->|"접수·상태 조회 HTTP"| http
    db[("PostgreSQL")]
    redis[("Redis")]
    adapters -->|"대기 요청 상태 영속화"| db
    adapters -->|"claim·위치·복원"| redis
    purchase -->|"구매·재고 트랜잭션"| db
```

이 그림은 구매 대기 처리에서 독립적으로 바뀌는 책임만 묶어 보여 준다. 각 컴포넌트는 프로세스나 배포 단위가 아니다. 실제 구현의 기준은 [Scheduler](../../purchasewaiting/src/main/java/com/example/limitedticketing/purchasewaiting/infrastructure/adapter/in/scheduler/PurchaseWaitingScheduler.java)와 [Application Service](../../purchasewaiting/src/main/java/com/example/limitedticketing/purchasewaiting/application/service)다. Repository·출력 Port의 의존 방향은 [모듈 내부 설계](module-boundaries.md#레이어-구조)를 따른다.

## 처리 흐름

1. 기존 요청 키면 원장을 반환한다. 구매 자격을 확인하고 상품별 DB advisory transaction lock 아래 동시 재전송·속도·수용량을 판정한다.
2. DB 순번을 발급해 WAITING을 저장한다. 커밋 후 Redis에 등록한다.
3. 입력 Scheduler → Worker Port → Worker Service → Dispatch UseCase/Service가 실행 여유를 확인한다. 상품 선택 시작점을 순환한다.
4. [claim Lua](../../purchasewaiting/src/main/resources/redis/queue-claim.lua)가 등록된 대기 중 앞 순번을 processing으로 옮기고 토큰을 기록한다.
5. DB 상품 잠금·처리 수 상한 확인 후 `status=WAITING` 조건부 갱신으로 토큰·lease를 저장한다. 실행 거부는 해당 토큰의 권한만 반환하고 원래 순번으로 재대기한다.
6. 실제 처리는 행 잠금 아래 상태·토큰·lease를 확인한다. `REQUIRES_NEW` 예약이 성공하면 RESERVED, 품절은 SOLD_OUT, 다른 업무 실패는 FAILED로 확정한다. 시스템 오류는 PROCESSING을 유지한다.
7. 결과 커밋 후 Redis 슬롯을 같은 토큰으로 반환한다. 구매 커밋과 결과 기록 사이 중단은 기존 구매 멱등성으로 복구한다.

전체 Executor는 기본 10개 스레드·대기 버퍼 0이다. 상품별 PROCESSING 상한도 기본 10이며 DB가 최종 방어선이다. 이 값은 서로 별도 설정이다. 다중 앱의 전역 제한은 아직 구현하지 않았다.

## 복구

- 만료 PROCESSING을 `FOR UPDATE SKIP LOCKED`로 읽는다. 실행 중 행 잠금은 lease가 지났어도 건너뛴다. 회수한 행을 WAITING으로 커밋한 뒤 이전 토큰으로 Redis를 재대기한다.
- 상품별 WAITING을 복합 cursor로 끝까지 읽는다. 위치가 없으면 별도 트랜잭션에서 행을 잠그고 여전히 WAITING인지 확인한 뒤 원래 score로 복원한다.
- Redis 구성원 중 DB에 없거나 이미 최종 상태인 항목을 정리한다. CLOSED/없는 판매이고 활성 요청도 없으면 waiting·processing·token·이전 sequence 키를 제거한다.

Redis 영속화·replica는 현재 로컬 구성에 없다. 유실 후 순번과 대기는 DB에서 복구하지만 복구 전 위치 조회·신규 접수·진행 속도는 영향을 받는다. DB 자동 failover와 여러 프로세스의 장애 평가는 후속 범위다.

## 적용과 검증

V6 적용 전 이전 앱 Worker를 중지한다. 기존 마지막 순번은 원장 MAX로 초기화하고, 기존 PROCESSING은 새 토큰과 기존 처리 시작 시각+30초 lease로 변환한다. 혼합 버전 Worker를 동시에 실행하지 않는다.

Worker 10개가 결과 확정까지 외부 연결을 유지하며 내부 예약에도 연결을 사용하므로 기본 Hikari 풀은 30이다. 환경 설정과 연결 여유 조건은 [개발 안내](../development/README.md)를 따른다. 실행 중인 긴 트랜잭션은 lease만으로 강제 회수하지 않는다.

[업무·아키텍처 테스트 기준](../development/testing.md)과 [2단계 평가](../evaluations/load-testing/2026-10-16-stage-2-queue-consistency.md)에 검증 범위와 한계를 기록한다.


## Port/Adapter 구조

업무 명칭은 PurchaseWaiting으로 통일했다. [ADR 0007](../adr/0007-purchasewaiting-ports-and-adapters.md)의 입력·출력 infrastructure 배치는 유지하고, [ADR 0008](../adr/0008-application-entry-orchestration.md)에 따라 Controller·Scheduler 자체를 입력 Adapter로 본다. 업무 조합과 Port 구현은 Application이 맡는다. 다른 모듈도 동일한 입력·업무·출력 책임을 사용한다.

```text
purchasewaiting/
├── application/
│   ├── port/in/                 Input Port와 외부 Input/Output DTO
│   │   └── worker/              모듈 내부 Worker Port
│   ├── port/out/                외부 기능 계약과 작업 DTO
│   ├── usecase/                 업무 interface와 Command/Query/Result
│   ├── facade/                  공개 Port 구현·여러 UseCase 조합
│   └── service/                 UseCase 구현·트랜잭션, 단순 진입 Port 연결
├── domain/model/                PurchaseWaitingEntry·PurchaseWaitingStatus
├── domain/exception/            PurchaseWaitingException
├── domain/repository/           PurchaseWaitingEntryRepository·저장 결과
└── infrastructure/adapter/
    ├── in/                      Controller·Scheduler 등 Input Adapter
    │   ├── web/                 Controller·HTTP Response
    │   └── scheduler/           Scheduler
    └── out/                     Driven Adapter
        ├── persistence/         JPA Entity·Repository·DB 순번
        ├── redis/               대기·복구·입구 제한
        ├── module/              Member/Sale/Purchase 공개 계약 연결
        ├── execution/           제한된 ThreadPool
        └── id/                  UUID 발급
```

### 호출과 책임

```text
HTTP Controller → PurchaseWaitingCommandPort → PurchaseWaitingCommandFacade
 → EnqueuePurchaseWaitingUseCase → EnqueuePurchaseWaitingService
 → Domain Repository → PurchaseWaitingPersistenceAdapter

HTTP Controller → PurchaseWaitingQueryPort → PurchaseWaitingQueryService
 → FindPurchaseWaitingUseCase → FindPurchaseWaitingService

Scheduler → PurchaseWaitingWorkerPort → PurchaseWaitingWorkerService
 → Dispatch/Recover UseCase → Service → Output Port → Adapter

Process Service → ReserveWaitingPurchaseUseCase → ReserveWaitingPurchaseService
 → PurchaseReservationPort → PurchaseModuleAdapter → purchase의 Input Port
```

Controller는 HTTP 경로·헤더·인증 주체를 Input으로 변환하고 HTTP 응답을 만든다. Scheduler는 실행 주기를 맡는다. 이들은 Input Port를 호출하며 내부 UseCase나 구현 Service를 직접 참조하지 않는다.

PurchaseWaitingCommandFacade가 접수 UseCase의 UUID를 받아 조회 UseCase를 호출한다. Facade 전체를 transaction으로 감싸지 않아 접수 커밋과 Redis afterCommit 반영 후 조회하는 기존 경계를 유지한다. 단순 조회와 Worker 진입은 각각 PurchaseWaitingQueryService·PurchaseWaitingWorkerService가 Port를 구현해 내부 UseCase로 연결한다. 조합이 없는 기능에 Facade를 일괄 추가하지 않는다.

같은 모듈의 Application 내부 업무 호출은 UseCase interface를 사용하고, 다른 모듈과 입력 Adapter는 Input Port를 호출한다. Aggregate 저장소 호출은 Domain Repository를, Redis·다른 모듈 기능 등 Application의 외부 기능 호출은 Output Port를 사용한다. Worker Port는 Scheduler의 코어 진입 계약이며 다른 모듈에 공개하는 api는 아니다.

Process Service가 처리 행 잠금·토큰/lease 확인·예약 결과 분류·Domain 전이·결과 저장을 맡으며 별도 Completion Service는 없다. Reserve Service의 REQUIRES_NEW와 Restore Service의 별도 transaction은 유지한다. Recovery의 만료 조회·커밋 후 Redis 보충·현재 WAITING 재확인도 유지한다.

Finder/Support는 없다. Aggregate 조회는 Domain Repository, 소유권을 확인한 업무 조회는 Find UseCase, 상태 규칙은 Domain이 담당한다. 잠금 조회는 조회라는 이유만으로 read-only transaction으로 바꾸지 않는다.

의존성 필드·생성자 매개변수·테스트 의존성 변수는 타입의 역할 접미사를 유지한다. 예: `purchaseWaitingCommandPort`, `findPurchaseWaitingUseCase`, `purchaseWaitingJpaRepository`, `processPurchaseWaitingService`. 다른 모듈 Input Port를 받는 변수도 `purchaseCommandPort`처럼 명명한다.

### DTO 경계

| 위치 | 계약 |
|---|---|
| `port.in.dto` | Enqueue/Find Input, PurchaseWaitingOutput. Domain과 HTTP에 의존하지 않음 |
| `usecase.dto.command` | 접수·claim·처리·예약의 내부 업무 입력 |
| `usecase.dto.query` | 회원 소유권을 포함한 상태 조회 입력 |
| `usecase.dto.result` | PurchaseWaitingResult. 기존 두 단계 Result를 합침 |
| `port.out.dto` | Redis 작업용 PurchaseWaitingClaim |
| `domain.repository` | Aggregate 영속 계약과 PurchaseWaitingInsertResult |
| `adapter.in.web.response` | PurchaseWaitingResponse. 기존 JSON 필드 유지 |

내부 DTO의 waitingEntryId는 HTTP Response의 queueEntryId로 변환한다. URL·응답 필드·오류 코드·DB 이름·Redis 키·Lua·환경 변수는 바꾸지 않았다. Input Port와 DTO만 Modulith의 공개 api Named Interface이고 Worker Port는 내부 하위 패키지다.

Aggregate 영속성 계약의 소유 기준은 [ADR 0009](../adr/0009-domain-repository-ownership.md), 전체 모듈의 의존 방향과 검사 기준은 [모듈 설계](module-boundaries.md#레이어-구조) 및 [테스트 기준](../development/testing.md#아키텍처-검사-정책)을 따른다.
