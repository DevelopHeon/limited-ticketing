# 4단계 · 상품 조회 캐시 구현 스펙

## 목적과 범위

기준 revision은 `77bf76bc9c826d0262be216c2ca1b1c38c89abc2`다. 기존 master에서 작업하며 시작 전 PurchaseCommandService·PurchaseReservationResult의 미커밋 결과 변환 변경은 보존하고 이번 단계 commit에서 제외한다. 진행 상태는 [로드맵](roadmap.md), 전체 범위는 [플래시세일 계획](2026-10-15-flash-sale-implementation-plan.md)이 소유한다.

상품 상세·목록의 반복 조회에서 PostgreSQL 읽기를 줄이고 Cache-Aside, TTL, 무효화 경합, 동시 miss와 Redis 장애 시 동작을 학습한다. `sale` 모듈이 구현을 소유한다. 별도 cache 업무 모듈·화면·가격 수정 API·랭킹·Kafka 캐시 무효화 파이프라인은 추가하지 않는다.

사용자는 표시 정보의 짧은 지연을 허용하고 실제 구매 가능 여부는 DB에서 검증하는 기준을 선택했다. 아래 세부 시간·용량과 구현 방식은 이번 단계 기준안이며 최적 성능을 실측한 값은 아니다.

## 읽기·쓰기 계약

| 경로 | 처리 |
|---|---|
| GET /api/sale-products/{id} | 상세 Cache-Aside |
| GET /api/sale-products | status·page·size·sort·direction별 목록 Cache-Aside |
| 구매용 findPurchasable | 항상 PostgreSQL에서 상태·판매 기간·가격 확인 |
| 내부 exists·isClosed | 항상 PostgreSQL 조회. 대기열 정리 등 업무 판정에 표시 캐시를 사용하지 않음 |
| 판매 생성·기간 변경·오픈·종료 | DB commit 후 캐시 무효화 시도. rollback이면 무효화하지 않음 |

캐시 hit면 DB 조회 없이 기존 HTTP 계약으로 응답한다. miss면 DB 조회 결과를 저장한다. 없는 상품의 404는 캐시하지 않으며 빈 목록은 정상 결과로 캐시한다. Redis 오류·잘못된 JSON·지원하지 않는 캐시 버전은 DB로 전환하고 DB 오류는 숨기지 않는다. 캐시 쓰기·무효화 실패 때문에 이미 commit한 판매 변경을 실패 응답으로 바꾸지 않는다.

상세는 상품명·설명·가격·판매 기간·상태, 목록은 현재 응답의 항목·페이지·총수를 저장한다. 재고·회원별 구매 자격·예약 결과는 저장하지 않는다. 목록 miss의 Product 조회는 `findAllById`로 일괄 수행해 항목별 추가 조회를 제거한다. 같은 정렬값의 페이지 순서는 ID를 보조 정렬로 고정한다.

## TTL과 유효 시간

| 항목 | 기준안 |
|---|---|
| 상세 TTL 상한 | 30,000ms |
| 목록 TTL 상한 | 10,000ms |
| 하향 jitter | 최대 10%: 상세 27~30초, 목록 9~10초 |
| 캐시 재생성 lock Lease | 2,000ms |
| 동시 miss의 대기 상한 | 200ms, 25ms 간격 |
| 조회 Redis 연결·명령 timeout | 각각 200ms·100ms, 전체 HTTP 시간의 절대 상한은 아님 |

조회 hit로 TTL을 연장하지 않는다. DB 조회 전 Redis 시각으로 freshness deadline을 정하고, 저장 시 남은 시간만 TTL로 부여한다. 오래 걸린 DB 조회 뒤 새 30초를 시작하지 않는다. deadline 또는 lock Lease가 만료되면 결과는 응답하되 캐시에 저장하지 않는다. 이 정책은 엄격한 최신 읽기·전체 HTTP 완료 시각까지의 일관성을 보장하지 않는다.

