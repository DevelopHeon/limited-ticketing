# 문서 안내

처음 읽는 경우 [프로젝트 소개](../README.md) → [C4 아키텍처](architecture/README.md) → [로컬 개발](development/README.md) 순서로 읽는다. 현재 계약, 결정 이유, 진행 계획, 과거 기록과 측정 근거를 아래 영역으로 구분한다.

| 영역 | 진입 문서 | 관리하는 내용 |
|---|---|---|
| `architecture/` | [시스템 개요](architecture/README.md) | C1·C2, [로컬 Deployment](architecture/deployment.md), [모듈·계층](architecture/module-boundaries.md), [구매 대기 C3·계약](architecture/purchase-waiting.md) |
| `development/` | [개발 안내](development/README.md) | 실행·환경·API, [업무 테스트·아키텍처·컨벤션 검증](development/testing.md) |
| `adr/` | [결정 목록](adr/README.md) | 결정별 배경·대안·채택 내용·비용·근거 |
| `performance/` | [부하 테스트 실행](performance/load-testing.md) | 실제 스크립트·SQL의 준비와 실행, 관찰·최종 DB 검증 절차 |
| `evaluations/` | [기존 구매 대기 평가](evaluations/load-testing/2026-10-15-existing-results.md) | 실행별 원본 식별자·조건·측정 수치·미확인 범위 |
| `plans/` | [로드맵](plans/roadmap.md) | 미완료 인수 기준과 확장 도입 조건 |
| `archive/` | [구매 대기 구현 스펙 보관본](archive/specs/2026-10-15-purchase-waiting-implementation.md) | 원문과 당시 상태 보존, 현재 기준 문서로 연결 |

## 작업별 읽는 순서

- 대기 처리 변경: [구매 대기 계약](architecture/purchase-waiting.md) → [선택 이유](adr/0003-purchase-waiting.md) → [테스트 기준](development/testing.md).
- 성능 비교: [실행 방법](performance/load-testing.md) → [기존 결과와 한계](evaluations/load-testing/2026-10-15-existing-results.md) → [다음 검증](plans/roadmap.md#다음-검증).
- 모듈 경계 변경: [모듈 내부·공개 경계](architecture/module-boundaries.md) → [검사 역할](adr/0004-architecture-guards.md).

ADR·평가·완료 스펙은 각각 결정·평가 실행·구현 작업 단위로 추가한다. 참고 저장소의 역할 구분은 적용하되 이 프로젝트에 없는 영역이나 빈 문서 폴더는 만들지 않는다. 현재 계약은 영역 문서 하나에서 갱신하고 ADR·archive의 과거 내용을 새 계약으로 덮어쓰지 않는다.

## 소스와 생성 산출물

- [k6 스크립트와 SQL](../load-test/k6)은 실행 파일이므로 소스 위치를 유지한다.
- `load-test/k6/results/`는 Git에서 제외하는 로컬 측정 원본이다. 평가 문서에는 원본 경로·hash·조건과 보존 위치를 명시한다.
- `app/build/spring-modulith-docs/`는 테스트로 생성한 모듈 문서다. 수동 편집하지 않는다.
- 코드 규칙은 [Checkstyle 설정](../config/checkstyle/checkstyle.xml), 의존성 규칙은 모듈 메타데이터와 아키텍처 테스트가 기준이다.
