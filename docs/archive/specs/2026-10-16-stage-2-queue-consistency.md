# 2단계 스펙 · 대기 정합성과 입구 보호

상태: 구현·검증·리뷰 완료·검토 대기. 작성일: 2026-10-16. 시작 revision: `e6e5287`.
[전체 계획](../../plans/2026-10-15-flash-sale-implementation-plan.md)의 2단계만 진행한다. 기존 checkout의 master·앱·DB·Redis를 사용하고 한국어 commit/push 뒤 검토 대기한다.

## 목적과 확정한 정책

Redis 유실·중복 claim·시간 초과 복구에도 DB가 수락한 요청을 추적하고 구매·재고 효과를 중복 적용하지 않는다. 1단계 5,000건의 완료 p95 83.37초와 정확성·성능을 구분한다. Kafka·캐시·랭킹은 이번 단계에서 구현하지 않는다.

사용자가 승인한 입구 정책:

- 상품별 `WAITING + PROCESSING` 최대 10,000건. 초과는 429 `QUEUE_CAPACITY_EXCEEDED`다.
- 동일 회원·상품의 신규 진입은 1초당 1회. 초과는 429 `QUEUE_RATE_LIMITED`다. 이미 수락한 같은 요청 키의 재조회/재전송은 제한하지 않는다.
- 수락 전 Redis 사용 불가는 503 `QUEUE_UNAVAILABLE`이다. DB 커밋 이후 Redis 등록 실패는 202를 유지하고 DB 원장으로 복구한다.
- 429/503은 `Retry-After: 1`을 제공한다. 거절 요청의 Queue Entry는 생성하지 않는다.
- 직접 예약 HTTP API는 `baseline` profile에서만 제공한다. 대기 처리의 내부 Purchase Facade 호출·결제/취소 API는 유지한다.

## 순번과 수용량

상품별 마지막 순번을 PostgreSQL 테이블에 보존하고 기존 원장의 MAX로 초기화한다. Redis INCR를 순번 원장으로 사용하지 않는다. ZSET score의 정수 정확도 범위인 `1..2^53-1`을 지킨다.

상품별 DB transaction advisory lock으로 신규 접수와 DB claim의 판정 구간을 직렬화한다. 원장의 활성 요청 수와 PROCESSING 수를 확인해 Redis 유실 시에도 수용량·상품별 처리 상한을 초과하지 않는다. indexed count와 잠금 비용은 부하에서 관측하며 상수 시간 카운터를 검증 없이 중복 원장으로 추가하지 않는다.

기존 순번 동점도 읽을 수 있도록 복구 cursor는 `(queue_sequence, id)`다. ZSET은 `(score, UUID 문자열)` 정렬을 사용한다. 순번은 등록된 대기 항목의 입장 선택 기준이며 병렬 구매 성공의 FIFO를 보장하지 않는다. Redis 등록 지연/복구 이전 이미 입장한 후순번 작업을 취소하지 않는다.

## 처리 권한과 복구

- Redis claim은 WAITING → PROCESSING 이동과 시도 토큰 저장을 Lua 한 번으로 처리한다.
- DB claim은 `status=WAITING` 조건부 갱신이며 시도 토큰·processing_started_at·lease_until을 저장한다. 중복 claim은 한 시도만 성공한다.
- 처리 시작은 Queue Entry 행 잠금과 토큰·상태·lease 검사로 보호한다. 예전 시도는 구매 Facade를 호출하거나 새 시도의 상태를 변경할 수 없다.
- 구매 예약은 별도 transaction으로 수행한다. Queue Entry 행 잠금은 결과 확정까지 유지한다. 구매 커밋 뒤 결과 확정 전 중단은 기존 구매 멱등성으로 재처리한다. 품절 예외의 rollback이 결과 기록까지 취소시키지 않는다.
- 만료 복구는 `FOR UPDATE SKIP LOCKED`로 실제 실행 중 잠긴 행을 건너뛴다. 시간 초과만으로 살아 있는 transaction의 권한을 빼앗지 않는다.
- Redis 슬롯 반환·재대기는 토큰 일치 시에만 수행한다. 구 시도가 신 시도의 슬롯을 지울 수 없다. WAITING 누락 복원은 DB 행 잠금 아래 현재 상태를 재확인한다.
- 종료된/존재하지 않는 DB 요청의 Redis 잔여 항목을 제거한다. 수락 요청을 임의 삭제하거나 전체 DB/Redis를 초기화하지 않는다.