TTL만 사용하는 대안은 간단하지만 변경 직후 오래된 표시가 남는다. Write-through는 DB와 캐시의 이중 쓰기·목록 조합 갱신 부담이 있다. 이번에는 Cache-Aside에 commit 후 무효화와 TTL 복구를 결합한다. [Redis Cache-Aside 안내](https://redis.io/docs/latest/develop/use-cases/cache-aside/)를 참고한다.

## 무효화·동시 miss의 경합

- 조회 Redis에 catalog generation UUID를 둔다. 상세·목록 key에는 형식 버전과 generation을 포함한다. 목록 key는 검증된 status·page·size·sort·direction을 모두 구분한다.
- 판매 변경 commit 후 generation을 새 UUID로 바꾼다. 이전 상세·목록 key는 즉시 조회 대상에서 빠지고 TTL로 제거된다. SCAN·KEYS·FLUSHDB로 삭제하지 않는다. 작은 상품 카탈로그에서 전체 generation 무효화를 선택하므로 관계없는 상품의 hit율도 낮아지는 비용이 있다.
- 캐시 읽기·lock 획득 Lua는 현재 generation과 요청 generation을 확인한다. generation이 바뀌면 제한된 횟수만 다시 확인하고 DB로 전환한다. generation key가 eviction되면 새 UUID를 만들어 이전 세대를 재사용하지 않는다.
- 같은 key의 miss는 SET NX/PX와 UUID token으로 재생성 소유자를 정한다. 나머지 요청은 제한된 시간만 다시 조회하고, 시간 초과·Redis 장애 시 DB로 전환한다. 장애·느린 DB까지 요청당 DB 조회 한 번을 전역 보장하는 장치는 아니다.
- 저장 Lua는 현재 generation·lock token·미만료 freshness deadline을 확인한다. 변경 전에 읽던 요청이나 이전 소유자가 뒤늦게 낡은 값을 다시 채우지 못하게 한다. lock 해제는 token 일치 시에만 수행한다.
- DB commit 직후 프로세스 종료나 Redis 장애로 무효화가 누락되면 기존 TTL이 복구 상한 역할을 한다. 내구성 있는 무효화 Outbox는 이번 범위가 아니다.

## 구조와 실행 구성

```text
Controller → Input Port → PortService → Find Detail/List UseCase
  → CachedSaleProductQueryService (transaction 없음)
      ├─ SaleProductReadCachePort → Redis Adapter
      └─ Load Detail/List UseCase → SaleProductQueryService (DB read-only transaction)

Command Service → Domain Repository → DB commit
  → afterCommit → SaleProductReadCachePort.invalidate
```

PortService는 계속 UseCase만 호출한다. Cache Adapter에는 Redis key·Lua·JSON 직렬화·지표·TTL·연결 설정을 둔다. Application 계약에는 RedisTemplate·Lua·Kafka 타입을 노출하지 않는다. 캐시 대기와 네트워크 작업 때문에 DB transaction을 열어 두지 않는다.

기존 Compose project에 조회용 `redis-read` 서비스를 추가한다. Redis 7, 호스트 기본 port 6380, maxmemory 256MB, allkeys-lru, persistence 없음으로 시작한다. 대기열 Redis의 설정·연결은 유지한다. 이는 별도 실험용 프로젝트 복제가 아니라 승인된 제품 구성의 조회 저장소 추가다. host·port·timeout·enabled·TTL·Lease·대기 간격은 YAML/env로 조정한다.

조회 전용 ConnectionFactory·StringRedisTemplate은 명시적 qualifier로 연결한다. 기존 Boot 기본 연결과 대기열 StringRedisTemplate을 대체하지 않는지 테스트한다. `sale-cache.enabled=false`로 같은 DB 일괄 조회 경로의 캐시 없는 비교를 제공한다.

## 구현 순서와 완료 기준

1. 조회·캐시 계약과 DB loader를 분리한다. 목록 Product 일괄 조회, 필터·정렬·페이지 결과와 내부 DB 판정을 보존하는 테스트를 먼저 작성한다.
2. 조회 Redis 구성과 Adapter, generation·claim·조건부 저장·token 해제 Lua를 구현한다. 실제 Redis에서 TTL·동시 miss·손상 값·이전 token·무효화 경합을 검증한다.
3. 기존 판매 Command에 afterCommit 무효화를 연결한다. commit·rollback·무효화 실패, warm 캐시가 있어도 닫힌 판매의 구매를 거절하는 업무 테스트를 작성한다.
4. hit/miss/claim 대기·캐시 오류·저장 거절·무효화 지표를 노출한다. 상품 ID·요청 key를 metric label로 사용하지 않는다. 반복 polling 횟수와 HTTP 요청 수를 구분한다.
5. 기존 환경에서 작은 cold/warm·캐시 비활성·TTL 만료·캐시 재시작 비교를 수행한다. 원본과 fixture·실행 설정은 기존 load-test/k6/results의 새 식별자에 보존한다. 5,000건 구매 부하는 반복하지 않는다.
6. `./gradlew check`로 아키텍처·모듈·컨벤션·업무 회귀를 확인한다. 새 Java 테스트에는 한국어 DisplayName을 작성한다. 검사 완화로 통과시키지 않는다.
7. 현재 Architecture·개발/검증 안내·성능 절차와 roadmap을 갱신한다. 실측이 생성되면 조건·반복 수·한계와 함께 Evaluation에 기록한다. 독립 리뷰·수정·재검증 후 한국어 commit/push, Notion 기록을 남기고 사용자 검토를 기다린다.

### 필수 검증 사례

- warm 상세·목록 hit가 DB loader를 호출하지 않으며 기존 응답과 일치한다.
- status·page·size·sort·direction이 다른 목록이 캐시를 공유하지 않는다.
- 빈 목록은 캐시하며 404·DB 오류는 성공 캐시로 저장하지 않는다.
- 목록의 Product 읽기 수가 항목 수에 비례하지 않는다.
- 정상 Lease 안의 동시 miss에서 같은 key의 중복 DB 재생성을 줄인다. Lease 만료·대기 상한에서는 DB 전환과 늦은 저장 거절을 확인한다.
- 이전 generation·token의 fill/release가 새 캐시·소유자를 덮어쓰지 않는다.
- 변경 commit 후 새 상태를 조회하고 rollback이면 기존 캐시를 유지한다. 무효화 실패 시 변경 결과는 성공이며 TTL 후 DB에서 복원된다.
- Redis 유실·중단·손상된 캐시에서도 조회가 DB로 전환되고 재고·구매 판정은 DB를 따른다.
- 조회 Redis 설정을 바꾸거나 중단해도 대기열 Redis의 key·연결은 영향을 받지 않는다.

성능 합격을 임의 p95 숫자로 정하지 않는다. 같은 fixture·동시성·앱/DB 설정에서 cache 비활성·cold·warm의 표본 수, hit율과 지연을 비교하고 개선되지 않으면 그대로 기록한다. 단위·통합 테스트 통과를 5,000명 처리 성능이나 다중 인스턴스 장애 내성으로 표현하지 않는다.
