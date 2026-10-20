# 현재 진행 상태와 다음 검토

이 문서가 프로젝트의 진행 상태·다음 행동·남은 검증을 소유한다. 제품 요구사항·단계별 인수 기준은 [전체 계획](2026-10-15-flash-sale-implementation-plan.md), 현재 계약은 [Architecture](../architecture/README.md), 실측은 [Evaluation](../README.md)에 둔다.

## 현재 상태

- 제품 방향: UI 없는 한정 상품 플래시세일. Redis ZSET·Kafka·상품 캐시·인기/판매 랭킹·내결함성·부하 평가를 학습한다.
- 1·2단계와 전체 모듈 구조·명명·문서 정리를 완료했다. Aggregate 영속성은 Domain Repository, 기술·다른 모듈 기능은 Application Output Port를 사용하는 기준을 유지한다.
- 3단계(`b5b09ee` commit/push·Notion 기록 완료): [승인된 스펙](2026-10-19-stage-3-kafka-outbox.md)의 Kafka·Outbox·실행 예산·관리자 재처리·API/Worker 역할 분리와 현재 문서를 구현했다. Outbox SQL은 JdbcRepository로 분리했고 운영 구독자가 없던 Spring 구매 예약 이벤트는 제거했다.
- 필수 검사와 작은 전달·복구 실험, 독립 리뷰·결함 수정·재리뷰를 마쳤다. 실행 중 중복은 ACK하지 않고 재전달하며 발행 backoff는 실제 실패 시점부터 계산한다.
- 사용자가 별도로 요청한 Outbox 비동기 리팩토링은 `d3088f4` commit/push·Notion 기록을 마쳤다. Kafka 조정 값의 YAML 이동과 사용자가 수정한 Outbox Scheduler 설정 키도 후속 변경으로 구현·검증·독립 리뷰를 마쳤다. 한국어 commit/push와 기존 Notion 기록 갱신 후 사용자 검토를 기다린다.
- 다음 단계는 사용자의 명시적인 시작 요청 후 논의한다. 4단계 캐시를 자동 시작하지 않는다.
- Publisher는 기존 `ReservationOutboxMessage`를 받는 계약으로 정리했다. Consumer의 계약 DTO 자동 변환은 검토 후 보류했으며 문자열 수신·검증·DLT 경로를 유지한다. `./gradlew check :app:bootJar --continue`에서 Java 256개 테스트와 아키텍처·컨벤션 검사 및 빌드가 통과했다. 독립 리뷰에서도 결함이 발견되지 않았으며 한국어 commit/push와 기존 Notion 기록 갱신 후 사용자 검토를 기다린다.

## 단계별 진행

