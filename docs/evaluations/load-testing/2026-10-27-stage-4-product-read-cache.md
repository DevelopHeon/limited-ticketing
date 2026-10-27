# 4단계 상품 표시 캐시 실험

## 조건과 원본

2026-10-27의 작은 로컬 읽기 실험이다. 기준 HEAD는 `77bf76bc9c826d0262be216c2ca1b1c38c89abc2`이고, 4단계 미커밋 소스는 각 manifest의 sourceHashes와 실행 JAR SHA-256으로 식별한다. 비교에 사용한 JAR hash는 `ff9680c6f7c25b50e2486521d6cf002304189c3a0c01b6a36a7697c347b1b2a0`이다.

최종 검증에서 테스트만 추가한 뒤 bootJar를 다시 생성한 산출물 hash는 `7ffddb81c078cd27a00520713cf5e4b3d69a54d42a14187b8c3b440870fec7ac`다. 비교 14개 manifest의 운영 소스·설정·시나리오 sourceHashes는 현재 파일과 모두 일치했고 마지막 빌드의 compileJava·processResources·모듈 jar는 모두 UP-TO-DATE였다. 측정 JAR와 재생성 JAR의 바이트 동일성을 주장하지 않으며 측정 당시 hash와 원본 manifest를 유지한다.

- 환경: macOS 15.6.1 arm64, OpenJDK 17.0.9, Docker CPU 5개·RAM 8,232,828,928 bytes, k6 v2.1.0. 앱은 호스트 JVM `-Xms256m -Xmx512m`, `local,api`, Hikari 최대 30이다.
- 기존 Compose project·PostgreSQL 17·대기열 Redis를 사용하고 조회용 redis-read를 추가했다. 조회 Redis의 실제 설정은 maxmemory 268,435,456 bytes·allkeys-lru·save 없음·appendonly no였다.
- fixture는 새 OPEN 판매 100개, 각 재고 100개이며 ID 26~125다. 기존 원장을 포함한 DB 전체는 판매 125개·OPEN 104개다. 목록 측정은 전체 OPEN을 대상으로 하며 fixture만 필터링한 결과가 아니다.
- cache 비활성·활성은 같은 JAR·DB·fixture·JVM 설정에서 앱만 순서대로 재시작했다. Worker·Consumer는 실행하지 않았다. 별도 worktree·Compose project·대체 DB를 만들지 않았다.
- paired steady는 VU 5·300회씩 상세/목록 각각 3회다. 상세는 상품 10개에 80%를 분배하고 목록은 OPEN·첫 페이지·20개·가격 ASC다. 사전 조회는 custom Trend에서 제외한다.

원본 경로는 `load-test/k6/results/stage4-20261027T112825-b25dbe/`다. `fixture.json`, runtime JSON·로그, 각 실행의 manifest·summary·metrics, DB snapshot, runtime-probes와 최종 `artifacts.sha256`를 보존한다. 로컬 경로이며 Git이나 Notion에서 직접 접근 가능한 결과 링크는 아니다.

## steady 결과

각 셀은 해당 실행 300개 측정 요청의 p95이며 단위는 ms다. 사전 조회와 일반 k6 HTTP 지표를 섞지 않았다.

| 경로 | 캐시 | 1회 | 2회 | 3회 |
|---|---|---:|---:|---:|
| 상세 | 비활성 | 5.816 | 4.283 | 4.002 |
| 상세 | warm | 4.313 | 4.400 | 3.221 |
| 목록 | 비활성 | 13.074 | 10.474 | 6.298 |
| 목록 | warm | 6.061 | 3.491 | 7.720 |

상세는 반복별 p95의 중앙값이 4.283→4.313ms로 사실상 비슷하고 2회째에는 warm이 조금 느렸다. 이 조건에서 상세 지연 개선을 입증했다고 보지 않는다. 목록은 반복별 p95 중앙값이 10.474→6.061ms였지만 3회째는 warm이 더 느렸다. 이 중앙값은 900개 요청을 합친 p95가 아니며, 실행 순서를 무작위화하지 않은 짧은 실험의 참고값이다.

비활성 경로도 Product 일괄 조회를 적용한 같은 DB loader다. 기존 항목별 Product 조회 대비 개선과 캐시 효과를 합산하지 않았다. 로컬 메모리 내 작은 DB, JVM/JIT·GC·호스트 스케줄링의 영향을 통제한 장시간 처리량 시험은 아니다. 원인을 분리 측정하지 않았으므로 특정 요인 때문에 차이가 났다고 단정하지 않는다.

## cold·hit와 SQL

