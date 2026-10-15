# ADR 0004 · 의존성 검사와 업무 테스트의 책임 분리

- 상태: **Accepted · 회고 작성**
- 정리일: 2026-10-15. 현재 정책 적용 commit: `b545618252b58b41e2107828409418bc3fb215cb`.
- 대안 비교는 정리 시점의 설명이며 정책 요구와 실제 검사 코드를 근거로 작성했다.

## 배경

코드 패키지가 분리되어 있어도 내부 계층을 역참조하거나 다른 모듈의 Repository를 직접 사용하면 경계가 무너진다. 반대로 모든 규칙을 하나의 테스트로 검사하면 모듈 내부 규칙과 공개 계약 위반을 구분하기 어렵다. 검사 통과와 커버리지만으로 업무 규칙이 맞다는 보장도 얻을 수 없다.

## 비교한 대안

| 대안 | 이점 | 감수할 비용 |
|---|---|---|
| 리뷰에만 의존 | 도구와 규칙 유지가 불필요함 | 반복 위반을 놓치기 쉬움 |
| ArchUnit으로 모든 경계 검사 | 하나의 도구로 통일 | Modulith 공개 인터페이스·모듈 설정과 규칙 중복 |
| ArchUnit·Modulith·Checkstyle 역할 분리 | 위반의 범위와 원인이 명확함 | 각 검사의 범위와 실제 업무 테스트를 함께 유지해야 함 |
| 실행 모듈의 통합 테스트만 작성 | 실제 연결을 검증 | 순수 업무 분기의 피드백이 느리고 실패 원인이 넓어짐 |

## 결정

ArchUnit은 각 업무 모듈 내부의 계층·패키지 의존 방향과 DTO 노출을 검사한다. Spring Modulith는 모듈 간 Named Interface 사용·허용 의존·순환 참조를 검사한다. Checkstyle은 전체 main/test 소스의 `var`와 프로젝트 메서드 선언의 `setXxx`를 검사한다. 외부 라이브러리 메서드 호출은 이 명명 규칙의 대상이 아니다.

Domain·Application Service 단위 테스트는 각 업무 모듈에서 허용·금지 상태 전이와 업무 분기를 검증한다. `app`의 통합 테스트는 실제 DB·Redis·HTTP·모듈 조립·트랜잭션·동시성을 검증한다. 모든 테스트 클래스와 메서드의 한국어 `@DisplayName`은 검증하는 규칙을 드러낸다. 검사 명령과 상세 책임은 [테스트 기준](../development/testing.md)이 소유한다.

## 결과와 비용

의존 방향과 모듈 공개 계약 위반을 다른 범위에서 탐지한다. 검사 실패를 피하려고 예외나 허용 목록을 임의로 확대하지 않는다. 코드 구조 검사는 비즈니스 정합성을 대신할 수 없어 동작 테스트가 별도로 필요하다. `setXxx` 금지는 형식 규칙이며 의미 있는 업무 동사인지까지 자동 판정하지 못한다. DTO 변환·접근자 존재만 확인하는 테스트를 늘리는 대신 실제 실패 조건과 부수효과를 검증한다.

## 근거와 검증

- [LayeringArchitectureTests](../../app/src/test/java/com/example/limitedticketing/architecture/LayeringArchitectureTests.java): 모듈 내부 방향·경계.
- [ModularityTests](../../app/src/test/java/com/example/limitedticketing/architecture/ModularityTests.java): 모듈 간 공개 계약·순환 의존.
- [Checkstyle 설정](../../config/checkstyle/checkstyle.xml), [루트 빌드 설정](../../build.gradle): 코드 규칙과 `check` 연결.
- [테스트 기준](../development/testing.md#자동-검증): 업무 테스트와 통합 테스트의 검증 대상.
