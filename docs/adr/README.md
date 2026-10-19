# 아키텍처 결정 기록

현재 시스템의 구성은 [아키텍처](../architecture/README.md)가 설명하고, 이 디렉터리는 중요한 선택의 배경·대안·비용을 결정별로 보존한다. 같은 결정이 대체되면 이전 기록을 삭제하지 않고 Superseded로 표시해 후속 ADR과 연결한다.

ADR 0001–0004는 **2026-10-15에 기존 구현과 문서를 근거로 회고 작성**했다. ADR 0005·0006은 이번 논의와 구현의 결정이며 구현 적용 상태를 본문에서 구분한다. Accepted는 현재 코드에 채택된 상태를 뜻하며 최초 결정일을 의미하지 않는다. 대안 표는 지금 정리한 비교이며 당시 실험이나 논의 기록으로 간주하지 않는다.

| ADR | 상태 | 결정 |
|---|---|---|
| [0001](0001-modular-monolith.md) | Accepted · 회고 | 하나의 실행 앱 안에서 업무별 모듈과 공개 계약 유지 |
| [0002](0002-postgresql-consistency.md) | Accepted · 회고 | PostgreSQL 조건부 전이와 제약으로 구매·재고 정합성 보호 |
| [0003](0003-purchase-waiting.md) | Accepted · 회고 | 구매 대기를 업무 모듈로 분리하고 Redis는 순서·슬롯에 사용 |
| [0004](0004-architecture-guards.md) | Accepted · 회고 | 모듈 내부·모듈 간·코드 규칙을 서로 다른 검사로 보호 |
| [0005](0005-inbound-outbound-adapters.md) | Superseded · ADR 0007·0008 | 최초 presentation 입력·infrastructure 출력 배치 |
| [0006](0006-durable-queue-admission-and-attempts.md) | Accepted | DB 순번·입구 상한·시도 토큰·lease와 실행 슬롯 보호 |
| [0007](0007-purchasewaiting-ports-and-adapters.md) | Partially Superseded · ADR 0008·0009 | 입력·출력 Adapter를 infrastructure에 배치하고 Port/UseCase/Service 이름 통일 |
| [0008](0008-application-entry-orchestration.md) | Accepted | Controller·Scheduler를 입력 Adapter로 보고 업무 조합은 Application Facade에 배치 |
| [0009](0009-domain-repository-ownership.md) | Accepted | Aggregate 영속성은 Domain Repository, 검색·외부 기능은 Application Output Port |

| [0010](0010-kafka-reservation-outbox.md) | Accepted | 예약 상태와 Outbox 원자 저장·Kafka 전달·업무 멱등성·API/Worker 역할 |

진행 중인 도입 후보는 [로드맵](../plans/roadmap.md)에 둔다. Kafka·Outbox는 ADR 0010을 따르며, 독립 서비스 분리는 도입하지 않는다. 완료 스펙 원문은 [보관본](../archive/specs/2026-10-15-purchase-waiting-implementation.md)에서 확인한다.
