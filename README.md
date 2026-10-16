# Limited Ticketing

한정 수량 상품을 사용자당 1개까지 예약하고 결제하는 과정을 구현하는 프로젝트입니다. 판매 시작 시점의 동시 요청을 안전하게 처리하고, 측정된 병목을 근거로 기술을 점진적으로 도입하는 것을 목표로 합니다.

현재는 **Spring Modulith 기반 모듈러 모놀리스**로 구성되어 있으며, 모든 도메인 모듈이 하나의 Spring Boot 애플리케이션과 PostgreSQL 데이터베이스를 공유합니다. Redis ZSET은 대기 순서와 DB 진입 동시성만 제어하고, 구매 및 재고의 최종 정합성은 PostgreSQL이 보장합니다.

이 프로젝트는 처음부터 Redis나 메시지 브로커를 전제로 설계하지 않았습니다. PostgreSQL 조건부 원자 갱신으로 정합성을 확보한 뒤 k6로 병목을 측정했고, 실제 재고 행 경합이 관찰된 뒤에 Redis 대기열을 추가했습니다. 이후에도 Redis를 재고의 원장으로 사용하지 않고 장애 시 PostgreSQL 상태를 기준으로 복구할 수 있도록 경계를 유지합니다.

## 주요 기능

- 판매 상품 생성과 판매 기간 설정
- 판매 상태 전이: `DRAFT -> OPEN -> CLOSED`
- 사용자당 판매 상품 1개 구매 제한
- 결제 대기 구매 예약과 취소 및 만료
- 구매별 복수 결제 시도와 결제 멱등성
- 조건부 원자 갱신을 이용한 동시 재고 예약
- Redis ZSET 기반 상품별 대기열과 비동기 예약 처리
- DB와 Redis 불일치 및 중단된 처리 항목 복구
- Spring Modulith를 이용한 모듈 경계 검증 및 문서 생성
- Testcontainers PostgreSQL/Redis 기반 통합 및 동시성 테스트
- k6 기반 판매 오픈 부하 테스트

## 아키텍처

`app`이 모든 업무 모듈을 조립하고 하나의 Spring Boot 프로세스로 실행합니다. 모듈은 코드 소유권과 의존 방향의 경계입니다. PostgreSQL은 업무 상태를, Redis는 구매 대기 순번과 처리 슬롯을 관리합니다.

전체 시스템과 실행 구성은 [C4 아키텍처](docs/architecture/README.md), 코드 경계는 [모듈 설계](docs/architecture/module-boundaries.md), 현재 로컬 배치는 [Deployment](docs/architecture/deployment.md)를 참고합니다.

## 기술 스택

| 구분 | 기술 |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.5, Spring Modulith 1.4 |
| Persistence | Spring Data JPA, PostgreSQL 17, Flyway |
| Queue | Redis 7, ZSET, Lua Script, Spring TaskExecutor |
| Security | Spring Security, 로컬 테스트용 Header 인증 |
| Test | JUnit 5, Spring Boot Test, Testcontainers, Spring Modulith Test |
| Load Test | k6 |
| Build | Gradle Multi-Project Build |

## 시작하기

프로젝트 루트에서 PostgreSQL과 Redis를 실행한 뒤 애플리케이션을 시작합니다.

```bash
cp .env.example .env
docker compose up -d
SPRING_PROFILES_ACTIVE=local ./gradlew :app:bootRun
```

기본 주소는 `http://localhost:8081`입니다. 별도 터미널에서 전체 코드 규칙과 테스트를 검증합니다.

```bash
./gradlew check
```

설정 값, 로컬 인증 헤더와 API 목록은 [개발 및 검증 가이드](docs/development/README.md)를 참고합니다.

## 학습 구현 진행

**한정 상품 플래시세일**을 기준으로 Redis ZSET·Kafka·조회 캐시·인기/판매 랭킹·장애 복구·5,000명 부하 평가를 학습합니다. [전체 구현 계획](docs/plans/2026-10-15-flash-sale-implementation-plan.md)과 [1단계 스펙](docs/archive/specs/2026-10-15-stage-1-observability-baseline.md)을 검토한 뒤 단계별로 진행합니다. 각 단계는 검증·한국어 commit/push 후 검토 대기하며, 다음 단계는 별도 시작 요청과 필요한 논의 후 진행합니다. 현재 **2단계 구현·검증·리뷰 완료 후 검토 대기**입니다. [2단계 평가](docs/evaluations/load-testing/2026-10-16-stage-2-queue-consistency.md)를 확인하고 다음 단계는 별도로 시작합니다.

## 문서

[문서 목차](docs/README.md)에서 목적에 맞는 문서를 선택할 수 있습니다.

| 문서 | 내용 |
|---|---|
| [아키텍처](docs/architecture/README.md) | C4, 모듈·계층, 데이터 정합성과 구매 대기 계약 |
| [개발 안내](docs/development/README.md) | 로컬 실행, 환경 변수와 API |
| [테스트 기준](docs/development/testing.md) | 업무 테스트, 통합 테스트, 아키텍처·코드 규칙 검사 |
| [ADR](docs/adr/README.md) | 주요 선택의 배경, 대안과 비용 |
| [부하 테스트](docs/performance/load-testing.md) | 직접 예약·대기 접수 실행과 최종 상태 검증 |
| [평가 기록](docs/evaluations/load-testing/2026-10-15-existing-results.md) | 기존 측정 수치, 원본 식별자와 해석 한계 |
| [구현 계획](docs/plans/2026-10-15-flash-sale-implementation-plan.md) | 플래시세일 학습 범위·단계별 작업·검토 규칙 |
| [1단계 스펙](docs/archive/specs/2026-10-15-stage-1-observability-baseline.md) | 관측·재현 가능한 기준선의 구현·인수 계약 |
| [로드맵](docs/plans/roadmap.md) | 현재 진행 상태와 다음 단계 검토 |
| [완료 스펙](docs/archive/specs/2026-10-15-purchase-waiting-implementation.md) | 당시 구현 계획 원문과 현재 문서 연결 |
