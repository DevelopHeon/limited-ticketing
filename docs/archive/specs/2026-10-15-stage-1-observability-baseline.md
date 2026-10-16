# 1단계 스펙 · 관측과 재현 가능한 플래시세일 기준선

상태: **실행 인수 완료·검토 대기 / 1단계 보관 스펙**. 작성일: 2026-10-15. 기준 코드: `0b723512e2bf5c28ba6c80a7173f4e55d8efa00e`.

전체 제품·학습 범위와 단계 종료 규칙은 [구현 계획](../../plans/2026-10-15-flash-sale-implementation-plan.md)을 따른다. 이 문서는 **1단계의 구현 계약과 인수 기준**을 소유한다. 현재 실행 절차는 부하 가이드, 검증 결과와 미달 목표는 1단계 평가가 소유한다. 이 문서는 구현한 1단계 계약의 보관본이다. 측정 revision은 f10edf1, 판정/소유권 보완 revision은 5213635·2049265·6f18fee다.

## 1. 목적과 범위

현재 직접 예약과 Redis ZSET 대기 예약을 같은 유입 모델·초기 재고·관측 조건으로 측정한다. HTTP 수락 지연과 비동기 최종 결과를 구분하고 원본·설정·최종 DB 결과를 연결한다. 뒤 단계의 Kafka·캐시·랭킹을 검증할 측정 기준을 확보한다.

포함: Actuator/Prometheus/Grafana, 반복 가능한 fixture·공통 k6 시나리오·실행 manifest, Redis/DB 관측, 자동 최종 결과 검증, 기준선 보고서.

범위 밖: Kafka·Outbox·캐시·랭킹 구현, 재고·순번·claim/복구 알고리즘 수정, 다중 앱 운영, 실제 인증·PG, 최종 5,000명 성능 목표 달성. 측정 중 발견한 업무 결함은 숨기지 않고 기록하며, 수정이 필요하면 범위 변경을 논의한다.

## 2. 기존 코드에서 확인한 출발점

