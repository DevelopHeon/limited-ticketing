# 1단계 평가 · 플래시세일 관측과 기준선

실행일: **2026-10-15, Asia/Seoul**. 측정 코드 revision: `f10edf1`. 모든 아래 본 측정은 `workingTreeDirty=false`인 동일 코드에서 실행했다. 전체 계획은 [구현 계획](../../plans/2026-10-15-flash-sale-implementation-plan.md), 실행 절차는 [부하 가이드](../../performance/load-testing.md)가 소유한다.

## 결론

**1,000건 기준선은 direct/waiting 각 3회, 5,000건 관찰은 각 1회에서 workload·재고·업무 연결·관측 검증을 통과했다.** 취소·만료 없는 각 측정 snapshot에서 1,000건은 예약 100건·품절 900건, 5,000건은 예약 500건·품절 4,500건이었다. waiting/processing 잔여 항목·잘못된 구매 연결·중복 연결·외부 회원 연결은 0건이다.

5,000건 waiting의 HTTP 접수 p95는 **273.97ms**, 접수부터 최종 결과까지 p95는 **83.37초**였다. HTTP 목표안 500ms는 통과했으나 완료 목표안 60초는 미달했다. **이 결과는 5,000건을 10초 동안 유입한 관찰이며, 5,000개의 동시 연결·1초 폭주·조회 부하·장애 내결함성 전체를 검증한 결과가 아니다.**

1단계 목적은 정확하게 관찰·기록하는 기준선을 확보하는 것이다. 최종 성능 목표 달성이나 2단계 ZSET 강화 완료로 표시하지 않는다.

## 환경과 방법

| 항목 | 실제 조건 |
|---|---|
| 호스트 | macOS arm64, CPU 10개, RAM 32GB; Docker CPU 5개·RAM 약 7.67GiB |
| 앱 | 별도 JVM, 포트 18081, local profile, Java 17.0.9, `-Xms256m -Xmx768m` |
| DB·대기 저장소 | 전용 Compose project `limited-ticketing-baseline`, PostgreSQL 15432 / Redis 16379 |
| 자원 제한 | PostgreSQL 1 CPU/1GB, Redis 0.5 CPU/256MB, Prometheus·Grafana 각각 0.5 CPU/512MB |
| 관측 | Prometheus v3.15.0·Grafana 13.2.4, scrape 5초, Redis/DB CSV 샘플 1초 |
| 발생기 | 호스트 k6 2.1.0 (`commit/devel`), Python 3.13.0·psql 15.13, 앱과 같은 머신 |
| 업무 조건 | 회원·상품·키를 매 실행 새로 준비, 재고 100/500, 구매당 수량 1, 측정 중 결제·취소·만료 없음 |
| Worker | 상품별 슬롯 10·claim batch 10·dispatch delay 100ms, recovery 5초·timeout 30초 |
| 유입 | baseline 100/s·목표 1,000건, observe-5000 500/s·목표 5,000건. 실제 iterations 일치·drop 0 |
| 실행 순서 | direct/waiting 교차 3회 → direct/waiting 5,000건 각 1회 |
| 준비/종료 | 별도 fixture 20건 warmup·키 선발급, 최대 120초 drain, 최종 snapshot 뒤 자기 fixture만 API로 취소·판매 종료 |

앱·발생기는 CPU를 독점하지 않았고 기존 IDE 앱과 다른 Docker 서비스가 호스트에 남아 있었다. 호스트 잡음·JIT 상태·실행 순서의 영향을 완전히 통제한 벤치마크로 표현하지 않는다. Worker 설정과 JVM 한도는 실제 실행 명령의 값이며 원격 설정 endpoint로 재조회한 것은 아니다. image digest·상세 자원·도구 버전은 각 manifest에서 확인한다.

## 실행별 결과

HTTP 지연 단위는 ms, 완료 지연 단위는 초다. direct는 HTTP가 예약 결과를 반환하므로 별도 Queue Entry 완료 지연이 없다. waiting HTTP는 202 접수 응답이고 완료 지연은 DB `completed_at-created_at`의 전체 표본이다.

| runId | flow | iterations | HTTP p95 ms | HTTP p99 ms | 완료 p95 초 | 지연 목표 재판정 |
|---|---|---:|---:|---:|---:|---|
| `20261015T092407Z-direct-23555e1f` | direct | 1000 | 211.93 | 409.18 | — | PASS |
| `20261015T092428Z-waiting-0987b300` | waiting | 1000 | 21.93 | 93.35 | 4.74 | PASS |
| `20261015T092456Z-direct-38b526d7` | direct | 1000 | 10.93 | 17.39 | — | PASS |
| `20261015T092516Z-waiting-ad4d9806` | waiting | 1000 | 14.15 | 20.54 | 3.77 | PASS |
| `20261015T092545Z-direct-18bceb5b` | direct | 1000 | 10.45 | 16.36 | — | PASS |
| `20261015T092605Z-waiting-d15b99b6` | waiting | 1000 | 31.70 | 125.16 | 4.03 | PASS |
| `20261015T092633Z-direct-3182a96f` | direct | 5000 | 139.02 | 296.60 | — | PASS |
| `20261015T092701Z-waiting-a2ee3d94` | waiting | 5000 | 273.97 | 518.20 | 83.37 | FAIL |

