# 2단계 평가 · 대기 정합성과 입구 보호

평가일: 2026-10-16. 구현 시작 revision은 `e6e5287`이며 측정은 2단계 미커밋 변경을 포함한 jar로 수행했다. 원본 manifest의 dirty 상태를 유지한다. 현재 계약은 [대기 설계](../../architecture/purchase-waiting.md), 선택 이유는 [ADR 0006](../../adr/0006-durable-queue-admission-and-attempts.md), 인수 항목은 [보관 스펙](../../archive/specs/2026-10-16-stage-2-queue-consistency.md)을 따른다.

## 결과와 범위

이번 단계는 입력 계층 분리·순번 영속화·단일 claim·구 시도 차단·복구·수용량·속도 제한을 구현했다. 5,000건 성능 목표 달성을 주장하지 않는다. 사용자의 요청에 따라 매 단계 대규모 부하를 반복하지 않고 실패 경계 테스트·10건 Smoke·ZSET 단독 실험으로 검증했다.

- 실제 DB/Redis 테스트: 순번 Redis 키 유실, 순번 동점 batch 경계, 동시 claim, 구 토큰의 구매·Redis 반환/재대기 차단, lease 만료 후 실행 중 잠금 보호.
- 입구 테스트: 동시 수용량 제한, 동일 키 동시 재전송, 다른 키 1초 제한, 429/503의 Retry-After, 거절 원장 비생성, Redis 장애 중 기존 상태 조회.
- 실행 테스트: 전역 슬롯 포화 시 claim 없음, Executor 버퍼 0·즉시 거부, 거부 시 DB/Redis 원상 복귀, DB 커밋 뒤 등록 실패 복구, Redis 슬롯 유실 후 DB 처리 상한 유지.
- 전체 Worker의 외부 트랜잭션을 함께 시작시키는 테스트에서 연결 풀 10개 고갈을 재현했다. 기본 풀 30개로 변경하고 같은 테스트를 통과시켰다. Worker 하나가 최대 두 연결을 쓰는 비용을 숨기지 않는다.
- 기본 직접 예약 HTTP 404와 baseline 예약 계약을 함께 검사한다. 구매·결제·재고 기존 업무 테스트와 ArchUnit·Modulith·Checkstyle을 유지한다.

검증 명령: `./gradlew check`, `python3 -m unittest discover -s load-test/k6 -p test_run_baseline.py`(22개), `node --test load-test/k6/baseline-contract.test.mjs`(4개), `python3 -m unittest discover -s observability/tests -p test_setup.py`(3개). 최종 Java 161개 모두 통과했으며 실패·오류·skip은 0이다.

## 실행 조건과 Smoke

기존 checkout/master, 앱 8081·PostgreSQL 5432·Redis 6379를 사용했다. Mac RAM 32GB, Docker CPU 5·RAM 약 8GB, JVM `-Xms256m -Xmx768m`, profile `local,baseline`, 전역/상품별 Worker 각 10·batch 10·claim 주기 100ms·Hikari 최대 30이다. 이전 앱을 중지한 뒤 V6 migration을 적용했다. 앱·DB·Redis를 별도 격리 배포하지 않았다. Testcontainers는 자동 통합 테스트의 fixture로만 사용했다.

Smoke는 flow별 10회원·재고 3, 5/s·nominal 2초, 사전 warmup 20건이다. 측정 중 다른 Gradle 테스트는 실행하지 않았다. 각 실행의 workload/integrity/observability/latency와 cleanup은 모두 PASS다. 최종 SQL snapshot은 cleanup 전 값이다. cleanup은 해당 fixture의 구매 취소·판매 종료만 수행했다.

| flow | 요청/수락 | 예약 | 품절 | HTTP p95 | 접수→최종 결과 p95 |
|---|---:|---:|---:|---:|---:|
| direct | 10 / 직접 결과 | 3 | 7 | 86.13ms | 해당 없음 |
| waiting | 10 / 10 | 3 | 7 | 69.73ms | 0.204초 |

두 flow 모두 재고 `(available,reserved,sold,total)=(0,3,0,3)`, 중복 구매·잘못된 구매 연결·다른 회원 연결 0이다. waiting의 최종 원장은 RESERVED 3·SOLD_OUT 7·FAILED 0이다. 결제 완료가 아닌 예약 결과이며 sold=0이다. 이 작은 표본을 5,000건 성능 개선 근거로 사용하지 않는다.

