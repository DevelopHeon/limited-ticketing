# 3단계 · Kafka 전달과 복구 실험

측정일: 2026-10-19. 기준 revision은 `ac2487a86b6ff2ca9e0acbb74628a0ea627fd9ac`이며 그 이후 3단계 작업 트리로 실행했다. 구현 계약은 [구매 대기 설계](../../architecture/purchase-waiting.md), 인수 범위는 [3단계 스펙](../../plans/2026-10-19-stage-3-kafka-outbox.md)을 따른다. 이 기록은 작은 전달·복구 실험이며 처리량·5,000건 지연 목표 달성을 주장하지 않는다.

## 환경과 원본

- 기존 Mac·PostgreSQL·Redis와 같은 Compose project `limited-ticketing`을 사용했다. 별도 DB·Redis·worktree는 만들지 않았다.
- API는 `local,api` 8081, Worker는 `local,worker` 8082로 같은 DB를 사용했다. 각 JVM 최대 heap은 512MiB다.
- Kafka 4.2.2 combined KRaft 3 노드, 노드별 CPU 0.75·메모리 768MiB·heap 384MiB. topic은 6 partitions·RF3·minISR2, Producer는 acks=all·idempotence다.
- 이미지 index digest는 `sha256:1213eb3943d551e5ed1fca7a4e109001cee35770b66a02a0c37a8964efe09b69`다. macOS arm64 manifest는 `sha256:031e51202f27021e6afcc1f55d1d0efd9c54864a96d57506e7c1af7dc97839dd`다.
- 원본은 Git 제외 로컬 경로 `load-test/k6/results/stage3-20261019T120700/`에 보존한다. fixture·응답·DB/Redis 검증·topic/offset·metrics·로그를 포함하며 `artifacts.sha256`으로 75개 원본의 식별자를 보존한다. Notion에서 직접 접근 가능한 원격 링크가 아니다.
- 고정 실행 JAR의 식별자는 원본 `runtime-jar.sha256`에 있다. 초기 실행은 빌드 경로 JAR를 사용했고, 아래 클래스 로딩 오류 이후 고정 사본으로 다시 검증했다. 마지막 60초 경고 Scheduler 추가는 별도 단위·전체 검사로 검증했다.

## 방법

기존 [fixture SQL](../../../load-test/k6/sql/prepare-flash-sale.sql)과 [Environment 도구](../../../load-test/k6/baseline_environment.py)의 `prepare_fixture`, `issue_keys`, `http`, `verify`, `cleanup`을 사용했다. 각 회원의 키를 발급하고 대기 접수 HTTP를 순차 호출한 뒤 DB 원장이 모두 최종 상태가 될 때까지 확인했다. 정리 시 해당 fixture의 구매만 취소하고 판매를 종료했다. Outbox·대기 행과 과거 원본은 삭제하지 않았다.

단일 상품 10건, Broker 1대 중단 중 2건, Broker 2대 중단 중 2건, API 단독 2건, 6개 상품 각 2건을 수행했다. 각 조건은 1회이며 k6의 일정 도착률 부하는 실행하지 않았다. 순차 HTTP·DB 완료 시각으로 확인한 Smoke 결과를 기존 1,000/5,000건 실험과 직접 비교하지 않는다.

