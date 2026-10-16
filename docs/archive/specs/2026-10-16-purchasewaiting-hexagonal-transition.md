# purchasewaiting Port/Adapter 구조 전환

상태: 구현·검증·코드리뷰 완료. commit/push 후 사용자 검토 대기. 시작 revision: `a04cc26a1e4243246e31f3343f1a7190016c8630`.
사용자가 채팅에서 제시한 전체 구조와 infrastructure/adapter/in·out 배치를 선택하고 purchasewaiting부터 구현하도록 요청했다. 다른 모듈의 구조 전환과 3단계 Kafka 구현은 이번 범위가 아니다.

## 계약과 범위

- Controller → Input Port → Driving Adapter → UseCase → Service → Output Port → Driven Adapter.
- Port·UseCase는 interface, Adapter·Service는 구현체다. UseCase와 업무 조율은 application, 상태 규칙은 domain에 둔다.
- 의존성 변수와 생성자 매개변수에도 Port·UseCase·Repository·Service 등 타입의 역할 접미사를 모두 유지한다.
- 이름은 PurchaseWaiting으로 통일한다. Facade·Finder·Support를 제거하고 Domain Repository는 Application의 Entry Query/Command Port로 이전한다.
- 다른 모듈 호출은 Member/Sale/Purchase ModuleAdapter에 격리한다. 다른 모듈 코드는 변경하지 않는다.
- Input/Output, Command/Query/Result, 출력 작업 DTO, HTTP Response를 구분한다. 중복 내부 Result는 합친다.
- Completion은 Process Service에 통합한다. 행 잠금·토큰/lease 검사·구매 REQUIRES_NEW·회복 SKIP LOCKED·커밋 후 Redis 등록은 보존한다.
- HTTP 경로/응답 필드/오류 코드, DB/Flyway, Redis 키/Lua와 설정값은 보존한다. 대규모 부하와 새 외부 서비스는 추가하지 않는다.
- 기존 AGENTS.md 사용자 미커밋 변경은 보존하고 commit에서 제외한다.

## 실행과 검증

1. 기존 전체 검사 및 장애 중 멱등 재전송 characterization → 원래 업무 동작 확인.
2. 패키지/계약/서비스/DTO/입출력 구현 전환 → 모듈 compile, Checkstyle 및 업무 단위 테스트.
3. app 호출부·실제 DB/Redis 통합 테스트를 새 계약에 맞춤 → 기존 동시성·복구·트랜잭션 테스트 유지.
4. 전체 ./gradlew check 실행 → LayeringArchitectureTests 외 실패는 수정. 사용자 지시로 기존 LayeringArchitectureTests는 수정·삭제·비활성화하지 않고 예상 실패를 보고한다. 새 정책은 다른 모듈 전환 후 개정한다.
5. 현재 문서·ADR·링크 갱신 → 별도 리뷰어 1명 read-only 검토·필요 수정 재검증/재리뷰 → 한국어 master commit/push → Notion 기록 후 검토 대기.

## 승인된 전환 예외

기존 LayeringArchitectureTests의 고정된 presentation 위치 및 자신의 Input Port 역참조 정책과 새 구조가 충돌한다. 사용자가 이번 요청에서 해당 테스트 실패를 일단 무시하고 모든 모듈 전환 뒤 변경하도록 명시했다. 이 테스트만 과도기 미통과로 기록하며 전체 검사 성공으로 표현하지 않는다. 검사 설정은 완화하지 않는다.

## 검증 기록

- 변경 전 `./gradlew check`: 161개 통과. Redis 장애 중 수락한 키의 재전송을 검증하는 characterization을 기존 구조에서 먼저 확인했다.
- 변경 후 `./gradlew check :app:bootJar --continue`: 162개 중 159개 통과, 기존 LayeringArchitectureTests에서 승인된 과도기 실패 3개. Checkstyle·Modulith와 나머지 업무 테스트는 통과했고 bootJar를 생성했다.
- 3개 실패는 Scheduler의 presentation 위치 정책 1개, 이전 presentation 및 application.command/query/support 패키지의 빈 검사 대상 2개다. 실제 역방향 참조가 새로 발견된 것으로 해석하지 않는다.
- 기존 local 앱·DB·Redis의 waiting smoke 1회: 신규 접수 10건, RESERVED 3건·SOLD_OUT 7건, 잔여 WAITING/PROCESSING 0건, 중복 구매·연결 불일치 0건. 재고는 available 0·reserved 3·sold 0이다. 결제 완료 측정이 아니다.
- 실행: `DATABASE_URL=<기존 로컬 DB 연결> SPRING_PROFILES_ACTIVE=local BASELINE_JVM_OPTIONS='-Xms256m -Xmx768m' python3 load-test/k6/run-baseline.py --flow waiting --scenario smoke --base-url http://localhost:8081`.
- 원본: `load-test/k6/results/20261016T070839Z-waiting-ca039ded/`. manifest에 시작 revision `a04cc26a1e4243246e31f3343f1a7190016c8630`과 `workingTreeDirty=true`를 보존했다. runner는 실행 jar hash를 자동 수집하지 않는다. 소규모 회귀 확인이며 1,000/5,000건 성능 개선 근거로 쓰지 않는다.
- 문서 상대 링크·앵커·bash·Mermaid 렌더링을 확인했다. Lua·기존 측정 원본은 변경하지 않았다.

## 코드리뷰와 남은 사항

별도 리뷰어 1명이 기준 revision과 현재 소스·호출부·테스트·검증 결과를 read-only로 비교했다. 기능·경계 회귀나 요구사항 누락은 발견하지 못했다. manifest에 없는 jar hash 보존 기록(P3) 1건을 사실대로 정정했고 원본은 수정하지 않았다.

실행 후 별도로 계산한 jar SHA-256은 `5bf1a5b6b8a9b18965f23fd317a0bc008f83cc2135ba3cbbd3cc0facf4bac213`이다. Smoke manifest SHA-256은 `a0161aa362760a8a52be76b91a6c488bc79ebcfd80925a0fef6cb51faa676d62`다. 이 계산은 manifest의 자동 수집 정보와 구분한다.

현재 기준은 [구매 대기 구조](../../architecture/purchase-waiting.md), 결정 근거는 [ADR 0007](../../adr/0007-purchasewaiting-ports-and-adapters.md), 미완료 구조 검증은 [테스트 기준](../../development/testing.md#purchasewaiting-구조-전환-중-검사)이 소유한다. 다른 모듈 전환과 대규모 성능 평가는 진행하지 않았으며 다음 단계는 사용자 검토 후 명시적인 요청을 기다린다.