실제 jar SHA·profile·PID·연결/Worker 설정은 로컬 `load-test/k6/results/stage2-runtime/after-runtime.json`에 남겼다. manifest의 base revision은 수정하지 않았다. 기본 검토 실행에서는 `baseline`을 제거한다.

## ZSET 크기·batch 비용

실제 module resource `queue-claim.lua`를 [실험 도구](../../../load-test/k6/zset-claim.py)가 읽었다. 전용 `lab:<runId>:*` 키에서 1,000/5,000/10,000 member × batch 1/10/50/100, 조건당 warmup 5회 뒤 표본 20회, 총 유효 240회다. 모든 ZSET 인코딩은 skiplist였다.

서버 `INFO commandstats`의 EVAL usec 증가분을 기록했다. timed 명령은 운영 claim Lua 하나다. 전후 EVAL 호출 수 차이가 1인지 확인해 다른 EVAL이 겹친 표본은 거부한다. reset·Docker CLI·네트워크 시간은 제외한다. 매 표본의 이동 수·원래 크기 복원과 종료 시 키 제거를 assert로 검사했다. 전체 실행 원본 CSV와 script SHA가 manifest에 남는다.

| member 수 | batch | p50 μs | p95 μs | max μs | waiting MEMORY USAGE bytes |
|---|---:|---:|---:|---:|---:|
| 1,000 | 1 | 59 | 99 | 1349 | 124,224 |
| 1,000 | 10 | 101 | 246 | 872 | 119,424 |
| 1,000 | 50 | 360 | 718 | 919 | 116,224 |
| 1,000 | 100 | 802 | 1295 | 1525 | 116,224 |
| 5,000 | 1 | 58 | 87 | 102 | 675,136 |
| 5,000 | 10 | 126 | 329 | 758 | 619,136 |
| 5,000 | 50 | 332 | 420 | 1074 | 635,136 |
| 5,000 | 100 | 783 | 890 | 914 | 667,136 |
| 10,000 | 1 | 82 | 240 | 3464 | 1,237,440 |
| 10,000 | 10 | 114 | 155 | 232 | 1,237,440 |
| 10,000 | 50 | 377 | 648 | 776 | 1,237,440 |
| 10,000 | 100 | 832 | 1694 | 2290 | 1,237,440 |

기본 batch 10의 p95는 155~329μs였다. batch가 커지면 한 번의 실행에서 더 많은 항목을 이동하므로 비용이 늘었다. member 크기에 따른 단조 증가나 특정 시간 복잡도를 이 표본만으로 증명하지 않는다. 메모리는 각 조건의 초기 waiting ZSET 값이며 processing/token이나 Redis 전체 메모리는 포함하지 않는다. skiplist 무작위 높이 때문에 같은 크기도 값이 다르다.

기존 Redis에서 앱을 중지하고 측정했지만 일부 Java 통합 테스트와 호스트 자원을 공유했다. CPU/가상화 경합과 20개 표본의 tail 불확실성이 남는다. 운영 보장이나 전체 DB 예약 병목의 근거가 아니다.

### 초기 계측 오류 보존

`20261016T031003Z-zset-336185ba`는 Lua 안에서 TIME 차이를 재어 모든 duration이 0이었다. Lua 실행 중 TIME이 고정되어 이 계측은 **무효**다. 원본 manifest의 COMPLETE는 스크립트 종료 상태일 뿐 유효 측정을 뜻하지 않는다. 원본·hash를 수정하지 않고 후속 실험에서 INFO 방식으로 교체했다. 위 표는 후속 유효 실행만 사용한다.

## 시작 전에 수행한 5,000건 probe의 한계

사용자가 반복 대규모 부하가 불필요하다고 정리하기 전에 이전 jar로 시작한 두 실행은 보존했다. 이후 추가 5,000건 실행은 하지 않았다.

| run | 실제 iteration | dropped | 예약/품절 | 판정 |
|---|---:|---:|---|---|
| `20261016T024212Z-waiting-03e05106` | 4,477 | 525 | 500 / 3,977 | workload·integrity FAIL, latency INCOMPLETE |
| `20261016T024558Z-waiting-7ccb9c02` | 4,472 | 528 | 500 / 3,972 | workload·integrity FAIL, latency INCOMPLETE |

목표 5,000과 원본 iteration/drop을 맞춰 수정하지 않는다. 첫 실행은 Java 통합 테스트와 호스트 자원도 겹쳤다. 두 실행 모두 cleanup PASS이며 미완료 원장을 남기지 않았다. 관측된 재고·구매 연결 오류는 없지만 기대 요청 수를 충족하지 않아 성공 평가로 표현하지 않는다. 1단계 별도 환경의 결과와 조건도 달라 전후 처리량 비교로 사용하지 않는다.

