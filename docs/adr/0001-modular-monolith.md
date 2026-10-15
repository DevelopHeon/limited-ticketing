# ADR 0001 · 업무별 모듈러 모놀리스

- 상태: **Accepted · 회고 작성**
- 정리일: 2026-10-15. 최초 결정일 미확인.
- 확인 기준: `b545618252b58b41e2107828409418bc3fb215cb`의 코드·기존 설계 문서. 대안 비교는 정리 시점의 설명이다.

## 배경

한정 수량 구매는 판매 가능 여부·회원 자격·재고 예약·구매·결제 전이가 연결된다. 현재 독립 배포 요구가 확인되지 않은 상태에서 서비스와 DB를 먼저 분리하면 네트워크 실패와 분산 정합성 처리가 추가된다. 한편 하나의 코드 영역에 모든 업무를 두면 서로의 Repository와 모델을 직접 사용하는 경로가 늘어난다.

## 비교한 대안

| 대안 | 이점 | 감수할 비용 |
|---|---|---|
| 경계 없는 단일 프로젝트 | 초기 조립이 단순함 | 업무 내부 구현에 대한 직접 의존을 막기 어려움 |
| 업무별 모듈 + 단일 실행 앱 | 경계를 검사하면서 같은 DB 트랜잭션 사용 | 독립 배포·업무별 프로세스 확장이 불가능함 |
| 업무별 마이크로서비스 | 독립 배포와 자원 확장 | 통신 실패, 관측, 이벤트 전달, 분산 데이터 정합성 필요 |

## 결정

`app`이 업무별 Gradle 모듈을 조립해 하나의 Spring Boot 프로세스로 실행한다. 모듈 간에는 Named Interface로 공개한 Facade와 DTO만 사용한다. Aggregate 간 참조는 식별자로 표현하며 다른 모듈의 Domain·JPA Entity·Repository를 호출하지 않는다. 전체 구조는 [C4](../architecture/README.md), 공개 경계는 [모듈 설계](../architecture/module-boundaries.md#모듈-경계와-의존-규칙)가 소유한다.

## 결과와 비용

단일 DB 트랜잭션으로 구매·재고·결제의 원자성을 유지할 수 있다. 의존 방향은 컴파일 의존성과 Modulith 검증으로 함께 보호한다. 모든 모듈이 배포·프로세스 장애·DB 자원을 공유하며, 코드 경계가 운영 격리나 독립 처리량을 보장하지는 않는다. 독립 배포 필요나 단일 프로세스 한계가 측정되면 [확장 조건](../plans/roadmap.md)을 기준으로 재검토한다.

## 근거와 검증

- [settings.gradle](../../settings.gradle), [app 의존성](../../app/build.gradle): 업무 모듈과 실행 조립.
- [purchase 모듈 메타데이터](../../purchase/src/main/java/com/example/limitedticketing/purchase/package-info.java): 허용 공개 API 지정.
- [ModularityTests](../../app/src/test/java/com/example/limitedticketing/architecture/ModularityTests.java): 업무 모듈 감지, 공개 계약·순환 의존 검증.
- 검증 절차와 통합 테스트 책임은 [테스트 기준](../development/testing.md)을 따른다.
