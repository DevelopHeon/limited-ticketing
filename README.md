# Limited Ticketing

한정 수량 상품을 사용자당 1개까지 예약하고 결제하는 과정을 구현하는 프로젝트입니다. 판매 시작 시점의 동시 요청을 안전하게 처리하고, 측정된 병목을 근거로 기술을 점진적으로 도입하는 것을 목표로 합니다.

현재는 **Spring Modulith 기반 모듈러 모놀리스**로 구성되어 있으며, 도메인 모듈이 같은 애플리케이션 코드와 PostgreSQL 데이터베이스를 공유합니다. 기본 실행은 API와 Worker를 함께 구동하고, 같은 산출물을 API/Worker 역할로 나눠 실행할 수 있습니다. Redis ZSET은 대기 순서와 DB 진입 동시성만 제어하고, 구매 및 재고의 최종 정합성은 PostgreSQL이 보장합니다.

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
- Transactional Outbox·Kafka 예약 전달과 중복 소비 방어·관리자 재처리
- 조회 전용 Redis의 상품 상세·목록 캐시와 commit 후 무효화·동시 miss 제어
- Spring Modulith를 이용한 모듈 경계 검증 및 문서 생성
- Testcontainers PostgreSQL/Redis/Kafka 기반 통합 및 동시성 테스트
- k6 기반 판매 오픈 부하 테스트

## 아키텍처

`app`이 모든 업무 모듈을 조립하고 Spring Boot 애플리케이션을 실행합니다. API와 Worker는 기본 통합 실행 또는 역할별 분리 실행을 지원합니다. 모듈은 코드 소유권과 의존 방향의 경계입니다. PostgreSQL은 업무 상태를, Redis는 구매 대기 순서와 처리 슬롯을 관리합니다.

전체 시스템과 실행 구성은 [C4 아키텍처](docs/architecture/README.md), 코드 경계는 [모듈 설계](docs/architecture/module-boundaries.md), 현재 로컬 배치는 [Deployment](docs/architecture/deployment.md)를 참고합니다.

## 기술 스택

| 구분 | 기술 |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.5, Spring Modulith 1.4 |
| Persistence | Spring Data JPA, JdbcTemplate, PostgreSQL 17, Flyway |
| Queue / Cache | Redis 7, ZSET·Lua·상품 표시 캐시, Kafka 4.2, Spring Kafka |
| Security | Spring Security, 로컬 테스트용 Header 인증 |
| Test | JUnit 5, Spring Boot Test, Testcontainers, Spring Modulith Test |
| Load Test | k6 |
| Build | Gradle Multi-Project Build |

## 시작하기

프로젝트 루트에서 PostgreSQL·Redis·Kafka를 실행한 뒤 애플리케이션을 시작합니다.

```bash
test -f .env || cp .env.example .env
docker compose --profile kafka up -d
SPRING_PROFILES_ACTIVE=local ./gradlew :app:bootRun
```

기본 주소는 `http://localhost:8081`입니다. 별도 터미널에서 전체 코드 규칙과 테스트를 검증합니다.

```bash
./gradlew check
```

설정 값, 로컬 인증 헤더와 API 목록은 [개발 및 검증 가이드](docs/development/README.md)를 참고합니다.

## 학습 범위와 문서

한정 상품 플래시세일을 기준으로 Redis ZSET·Kafka·조회 캐시·인기/판매 랭킹·장애 복구·부하 평가를 학습한다. [현재 진행 상태와 다음 작업](docs/plans/roadmap.md), [제품 요구사항과 단계별 인수 기준](docs/plans/2026-10-15-flash-sale-implementation-plan.md)을 구분해 읽는다. 단계별 실행·리뷰·commit/push 규칙은 [AGENTS.md](AGENTS.md)를 따른다.

[문서 안내](docs/README.md)는 구조, 결정 이유, 실행·검증, 앞으로의 작업, 실험 결과를 찾는 진입점이다. README에는 단계 완료 상태나 개별 측정값을 중복 기록하지 않는다.