- 상세 cold는 ID 100개를 VU 1로 한 번씩 읽었다. p95 11.811ms, 중앙값 8.546ms, 최대 134.551ms였다.
- 목록 cold는 서로 다른 페이지 5개를 VU 1로 읽었다. 중앙값 18.700ms, 범위 18.324~54.222ms다. 5개 표본의 percentile을 안정적인 지표로 해석하지 않는다.
- cold 상세는 claimed 100회, cold 목록은 claimed 5회였다. 목록 cold의 setup에는 대상 DB를 대조하기 위한 상세 hit 1회가 추가된다.
- warm 상세 실행마다 lookup hit 400회(사전 조회 100+측정 300), warm 목록 실행마다 상세 hit 100·목록 hit 301회(사전 목록 1+측정 300)였다. 이 lookup 전체에서 busy·추가 claim·decode 오류는 관찰되지 않았다. before/after 지표에는 setup도 포함되므로 HTTP 측정 요청 300개만의 counter로 바꾸어 해석하지 않는다.
- 실제 PostgreSQL 통합 테스트의 Hibernate Statistics에서 warm 상세 HTTP·목록 조회의 SQL 0건, 목록 miss의 SQL 최대 3건(판매·count·Product 일괄)을 확인했다. 실측 앱 counter를 SQL 실행 횟수로 간주하지 않았다.

최종 cold/steady 14개 실행의 custom 측정 요청은 총 3,705개이며 모두 workload PASS·예상 밖 응답 0·checks 100%였다. warmup 요청은 이 수에 포함하지 않는다. 동시성은 최대 VU 5이며 5,000명 동시 접속 시험이 아니다.

## 실제 TTL·중단·재시작과 정합성

`runtime-probes/verification.json`의 관찰값이다.

- 상세 TTL은 hit 전 27,232ms, hit 후 27,034ms로 연장되지 않았다. 31초 뒤 key의 PTTL=-2를 확인하고 재조회 후 TTL 27,026ms로 복원됐다. 기본 TTL을 실제로 기다린 1회 실험이다.
- 조회 Redis만 중단한 상태에서 상세 HTTP 200, health UP, 대기열 Redis marker 보존을 확인했다. 해당 상세 한 건의 HTTP 전체 시간은 115.034ms였다. 장애 지연의 p95·상한을 측정한 값은 아니다.
- 조회 Redis 재시작 뒤 generation이 바뀌고 상세를 다시 채웠으며 대기열 marker는 유지됐다. marker는 실험 소유 key 하나만 반환·제거했다.
- 읽기 실험 전후 catalog hash는 `1a4b4be0e1258ebcac873d1fed4e3565`로 같았다. 구매 1,127건·대기 9,100건과 전체 재고 합도 같았다. fixture 재고는 available 10,000·reserved 0·sold 0으로 유지됐다.
- 읽기 비교 snapshot을 저장한 뒤 fixture 판매 100개만 정상 관리자 API로 CLOSED 처리했다. cleanup은 별도 기록이며 비교용 db-after나 기존 manifest를 수정하지 않았다. 생성 원장은 삭제하지 않았다. 측정용 앱은 종료했다.

## 검증 과정과 제외한 실행

`./gradlew check :app:bootJar`에서 Java 318개·실패/오류/skip 0, ArchUnit·Spring Modulith·Checkstyle과 빌드를 확인했다. Python 평가 도구 25개·Node 계약 8개도 통과했다. 이는 VU 5의 실측과 별개의 동작·경계 검증이다.

첫 전체 검사에서는 출력 Adapter의 내부 UseCase DTO 의존과 선택적 캐시 장애의 global health DOWN이 검출됐다. Output DTO로 분리하고 필수 대기열 Redis만 health에 반영하도록 수정했으며 기존 검사를 완화하지 않았다. generation 문자열 손상도 DB 조회로 복원하는 회귀를 추가했다.

초기 `disabled-detail-*`, `disabled-list-*` 6개 실행은 generation 방어 보완 전 JAR였다. 모두 원본을 보존하되 같은 최종 JAR의 paired 비교를 위해 표에는 `final-disabled-*`, `final-cold-*`, `final-warm-*`만 사용했다. 기존 파일·manifest·hash를 새 값으로 덮어쓰지 않았다.

다중 API 인스턴스·실제 memory eviction 부하·장기 DB fallback 용량·Redis HA·5,000명 구매 부하는 이 단계에서 검증하지 않았다. 개별 key·generation 유실과 token/Lease 경합은 통합 테스트로 확인했으며 실제 대규모 eviction 실험과 구분한다.

독립 리뷰는 기준 revision 대비 변경·관련 호출부·테스트와 최종 14개 manifest의 수치를 대조했으며 확정 결함을 발견하지 못했다. 리뷰어는 파일을 수정하거나 테스트를 재실행하지 않았다. 원본 141개 파일의 SHA-256 목록을 `artifacts.sha256`에 고정했고, 해당 목록의 SHA-256은 `488677922bc8df85cabb85b3cc87a16bff3c667437bc0df8a301ca3dd0fe438f`다.

재현 절차는 [상품 조회 비교](../../performance/catalog-read-cache.md), 현재 구현 계약은 [상품 표시 캐시](../../architecture/product-read-cache.md)를 따른다.
