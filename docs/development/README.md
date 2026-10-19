# 로컬 개발과 API

모든 명령은 프로젝트 루트에서 실행한다. 시스템 설계는 [아키텍처](../architecture/module-boundaries.md), 성능 측정은 [부하 테스트](../performance/load-testing.md)를 참고한다.

## 작업 위치와 결과

기존 `limited-ticketing` checkout의 `master`에서 직접 작업한다. worktree는 여러 branch의 병렬 작업 등 작업 영역 분리가 필요한 복잡한 경우에만 사용한다. 앱·DB·Redis는 기존 로컬 환경을 사용한다. 단계별 검증·한국어 commit/push 후 다음 단계 시작 요청을 기다린다. 저장소의 [작업 규칙](../../AGENTS.md)을 따른다.

측정 원본은 기존 프로젝트의 `load-test/k6/results/`, 평가 보고서는 `docs/evaluations/load-testing/`에서 검토한다. 측정 도구·SQL·테스트는 Git으로 관리하고 실행 결과와 비밀 설정은 제외한다. 실행·보존 절차는 [부하 테스트](../performance/load-testing.md)가 소유한다.

## 로컬 실행

### 요구 사항

- JDK 17
- Docker 및 Docker Compose

### PostgreSQL·Redis·Kafka 실행

```bash
test -f .env || cp .env.example .env
docker compose --profile kafka up -d
docker compose ps
```

기본 연결 정보는 다음과 같습니다.

| 대상 | 기본값 |
|---|---|
| Application | `http://localhost:8081` |
| PostgreSQL | `localhost:5432/limited_ticketing` |
| Redis | `localhost:6379` |
| Kafka bootstrap | `localhost:19092,localhost:29092,localhost:39092` |

