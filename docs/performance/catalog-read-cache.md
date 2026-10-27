# 상품 조회 캐시 재현과 비교

이 절차는 구매 부하와 별개의 작은 읽기 실험이다. [캐시 계약](../architecture/product-read-cache.md), [구매 부하 절차](load-testing.md)를 구분한다. 동시 사용자 5,000명이나 장애 중 DB 처리 용량의 증거로 사용하지 않는다.

## 환경과 fixture

기존 checkout·Compose project·PostgreSQL·대기 Redis를 사용하고 제품 구성의 조회 전용 `redis-read`를 추가한다. Bash·Python 3·k6·Docker가 필요하다. 앱은 하나만 실행한다. 실행 중인 JAR를 재빌드하지 않는다.

```bash
cd /Users/gimhuiheon/Documents/study/limited-ticketing
docker compose up -d --wait postgres redis redis-read
# 앱이 실행 중이지 않을 때 한 번 빌드하고 모든 모드에서 같은 파일을 사용한다.
./gradlew :app:bootJar
```

터미널 B에서 결과 경로를 새로 만들고 SQL로 읽기 전용 상품 100개를 준비한다. 기존 상품·구매·대기 원장은 삭제하지 않는다. 생성한 상품은 OPEN, 재고는 각 100개다. 회원·구매는 만들지 않는다.

```bash
set -o noclobber
CATALOG_RUN_ID="catalog-$(date +%Y%m%dT%H%M%S)-$(python3 -c 'import uuid; print(uuid.uuid4().hex[:8])')"
CATALOG_DIR="$(pwd)/load-test/k6/results/$CATALOG_RUN_ID"
mkdir -p load-test/k6/results
mkdir "$CATALOG_DIR"
catalog_psql() {
  docker compose exec -T postgres sh -c \
    'exec psql -X -qAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" "$@"' catalog-psql "$@"
}
catalog_psql -v run_id="$CATALOG_RUN_ID" -v product_count=100 \
  < load-test/k6/sql/prepare-catalog-read.sql > "$CATALOG_DIR/fixture.json"
catalog_psql -v "fixture=$(cat "$CATALOG_DIR/fixture.json")" \
  < load-test/k6/sql/verify-catalog-read.sql > "$CATALOG_DIR/db-before.json"
```

SQL은 초기 fixture 준비 전용이며 캐시 무효화 업무를 호출하지 않는다. cold 측정 전에는 캐시 비활성 앱만 실행해 기존 캐시가 TTL로 만료될 시간을 확보한다. 기존 데이터가 있는 조회 Redis를 재사용하면 마지막 캐시 쓰기 이후 31초 이상 기다리고 다른 앱이 캐시를 채우지 않는지 확인한다. FLUSHDB·업무 키 일괄 삭제로 대신하지 않는다.

## 캐시 비활성 비교

터미널 A에서 기존 앱을 종료하고 아래처럼 실행한다. 모드 전환 때 같은 JAR·DB·JVM 설정을 유지한다. DB/Redis port를 바꿨다면 개발 안내에 따라 앱 환경 변수도 맞춘다.

```bash
SPRING_PROFILES_ACTIVE=local,api SALE_CACHE_ENABLED=false \
  java -Xms256m -Xmx512m -Duser.timezone=Asia/Seoul \
  -jar app/build/libs/app-0.0.1-SNAPSHOT.jar
```

터미널 B에서 health UP을 확인한 뒤 각 상세·목록을 300회·VU 5로 3회씩 실행한다. runner는 `sale_cache_enabled`의 실제 값과 지정 모드를 대조한다. label을 다시 사용하면 기존 결과를 덮어쓰지 않고 실패한다.

```bash
curl -fsS http://localhost:8081/actuator/health
for repeat in 1 2 3; do
  for mode in detail list; do
    python3 load-test/k6/run-catalog-read.py --fixture "$CATALOG_DIR/fixture.json" \
      --label "disabled-$mode-$repeat" --cache-enabled false --mode "$mode" \
      --phase steady --iterations 300 --vus 5
  done
done
```

## cold와 warm