| 단계 | 상태 | 확인할 근거 |
|---|---|---|
| 1. 관측·재현 기준선 | 완료 | [1단계 평가](../evaluations/load-testing/2026-10-15-stage-1-baseline.md) |
| 2. ZSET 대기 정합성·입구 보호 | 완료 | [2단계 평가](../evaluations/load-testing/2026-10-16-stage-2-queue-consistency.md) |
| 전체 모듈 구조·명명·문서 정리 | 완료 | [모듈 설계](../architecture/module-boundaries.md), [ADR 0008](../adr/0008-application-entry-orchestration.md), [ADR 0009](../adr/0009-domain-repository-ownership.md) |
| 3. Kafka·Outbox 예약 전달 | 구현·검증·리뷰 완료 | [인수 계약](2026-10-19-stage-3-kafka-outbox.md), [3단계 실험](../evaluations/load-testing/2026-10-19-stage-3-kafka-outbox.md) |
| 3단계 후속 · 비동기 Outbox | 구현·검증·리뷰 완료 | [발행 경계](../architecture/purchase-waiting.md#outbox-비동기-발행-경계), [ADR 0011](../adr/0011-asynchronous-outbox-claim-lease.md) |
| 4. 상품 조회 캐시 | 미착수 | 상세/목록 캐시·무효화·동시 miss 제어·cold/warm 비교 |
| 5. 인기·판매 랭킹 | 미착수 | 일간/7일 집계·ZSET·상품 캐시 결합·복원 |
| 6. 다중 프로세스·장애 복구 | 미착수 | Consumer/Broker/Redis/DB 장애와 복구 검증 |
| 7. 종합 부하·학습 정리 | 미착수 | 집중·분산·조회·폭주·지속 평가 |

## 다음 검증

후속 리팩토링 기준 revision은 `b5b09ee6dc8aee4a787e8720b9d71b1acc715433`다. 시작 시 미커밋 변경은 없었다. `./gradlew check :app:bootJar --continue`가 Java 253개 테스트에 실패·오류·skip 없이 통과했다. ArchUnit·Modulith·Checkstyle을 포함한다. Worker의 비-interface Port와 Domain을 노출한 DTO를 임시 주입해 각각 검사 실패를 확인했고 원복 후 전체 검사를 통과했다.

기존 3단계에서 Python 부하 도구 22개·Node 계약 4개·관측 비밀값 도구 3개 테스트가 통과했다. 새 DISPATCHED를 미완료로 판정하도록 도구를 갱신했다. 기존 Archive·과거 평가와 측정 원본은 수정하지 않았다. Markdown 상대 링크·앵커·bash 구문과 Mermaid 6개 렌더를 확인했다.

실제 Kafka 통합 테스트는 RF1·minISR1이며 DB 확인·DLT 오류는 Spy로 주입해 실제 전송과 offset을 확인한다. 로컬 RF3·minISR2에서는 1 Broker 중단 중 처리, 2 Broker 중단 중 Outbox 보존, 복구·Worker 재시작, API 단독 접수, key별 partition과 lag를 별도로 확인했다. 조건·실패 실행과 산출물 고정 조치는 [3단계 실험 기록](../evaluations/load-testing/2026-10-19-stage-3-kafka-outbox.md)을 따른다.

후속 구현은 Claim/Lease·Kafka Future·인스턴스별 In-flight·제한된 콜백 Executor·독립 DB transaction을 포함한다. 동시 Claim, 만료 후 재선택과 이전 콜백 차단, 열린 transaction 진입 거부, 콜백 거부·DB 오류의 Permit 반환, ACK 뒤 DB 저장 실패 후 중복 발행의 구매·재고 멱등성을 검증했다. 기존 로컬 DB에서 V7→V8을 적용하고 health UP, 기존 Outbox 30개와 eventId/payload 합성 hash 보존을 확인한 뒤 검증 앱을 종료했다.

독립 리뷰에서 호출자 transaction 중단만으로는 기존 연결·잠금이 해제되지 않는 점을 확인했다. 열린 transaction 진입을 NEVER로 거부하도록 수정하고 회귀의 RED→GREEN 및 전체 검사를 확인했다. 수정 부분 재리뷰에서도 추가 결함이 발견되지 않았다. 이 결과를 별도 한국어 commit/push와 기존 Notion 기록 갱신으로 보존하고 사용자 검토를 기다린다. 후속 리팩토링에서 3 Broker 장애 실험과 처리량 측정을 반복하지 않았다. 5,000건 부하·다중 Worker 처리량·장기 안정성·DB 자동 failover·운영 인증은 이번 완료 조건으로 수행하지 않았으며 달성했다고 주장하지 않는다.

Kafka 설정 후속 작업은 `d3088f4`를 기준으로 한다. 기존 값은 유지하면서 Producer·Consumer·Listener와 Topic·DLT·ErrorHandler 조정 값을 YAML에서 읽도록 변경했다. Scheduler의 `publisher-fixed-delay` 키 수정도 사용자 요청으로 함께 포함한다. 설정 override가 무시되는 회귀 3개의 RED→GREEN을 확인했고 `./gradlew check :app:bootJar --continue`가 Java 256개 테스트에 실패·오류·skip 없이 통과했다. 독립 리뷰에서 변경 코드·호출부·통합 테스트·실행 결과를 교차 확인했고 결함은 발견되지 않았다. 설정 위치와 조정 범위는 [개발 안내](../development/README.md#kafka-관찰과-복구)를 따른다. 별도 성능 측정은 수행하지 않았다.

## 메시징 확장 판단 기준

Kafka 선택 이유와 감수한 비용은 [ADR 0010](../adr/0010-kafka-reservation-outbox.md)이 소유한다. 기존 1,000/5,000건 지연과 비교 가능한 부하는 아직 재측정하지 않았다. 구조 변경이나 Kafka 도입 자체를 성능 개선으로 주장하지 않는다.

후속 단계의 상세 인터페이스를 미리 구현하지 않는다. 캐시·랭킹·다중 Worker 확장의 요구와 장애·성능 판정 기준은 해당 단계 시작 시 논의한다. SSE/WebSocket·실제 PG·운영 인증·PostgreSQL 자동 failover는 별도 후보이며 단일 DB 구조에 Saga나 서비스별 DB 분리를 선행 도입하지 않는다.