반복 가능한 일반 Smoke는 [기존 부하 도구](../../performance/load-testing.md#자동-기준선--현재-평가-도구)의 `--flow waiting --scenario smoke`를 사용한다. 그 도구의 기본 Smoke는 10건·재고 3개·초당 5건이므로 아래 순차 10건·재고 5개와 조건이 다르다. 동일 조건 재현 시 SQL의 회원 수·재고 수와 HTTP 호출 방식을 맞춘다.

Broker 장애를 재현할 때는 실험 소유 요청만 있는지 확인하고 1대 또는 2대를 중지한다. 원본의 fixture별 DB/Outbox를 확인한 뒤 반드시 노드를 복구한다.

```bash
docker compose --profile kafka stop kafka-3
# 같은 Smoke를 실행하고 topic의 ISR 및 원장을 확인한다.
docker compose --profile kafka start kafka-3

docker compose --profile kafka stop kafka-2 kafka-3
# HTTP 접수와 DISPATCHED·미발행 Outbox·미변경 재고를 확인한다.
docker compose --profile kafka start kafka-2 kafka-3
```

## 관측 결과

| 조건 | 표본·반복 | 확인한 결과 | 주요 원본 |
|---|---|---|---|
| 단일 상품·재고 5개 | 10건·1회 | RESERVED 5·SOLD_OUT 5, 재고 보존·Redis 잔류 0. 완료 p95 0.80201435초 | `hot-key/verification.json` |
| Broker 3번 중단·재고 1개 | 2건·1회 | RESERVED 1·SOLD_OUT 1. 모든 partition의 ISR이 1·2 두 노드로 유지. 완료 p95 0.27754995초 | `one-broker-down/`, `topic-one-broker-down.txt` |
| Broker 2·3번 중단 | 2건·1회 | HTTP 202 후 DISPATCHED 2, 예약·재고 변경 0. Outbox 2건 미발행·발행 실패 횟수 각각 5 | `two-brokers-down/pending.json`, `two-brokers-outbox-pending.txt` |
| Broker 복구와 Worker 재시작 | 위 2건 | 같은 원장·Outbox로 RESERVED 1·SOLD_OUT 1, Redis 잔류 0. 의도적으로 중단한 시간을 정상 처리 지연으로 평가하지 않음 | `two-brokers-down/verification.json` |
| 고정 JAR의 API 단독 접수 | 2건·1회 | Worker가 없을 때 WAITING 2. Worker 시작 후 RESERVED 1·SOLD_OUT 1 | `api-only-fixed/pending.json`, `api-only-fixed/verification.json` |
| 상품 key 분산 | 6개 상품·각 2건·1회 | 각 상품 RESERVED 1·SOLD_OUT 1. 12개 메시지가 4개 partition 사용 | `distributed-1/`~`distributed-6/`, `partition-summary.json` |

상품 ID 11의 10개 메시지는 partition 0에 모였다. 분산 상품 ID 16~21의 partition은 각각 5·0·4·1·4·0이었다. key가 다르다고 모든 partition에 하나씩 배치되지는 않는다. 이 기록은 key와 partition 관계를 확인하며 처리량 개선의 증거가 아니다.

`group-final.txt`에서 Consumer 3개가 각각 2개 partition을 할당받았다. 기록이 있는 4개 partition은 committed offset과 log end가 같아 lag 0이었다. 기록이 없는 2개는 log end 0·committed offset 없음(`-`)이었다. `worker-metrics.prom`의 현재 `records_lag`도 6개 모두 0이었다. 일부 window 최대 지표의 NaN을 lag 0으로 바꿔 해석하지 않았다. Outbox pending은 0, Consumer running은 1이었다.

API·Worker의 health 200, API 판매 조회 200·Worker 판매 조회 404, 두 역할의 metrics 무인증 401·관리자 200을 확인했다. 상충하는 `api,worker` profile은 시작 실패를 확인했다. 상세 결과는 `role-checks.json`, `conflicting-roles.log`에 있다.

## 실패 실행과 정정

첫 API 실행을 짧게 종료되는 자식 프로세스로 시작해 앱이 남지 않았다. 빈 `api.log`와 초기 `run.json`을 보존하고 실제 실행은 `api-runtime.log`에서 구분한다.

이후 실행 중인 `app/build/libs` JAR를 다시 빌드해 `NoClassDefFoundError: ch/qos/logback/classic/spi/ThrowableProxy`와 HTTP timeout이 발생했다. `api-without-worker`는 2명 fixture 중 원장 1건만 남았으며 정상 API 단독 실험으로 집계하지 않는다. 원본을 덮어쓰지 않고 `api-without-worker-recovered`에서 같은 요청 키로 결과를 확인·재전송해 최종 RESERVED 1·SOLD_OUT 1과 정리를 검증했다. JAR를 결과 경로에 고정한 후 `api-only-fixed`를 새로 실행해 위 표의 API 단독 결과를 확인했다.

이 오류는 Broker 장애의 관측값이나 정상 지연값에 합치지 않는다. 실행 산출물을 바꾸지 않는 절차를 [개발 안내](../../development/README.md#api와-worker-역할-분리)에 반영했다.

## 자동 검증과 한계

자동 검증은 실제 Kafka 전달·중복·offset·DLT와 실제 DB 원자성·재처리 경합을 포함한다. DB 확인/DLT 실패는 Spy로 주입해 실제 전송·offset을 확인했다. PostgreSQL 자체 중단·자동 failover나 운영 배포 장애를 검증한 것은 아니다. 단일 Broker 테스트(RF1·minISR1)와 이 로컬 복제 실험(RF3·minISR2)을 구분한다. 검사 명령은 [테스트 기준](../../development/testing.md), 최종 검사·리뷰와 다음 행동은 [로드맵](../../plans/roadmap.md)에 둔다.

5,000건 재측정·다중 Worker 처리량·장기 안정성·운영 인증·DB failover는 이번 실험 범위가 아니다. 기존 지연 목표를 달성했다고 주장하지 않는다.
