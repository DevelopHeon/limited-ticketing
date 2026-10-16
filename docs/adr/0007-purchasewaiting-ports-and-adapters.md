# ADR 0007 · purchasewaiting의 Port/Adapter와 UseCase 명명 통일

- 상태: **Accepted · purchasewaiting에 우선 적용**
- 결정일: 2026-10-16. 사용자는 실용적 최소 추상화보다 헥사고날+DDD의 역할과 호출 흐름을 일관되게 학습하는 것을 우선했다.
- [ADR 0005](0005-inbound-outbound-adapters.md)의 presentation 입력 배치는 purchasewaiting에 한해 이 결정으로 대체한다. 다른 모듈은 아직 기존 구조다.

## 배경과 대안

Facade·공개 API·Support Finder·Store 이름이 섞여 업무 흐름을 따라가기 어려웠다. 카카오스타일의 [코드 사례](https://devblog.kakaostyle.com/ko/2025-03-21-1-domain-driven-hexagonal-architecture-by-example/)를 참고해 Port → Driving Adapter → UseCase → Service → Port → Adapter 이름을 일관되게 쓰기로 했다. 원문은 실제 패키지 트리를 공개하지 않으므로 폴더는 이번 프로젝트에서 정한 배치다.

기존 presentation을 유지하는 방식은 기존 모듈과 일관적이다. 입력·출력을 infrastructure/adapter/in·out으로 묶는 방식은 두 어댑터 모두 업무 핵심 밖이라는 점을 드러낸다. 사용자가 학습 목적에 맞춰 후자를 선택했다.

## 결정

- Input/Output Port와 UseCase는 interface, Driving/Driven Adapter와 Service는 구현체다. Controller와 Scheduler는 입력 Adapter 영역에 둔다.
- 의존성 필드·생성자 매개변수·테스트 의존성 변수도 `Port`, `UseCase`, `Repository`, `Service`, `Adapter`, `Facade` 등 타입의 역할 접미사를 유지한다.
- application.service가 UseCase를 구현하고 업무·트랜잭션을 조율한다. Domain은 상태·허용 전이·시도 소유권을 소유한다.
- 다른 모듈 호출은 출력 Port와 ModuleAdapter로 격리한다. 해당 모듈을 변경하기 전에는 Adapter 내부에서 기존 공개 Facade를 호출한다.
- Input/Output, Command/Query/Result, 출력 작업 DTO, HTTP Response를 구분한다. 내부 결과 두 단계는 하나로 합치고 ID만 필요한 결과는 UUID로 반환한다.
- 단순 조회는 Entry Query Port를 사용한다. Finder/Support 계층을 없애고 Completion은 Process Service에 통합한다.
- 기본 HTTP·오류 코드·DB/Flyway·Redis 키/Lua·설정과 잠금/lease/REQUIRES_NEW/afterCommit 경계는 보존한다.

## 결과와 비용

이름만으로 경계·역할을 따라갈 수 있다. 인터페이스와 DTO 변환, ModuleAdapter 파일은 증가한다. 별도 Driving Adapter는 입력 변환과 UseCase 조합을 명시하기 위한 선택이며 헥사고날의 필수 계층은 아니다. 패키지 이동만으로 정합성이나 성능이 개선됐다고 주장하지 않는다.

사용자는 기존 LayeringArchitectureTests가 새 구조와 충돌해도 이번에는 수정·삭제·비활성화하지 않고 다른 모듈 전환 이후 개정하도록 요청했다. 전체 check 실패는 숨기지 않고 정확한 대상·위반 내용을 보고한다. Modulith·Checkstyle·업무 테스트는 이번에도 검증한다. 따라서 모든 모듈의 구조 검증이 완료된 상태로 표현하지 않는다.

## 근거

- [현재 구매 대기 구조와 계약](../architecture/purchase-waiting.md)
- [실행 계획과 사용자 승인 범위](../archive/specs/2026-10-16-purchasewaiting-hexagonal-transition.md)
- [테스트와 과도기 검사 상태](../development/testing.md)