### 애플리케이션 실행

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew :app:bootRun
```

Flyway가 시작 시 [DB migration](../../app/src/main/resources/db/migration)의 스키마를 적용하고 Hibernate는 `ddl-auto=validate`로 매핑 일치 여부만 검사합니다.

대기열 Worker와 복구 작업의 주요 환경 변수는 다음과 같습니다. 기본값의 기준은 [application.yml](../../app/src/main/resources/application.yml)입니다.

| 환경 변수 | 기본값 | 설명 |
|---|---:|---|
| `QUEUE_MAX_OUTSTANDING` | 10000 | 상품별 WAITING + DISPATCHED + PROCESSING 접수 상한 |
| `QUEUE_GLOBAL_CONCURRENT_PROCESSING` | 10 | DB가 보호하는 전체 DISPATCHED + PROCESSING 입장 상한 |
| `DB_POOL_MAX_SIZE` | 30 | Hikari 최대 연결 수 |
| `QUEUE_MAX_CONCURRENT_PROCESSING` | 10 | 상품별 DISPATCHED + PROCESSING 입장 상한 |
| `QUEUE_CLAIM_BATCH_SIZE` | 10 | Worker가 한 번에 claim하는 최대 항목 수 |
| `QUEUE_WORKER_FIXED_DELAY` | 100ms | 대기 항목 claim 주기 |
| `QUEUE_WORKER_INITIAL_DELAY` | 1000ms | 애플리케이션 시작 후 첫 Worker 실행 지연 |
| `QUEUE_RECOVERY_FIXED_DELAY` | 5000ms | DB/Redis 불일치와 stale PROCESSING 복구 주기 |
| `QUEUE_RECOVERY_INITIAL_DELAY` | 5000ms | 애플리케이션 시작 후 첫 Recovery 실행 지연 |
| `QUEUE_RECOVERY_BATCH_SIZE` | 1000 | Recovery의 DB keyset 및 존재 확인 배치 크기 |
| `QUEUE_PROCESSING_TIMEOUT_SECONDS` | 30 | 처리 시작 권한의 lease, 실행 중 잠긴 행은 회수하지 않음 |

Consumer의 외부 결과 transaction과 내부 예약 transaction은 연결을 각각 사용한다. 기본 Consumer concurrency는 3이며 Hikari 풀은 30이다. 여러 Worker를 실행하면 모든 Consumer·HTTP·Publisher·복구 작업의 연결 여유를 함께 계산한다. V7 적용 전 이전 Worker를 중지하고 새 버전만 실행한다.

### API와 Worker 역할 분리

기본 `local`은 API·Dispatcher·Publisher·Consumer·복구를 함께 실행한다. 같은 산출물을 분리할 때는 두 터미널을 사용한다. 기존 DB·Redis·Kafka와 같은 설정을 공유하며 별도 Compose project를 만들지 않는다.

```bash
./gradlew :app:bootJar
SPRING_PROFILES_ACTIVE=local,api java -jar app/build/libs/app-0.0.1-SNAPSHOT.jar
```

```bash
SPRING_PROFILES_ACTIVE=local,worker java -jar app/build/libs/app-0.0.1-SNAPSHOT.jar
```

실행 중인 JAR 경로에 다시 `bootJar`를 빌드하면 지연 클래스 로딩이 실패할 수 있다. 재빌드 전 앱을 중지하거나, 검증할 JAR를 Git 제외 결과 경로에 복사해 고정하고 두 역할이 같은 복사본을 사용한다.

API 기본 포트는 8081, Worker는 8082(`WORKER_PORT`)다. API는 업무 HTTP만 제공하고 Dispatcher·Publisher·Consumer·만료·복구 스케줄러를 실행하지 않는다. Worker는 업무 Controller를 제공하지 않으며 health와 관리자 metrics를 노출한다. `api,worker`를 동시에 지정하면 시작을 거부한다. 통합 실행 중인 앱과 분리 역할을 중복 실행하지 않는다.

Kafka는 combined KRaft 3개 노드·고정 image digest·RF3·minISR2를 사용한다. 각 노드는 CPU 0.75·메모리 768MiB·JVM heap 384MiB다. reservation과 DLT topic은 각각 6 partitions다. `KAFKA_BOOTSTRAP_SERVERS`로 호스트 연결을 변경할 수 있다. 이 로컬 PLAINTEXT 구성은 운영 인증 구성이 아니다.

### Kafka 관찰과 복구

```bash
docker compose --profile kafka exec -T kafka-1 /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka-1:9092 --describe --topic limited-ticketing.purchasewaiting.reservation.v1
docker compose --profile kafka exec -T kafka-1 /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka-1:9092 --describe --group purchasewaiting-reservation-v1
curl -H 'X-Admin-Id: admin' http://localhost:8082/actuator/prometheus
```

통합 실행이면 metrics 포트도 8081이다. 분리 실행에서 기존 Prometheus 기본 대상은 API 8081이며 Worker metrics는 8082에서 별도로 확인한다. `reservation.outbox.pending`, `reservation.outbox.oldest.seconds`, `reservation.dispatched.oldest.seconds`, 상태별 `reservation.waiting.entries`, `reservation.execution.attempts`, `reservation.consumer.duration`과 Kafka client의 lag·assignment 지표를 함께 본다. Worker의 전달 지연 Monitor가 60초 주기로 확인해 60초 이상인 경우 WARN을 남긴다. Broker·Publisher·Consumer 상태와 token을 조사한다.

JSON/version/key 오류는 DLT 전달 확인 후 offset을 저장한다. DB 확인 또는 DLT 전달이 불가해 Consumer가 정지하면 먼저 원인을 해결한 뒤 Worker를 재시작한다. offset을 임의로 건너뛰지 않는다. 기술 FAILED 또는 발행 후 60초 이상 지난 DISPATCHED는 관리자 `POST /api/admin/queue-entries/{id}/replay`로 재처리하며, 허용·거부 조건은 [현재 계약](../architecture/purchase-waiting.md#관리자-재처리)을 따른다.

직접 예약 HTTP를 비교 측정할 때만 `SPRING_PROFILES_ACTIVE=local,baseline`으로 실행한다. 기본 `local`에서는 직접 예약 HTTP를 제공하지 않는다.

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
| `POST` | `/api/sale-products/{id}/purchases` | 직접 구매 예약 (`baseline` 전용) |
| `POST` | `/api/purchases/{id}/cancel` | 구매 예약 취소 |
| `POST` | `/api/purchases/{id}/payments` | 결제 시도 생성 |
| `POST` | `/api/payments/{id}/approve` | 결제 승인 |
| `POST` | `/api/payments/{id}/fail` | 결제 실패 처리 |
| `POST` | `/api/sale-products/{id}/queue-entries` | 대기열 기반 예약 접수 |
| `GET` | `/api/queue-entries/{id}` | 대기 순번 및 처리 상태 조회 |
| `POST` | `/api/admin/queue-entries/{id}/replay` | 기술 실패·장기 전달 대기 재처리 |

요청과 응답의 상세 계약은 Controller 코드 및 [대기열 명세](../architecture/purchase-waiting.md)를 참고합니다.

Redis 대기열 테스트 데이터 생성부터 Smoke/Load 실행, Redis 관찰 및 DB 검증까지의 명령은 [k6 부하 테스트 가이드](../performance/load-testing.md)에 정리되어 있습니다.

`spring.data.redis.repositories.enabled=false`는 Redis Repository 자동 구성을 끄며, `StringRedisTemplate` 기반 ZSET 사용은 유지한다.

## 코드 검증

테스트 책임과 검사 명령은 [테스트 기준](testing.md)에서 관리한다.

## 관측과 자동 기준선

Actuator의 health는 상세 내부 정보 없이 공개하고, prometheus는 local/test 관리자에게만 제공한다. 다른 Actuator 경로는 차단한다. 기존 앱·DB·Redis와 같은 Compose project의 `observability` profile을 사용한다.

Grafana 비밀번호는 `.env` 대신 Git 제외 파일 `.local/observability/secrets/grafana_admin_password`로 관리한다. 프로젝트 루트에서 비밀번호를 준비하고 관측 서비스를 실행한다.

```bash
bash scripts/setup-observability.local.sh
docker compose --profile observability up -d prometheus grafana
```

준비 명령은 OpenSSL로 비밀번호를 생성하며 기존 파일의 값은 보존한다. 비어 있는 기존 파일은 덮어쓰지 않고 실패한다. 디렉터리는 700, 파일은 600 권한으로 유지하며 비밀번호를 출력하지 않는다. 직접 로컬에서 파일을 확인하고 문서·터미널 기록·Git에 복사하지 않는다. 다른 위치에서 스크립트를 실행해도 비밀번호는 이 프로젝트 안에 준비된다.

`secret-init`이 Docker secret을 컨테이너 전용 `grafana-credentials` 볼륨에 복사하고 Grafana UID 472·파일 권한 600을 적용한다. Grafana는 해당 볼륨을 읽기 전용으로 마운트하고 [파일 기반 비밀번호 설정](https://grafana.com/docs/grafana/latest/setup-grafana/configure-docker/)으로 읽는다. 호스트 파일 권한을 낮추지 않고 Linux의 UID 차이를 처리한다. 기본 profile의 PostgreSQL·Redis 실행에는 비밀번호 준비가 필요하지 않다.

Grafana는 `http://localhost:3000`, 사용자는 `admin`이며 최초 비밀번호는 위 파일에 있다. Prometheus는 `http://localhost:9090`이다. `.env`는 포트·scrape 대상 등 비밀이 아닌 관측 설정을 맡고, 기존 `GRAFANA_ADMIN_PASSWORD` 변수는 사용하지 않는다. 기존 `.env`는 덮어쓰지 않는다.

이미 `grafana-data`에 관리자 계정이 초기화되어 있으면 파일을 바꿔도 기존 계정 비밀번호는 자동 변경되지 않는다. 기존 암호를 계속 사용하고, 변경이 필요하면 로그인 후 계정 설정에서 변경한다. 비밀번호를 맞추려고 데이터 볼륨을 삭제하지 않는다. 최초 설정 파일과 변경한 계정 암호가 서로 다를 수 있으므로 기존 계정의 변경 이력을 함께 확인한다.

평가 도구 사용은 [자동 기준선](../performance/load-testing.md#자동-기준선--현재-평가-도구)을 따른다.
