# ADR 0012 · 표시 조회 캐시와 DB 업무 판정 분리

- 상태: Accepted
- 결정일: 2026-10-27
- 관련: [4단계 인수 계약](../plans/2026-10-27-stage-4-product-read-cache.md), [현재 캐시 설계](../architecture/product-read-cache.md)

## 배경

상품 상세·목록의 반복 조회에서 캐시와 DB 읽기 비용·동시 miss·갱신 경합을 학습해야 한다. 같은 Redis에 캐시와 구매 대기 상태를 섞으면 cache eviction 정책이 대기열에 영향을 줄 수 있다. 사용자는 상세 최대 30초·목록 최대 10초의 표시 지연을 허용하고 구매 가능 여부는 DB에서 확인하는 기준을 선택했다.

## 검토한 대안

| 대안 | 판단 |
|---|---|
| TTL만 사용 | 단순하지만 변경 직후 목록·상세를 빨리 갱신하기 어려움 |
| DB 저장과 캐시 값을 함께 갱신 | 상세·여러 목록 조합과 이중 쓰기의 실패 경계를 처리해야 함 |
| Cache-Aside·commit 후 무효화·TTL | 표시의 짧은 지연을 수용하고 DB를 원장으로 유지하므로 선택 |
| 프로세스 내부 캐시만 사용 | Redis 읽기·eviction·프로세스 간 무효화 학습 범위를 충족하지 못함 |

## 결정

`sale`의 Output Port와 Redis Adapter로 표시 캐시를 구현한다. Input Port·PortService·UseCase 경계를 유지하고 DB loader는 별도 read-only UseCase로 호출한다. 실제 구매용 조회, exists·isClosed는 캐시를 우회한다.

조회 Redis를 기존 Compose project의 별도 서비스로 배치하고 allkeys-lru·메모리 상한을 설정한다. 상품 변경 commit 후 catalog generation UUID를 교체한다. 이전 세대는 TTL로 만료시키며 scan 삭제를 사용하지 않는다. key별 짧은 claim과 조건부 fill·token 해제로 이전 요청의 늦은 쓰기를 막는다. freshness deadline은 DB 조회 전 Redis 시각으로 정한다.

표시 지연을 TTL로 회복할 수 있어 캐시 무효화에 내구성 Outbox·Kafka·추가 Executor를 도입하지 않는다. 캐시 오류는 DB로 전환하고 DB 실패는 전달한다. 캐시 실패가 이미 commit한 변경을 취소하지 않는다.

## 비용과 한계

- 상세·목록 전체를 세대 단위로 무효화하므로 한 상품 변경도 다른 상품의 hit율에 영향을 준다.
- generation 확인과 Lua가 Redis 왕복 비용을 만든다. 작은 로컬 데이터에서는 지연이 반드시 줄어든다고 가정하지 않는다.
- Redis 장애·느린 DB·Lease 만료·eviction 시 DB 중복 조회는 가능하다. 캐시는 DB 용량 계획의 대체물이 아니다.
- DB commit과 무효화는 원자적이지 않다. commit 후 종료·무효화 실패에서는 TTL까지 오래된 표시가 남을 수 있다.
- afterCommit 무효화는 별도 스레드를 만들지 않는다. commit은 끝났어도 호출자의 응답·연결 정리는 짧은 Redis 명령 완료까지 기다릴 수 있다.
- 조회 Redis 장애는 전역 health를 DOWN으로 만들지 않고 캐시 오류 지표로 관찰한다. 필수 대기열 Redis의 health 검사는 유지한다.

검증 절차·실측·미검증 범위는 [테스트 기준](../development/testing.md), [조회 실험](../performance/catalog-read-cache.md), [로드맵](../plans/roadmap.md)을 따른다.