| 근거 | 현재 상태 | 이번 단계에 주는 제약 |
|---|---|---|
| [구매 Service](../../../purchase/src/main/java/com/example/limitedticketing/purchase/application/command/service/PurchaseCommandService.java) | 회원·판매 검사, DB 멱등성, 조건부 재고 예약, 5분 만료 | 유효한 새 회원·판매·키로만 비교 |
| [대기 Service](https://github.com/DevelopHeon/limited-ticketing/blob/a04cc26a1e4243246e31f3343f1a7190016c8630/purchasewaiting/src/main/java/com/example/limitedticketing/purchasewaiting/application/command/service/ReservationQueueCommandService.java) | DB 커밋 후 Redis 등록 | 202가 최종 예약 성공을 의미하지 않음 |
| [Worker](https://github.com/DevelopHeon/limited-ticketing/blob/e6e5287a7767880b808820c54ed11636d17da2e1/purchasewaiting/src/main/java/com/example/limitedticketing/purchasewaiting/infrastructure/scheduler/ReservationQueueWorker.java) | 기본 상품별 슬롯 10, claim 10, 주기 100ms, 복구 5초·timeout 30초 | 설정·관측 기간을 manifest에 고정 |
| [기존 direct 시나리오](../../../load-test/k6/sale-open.js) | VU당 1회, 키 발급 setup | 기존 waiting arrival 모델과 그대로 비교하면 유입 조건이 다름 |
| [기존 waiting 시나리오](../../../load-test/k6/waiting-room.js) | iteration 중 키 발급 후 접수, 응답 본문의 실패 check가 threshold와 분리됨 | 새 시나리오는 키 선발급·payload 검증·정확한 실행 건수 확인 |
| [기존 SQL](../../../load-test/k6/sql/verify-waiting-room.sql) | 상태별 수량 출력 | 출력만 보고 사람이 통과 판단하지 않도록 자동 판정 추가 |
| [보안 설정](../../../app/src/main/java/com/example/limitedticketing/configuration/security/SecurityConfig.java) | `/api` 밖 요청은 기본 허용 | Actuator 추가 시 명시적 접근 규칙 필요 |

현재 아키텍처·단위/통합 테스트 책임은 [설계](../../architecture/README.md), [테스트 기준](../../development/testing.md)을 따른다. 이전 성능 JSON은 [기존 평가](../../evaluations/load-testing/2026-10-15-existing-results.md)로 유지하고 새 실행과 섞지 않는다.

## 3. 관측 실행 구성과 HTTP 계약

### 3.1 실행 환경

기존 Compose의 PostgreSQL·Redis와 호스트에서 실행하는 앱을 유지한다. `observability` profile로 Prometheus·Grafana만 추가한다. 기본 profile의 서비스 시작 동작은 유지한다.

| 항목 | 계약 |
|---|---|
| 앱 | 기존 `http://localhost:8081`, `local` profile |
| Prometheus | 호스트 공개 `127.0.0.1:9090`, scrape interval 5초 |
| Grafana | 호스트 공개 `127.0.0.1:3000`, datasource·dashboard 자동 provisioning |
| scrape 대상 | 기본 `host.docker.internal:8081/actuator/prometheus`; Linux는 host-gateway 매핑 확인 |
| scrape 인증 | local/test 전용 `X-Admin-Id: admin` header, Prometheus `http_headers` 설정 |
| 이미지 | 구현 시작 시 호환 patch 버전/digest 고정, `latest` 사용 금지 |
| 자원 | 실행 전 앱 JVM·각 컨테이너 제한과 호스트 CPU/RAM·Docker 할당량 기록 |

예정 기동 명령은 다음과 같다. 서비스가 구현된 뒤 실행한다.

```text
docker compose --profile observability up -d
SPRING_PROFILES_ACTIVE=local ./gradlew :app:bootRun
```

관측 도구와 앱을 같은 머신에서 실행한 결과임을 표시한다. 별도 부하 발생기로 바꾸면 새 환경의 평가로 구분한다. Grafana 설정의 암호는 로컬 환경 변수로 주입하고 manifest에서 제외한다.

이번 측정 환경은 현재 Mac의 별도 Compose project `limited-ticketing-baseline`이다. 앱 18081, PostgreSQL 15432, Redis 16379, Prometheus 9090, Grafana 3000을 사용한다. PostgreSQL 1CPU/1GB, Redis 0.5CPU/256MB, 관측 서비스 각각 0.5CPU/512MB, 호스트 앱 JVM `-Xms256m -Xmx768m`로 측정한다. 기본 개발 연결 값은 유지하고 `.env` 및 `APP_METRICS_TARGET`으로 측정 환경을 분리한다. Prometheus v3.15.0·Grafana 13.2.4를 고정하고 실제 image digest는 manifest에서 기록한다.

### 3.2 접근과 계측

| 요청 | 기대 결과 |
|---|---|
| `GET /actuator/health` | 비인증 허용, 상세 내부 정보 미노출 |
| `GET /actuator/prometheus`, 비인증 | 401 |
| 같은 요청, `X-Member-Id`만 제공 | 403 |
| 같은 요청, local/test의 관리자 header | 200, Prometheus text 응답 |
| 기타 `/actuator/**` | 비인증 거부·인증 사용자 403, discovery 비활성화 |

`health`, `prometheus`만 HTTP expose한다. 기존 상품 GET 공개·관리 API ADMIN·나머지 API 인증 정책을 유지한다. 운영 인증을 구현한 것으로 설명하지 않는다.

HTTP 지연 histogram을 활성화해 `http_server_requests_seconds_*`의 count/sum/bucket을 수집한다. JVM heap/GC, thread, Hikari active·pending·timeout 지표를 노출한다. metric label은 URI template·method·status·outcome 등 유한한 차원만 사용하고 memberId·requestKey·queueEntryId·runId를 넣지 않는다. runId는 산출물과 로그에서 연결한다. 거래량이 적어 percentile 계산이 불가능한 구간은 0ms로 표시하지 않고 표본 없음으로 표시한다.

Application/Domain에 `MeterRegistry`를 직접 추가하지 않는다. 1단계의 업무 결과·처리 시간은 k6와 최종 SQL로 검증한다. 대시보드는 request rate·HTTP p95/p99·오류·JVM·Hikari·scrape UP을 표시하고 Redis/DB 샘플은 보고서에서 같은 UTC 타임라인으로 연결한다. exporter를 새로 여러 개 추가하지 않는다.

## 4. 공통 부하 실행 계약

### 4.1 CLI와 준비 조건

예정 진입점:

```text
python3 load-test/k6/run-baseline.py --flow waiting --scenario baseline --base-url http://localhost:8081 --output-root load-test/k6/results
```

`--flow`는 `direct` 또는 `waiting`, `--scenario`는 아래 표의 값만 허용한다. 연결은 `DATABASE_URL`과 `BASE_URL`/명시 CLI 인자로 받는다. 관측 조회는 `PROMETHEUS_URL`(기본 `http://localhost:9090`)을 사용하고 Redis 샘플은 기존 Compose `redis` 서비스에 Docker CLI로 접속한다. PostgreSQL 접속 비밀과 인증 header는 manifest·로그에 출력하지 않는다. 부하 테스트 전용 DB와 local 인증 앱을 대상으로 실행한다. Python 3·k6·psql·Docker CLI 미설치나 연결 실패는 부하 시작 전에 실패 처리한다.

실행기는 고유 runId를 생성하고 출력 디렉터리를 배타적으로 만든다. `flow`마다 새 fixture를 사용하며 이전 산출물·DB·Redis를 전체 초기화하지 않는다. fixture SQL은 실행 식별자·memberCount·inventoryQuantity를 명시적으로 받아 활성 회원·OPEN 상품 1개·초기 재고를 한 트랜잭션에서 만든다. runId 기반 충돌은 기존 fixture 재사용으로 우회하지 않고 실패 처리한다.

실험과 별도 fixture로 20회 예약 warmup을 하고 정확성만 확인한다. 측정 fixture 회원별 `PURCHASE` 요청 키를 사전 발급하고 모두 성공한 뒤 측정을 시작한다. 입력 fixture JSON은 `runId`, `saleProductId`, `initialInventory`, `members[{memberId,purchaseRequestKey}]`를 갖는다. JSON 경로는 절대 경로로 k6에 전달한다. 회원 수와 키 수가 목표 iteration 수와 정확히 일치하고 중복 회원·키가 없어야 한다.

### 4.2 시나리오

| scenario | 고유 회원/iteration | rate | duration | 초기 재고 | preAllocatedVUs / maxVUs |
|---|---:|---:|---:|---:|---:|
| `smoke` | 10 | 5/s | 2초 | 3 | 20 / 100 |
| `baseline` | 1,000 | 100/s | 10초 | 100 | 200 / 1,000 |
| `observe-5000` | 5,000 | 500/s | 10초 | 500 | 500 / 2,000 |

새 `flash-sale-baseline.js`는 두 flow 모두 `constant-arrival-rate`로 실행한다. iteration당 업무 HTTP 요청은 1회이고 key 발급·polling·결제·재시도는 측정 구간에 넣지 않는다. 계획 요청 수는 `rate × duration`, k6 2.1.0의 최소 1초 제약과 마지막 경계의 추가 iteration을 함께 피하도록 scenario 종료는 마지막 목표 시작 시각과 다음 시작 시각의 중간인 `duration*1000 - 500/rate` ms로 둔다. 부하를 건너뛰는 guard가 실행되어 raw iterations가 늘어난 경우에도 workload는 실패로 남긴다. Smoke는 2초·5/s로 조정해 10건을 유지하며 baseline은 10초·100/s, observe-5000은 10초·500/s를 유지한다. **실제 iterations·수락/업무 결과 합계가 목표와 같은지 별도로 검증**한다. 건수 차이는 임의 보정하지 않는다. `gracefulStop=30s`, 개별 HTTP timeout 10초다.

상품별 Worker 슬롯·claim·지연·복구 설정은 기존 기본값을 사용하고 manifest에 기록한다. 실험 중 동작 설정을 바꾸지 않는다. direct도 Worker·관측 설정을 동일하게 유지하고 측정 fixture에 대기 요청만 만들지 않는다. 전용 실험 DB에 다른 활성 대기가 없어야 한다. setup 시작 시와 측정 직전 이를 확인하고 있으면 실행을 거부한다. 이전 실험의 구매 만료 작업도 측정에 섞이지 않게 실행 전 정리한다. 정리는 원본 수집 뒤 실험 소유 회원/판매의 PENDING_PAYMENT 구매만 기존 취소 API로 반환하고 판매를 닫는 방식으로 하며, 업무 외 행을 수정하거나 전체 DB/Redis를 삭제하지 않는다. warmup도 drain·정리 후 측정 fixture를 준비한다. 정리 실패는 기록하고 다음 실행 전에 해결한다. 검증 snapshot은 정리 전 시각의 결과임을 표시한다.

### 4.3 응답과 결과 분류

| flow | 예상 응답 | 정상 업무 판정 |
|---|---|---|
| direct | 201 또는 409 | 201은 유효 purchaseId·`PENDING_PAYMENT`; 409는 오류 code=`INSUFFICIENT_INVENTORY`만 품절 |
| waiting | 202 | 유효 queueEntryId와 허용 상태를 포함하고 최종 상태는 SQL로 판정 |

HTTP 상태가 맞아도 JSON 누락·파싱 오류·필수 ID/상태 오류는 실패다. waiting의 응답 상태는 Worker와 경합할 수 있으므로 `WAITING/PROCESSING/RESERVED/SOLD_OUT/FAILED` 중 하나여야 하며 FAILED는 업무 실패로 기록한다. payload check를 threshold에 연결한다. 401·403·예상 외 409·429·503·timeout은 별도 오류이고 정상 품절로 합치지 않는다.

k6 summary에는 `iterations`, `dropped_iterations`, `checks`, 요청/응답 오류 수, direct 예약/품절 수, waiting 수락 수, HTTP 업무 요청 지연 p50/p95/p99를 저장한다. direct의 예약 성공과 품절 응답 지연은 별도 Trend로도 저장해 빠른 품절 응답이 성공 경로 지연을 가리지 않게 한다. k6 HTTP name tag는 URI template로 고정한다. 서버 HTTP 지표에는 setup·scrape 영향이 섞일 수 있어 측정 시작/끝과 URI별 범위를 명시한다.

## 5. 최종 상태와 관측 데이터

측정 종료 후 waiting은 최대 120초까지 2초마다 활성 상태를 확인한다. direct도 동일한 최종 SQL 검증을 실행한다. 측정+drain 동안 결제·취소·만료가 발생하면 비교 시나리오 위반으로 실패 처리한다. 요청은 5분 예약 만료 전에 검증이 끝나야 하며 시간 초과를 스케줄러 비활성화로 숨기지 않는다.

`verify-flash-sale.sql`은 fixture의 판매 ID·회원·초기 재고로 범위를 제한한 JSON 한 건을 반환한다. 실행기는 최소 다음을 검사한다.

| 검증 | direct | waiting |
|---|---|---|
| 최종 구매 | PENDING_PAYMENT=초기 재고, 나머지 구매 상태 0 | 동일 |
| 대기 요청 | 0 | 전체=실제 accepted=목표 iterations |
| 대기 결과 | 해당 없음 | RESERVED=초기 재고, SOLD_OUT=회원 수-초기 재고, FAILED/WAITING/PROCESSING=0 |
| 재고 | available=0, reserved=초기 재고, sold=0, 합계=초기 재고 | 동일 |
| 업무 연결 | 요청별 중복 구매 0 | RESERVED의 purchaseId·회원·판매·요청 키 연결 불일치 0, 중복 연결 0 |
| Redis | 측정 판매 ID의 waiting/processing 구성원 0 | 최종 waiting/processing 구성원 0 |

단순 purchaseId 존재 확인만으로 동일 구매 중복 연결을 통과시키지 않는다. SQL 결과와 k6의 성공·품절/수락 건수를 대조한다. 120초 뒤에도 활성 항목이 있으면 마지막 DB·Redis 상태를 저장하고 완료 실패로 기록한다.

waiting의 `completed_at-created_at` p50/p95/p99를 DB에서 계산해 **접수부터 대기 결과 확정 시간**으로 저장한다. HTTP 접수 p95와 합치지 않는다. 표본 수·processing 경과·잔여 건수를 함께 남긴다. direct는 HTTP 응답이 업무 완료 시점이므로 그 지연을 비교하되 waiting의 단순 202 응답과 같은 의미라고 주장하지 않는다.

측정 시작부터 drain 종료까지 1초마다 `samples.csv`에 UTC 시각·Redis waiting/processing 수·해당 DB Lock 대기 세션 수를 수집한다. 명령 실패는 누락/오류로 표시하고 0으로 바꾸지 않는다. 설정한 관측 overhead는 baseline 보고서에 제한으로 기재한다. 최종 요청이 scrape에 들어오도록 결과 검증 뒤 5초의 scrape grace를 관측 구간에 포함하며 측정·drain·관측 종료 시각을 구분한다. Prometheus의 해당 구간 HTTP/JVM/Hikari 시계열을 조회해 `prometheus.json`으로 보존한다. 표본 누락과 scrape 실패는 보고서에 표시한다.

## 6. 산출물·판정·원본 보존

예정 구조:

```text
load-test/k6/results/<runId>/
├── manifest.json
├── fixture.json
├── summary.json
├── verification.json
├── samples.csv
├── prometheus.json
├── k6.stdout.log
└── k6.stderr.log
```

manifest schemaVersion은 1이다. UTF-8 JSON으로 시작 시 생성하고 종료 시 실패 여부와 artifact hash를 확정한다. 중단되어도 시작 정보와 원본 로그가 남아야 한다.

| 필드 | 필수 내용 |
|---|---|
| 식별 | runId, startedAt/finishedAt UTC, flow, scenario, schemaVersion |
| 코드·도구 | gitRevision, workingTreeDirty, k6/Python/Java/psql 버전, 컨테이너 image/digest |
| 환경 | OS/architecture·CPU/RAM·Docker 할당·각 서비스/JVM 제한·앱 profile·관측 설정 |
| 입력 | rate·duration·VUs·timeout·drainDeadline·초기 수량·판매 ID·Worker 설정·fixture hash |
| 결과 | 실제 iteration·drop·오류·업무별 수량, k6/process exitCode, 단계별 verdict, 마지막 오류 |
| 원본 | manifest 자신을 제외한 각 artifact의 상대 경로·SHA-256·표본 누락·중단 상태 |

fixture에는 로컬 테스트 회원과 요청 키만 담고 DB URL 비밀번호·환경 변수 전체·관리자 header를 기록하지 않는다. raw 원본은 기존 Git 제외 `load-test/k6/results/`에 보존한다. 평가 보고서에는 실제 보존 위치·hash·재현 명령·조건을 기록한다. 현재 작업 PC에만 있으면 그 사실을 표시하고 다른 checkout에서 원본을 재현 가능하다고 주장하지 않는다. 외부 업로드는 별도 명시적 요청을 따른다.

판정은 `workload`, `integrity`, `observability`, `latency`로 분리해 각각 PASS/FAIL/INCOMPLETE를 저장한다. latency 기준은 전체 계획의 목표안과 비교하되 1단계 완료 조건으로 강제하지 않는다. 도구 exit code는 전체 PASS=0, 완결된 평가의 FAIL=1, 준비/도구/중단으로 평가 불완전=2로 한다. 어느 경우든 원본과 가능한 최종 검증을 보존하고 실패 command 뒤에 결과 수집을 생략하지 않는다.

1단계 baseline의 업무 정확성이 실패하면 단계 완료 처리하지 않는다. `observe-5000`은 한계 관찰이며 처리 미완료·drop·자원 부족을 정확히 기록해도 1단계 산출물이 될 수 있다. 이 결과로 최종 5,000명 목표를 달성했다고 표시하지 않는다.

## 7. 테스트·인수 기준

| ID | 검증 | 합격 근거 |
|---|---|---|
| S1-01 | 지표 접근 정책·기존 API 인증 회귀 | 실제 HTTP TestRestTemplate+Testcontainers 통합 테스트, 관리자 200/회원403/비인증401 |
| S1-02 | HTTP/JVM/Hikari·Prometheus·Grafana 연결 | 실제 요청 후 계측 count 증가, scrape UP·패널 데이터 |
| S1-03 | 입력·fixture·출력 경로 안전성 | 중복 입력/기존 run 경로 거부, direct/waiting 새 fixture 비간섭 |
| S1-04 | 예상 업무 실패와 시스템 실패 구분 | 품절 외 409·payload 오류·drop·timeout을 실패로 판정 |
| S1-05 | 비동기 완료·재고·연결 검증 | 두 flow Smoke와 baseline의 SQL/k6 대조 PASS |
| S1-06 | 실패 원본 보존 | 의도한 SQL 불일치·불완전 summary·중단 시 manifest와 로그 유지 |
| S1-07 | 반복 가능한 비교 | baseline 각 flow 3회, observe-5000 각 flow 1회와 원본 hash·설정 |
| S1-08 | 기존 보호 규칙 유지 | `./gradlew check` PASS, Python 테스트 PASS, 기존 검사 완화 없음 |
| S1-09 | 평가와 인계 | 결과·한계·미달·다음 논의 기록, 한국어 commit/push 확인 후 검토 대기 |

Java 테스트는 한국어 `@DisplayName`으로 업무/접근 규칙을 설명한다. Python unittest는 한국어 설명을 사용하고 파일명/클래스명은 English로 유지한다. 검증하려는 분기·실패 결과가 없는 설정값 복제 테스트는 만들지 않는다.

1단계 종료 시 학습자가 설명할 질문: 202 지연과 결과 확정 시간의 차이, 유입률과 VU의 차이, drop과 서버 오류의 차이, 정확성 통과와 성능 통과의 차이, 병목이 재고 Lock·연결 풀·Redis·발생기 중 어디인지, 관측 도구가 측정에 준 영향.

## 8. 시작 시 논의와 종료

시작 요청을 받으면 실행 장비·Docker/앱 자원 한도·도구 patch 버전·실험용 DB를 확인한다. 논의 결과를 본 스펙에 반영하고 1단계만 구현한다. 환경 제약 때문에 목표·실험 범위를 바꿔야 한다면 먼저 논의하며, 결과를 얻기 위해 검사나 정확성 기준을 낮추지 않는다.

완료 후 현재 실행 문서·배포 그림·평가를 갱신한다. 본 스펙은 완료 revision·검증·현재 문서 링크를 남겨 `archive/specs/`로 옮긴다. 미완료 인수는 로드맵/평가에 유지한다. 한국어 commit/push 후 검토 대기하며 **2단계를 자동 시작하지 않는다**.

## 근거 자료

- [Spring Boot 3.5 Metrics](https://docs.spring.io/spring-boot/3.5/reference/actuator/metrics.html): Micrometer·Prometheus·HTTP histogram.
- [Spring Boot Actuator 보안](https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html#actuator.endpoints.security): 사용자 SecurityFilterChain이 있을 때 접근 정책을 직접 구성.
- [Prometheus scrape 설정](https://prometheus.io/docs/prometheus/latest/configuration/configuration/): scrape interval·HTTP header 지원. 실제 고정 버전의 지원 여부를 구현 시 확인.
- [k6 arrival-rate](https://grafana.com/docs/k6/latest/using-k6/scenarios/executors/constant-arrival-rate/): iteration 시작률과 VU의 구분.

현재 문서: [실행 가이드](../../performance/load-testing.md), [평가 결과](../../evaluations/load-testing/2026-10-15-stage-1-baseline.md), [진행 상태](../../plans/roadmap.md).
