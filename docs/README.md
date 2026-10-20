# 문서 안내

[프로젝트 소개](../README.md)에서 시작하고, 현재 진행 상태와 다음 작업은 [로드맵](plans/roadmap.md)에서 확인한다.

| 알고 싶은 내용 | 확인할 곳 |
|---|---|
| 지금 어떻게 구현되어 있는가? | [architecture/](architecture/README.md): C4, [모듈·계층·영속성 경계](architecture/module-boundaries.md), [주요 시퀀스](architecture/business-flows.md), [구매 대기 흐름](architecture/purchase-waiting.md) |
| 왜 이 기술과 구조를 선택했는가? | [adr/](adr/README.md): 결정의 배경·대안·비용 |
| 어떻게 실행하고 검증하는가? | [development/](development/README.md), [테스트 기준](development/testing.md), [ZSET·Kafka 학습 실험](development/zset-kafka-study.md), [performance/](performance/load-testing.md) |
| 앞으로 무엇을 구현할 것인가? | [plans/roadmap.md](plans/roadmap.md), [제품 요구사항과 단계별 인수 기준](plans/2026-10-15-flash-sale-implementation-plan.md) |
| 실제 실험 결과는 어땠는가? | evaluations/: [기존 결과](evaluations/load-testing/2026-10-15-existing-results.md), [1단계](evaluations/load-testing/2026-10-15-stage-1-baseline.md), [2단계](evaluations/load-testing/2026-10-16-stage-2-queue-consistency.md), [3단계](evaluations/load-testing/2026-10-19-stage-3-kafka-outbox.md) |

## 문서 관리 기준

- **현재 진행 상태는 roadmap.md 하나에서 관리한다.** 구현 중·검증 중·검토 대기·미착수와 다음 행동을 README·ADR·실행 안내에 반복하지 않는다. Architecture는 구현된 계약, ADR은 결정의 유효 상태, Evaluation·Archive는 명시된 revision의 과거 사실을 설명한다.
- 작은 기능 수정은 해당 현재 문서와 테스트를 갱신한다. Plan·Spec은 여러 경계·새 동작 계약·단계별 인수가 필요한 규모 있는 작업에만 만든다. 짧은 설계와 실행 순서는 한 문서에 묶고 매 변경마다 세 문서를 만들지 않는다.
- Evaluation은 실제 실험 결과·조건·원본 식별자·한계를 설명할 때 작성한다. 단순 테스트 통과 기록을 Evaluation으로 늘리지 않는다. 원본·manifest·hash·실패 실행은 보존한다.
- ADR은 모듈 경계, 정합성·트랜잭션, 외부 기술 선택처럼 오래 영향을 주는 아키텍처 결정에만 작성한다. 이름 변경·패키지 이동·기존 결정의 다른 모듈 적용·문서 정리 자체에는 새 ADR을 만들지 않는다. 선택 이유·대안·비용이 기존 ADR에 있으면 적용 범위와 대체 관계를 갱신한다.
- 앞으로 완료하는 Plan·Spec은 자동으로 Archive에 넣지 않는다. 당시 인수 계약·재현 조건·대안·실패 분석 등 보존 가치가 있는 문서만 보관한다. 단순 작업 목록·중복 설명은 현재 문서에 필요한 내용만 반영하고 Git 이력으로 남긴다. 이미 보관한 문서는 당시 원문을 유지한다. 중복 설명만 있고 고유한 판단·인수 조건·실험 근거가 없는 문서는 참조를 정리한 뒤 제거할 수 있지만, 남기는 원문을 현재 구조에 맞춰 고쳐 쓰지 않는다.
- 사실별 기준 문서는 하나다. 다른 문서는 요약과 링크로 연결한다. 이동·통합·삭제 후 링크·앵커·명령·도표를 검증하며, 코드가 바뀌면 프로젝트 검사를 실행한다.

## 생성·갱신 기준

