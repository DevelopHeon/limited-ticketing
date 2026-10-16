# ADR 0006 · 영속 순번과 시도별 처리 권한

- 상태: **Accepted**
- 결정일: 2026-10-16. 2단계 입구 정책은 사용자가 승인했다.

## 배경과 대안

Redis INCR 유실 뒤 새 요청의 순번이 기존 DB 원장보다 작아질 수 있었다. DB의 조회 후 저장 방식 claim은 동시 호출에서 여러 Worker에 권한을 줬다. 시간만 보고 처리 슬롯을 지우면 살아 있는 이전 Worker가 재처리와 겹치거나 새 시도의 슬롯을 제거할 수 있었다.

Redis 영속화만으로 순번을 보존하는 대안은 DB 원장과의 장애 경계를 남긴다. Redis 전역 락이나 별도 수량 카운터는 소유권·복구 상태를 더 만든다. 이번에는 기존 PostgreSQL 원장과 트랜잭션을 활용한다.

## 결정

- 상품별 마지막 순번은 PostgreSQL의 upsert로 발급하고 ZSET 정수 score 범위를 제한한다.
- 상품별 transaction advisory lock 아래 원장의 활성 수와 처리 수를 확인한다. 활성 수는 접수 상한 10,000, 처리 수는 상품별 기본 10이다.
- 동일 회원·상품의 신규 진입은 Redis SET NX와 1초 TTL로 제한한다. 같은 키의 동시 재전송은 상품 잠금 후 재조회하여 하나의 원장을 반환한다.
- Redis와 DB의 claim은 시도 토큰을 공유한다. DB의 조건부 갱신·lease와 처리 시작 행 잠금이 실제 구매 호출 권한을 보호한다.
- 처리 행 잠금은 결과 기록까지 유지한다. 구매는 `REQUIRES_NEW`로 분리해 품절 등의 rollback이 대기 결과 기록을 취소하지 않게 한다.
- 복구는 만료된 행 중 `SKIP LOCKED`로 잠금 없는 행만 회수한다. Redis 반환·재대기는 이전 토큰이 일치해야 한다.
- 누락 WAITING 복원은 현재 DB 행을 잠가 재확인한다. 동점 복구 cursor는 `(queue_sequence, id)`다.
- 실행 버퍼는 없애고 프로세스 전체 10개 슬롯 안에서 상품 선택 시작점을 순환한다. 직접 예약 HTTP는 `baseline`에서만 연다.

## 결과와 비용

Redis가 유실되어도 순번 원장과 상품별 처리 상한이 남는다. DB 커밋 뒤 Redis 등록 실패는 이미 수락한 요청으로 유지하고 재구성한다. Redis 장애 중 신규 접수는 503, 기존 상태 조회는 DB 기준으로 응답하며 위치는 null이다.

상품별 잠금과 indexed count는 hot 상품의 비용이다. 이를 처리량 개선이라고 주장하지 않는다. 기존 5,000건 완료 p95 83.37초의 병목은 이 변경만으로 확정하지 않는다.

Worker 하나는 외부 대기 트랜잭션과 내부 예약 트랜잭션에서 최대 두 DB 연결을 사용한다. 기본 Hikari 최대값은 30, 전역 Worker는 10이다. 설정을 바꿀 때 풀을 최소 Worker 수보다 크게 두고 HTTP·스케줄 작업 여유도 확보해야 한다. 충분한 연결·DB 자원이 없는 환경에서는 Worker 수를 먼저 줄인다. 실행 중인 트랜잭션이 느리면 lease 이후에도 복구를 미룬다. 영구 정지는 DB 연결 종료/transaction timeout 등의 운영 경계가 필요하다.

이는 단일 앱 프로세스의 전역 제한이다. 여러 프로세스의 전체 Worker 수 제한과 Redis HA는 후속 단계다. 병렬 예약 완료의 엄격한 FIFO도 보장하지 않는다.

## 근거

- [현재 대기 계약](../architecture/purchase-waiting.md)
- [통합·동시성·장애 테스트](../../app/src/test/java/com/example/limitedticketing/integration/purchasewaiting)
- [V6 migration](../../app/src/main/resources/db/migration/V6__durable_queue_sequence_and_processing_lease.sql)
- [2단계 평가](../evaluations/load-testing/2026-10-16-stage-2-queue-consistency.md)
