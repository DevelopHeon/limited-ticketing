# ADR 0008 · 입력 Adapter와 Application 업무 조합 분리

- 상태: **Accepted**
- 결정일: 2026-10-16.
- [ADR 0007](0007-purchasewaiting-ports-and-adapters.md)의 별도 입력 Adapter 업무 조합·Port 구현 책임을 대체한다. infrastructure의 입력·출력 배치는 유지한다.

## 배경과 대안

PurchaseWaitingCommandAdapter는 접수 UseCase를 실행한 뒤 조회 UseCase를 호출했다. HTTP 변환과 무관한 실행 순서가 infrastructure에 있어 역할을 혼동하기 쉬웠다. 사용자는 Application Facade가 조합하고 Controller·Consumer·Scheduler를 Input Adapter로 보는 방향을 선택했다. 같은 모듈 내부 업무 호출은 UseCase, 외부 진입은 Port로 유지하도록 명시했다.

별도 Adapter를 계속 두는 방식은 기존 호출 사슬을 보존하지만 조합 책임의 위치가 불분명하다. UseCase만 공개하는 방식은 진입 단계를 줄이지만 사용자가 원하는 모듈 외부 Port·내부 UseCase 구분을 없앤다. 모든 진입에 Facade를 만드는 방식은 조합이 없는 조회·Worker에도 같은 이름을 붙인다. 이번에는 접수 조합만 Facade로 옮기고 단순 Port 연결은 Application 진입 Service로 옮긴다.

## 결정

- Controller·Scheduler는 infrastructure/adapter/in의 Input Adapter다. HTTP 변환·응답과 실행 주기를 맡고 Input Port를 호출한다. 별도 presentation 레이어로 이동하지 않는다.
- PurchaseWaitingCommandFacade가 Command Port를 구현하고 Enqueue·Find UseCase를 조합한다. Facade에는 전체 @Transactional을 추가하지 않는다. 접수 Service 커밋·Redis afterCommit 이후 조회 Service 실행을 유지한다.
- PurchaseWaitingQueryService와 PurchaseWaitingWorkerService는 각 Input Port를 구현하고 기존 내부 UseCase로 연결한다. Port의 외부 Input/Output을 내부 Command/Query/Result로 변환한다. 순차 업무 조합이 없는 이 진입에는 Facade 이름을 붙이지 않는다.
- Application 내부 업무 호출은 UseCase, 입력 Adapter와 다른 모듈의 진입은 Input Port를 사용한다. Application의 저장소·Redis·외부 기능 호출은 Output Port를 사용한다. Worker Port는 모듈 외부에 공개하지 않는다.
- Port·UseCase 인터페이스와 DTO, 의존성 역할 접미사, HTTP·DB·Redis 계약과 기존 업무 트랜잭션은 보존한다. 다른 모듈과 기존 LayeringArchitectureTests는 변경하지 않는다.

## 결과와 비용

업무 실행 순서가 Application에 모이고 infrastructure 입력에는 실제 외부 변환·트리거만 남는다. 내부 UseCase와 공개 Port를 구분하므로 단순 조회·Worker에도 두 계약을 연결하는 진입 Service가 남는다. 이는 명시적인 공개·내부 계약 구분의 비용이며, 더 적은 호출 계층을 택하지 않은 이유다. 새로운 업무 규칙이나 성능 개선은 추가하지 않는다.

## 최초 purchasewaiting 전환의 근거와 검증

- 기준 revision: `4c2e1077257060afb6779c92ee96b96054aec845`. 기존 사용자 미커밋 AGENTS.md 변경은 보존하고 이번 commit에서 제외한다.
- [현재 호출·패키지·DTO 경계](../architecture/purchase-waiting.md#portadapter-구조)
- [당시 검증과 현재 검사 기준](../development/testing.md#아키텍처-검사-정책)
- 변경 전 접수·멱등성·Redis 등록 후 순번·예약/품절 및 장애 중 수락한 키의 재전송 통합 테스트 2개를 실행해 통과했다. 변경 후 `./gradlew check :app:bootJar --continue`는 162개 중 159개 통과, 기존 LayeringArchitectureTests의 승인된 과도기 실패 3개, 오류·skip 0개다. Checkstyle·Modulith·나머지 업무 테스트와 bootJar 생성은 통과했다. 검사 파일·설정은 변경하지 않았다.
- 문서 상대 링크·앵커·bash 블록 검사와 Mermaid 5개 렌더링, `git diff --check`를 확인했다. 이번 변경에서 부하 측정은 반복하지 않았다.
- 독립 리뷰어 1명의 read-only 코드리뷰를 통과했다. package·클래스명·Component에서 Service로 역할 표기를 바꾼 것 외에 기존 세 진입 클래스의 내용이 동일함을 비교했고, 공개 범위·DI·DTO 변환·접수 커밋 후 조회 순서를 확인했다. 리뷰어는 Gradle·부하를 재실행하지 않고 개발 실행 로그·XML과 코드를 검토했다. 기존 로컬 앱을 새 jar로 재시작해 health UP을 확인했다.

## 전체 업무 모듈 적용

같은 입력 Adapter·Port·UseCase·진입 Service 책임 구분을 member·inventory·sale·purchase·payment에도 적용한다. 단순 연결은 EntryService, 실제 UseCase 조합은 Facade가 맡는다. Aggregate 영속 계약은 [ADR 0009](0009-domain-repository-ownership.md)를 따른다. 적용·검증 진행 상태는 [로드맵](../plans/roadmap.md)이 소유하며 위 실패 수치는 최초 전환 당시의 역사적 결과다.