| 문서 | 생성할 때 | 갱신할 때 |
|---|---|---|
| README | 프로젝트·문서 영역의 진입점이 필요할 때 | 소개·주요 기능·안내 경로가 바뀔 때 |
| Architecture | 기존 문서로 설명할 수 없는 설계 영역이 생길 때 | 실제 구현 구조·계약이 바뀔 때 |
| ADR | 대안과 비용을 나중에도 알아야 할 중요한 결정이 생길 때 | 결정 상태·후속 ADR 연결이 바뀔 때. 당시 이유는 보존 |
| Development·Performance | 반복 실행할 개발·검증·측정 절차가 생길 때 | 실행 방법·설정·판정 절차가 바뀔 때 |
| Plan·Spec | 규모 있는 작업에 범위·계약·인수 기준을 합의해야 할 때 | 해당 작업의 범위·계약이 바뀔 때. 진행 상태는 roadmap에만 기록 |
| Evaluation | 의미 있는 검증·실험 결과를 재현 근거와 남길 때 | 결과를 정정하거나 추가 분석할 때. 원래 조건·한계를 보존 |
| Archive | 완료 문서에 보존할 고유한 정보가 있을 때 | 원칙적으로 수정하지 않음 |

새 문서를 만들기 전에 다음을 확인한다.

1. **기존 문서에 반영할 수 있는가?** 대기 복구 변경은 purchase-waiting.md처럼 해당 영역 문서를 먼저 갱신한다.
2. **선택 이유를 나중에도 알아야 하는가?** 단순 구현 변경은 commit 이력으로 남기고 중요한 설계 판단만 ADR로 기록한다.
3. **계속 갱신할 정보인가?** 현재 구조·절차·진행 상태는 기존 문서로 관리하고, 특정 시점의 실험은 Evaluation으로 남긴다.

세부 실행 체크리스트와 일회성 작업 메모는 영구 기술 문서로 계속 쌓지 않는다. 삭제 여부는 문서 수가 아니라 고유 정보와 참조 필요성으로 판단한다. 기존 development/·performance/ 구분을 유지하며 별도 guides/나 문서 사이트를 만들지 않는다.

## 보존 중인 과거 기록

| 문서 | 보존 이유 |
|---|---|
| [최초 구매 대기 스펙](archive/specs/2026-10-15-purchase-waiting-implementation.md) | 최초 원문·당시 요구와 제약을 보존 |
| [1단계 인수 스펙](archive/specs/2026-10-15-stage-1-observability-baseline.md) | 평가의 유입 모델·관측·판정 조건을 재구성 |
| [2단계 인수 스펙](archive/specs/2026-10-16-stage-2-queue-consistency.md) | 순번·토큰·lease·복구·입구 정책의 당시 인수 계약을 보존 |
| [purchasewaiting 최초 구조 전환](archive/specs/2026-10-16-purchasewaiting-hexagonal-transition.md) | 당시 전환 계약·검증 결과·측정 원본 식별자·리뷰 이력을 보존 |

Archive는 현재 실행·설계의 기준이 아니다. 보관 문서의 과거 패키지 경로·검사 상태는 당시 기록이다. 현재 구조와 검증 절차는 Architecture·Development를 따른다. 과거 소스 확인이 필요하면 해당 기록의 revision으로 조회한다. 이번 패키지 이동으로 남은 원문의 상대 코드 경로·과도기 검사 앵커는 [변경 전 snapshot](https://github.com/DevelopHeon/limited-ticketing/tree/8f568dda02af26915745e0a28617d9978529bd1f)에서 확인할 수 있다.

## 소스와 생성 산출물

- [k6 도구·시나리오·SQL](../load-test/k6)은 재현 가능한 실행 소스다. 측정 원본 `load-test/k6/results/`는 Git에서 제외한다.
- `app/build/spring-modulith-docs/`는 코드에서 생성하는 모듈 문서이며 수동 관리하지 않는다.
- [Checkstyle](../config/checkstyle/checkstyle.xml), 아키텍처 테스트와 모듈 메타데이터가 자동 검사 규칙을 소유한다.