1,000건 3회의 HTTP p95 중앙값은 direct **10.93ms**, waiting **21.93ms**다. 첫 direct p95는 211.93ms로 뒤의 약 10ms보다 컸다. warmup 20건으로 JIT·캐시·환경 변동이 충분히 안정화되었다고 결론 내릴 수 없다. 서로 다른 실행의 percentile을 평균내어 전체 요청의 percentile로 사용하지 않는다.

1,000건 waiting의 완료 p95는 **3.77~4.74초**였다. 5,000건의 83.37초로 증가한 점은 접수와 실제 처리 속도를 분리해야 함을 보여 준다. Worker 슬롯/claim 주기/DB 경로 중 어디가 병목인지는 다음 단계에서 지표와 변경 전후 실험으로 확인한다. Kafka 도입만으로 이 지연이 해결된다고 추정하지 않는다.

## 정합성과 관측 검증

- 모든 본 측정에서 `available=0`, `reserved=초기 재고`, `sold=0`, 재고 합계 보존을 확인했다. snapshot 뒤의 정상 정리로 구매는 CANCELLED가 되므로 현재 DB를 과거 snapshot과 혼동하지 않는다.
- waiting의 RESERVED purchaseId·회원·판매 ID·요청 키 연결과 중복 구매/연결을 확인했다. 정상 수락 응답 수와 Queue Entry 수가 일치했다.
- 1초 샘플에서 관찰한 상품별 processing 최대는 10이었다. 순간적인 제한 위반이 전혀 없었다는 증명으로 사용하지 않는다.
- DB Lock 대기 샘플 최대는 0이었다. 짧은 경합을 1초 샘플이 놓칠 수 있어 “DB 락 경합이 없다”로 결론 내리지 않는다.
- HTTP histogram·JVM·Hikari와 scrape UP 시계열을 원본으로 저장했다. Grafana 자동 provisioning의 10개 패널 query가 모두 200을 반환했고 실제 frame이 있었다.
- Java `./gradlew check`에서 140개 테스트가 통과했고 Checkstyle 16개 보고서의 위반은 0건이었다. Python unittest 22개와 Node 응답 계약 테스트 4개도 통과했다.
- Prometheus·Grafana를 중지한 direct/waiting Smoke(`20261015T094909Z-direct-bc6ef597`, `20261015T094923Z-waiting-ab010bb9`)에서도 예약 3건·품절 7건·잔여 대기 0건으로 업무·정합성 판정은 PASS였다. 관측은 INCOMPLETE·전체 종료 코드는 2로 남겼다. 이는 관측 서비스 없이 업무가 동작함을 확인한 비교이며 성능 overhead의 정량 추정은 아니다. Actuator 계측 자체를 제거한 실험은 수행하지 않았다.

## 도구 검증 중 발견한 조정

- k6는 1초를 999ms로 줄이면 실행을 거부했고, 정확한 1초 또는 경계에 가까운 종료에서는 빈 추가 iteration이 관찰됐다. Smoke를 2초·5/s로 조정하고 마지막 목표 시작과 다음 시작의 중간에 종료 경계를 둔다. **실제 iterations가 목표보다 많으면 계속 FAIL이며 값을 보정하지 않는다.**
- 구매 취소의 실제 계약인 204를 정리 도구에 반영하고 회귀 테스트를 추가했다.
- 수집 버전 `f10edf1`의 latency 판정은 HTTP p95만 검사했다. 완료 p95 미달을 숨기지 않도록 `5213635`에서 완료 지연도 검사하게 고쳤다. **원본 manifest의 HTTP-only latency PASS를 덮어쓰지 않고 별도 재분석**했다.
- `2049265`에서 API의 상품 ID·고유 fixture 이름이 DB와 일치하는지 준비·정리 전에 확인하도록 보완했다. 다른 환경에 같은 숫자 ID가 있어도 취소하지 않는다.
- 최종 리뷰에서 Redis 조회 실패 때 SQL snapshot이 함께 사라지는 경로를 재현하고 수정했다. DB 증거는 보존하고 Redis 검증·정리는 불완전/보류로 남긴다. 기대 앱 instance·측정 flow URI·증가한 HTTP counter를 확인하도록 관측 판정도 강화했다. 두 문제의 회귀 테스트를 RED→GREEN으로 확인했다. 구현 revision은 `6f18fee`다. 보완 뒤 direct/waiting Smoke(`20261015T095304Z-direct-6ba2497c`, `20261015T095417Z-waiting-a34b2393`)도 네 판정 모두 PASS였다.
- DB별 로컬 배타 lock으로 같은 대상의 동시 측정을 막는다. lock과 외부 호출 실패는 불완전 결과로 기록한다. Worker 설정은 선언 출처를 표시한다.
- 측정 도구는 결과/환경 I/O/실행 조율, 실제 k6 응답 분류를 책임별 파일로 나눴다. 실제 servlet 계측 테스트는 TestRestTemplate을 사용했다. psql에 큰 fixture는 stdin으로 전달해 macOS 인자 길이 제한을 피했다.

