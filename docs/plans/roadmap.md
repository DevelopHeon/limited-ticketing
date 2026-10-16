# 현재 진행 상태와 다음 검토

이 문서가 프로젝트의 진행 상태·다음 행동·남은 검증을 소유한다. 제품 요구사항·목표와 단계별 인수 기준은 [전체 계획](2026-10-15-flash-sale-implementation-plan.md), 구현된 계약은 [Architecture](../architecture/README.md), 실측 결과는 [Evaluation](../README.md)에 둔다.

## 현재 상태

- 제품 방향: UI 없는 한정 상품 플래시세일. Redis ZSET·Kafka·상품 캐시·인기/판매 랭킹·내결함성·부하 평가를 학습한다.
- 기능 구현: 1·2단계 완료. 이후의 전체 모듈 구조 전환과 문서 정리를 검토한다.
- 구조 전환: 모든 업무 모듈의 입력 Port·내부 UseCase·Application 진입·infrastructure Adapter 배치를 통일했다. Aggregate 영속성은 Domain Repository, 상품 검색·외부 기능은 Application Output Port로 구분했다. 전체 검증과 독립 코드리뷰를 마쳤으며 commit/push 후 사용자 검토를 기다린다.
- 문서 정리: 현재 상태의 단일 소유, 규모에 따른 Plan/Spec, 중요한 결정의 ADR, 선택적 Archive 기준을 [문서 안내](../README.md#문서-관리-기준)에 적용했다.
- 다음 행동: 이번 변경의 코드·업무 테스트·문서와 검증 결과를 검토한다. Kafka 3단계는 사용자 시작 요청 이후 전달·상태 계약을 논의한다.

## 단계별 진행

| 단계 | 상태 | 확인할 근거 |
|---|---|---|
| 1. 관측·재현 기준선 | 완료 | [1단계 평가](../evaluations/load-testing/2026-10-15-stage-1-baseline.md) |
| 2. ZSET 대기 정합성·입구 보호 | 완료 | [2단계 평가](../evaluations/load-testing/2026-10-16-stage-2-queue-consistency.md), [현재 대기 계약](../architecture/purchase-waiting.md) |
| 2단계 이후 구조·문서 정리 | 검증·리뷰 완료·검토 대기 | [모듈 설계](../architecture/module-boundaries.md), [ADR 0008](../adr/0008-application-entry-orchestration.md), [ADR 0009](../adr/0009-domain-repository-ownership.md) |
| 3. Kafka·Outbox 예약 전달 | 미착수 | 전달·재소비·API/Worker 역할 분리·partition/offset 실험 |
| 4. 상품 조회 캐시 | 미착수 | 상세/목록 캐시·무효화·동시 miss 제어·cold/warm 비교 |
| 5. 인기·판매 랭킹 | 미착수 | 일간/7일 집계·ZSET·상품 캐시 결합·복원 |
| 6. 다중 프로세스·장애 복구 | 미착수 | Consumer/Broker/Redis/DB 장애와 복구 검증 |
| 7. 종합 부하·학습 정리 | 미착수 | 집중·분산·조회·폭주·지속 평가 |

## 다음 검증

변경 전 전체 검사는 162개 중 기존 LayeringArchitectureTests 3개가 실패한 과도기 상태였다. 전체 모듈 전환에 맞춰 정책을 개정했으며 기존 presentation 고정·빈 검사 대상 실패를 해소한다. 검사 범위를 축소하거나 허용 의존성을 넓혀 통과시키지 않는다.

`./gradlew check :app:bootJar`는 197개 테스트·ArchUnit·Modulith·Checkstyle을 모두 통과했다. 오류·skip은 없다. 부하 도구 Python 22개·Node 4개와 관측 비밀값 도구 3개도 통과했다. 독립 리뷰에서 기능·경계 회귀는 발견되지 않았으며 보존 기록 표의 표시 오류(P3)를 수정했다. 테스트 정책과 명령은 [테스트 기준](../development/testing.md)이 소유한다. 입력 Adapter의 내부 Service 우회와 다른 모듈 Domain Repository 참조를 임시 주입해 각각 ArchUnit·Modulith의 실패를 확인한 뒤 원본을 byte 단위로 복원했고 전체 검사를 다시 통과했다.

구조 변경을 성능 개선으로 주장하지 않는다. 이번에는 1,000/5,000건 부하를 반복하지 않는다. 이전 대규모 지연 목표 미달과 비교 한계는 [1단계 평가](../evaluations/load-testing/2026-10-15-stage-1-baseline.md), 2단계의 작은 Smoke·ZSET 단독 실험은 [2단계 평가](../evaluations/load-testing/2026-10-16-stage-2-queue-consistency.md)를 따른다. 다중 앱의 전역 처리 제한과 DB 자동 failover는 별도 검증 범위다.

## 메시징 확장 판단 기준

Kafka는 처리량 개선이 먼저 입증되어야만 도입하는 후보가 아니라 구성·partition·offset·재소비·내결함성 학습을 위한 필수 범위다. 실행 코드에는 Kafka·Outbox가 아직 없다. ZSET 입장 선택과 Kafka 작업 전달을 구분하고, 입장 DB 상태와 Outbox를 같은 transaction으로 저장하며 Consumer 처리·offset 경계는 멱등성과 복구로 보호하는 방향이다.

3단계 시작 전 상태·event envelope/version·key·partition 수·offset·retry/DLT·Publisher 순서·허용 backlog를 논의한다. 뒤 단계의 상세 인터페이스를 미리 구현하지 않는다. SSE/WebSocket, 실제 PG·운영 인증, PostgreSQL 자동 failover는 별도 후속 후보이며 단일 DB 구조에 Saga·서비스별 DB 분리를 선행 도입하지 않는다.
