# 로컬 개발과 API

모든 명령은 프로젝트 루트에서 실행한다. 시스템 설계는 [아키텍처](../architecture/module-boundaries.md), 성능 측정은 [부하 테스트](../performance/load-testing.md)를 참고한다.

## 로컬 실행

### 요구 사항

- JDK 17
- Docker 및 Docker Compose

### PostgreSQL과 Redis 실행

```bash
cp .env.example .env
docker compose up -d
docker compose ps
```

기본 연결 정보는 다음과 같습니다.

| 대상 | 기본값 |
|---|---|
| Application | `http://localhost:8081` |
| PostgreSQL | `localhost:5432/limited_ticketing` |
| Redis | `localhost:6379` |

### 애플리케이션 실행

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew :app:bootRun
```

Flyway가 시작 시 [DB migration](../../app/src/main/resources/db/migration)의 스키마를 적용하고 Hibernate는 `ddl-auto=validate`로 매핑 일치 여부만 검사합니다.

대기열 Worker와 복구 작업의 주요 환경 변수는 다음과 같습니다. 기본값의 기준은 [application.yml](../../app/src/main/resources/application.yml)입니다.

| 환경 변수 | 기본값 | 설명 |
|---|---:|---|
| `QUEUE_MAX_CONCURRENT_PROCESSING` | 10 | 상품별 동시 DB 예약 처리 수 |
| `QUEUE_CLAIM_BATCH_SIZE` | 10 | Worker가 한 번에 claim하는 최대 항목 수 |
| `QUEUE_WORKER_FIXED_DELAY` | 100ms | 대기 항목 claim 주기 |
| `QUEUE_WORKER_INITIAL_DELAY` | 1000ms | 애플리케이션 시작 후 첫 Worker 실행 지연 |
| `QUEUE_RECOVERY_FIXED_DELAY` | 5000ms | DB/Redis 불일치와 stale PROCESSING 복구 주기 |
| `QUEUE_RECOVERY_INITIAL_DELAY` | 5000ms | 애플리케이션 시작 후 첫 Recovery 실행 지연 |
| `QUEUE_RECOVERY_BATCH_SIZE` | 1000 | Recovery의 DB keyset 및 존재 확인 배치 크기 |
| `QUEUE_PROCESSING_TIMEOUT_SECONDS` | 30 | PROCESSING을 중단 상태로 판단하는 시간 |

로컬 프로필에서는 실제 인증 구현 대신 다음 헤더를 사용합니다.

```text
X-Member-Id: {memberId}
X-Admin-Id: admin
```

이 인증 방식은 `local`, `test` 프로필 전용이며 운영용 인증 수단이 아닙니다.

## 주요 API

| Method | Endpoint | 설명 |
|---|---|---|
| `POST` | `/api/admin/sale-products` | 판매 상품과 초기 재고 생성 |
| `PUT` | `/api/admin/sale-products/{id}/sale-period` | 판매 기간 변경 |
| `POST` | `/api/admin/sale-products/{id}/open` | 판매 시작 |
| `POST` | `/api/admin/sale-products/{id}/close` | 판매 종료 |
| `GET` | `/api/sale-products` | 판매 상품 목록 조회 |
| `GET` | `/api/sale-products/{id}` | 판매 상품 상세 조회 |
| `POST` | `/api/idempotency-keys` | 구매 또는 결제 요청 키 발급 |
| `POST` | `/api/sale-products/{id}/purchases` | 직접 구매 예약 |
| `POST` | `/api/purchases/{id}/cancel` | 구매 예약 취소 |
| `POST` | `/api/purchases/{id}/payments` | 결제 시도 생성 |
| `POST` | `/api/payments/{id}/approve` | 결제 승인 |
| `POST` | `/api/payments/{id}/fail` | 결제 실패 처리 |
| `POST` | `/api/sale-products/{id}/queue-entries` | 대기열 기반 예약 접수 |
| `GET` | `/api/queue-entries/{id}` | 대기 순번 및 처리 상태 조회 |

요청과 응답의 상세 계약은 Controller 코드 및 [대기열 명세](../architecture/purchase-waiting.md)를 참고합니다.

Redis 대기열 테스트 데이터 생성부터 Smoke/Load 실행, Redis 관찰 및 DB 검증까지의 명령은 [k6 부하 테스트 가이드](../performance/load-testing.md)에 정리되어 있습니다.

`spring.data.redis.repositories.enabled=false`는 Redis Repository 자동 구성을 끄며, `StringRedisTemplate` 기반 ZSET 사용은 유지한다.

## 코드 검증

테스트 책임과 검사 명령은 [테스트 기준](testing.md)에서 관리한다.