보류한 경미 항목: 키 선발급 중 준비가 실패하면 fixture JSON은 보존하지만 OPEN 판매와 부분 발급 키를 자동 정리하지 않는다. 당시 측정은 전용 로컬 DB에서 수행했다. 현재는 실행 가이드에 따라 기존 로컬 환경을 사용하며, 반복 실패로 데이터가 쌓이면 저장한 fixture의 소유권을 확인해 정리하는 경로를 추가한다.

초기 실패 Smoke의 원본도 삭제하지 않았다. 본 측정과 섞지 않으며 실패 원인별 manifest가 로컬 results에 남아 있다. 판정 보완 뒤 waiting Smoke에서도 workload·integrity·observability·latency 모두 PASS를 확인했다.

## 원본과 재분석 출처

원본은 당시 managed worktree에서 수집했고, 2026-10-15 사용자 요청에 따라 기존 `/Users/gimhuiheon/Documents/study/limited-ticketing/load-test/k6/results/`로 216개 파일을 복사했다. 복사 전후 SHA-256이 같고 기존 파일은 덮어쓰지 않았다. manifest의 당시 실행 경로와 측정 조건은 수정하지 않았다. Git에는 도구와 보고서를 저장하고 측정 원본은 제외한다. 원본은 이 로컬 checkout에 있으며 원격 clone에 제공된다고 주장하지 않는다. manifest에는 입력·설정·소스 revision·각 artifact SHA-256이 있으며 자체 hash는 아래 표로 고정한다.

| runId | manifest SHA-256 |
|---|---|
| `20261015T092407Z-direct-23555e1f` | `df69e772344361e347c1d11809304bdb49c010585001b3c3434d4484a48e52e3` |
| `20261015T092428Z-waiting-0987b300` | `883847c2ca7471d20b730c3eb7507a579e7f1c6e8f432a7d8f7958e9fb9c08cf` |
| `20261015T092456Z-direct-38b526d7` | `8f5f7c69840d66654c8fc4e13b6dd4830192c1bf02791dc67604389067e51fc2` |
| `20261015T092516Z-waiting-ad4d9806` | `602207422c5e22490fe4142049d8db7c0b0bec819f0c149192807f8d79b3e341` |
| `20261015T092545Z-direct-18bceb5b` | `7c669334144689d6e059f2b2a4e4ca1310206621810094de25d11766333e15ae` |
| `20261015T092605Z-waiting-d15b99b6` | `e46b51fd87be34388d9322a4f499b7293c3d8095ffd1645c052956d615a2aba8` |
| `20261015T092633Z-direct-3182a96f` | `c5ffacc15e7a6a916a9e18dd9f42df415a741b3017c634fe38ef1eb474810e06` |
| `20261015T092701Z-waiting-a2ee3d94` | `a2d329aa10429f1fe1d26863ceb68ea3a89f2fa78c4d551bbc9ea7c17e32b846` |

별도 재분석: `load-test/k6/results/20261015-stage1-reanalysis/results.json`. 재분석 구현은 `5213635`이며 원본 workload·SQL·관측 시계열을 다시 읽었다. 5,000건 waiting은 완료 p95 목표를 포함하면 latency FAIL이다. 이 보완은 업무 처리 코드를 바꾸지 않아 본 부하를 다시 실행하지 않고 보존한 원본으로 판정했다. 최종 리뷰의 instance·flow URI·counter 증가 판정은 `6f18fee`로 다시 적용해 `load-test/k6/results/20261015-stage1-final-reanalysis/results.json`에 보존했다. 8개 원본 모두 관측 PASS이며 완료 지연 미달 결과는 동일하다.

## 인수와 다음 검토

1단계 S1-01~S1-09의 실행 증거는 관리 지표 접근 테스트, 실제 scrape/dashboard, fixture·응답·실패 보존 테스트, 위 8개 본 측정과 원본, 단계 종료 commit/push로 연결한다. 성능 목표 미달은 그대로 다음 검토 항목으로 유지한다.

**다음 단계는 자동 시작하지 않는다.** 2단계 시작 요청 때 입력 Scheduler와 업무 조율 분리, 순번 원장·복구 cursor·lease/fencing, 슬롯/claim 주기·수용량·직접 경로 노출 정책을 논의한다. 특히 83초 결과를 줄일 때 공정성·DB 경합·복구 정확성에 어떤 비용이 생기는지 함께 확인한다.
