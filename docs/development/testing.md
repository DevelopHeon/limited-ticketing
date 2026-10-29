# 테스트 책임과 검증

모듈러 모놀리스에서도 각 업무 모듈에 Domain·Application Service 단위 테스트를 둔다. 실행 모듈 `app`은 실제 저장소와 모듈 연결을 사용하는 통합 테스트를 맡는다. 클래스가 존재한다는 사실보다 업무 규칙과 실패 시 부수효과를 검증한다.

## 자동 검증

```bash
./gradlew check
```

| 검증 | 목적 |
|---|---|
| Checkstyle | 모든 모듈의 main/test에서 `var`와 `setXxx` 메서드 선언 금지 |
| `ModularityTests` | 전체 업무 모듈 감지, Named Interface를 통한 공개 계약 사용, 금지된 모듈 참조와 순환 의존 검증 |
| `LayeringArchitectureTests` | 모듈 내부 Domain·Application·입력/출력 Adapter 방향, Port/UseCase/Domain Repository 추상 계약, 공개 DTO 노출 검증 |
| Domain 단위 테스트 | 회원 구매 자격, 판매 시간 경계, 재고 수량 보존, 구매·결제·대기 요청의 허용 상태 전이 검증 |
| Application Service 단위 테스트 | 각 업무 모듈에서 실제 Domain 객체와 고정 Clock을 사용해 구매 멱등성, 결제 소유권·만료, 대기 결과 분류와 시스템 오류 전파 검증 |
| `PurchaseConcurrencyIntegrationTest` | 동시 예약에서 정확한 재고 수량과 활성 구매 제약 검증 |
| `PurchaseLifecycleConcurrencyIntegrationTest` | 동일 구매의 동시 승인·취소 시 상태 전이 단일 성공과 다른 회원의 예약 재고 보호 |
| `PurchaseReservationIntegrationTest` | 구매 예약·취소·만료, 소유권과 재고 반환 검증 |
| `PaymentLifecycleIntegrationTest` | 결제 실패·재시도·승인, 소유권·만료와 구매 및 재고 전이 검증 |
| `SaleProductManagementIntegrationTest` | 판매 생성, 상태 전이, 재고 생성, 목록/상세 조회 검증 |
| `ObservabilityIntegrationTest` | 실제 HTTP의 지표 접근 401/403/200, 업무 API 인증 유지, histogram·JVM·Hikari 노출 검증 |
| `PurchaseWaitingIntegrationTest` | 실제 DB/Redis에서 순번 유실·동점 cursor·단일 claim·이전 토큰·실행 중 lease 복구·전역 입장 슬롯·DB 입장 거부·커밋 후 등록 실패·전체 Worker 연결 검증 |

`PurchaseWaitingProtectionIntegrationTest`는 동시 수용량·동시 멱등 재전송·1초 속도 제한·Redis 장애 전후·기본 직접 HTTP 차단을 검증한다. 기존 직접 예약 HTTP·결제 통합 테스트는 `test,baseline`으로 기존 예약 계약을 검증한다. 기존 Executor 전용 검증은 Kafka 전달 대기를 포함하는 DB 입장 상한·입장 거부 후 Redis 반환 검증으로 교체했다.

`PurchaseWaitingOutboxIntegrationTest`는 실제 PostgreSQL/Redis에서 입장+Outbox 원자성, 재시도 시각과 payload 보존, 동시 Claim·SKIP LOCKED·Lease 재선택·이전 콜백 차단, 열린 transaction 진입 거부·Kafka 전송의 transaction 비보유·독립 Claim/완료 commit, 관리자 인증·동시 재처리·이전 token·실행 예산 소진 복구를 검증한다.

`PurchaseWaitingKafkaIntegrationTest`는 실제 PostgreSQL/Redis/Kafka로 HTTP→Outbox→Consumer→예약, 중복 소비, ACK 후 완료 저장 실패와 Lease 만료 후 재발행, poison DLT, 총 3회 기술 실패·기존 구매 복원, DB 확인/DLT 발행 실패 시 정지·offset 유지·복구를 검증한다. 실패 경계는 Spy로 주입하지만 실제 Kafka 전송과 group offset을 검사한다. 테스트 Kafka는 RF1·minISR1이므로 복제 내결함성을 검증한 것으로 표현하지 않는다. 로컬 RF3·minISR2 Broker 장애는 별도 실험으로 확인한다. 일반 업무 통합 테스트에서는 listener와 polling을 멈추고 Port를 통해 실행 경계를 명시적으로 구동하며, 실제 Kafka 연결 검증과 구분한다.