터미널 A의 비활성 앱을 종료하고 같은 명령의 `SALE_CACHE_ENABLED=true`로 시작한다. 상세 cold는 같은 fixture의 ID를 한 번씩 조회한다. 목록 cold는 서로 다른 페이지를 한 번씩 조회하므로 steady의 고정 첫 페이지와 표본 조건이 다르다.

```bash
python3 load-test/k6/run-catalog-read.py --fixture "$CATALOG_DIR/fixture.json" \
  --label cold-detail --cache-enabled true --mode detail --phase cold --iterations 100 --vus 1
python3 load-test/k6/run-catalog-read.py --fixture "$CATALOG_DIR/fixture.json" \
  --label cold-list --cache-enabled true --mode list --phase cold --iterations 5 --vus 1
for repeat in 1 2 3; do
  for mode in detail list; do
    python3 load-test/k6/run-catalog-read.py --fixture "$CATALOG_DIR/fixture.json" \
      --label "warm-$mode-$repeat" --cache-enabled true --mode "$mode" \
      --phase steady --iterations 300 --vus 5
  done
done
catalog_psql -v "fixture=$(cat "$CATALOG_DIR/fixture.json")" \
  < load-test/k6/sql/verify-catalog-read.sql > "$CATALOG_DIR/db-after.json"
diff -u "$CATALOG_DIR/db-before.json" "$CATALOG_DIR/db-after.json"
```

steady는 상세 ID 100개를 먼저 조회해 DB/JVM과 캐시를 준비한다. 상세 측정은 요청의 80%를 앞의 상품 10개에, 나머지 20%를 다른 상품에 결정적으로 배분한다. 목록 측정은 OPEN·첫 페이지·20개·가격 오름차순이다. setup의 사전 조회는 `catalog_request_duration`과 `catalog_requests`에 포함하지 않는다. 일반 k6 HTTP 지표와 앱의 before/after lookup counter에는 사전 조회도 포함된다.

## 판정·장애 관찰·종료

- summary의 `catalog_requests=iterations`, `catalog_unexpected=0`, checks=1과 manifest PASS를 확인한다. 같은 JAR hash·fixture hash·VU·반복 수의 steady끼리 비교한다.
- latency는 custom Trend의 ms 값이다. 반복별 p95를 각각 기록하고 합쳐진 p95로 바꾸지 않는다. cold 목록 5개 표본의 percentile을 안정적인 성능 지표로 주장하지 않는다.
- 전후 DB hash·구매/대기 행 수·재고 합이 같아야 읽기 실험이 업무 데이터를 변경하지 않았다고 판단한다. 다른 writer가 있었으면 그 실행을 단독 비교 근거로 쓰지 않는다.
- `manifest.json`은 설정·source/JAR/fixture hash·k6 버전·원본 hash를 남긴다. `metrics.json`, `summary.json`, 앱 지표·로그를 Git에 올리지 않는다. 실패 실행도 보존한다.
- 조회 Redis의 중단·재시작은 다른 사용자가 없는 로컬 실험에서만 `docker compose stop redis-read`, `docker compose start redis-read`로 수행한다. 대기열 Redis는 멈추지 않는다. 중단 중 HTTP와 health, 대기열 PING, 재시작 후 cache 재생성 지표를 각각 기록한다.
- 실제 TTL 관찰은 자신의 상세를 조회한 뒤 같은 generation의 해당 key TTL을 읽는다. hit로 연장되지 않고 기본 상한 이후 miss로 복원되는지 본다. generation·다른 상품 key를 실험 목적으로 삭제하지 않는다.
- 결과를 보존한 뒤 fixture의 `saleProductIds`만 정상 관리자 close API로 종료한다. close 결과는 별도 cleanup 기록으로 남기며 비교용 db-after나 이미 완성한 manifest를 고쳐 쓰지 않는다. 생성 원장은 삭제하지 않는다. 실험용 앱을 종료하고 일반 실행 설정으로 복귀한다.

도구 검사: `python3 -m unittest discover -s load-test/k6 -p 'test_*.py'`, `node --test load-test/k6/catalog-read-contract.test.mjs`. Java의 캐시·정합성 검증은 [테스트 기준](../development/testing.md)을 따른다.
