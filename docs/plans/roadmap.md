# 현재 진행 상태와 다음 검토

제품 방향은 **한정 상품 플래시세일**로 확정했다. Redis ZSET·Kafka·캐시·인기/판매 랭킹·내결함성·부하 평가를 필수 학습 범위로 진행한다. 단계별 범위·요구사항·종료 절차는 [구현 계획](2026-10-15-flash-sale-implementation-plan.md)이 소유한다.

## 현재 상태

- 문서 준비: 전체 구현 계획과 [1단계 스펙](../archive/specs/2026-10-15-stage-1-observability-baseline.md) 작성 완료.
- 기능 구현 2단계: **구현·검증·리뷰 완료·검토 대기**. 문서 작성·commit/push를 기능 구현 완료로 계산하지 않는다.
- 다음 행동: [2단계 결과](../evaluations/load-testing/2026-10-16-stage-2-queue-consistency.md)를 검토한다. 3단계 시작 요청 이후 Kafka/Outbox의 상태·전달 계약을 논의한다.
- 각 단계는 검증·한국어 commit/push·결과 인계 후 검토 대기한다. 다음 단계는 자동 시작하지 않는다.

## 재사용하는 기존 구현

- [x] 업무 모듈과 공개 API 경계, PostgreSQL/Flyway 영속성
- [x] 구매·결제 생성 멱등성, 조건부 상태 전이와 재고 원자 갱신
- [x] Redis ZSET 대기 접수·처리·조회·단일 프로세스 Worker·DB 기반 복구
- [x] Domain·Application Service·통합·동시성 테스트, ArchUnit·Modulith·Checkstyle
- [x] 기존 직접 예약과 Redis 대기 진입 부하 실행 기록

마지막 항목은 기존 실행이 있었다는 의미다. [기존 평가](../evaluations/load-testing/2026-10-15-existing-results.md)의 1,000건 수락·약 580ms 결과는 새로운 5,000명 목표·동일 조건 비교·최종 업무 결과 검증을 완료한 근거가 아니다.

## 단계별 진행 계획

| 단계 | 상태 | 구현·평가 산출물 |
|---|---|---|
| 1. 관측·재현 기준선 | 완료 | 자동 관측, 동일 유입 모델, manifest·최종 DB 검증·기준선 |
| 2. ZSET 대기 정합성·입구 보호 | 구현·검증·리뷰 완료·검토 대기 | 입력 어댑터/업무 조율 분리·순번/복구·조건부 claim·수용량·요청 제한·직접 경로 제한 |
| 3. Kafka·Outbox 예약 전달 | 미착수 | 전달·재소비·API/Worker 역할 분리·partition/offset 실험 |
| 4. 상품 조회 캐시 | 미착수 | 상세/목록 캐시·무효화·동시 miss 제어·cold/warm 비교 |
| 5. 인기·판매 랭킹 | 미착수 | 일간/7일 집계·ZSET·상품 캐시 결합·복원 |
| 6. 다중 프로세스·장애 복구 | 미착수 | Consumer/Broker/Redis/DB 장애와 복구 검증 |
| 7. 종합 부하·학습 정리 | 미착수 | 5,000명 집중·분산·조회·폭주·지속 평가 |

## 다음 검증

1단계에서 1,000건 각 3회와 5,000건 각 1회의 업무 수량·관측 검증을 통과했다. [평가 보고서](../evaluations/load-testing/2026-10-15-stage-1-baseline.md)의 5,000건 waiting 완료 p95 **83.37초**는 60초 목표안 미달로 유지한다. 접수 p95 273.97ms와 구분한다. 2단계에서 순번·lease/fencing·입장 정책을 구현했으며 이번에는 실패 경계와 작은 Smoke를 평가했다. 매 단계 5,000건 부하는 반복하지 않는다. 성능 비교가 필요한 변경과 최종 종합 평가에서 적절한 규모로 수행한다.

## 메시징 확장 판단 기준

기존에는 Kafka를 처리량·독립 배포 요구가 생겼을 때 검토하는 후보로 두었다. 현재는 **Kafka 구성·partition·offset·재소비·내결함성 학습 자체가 요구사항**이므로 3단계 필수 범위로 변경했다. 현재 실행 코드에는 Kafka·Outbox가 아직 없다.

ZSET의 입장 선택과 Kafka 작업 전달은 역할을 구분한다. 입장 DB 상태와 Outbox를 같은 트랜잭션으로 저장하고, Consumer의 DB 처리와 offset 저장 경계는 멱등성과 복구로 보호하는 방향이다. 정확한 상태·event·retry 계약은 3단계 스펙을 확정한 후 구현한다.

SSE/WebSocket 알림, 실제 PG·운영 인증, PostgreSQL 자동 failover는 별도 후속 후보로 유지한다. 현재 단일 DB 구조에 Saga·서비스별 DB 분리를 선행 도입하지 않는다.

## 2단계 이후 구조 학습 작업

purchasewaiting만 [Port/Adapter 전환 기록](../archive/specs/2026-10-16-purchasewaiting-hexagonal-transition.md)에 따라 먼저 변경했고, 이후 [ADR 0008](../adr/0008-application-entry-orchestration.md)에 따라 업무 조합을 Application Facade로 이동했다. 기존 LayeringArchitectureTests는 사용자 지시로 다른 모듈 전환 후 개정한다. 다음 검토 대상은 이 모듈의 구조이며 다른 모듈 전환과 Kafka 3단계는 별도 요청 이후 진행한다.
