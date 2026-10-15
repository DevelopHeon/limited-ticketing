# 현재 단계와 로드맵

현재 구현과 미완료 검증을 구분한다. 대기열의 현재 계약은 [구매 대기 설계](../architecture/purchase-waiting.md), 측정 방법은 [부하 테스트](../performance/load-testing.md), 원본으로 확인한 기존 기록과 한계는 [평가 보고서](../evaluations/load-testing/2026-10-15-existing-results.md)에서 확인한다.

## 진행 현황

- [x] 도메인 모델과 모듈 경계 정의
- [x] PostgreSQL/Flyway 기반 영속성 구성
- [x] 구매 및 결제 멱등성 구현
- [x] 조건부 원자 갱신 기반 재고 동시성 제어
- [x] 직접 예약 k6 기준선 측정
- [x] Redis ZSET 대기열과 단일 프로세스 Worker 구현
- [x] Redis 대기열 1,000건 진입 시나리오 실행 및 수락/드롭 지표 확인
- [ ] 5,000/10,000건 Stress·Spike 재측정과 직접 예약 대비 결과 정리
- [ ] 상품별 대기 인원 제한, Rate Limit, Micrometer 운영 지표 추가
- [ ] SSE 또는 WebSocket 기반 대기 상태 알림 검토
- [ ] 필요 시 Transactional Outbox와 Kafka 기반 독립 Worker로 확장
- [ ] 서비스와 DB가 실제로 분리될 때 Saga 검토

## 단계별 진행 계획

| 단계 | 상태 | 수행 내용 | 선택 이유와 완료 기준 |
|---|---|---|---|
| 0. DB 직접 예약 기준선 | 완료 | 조건부 원자 재고 갱신과 k6 동시 요청 측정 | Redis 도입 전에도 재고 정합성은 DB가 보장함을 확인했다. 성공·재고 소진 건수와 PostgreSQL 락 대기 값을 기준선으로 남긴다. |
| 1. 대기열 최소 수직 슬라이스 | 완료 | DB Queue Entry, Redis ZSET, Lua claim, 단일 프로세스 워커, 상태 조회와 복구 | DB는 최종 상태, Redis는 순서와 슬롯이라는 경계를 확정했다. Testcontainers에서 멱등성, 중복 차단, `RESERVED`/`SOLD_OUT`, 복구를 통과하면 완료다. |
| 2. 대기열 부하 측정 | 진행 중 | k6로 1,000/5,000/10,000명의 대기열 진입을 실행하고, 상태 조회는 별도 시나리오로 측정 | 직접 예약과 비교해 진입 API p95, 대기열 적재량, 워커 처리량, 최종 `RESERVED`/`SOLD_OUT` 수, PostgreSQL 락 대기를 기록한다. 대기열이 DB 진입을 설정한 슬롯 수로 제한하는지 확인한다. |
| 3. 입구 보호와 운영 지표 | 대기 | 상품별 최대 대기 인원, 회원·IP rate limit, Micrometer 지표와 대시보드 추가 | Redis와 애플리케이션 자체가 무제한 요청의 병목이 되지 않게 한다. 거부율, 대기 시간, 처리 중 수, 복구 횟수, Redis 오류를 관측할 수 있어야 한다. |
| 4. 사용자 대기 경험 | 대기 | SSE 또는 WebSocket 알림, 예상 순번/대기 시간, 재고 소진 시 남은 항목 일괄 `SOLD_OUT` 처리 | polling 비용을 줄이고 사용자에게 최종 상태를 빠르게 전달한다. 실제 클라이언트 요구가 생길 때 구현한다. |
| 5. 다중 인스턴스·서비스 분리 | 보류 | 리더 선출 또는 분산 스케줄링, Outbox, Redis Stream/Kafka 워커 분리 | 단일 프로세스의 처리량·가용성 한계가 측정되거나 독립 배포가 필요할 때만 진행한다. 이 단계 전까지는 DB와 Redis의 이중 상태 복구를 단순하게 유지한다. |

## 다음 검증

대기열 진입 k6 시나리오는 구현되어 있고 1,000건 실행 기록이 있다. 다음 작업은 5,000/10,000건 Stress·Spike를 재측정하고, 같은 상품·재고·유입 조건에서 직접 예약과 비교하는 것이다. 상태 조회 부하는 진입 시나리오와 분리해 추가 측정한다. 1,000건 수락 기록을 성능 목표 달성이나 더 큰 규모의 검증 완료로 간주하지 않는다.

## 메시징 확장 판단 기준

현재는 하나의 애플리케이션 안에서 Spring TaskExecutor가 비동기 작업을 처리한다. Kafka나 Redis Streams는 아직 구현하지 않았다.

같은 프로세스의 비핵심 후처리가 필요하면 Spring Event Listener로 시작한다. 프로세스 종료에도 이벤트 전달이 보장되어야 하면 Outbox를 검토하고, 작업 전달을 독립적으로 확장해야 할 때 Kafka를 함께 검토한다.

다음 요구가 실제로 생길 때 Kafka를 검토한다.

- 구매 처리 워커를 API 애플리케이션과 독립 배포해야 한다.
- 여러 워커 인스턴스에서 작업을 분산해야 한다.
- 작업 전달의 재시도, 소비 지연, 소비자별 처리량을 브로커 차원에서 관찰해야 한다.
- 구매 완료 이벤트를 여러 독립 서비스에 전달해야 한다.

Kafka를 도입하면 ZSET과 `QueueEntry`를 함께 유지한다. ZSET은 사용자 대기 순번, `QueueEntry`는 상태 조회와 멱등성, Kafka는 입장 허용된 작업의 전달을 담당한다.

```text
ZSET 대기열 입장 허용
  -> QueueEntry: WAITING -> PROCESSING
  -> 같은 DB 트랜잭션에서 Outbox 이벤트 저장
  -> Outbox Publisher가 Kafka Producer로 발행
  -> Kafka Consumer가 구매 예약 처리
  -> QueueEntry: RESERVED / SOLD_OUT
  -> 처리 성공 뒤 offset commit
```

이 단계에서는 현재 `reservationQueueTaskExecutor`와 Worker의 구매 처리 부분이 Kafka Consumer로 대체된다. ZSET에서 순번을 꺼내 Outbox를 만드는 admission dispatcher와 DB/Redis 복구 작업은 남는다. DB 상태 변경과 Kafka 발행을 직접 연달아 호출하면 중간 장애에서 작업이 유실될 수 있으므로 Outbox 없이 Kafka를 붙이지 않는다.

서비스와 DB가 실제로 분리되어 보상 트랜잭션이 필요해질 때만 Saga를 검토한다. 현재 단일 DB 트랜잭션으로 해결하는 흐름에는 도입하지 않는다.