## 원본 식별자와 보존

아래 원본은 **현재 프로젝트의 로컬 `load-test/k6/results/<runId>/`**에 있다. Git에는 스크립트·SQL·검증·본 보고서만 포함한다. raw JSON·CSV·로그는 제외하며 기존 원본을 덮어쓰지 않았다. manifest 자신을 제외한 파일별 SHA는 manifest에서 확인한다.

| runId | manifest SHA-256 |
|---|---|
| `20261016T031816Z-direct-842f78d9` | `08871f174c0d6d1c0e875fbc339dd7d7a1f04db492b5cf00ba1e7e59b0e7565a` |
| `20261016T031853Z-waiting-7eaf0f67` | `7a64c31fc40f678615fd809b24a292951fdc836975b1c5696a5094ba391afde4` |
| `20261016T031216Z-zset-eabccdb6` | `7b50eafc74cd2a907c196f910d46f11ece1de80de900b0c495f5ae03486b8c09` |
| `20261016T031003Z-zset-336185ba` | `cff9ccc53f04f6a53935b950563f3da6394c23c34a2d33bd6713c6b44ee7dd78` |
| `20261016T024212Z-waiting-03e05106` | `17559f3db69d549ce812c53cb267991734ead7a8bcced75700ab5e6d2d6931ec` |
| `20261016T024558Z-waiting-7ccb9c02` | `1e7e14c2d4fa7a7a297b96048b23fb94199b9dfd8b69ead0dc2e3116a6a1e8f0` |

## 남은 검증과 완료 기록

5,000건 전후 비교, 장시간 장애·여러 앱의 전역 동시성, Redis HA·DB failover는 이번 평가로 확인하지 않았다. 1단계 완료 p95 83.37초의 성능 목표 미달은 유지한다. Kafka 도입만으로 지연이 해결된다고 판단하지 않는다.

별도 리뷰어 1명이 시작 revision부터 tracked·untracked 구현과 연결 호출부·테스트·측정 원본을 읽었다. Critical·Important·Minor 지적은 없었다. 리뷰어도 Java XML 161개와 Smoke manifest/수량, ZSET 240개 표본·12조건 percentile을 직접 대조했다. 코드 수정 없이 완료했다.

문서 검사는 25 Markdown·202 상대 링크/앵커·bash 28개·Mermaid 5개와 이전 archive 원문·raw hash를 확인했다. Mermaid는 렌더링하고 변경된 C2/C3를 확인했다. 테스트·관측 검증을 완화하거나 비활성화하지 않았다.

사용자 지침에 따라 기존 master에서 한국어 commit/push하고 2단계 검토 대기한다. 3단계 Kafka/Outbox는 사용자 시작 요청과 상태·전달 계약 논의 뒤 진행한다.


### 구현 중 확정한 판단

- 기존 master·로컬 앱/DB/Redis를 사용한다. 일반 기능에 별도 worktree/배포를 만들지 않는다. 사용자 승인 정책을 반복 확인하지 않았다.
- 상품별 DB 잠금+indexed count와 영속 순번을 선택했다. Redis reset에 의한 역순은 막지만 hot 상품의 잠금·count 비용은 추가된다.
- 행 잠금+토큰/lease·분리 구매 트랜잭션을 선택했다. 중복 효과를 막는 대신 연결을 더 사용하고 느린 실행은 lease 이후까지 회수를 늦춘다.
- 복구 cursor는 순번+UUID다. 복원 중 이미 입장한 후순번 작업을 취소하거나 성공 FIFO를 주장하지 않는다.
- 사용자 요청으로 반복 5,000건 HTTP를 제외했다. 작은 Smoke는 최종 규모 성능 근거가 아니며 큰 부하 비교는 남는다.
- 테스트의 완료 대기는 이후 dispatch tick도 실행하도록 바꿨다. 버퍼 없는 한 tick이 전체 fixture를 처리한다고 가정하지 않는다.
- 기존 사용자 `AGENTS.md` 변경은 수정·commit에 포함하지 않고 보존했다. 그 안의 리뷰·Notion 기록 절차는 따랐다.
- 리뷰 범위 밖인 다중 앱·HA·failover·장기 장애와 혼합 버전 실행은 현재 지원·검증 범위로 확대하지 않았다. 혼합 버전은 이전 Worker 중지 후 migration 계약으로 제한한다.