`ModularityDocumentationTests`는 Spring Modulith `Documenter`로 코드 기준의 모듈 문서를 생성한다.

`SaleProductReadCacheIntegrationTest`는 PostgreSQL과 서로 다른 두 Redis에서 warm hit의 SQL 0건, 목록 일괄 조회, stale OPEN의 구매 거절, rollback·commit 무효화, 이전 generation/token의 fill·release 거절, deadline·TTL·손상 값·generation 유실, 동시 miss, 조회 Redis 중단의 DB 전환과 대기열 데이터·health 보존을 검증한다. TTL 복구 테스트 일부는 데이터 key의 TTL을 짧게 조정해 만료를 관찰하며 기본 30초를 매번 기다리는 실험은 아니다. 판매 Service 단위 테스트는 캐시 오류와 DB 오류의 처리·afterCommit 등록 경계를 검증하며 실제 transaction은 통합 테스트로 확인한다.

```text
app/build/spring-modulith-docs/
├── components.puml
├── module-*.puml
├── module-*.adoc
└── all-docs.adoc
```

Checkstyle 규칙은 [Checkstyle 설정](../../config/checkstyle/checkstyle.xml)이 소유한다. `setXxx` 금지는 프로젝트가 선언한 메서드 이름에 적용하며 외부 라이브러리 메서드 호출은 검사하지 않는다. 의미 있는 업무 동사인지 여부는 코드 리뷰에서 확인한다.

Domain 및 Application Service 단위 테스트는 각 업무 모듈이 소유한다. Service 단위 테스트는 Spring Context 없이 Domain Repository와 자신의 Output Port를 대체하고 업무 분기와 허용·금지되는 부수효과를 검증한다. HTTP, 보안, 모듈 연결, 실제 DB·Redis와 트랜잭션·동시성은 실행 모듈 `app`의 통합 테스트에서 검증한다. 단위 테스트의 Mock으로 DB 정합성과 롤백을 검증했다고 간주하지 않는다. 모든 테스트 클래스와 테스트 메서드는 한국어 `@DisplayName`으로 검증하는 정책을 설명한다.

새 모듈, Named Interface, 모듈 간 의존성을 변경할 때는 `./gradlew check`를 실행해 코드 규칙, 아키텍처, 업무 동작과 모듈 문서 생성을 함께 검증한다. `./gradlew test`는 Checkstyle을 실행하지 않는다. 부하 테스트 절차는 [부하 테스트 가이드](../performance/load-testing.md), 남은 검증과 확장 계획은 [로드맵](../plans/roadmap.md)에서 관리한다.

테스트만 실행하려면 `./gradlew test`를 사용한다. 테스트 클래스와 메서드의 `@DisplayName`은 검증하는 업무 규칙을 한국어로 설명한다.

`RedisSaleProductReadCacheAdapterTest`는 알 수 없는 Lua 상태·누락된 응답·잘못된 claim이 DB 전환과 오류 지표로 이어지는지 검증한다. generation 충돌 후 재시도와 두 번의 시도 상한도 확인한다. 실제 Redis의 소유권·만료·무효화 경합은 기존 `SaleProductReadCacheIntegrationTest`가 검증한다.

## 부하 평가 도구의 계약 검사

Python unittest는 원본 덮어쓰기·불완전 결과·재고/구매 연결·DB 동시 실행·정리 소유권·지연 판정을, Node test는 실제 k6가 사용하는 응답 분류와 fixture 중복을 검사한다. 테스트는 한국어 설명을 제공한다. 실제 DB/Redis/HTTP/k6 연결은 Smoke와 최종 SQL 검증으로 확인한다.

```bash
python3 -m unittest discover -s load-test/k6 -p test_run_baseline.py
node --test load-test/k6/baseline-contract.test.mjs
```

전체 Java 규칙·아키텍처·업무 테스트는 기존처럼 `./gradlew check`로 실행한다.

## 관측 비밀값 준비 검사