## 계층과 실행 제한

Scheduler는 presentation에 두고 내부 입력 Facade만 호출한다. 대기 dispatch·복구·구매 만료 조율은 Application에서 수행한다. Redis·JPA·Executor와 계측은 infrastructure에 남긴다. 내부 스케줄 입력은 다른 모듈의 공개 Named Interface로 추가하지 않는다.

전체 실행 스레드 수와 상품별 DB PROCESSING 상한의 기본값은 각각 10이다. Executor의 1,000건 버퍼를 없애고 실행 가능한 수만 claim한다. 상품을 순환하며 선택한다. 실행 거부는 DB 권한을 조건부 반환하고 원래 순번으로 재대기한다. 엄격한 상품 간 FIFO·다중 호스트의 전역 동시성은 주장하지 않는다.

## 인수 기준

| ID | 검증 |
|---|---|
| S2-01 | 두 모듈 Scheduler의 presentation 이동, 내부 Facade 경유, ArchUnit 대표 위반 탐지와 Modulith 유지 |
| S2-02 | Redis sequence 키 유실 뒤 새 순번이 기존보다 큼, 동점이 batch 경계에 있어도 모든 WAITING 복원 |
| S2-03 | 같은 요청 동시 claim 1회, 복구 후 구 토큰의 구매·완료·슬롯 반환 무효 |
| S2-04 | lease가 지나도 실행 중 행 잠금은 복구가 덮어쓰지 않음, 중단된 시도는 재처리 가능 |
| S2-05 | 동시 신규 진입이 수용량을 넘지 않음, rate limit·멱등 재전송·503·Retry-After와 원장 비생성 |
| S2-06 | 기본 직접 예약 HTTP 차단·baseline 허용, 내부 예약·결제·재고 테스트 유지 |
| S2-07 | 전체/상품 슬롯 제한·Executor 거부 복구, 정상 예약/품절 수량과 재고 보존 |
| S2-08 | 실제 Lua의 ZSET 크기·claim batch별 서버 시간·메모리, 작은 Smoke의 최종 정합성 검증 |

모든 Java 테스트에 한국어 `@DisplayName`을 사용한다. 구조 검사는 내부 패키지를 ArchUnit, 모듈 간 공개 API를 Modulith로 구분한다. 기존 검사 완화로 통과시키지 않는다.

사용자 요청에 따라 매 단계 5,000건 부하는 반복하지 않는다. 2단계는 실패 경계 테스트·작은 Smoke·ZSET 단독 실험을 필수로 하며, 대규모 부하는 성능 비교가 필요한 변경 또는 최종 종합 평가에서 수행한다. 이미 시작한 측정 원본은 성공·실패와 무관하게 보존한다.

완료 시 현재 문서·ADR·평가를 갱신하고 본 스펙은 결과와 revision을 연결해 보관한다. 성능 목표 미달을 숨기지 않으며 3단계는 별도 시작 요청 뒤 진행한다.

현재 계약은 [구매 대기 설계](../../architecture/purchase-waiting.md), 선택 이유는 [ADR 0006](../../adr/0006-durable-queue-admission-and-attempts.md), 실제 결과와 한계는 [2단계 평가](../../evaluations/load-testing/2026-10-16-stage-2-queue-consistency.md)를 따른다. 이 스펙은 당시 인수 계약을 보존한다.