실제 임시 디렉터리에서 비밀번호 최초 생성·기존 값 보존·700/600 권한·출력 비노출·빈 파일 거부를 검사한다. Docker 기동·로그인·컨테이너 UID 접근은 실제 관측 서비스로 별도 확인한다.

```bash
python3 -m unittest discover -s observability/tests -p test_setup.py
```

매 단계 5,000건 부하를 반복하지 않는다. 기능·장애 회귀와 작은 Smoke를 기본으로 하고 처리 경로 성능 비교가 필요하거나 최종 종합 평가일 때 대규모 부하를 수행한다. ZSET 단독 실험은 실제 HTTP 처리량 평가와 구분한다.

## 아키텍처 검사 정책

ArchUnit은 각 모듈 내부의 Domain·Application·입력/출력 Adapter와 DTO 의존 방향을 검사한다. Domain Repository는 Domain의 interface다. Input/Output Port와 내부 UseCase도 interface이며 입력 구현은 PortService, 내부 업무 구현은 Service, 출력 구현은 infrastructure에 둔다. 입력 Adapter는 Input Port를 호출하고 내부 UseCase를 우회하지 않는다. 출력 Adapter는 자신의 입력 Adapter나 업무 구현을 호출하지 않는다.

Input Port 구현은 application/service의 PortService로 통일하며 facade 패키지는 금지한다. PortService는 내부 UseCase를 연결·조율하고 Repository·Output Port·구체 Service·Aggregate에 직접 의존하지 않는다. DTO 변환에 필요한 상태 enum은 허용한다. 내부 업무 Service는 자기 모듈의 Input Port나 구체 Service를 직접 호출하지 않고 내부 UseCase를 사용한다. 검사 대상은 운영 코드이며 테스트·생성 산출물은 제외한다. member·inventory처럼 HTTP/Scheduler 입력 Adapter가 없는 모듈에는 빈 입력 패키지를 강제하지 않는다. 나머지 필수 규칙은 대상이 없으면 실패한다. 모듈 간 공개 API·허용 의존·순환은 기존 Modulith 검증이 담당한다.

규칙을 변경하면 정상 코드 통과와 대표 금지 의존성의 실패를 확인한다. 임시 위반은 원복하고 전체 `./gradlew check`를 실행한다. 검사 범위·허용 의존성을 완화하거나 실패를 비활성화해 통과시키지 않는다. 검사 적용·실행 상태는 [로드맵](../plans/roadmap.md)이 소유한다.

## 업무 회귀 기준

접수·예약 PortService에 전체 transaction을 추가하지 않는다. `PurchaseWaitingIntegrationTest.queuesRequestsIdempotentlyAndProcessesOnlyTheInventoryWinner`는 접수 응답에 Redis 등록 이후 순번 1·2가 포함되고 멱등 재전송 및 최종 예약·품절이 재고와 일치하는지 검증한다. Aggregate Repository와 Port를 옮겨도 기존 상태·수량·오류·부수효과 assert는 유지한다.

InventoryCommandServiceTest는 0 이하 예약 수량의 영속화 차단, 존재하지 않는 원장과 실제 품절의 오류 구분, 중복 초기화 시 수량 덮어쓰기 금지를 검증한다. 예약·결제·취소·만료의 실제 롤백과 동시성은 app 통합 테스트가 검증한다. 위임 횟수·DTO 접근자·클래스 존재만 확인하는 테스트는 늘리지 않는다.

비동기 Outbox 단위 테스트는 미완료 Future·완료 콜백 대기까지의 In-flight 제한, 동시 polling, 동기/비동기 전송 실패, Claim 오류, 콜백 실행 거부, DB 완료/실패 기록 오류에서의 Permit 반환을 검증한다. 가변 Clock으로 지연 실패 후 backoff와 30초 상한을 유지하는지도 검사한다.

슬롯 반환 분리의 회귀는 `PurchaseWaitingIntegrationTest.completedReservationSurvivesSlotReleaseFailure`가 검증한다. Redis 반환 실패에도 예약 결과와 재고를 보존하고 복구에서 슬롯만 제거하며 재전달 시 구매 효과가 중복되지 않는지 확인한다. 이전 token의 슬롯 반환도 새 슬롯을 제거할 수 없는지 내부 UseCase 경유로 검증한다.
