# 로컬 Deployment

이 문서는 저장소에 구성된 **로컬 개발 환경**을 설명한다. 실행 명령과 환경 변수는 [개발 안내](../development/README.md)가 소유한다. 운영 배포 토폴로지는 아직 정의되지 않았다.

```mermaid
flowchart LR
    subgraph host["개발자 컴퓨터 · 로컬 환경"]
        client["HTTP Client / k6"]
        app["호스트 JVM · API 8081<br/>기본 실행은 Worker도 포함"]
        worker["선택: 별도 호스트 JVM · Worker 8082<br/>같은 실행 산출물·DB"]
        subgraph docker["Docker Compose"]
            pg["postgres 서비스<br/>postgres:17-alpine · 내부 5432"]
            redis["redis 서비스<br/>redis:7-alpine · 내부 6379"]
            readRedis["redis-read 서비스<br/>상품 표시 캐시 · 내부 6379<br/>256MB · allkeys-lru"]
            kafka["kafka profile · KRaft 3 노드<br/>호스트 19092·29092·39092<br/>노드별 영속 볼륨"]
            subgraph observability["선택: observability profile"]
                prometheus["Prometheus · 9090"]
                grafana["Grafana · 3000"]
                grafana -->|"시계열 조회"| prometheus
            end
            volume[("postgres-data<br/>PostgreSQL 영속 볼륨")]
            pg -->|"데이터 저장"| volume
        end
        prometheus -->|"관리자 header로 지표 scrape"| app
        client -->|"HTTP · localhost:8081"| app
        app -->|"JDBC · localhost:5432 → 5432"| pg
        app -->|"Redis · localhost:6379 → 6379"| redis
        app -->|"표시 캐시 · localhost:6380 → 6379"| readRedis
        worker -->|"같은 DB·Redis"| pg
        worker --> redis
        worker -->|"예약 발행·소비"| kafka
        app -->|"통합 실행 시 예약 발행·소비"| kafka
    end
```

[Deployment](https://c4model.com/diagrams/deployment)는 특정 환경에서 Container를 실행하는 위치와 연결을 보여 준다. 현재 [compose.yaml](../../compose.yaml)의 기본 서비스는 PostgreSQL·대기열 Redis·조회 Redis이며 Kafka 3개 노드는 `kafka`, 관측 서비스는 `observability` profile로 실행한다. Kafka 노드는 Broker/Controller 역할을 함께 실행하며 RF3·minISR2를 사용한다. 로컬 학습을 위한 PLAINTEXT와 localhost 포트이며 노드별 CPU 0.75·메모리 768MiB를 제한한다. 애플리케이션은 호스트 JVM에서 실행하며 API/Worker 역할 분리는 같은 산출물을 사용한다. 기본 통합 실행과 분리 역할을 중복 구동하지 않는다. 따라서 전체가 Docker나 Pod 하나에 배포된 것으로 설명하지 않는다.

PostgreSQL은 named volume을 사용한다. Redis에는 영속 볼륨·AOF·복제가 설정되어 있지 않아 컨테이너 재생성 후 ZSET·토큰·속도 제한 키가 유실될 수 있다. 순번 원장은 PostgreSQL에 남으며 [구매 대기 복구 정책](purchase-waiting.md#복구)에 따라 재구성한다.

`redis-read`는 같은 Compose project에 추가한 상품 표시 저장소다. 호스트 기본 port 6380, 메모리 256MB·allkeys-lru·persistence 없음으로 동작하고 유실 후 DB 조회로 재생성한다. 대기열 Redis의 eviction·연결 설정과 분리한다. [표시 캐시 계약](product-read-cache.md)을 따른다.

호스트 공개 포트는 [.env.example](../../.env.example)의 `POSTGRES_PORT`, `REDIS_PORT`, `READ_REDIS_PORT`로 바꿀 수 있다. 호스트에서 실행하는 앱의 DB·Redis 연결 값도 [application.yml](../../app/src/main/resources/application.yml)에 맞춰 설정해야 한다. Compose용 `.env`를 수정하는 것만으로 호스트 JVM의 환경 변수가 자동 설정되지는 않는다.

## 관측 구성과 과거 측정 환경

`observability` profile은 Prometheus v3.15.0과 Grafana 13.2.4를 추가한다. 관측 UI는 localhost에 공개하며 지표는 관리자 header로 scrape한다. `APP_METRICS_TARGET`으로 호스트 앱 주소를 지정한다. Grafana datasource·대시보드는 소스에서 자동 provisioning하고 데이터 볼륨을 유지한다. Grafana 비밀번호는 Git 제외 파일에서 Docker secret으로 전달한다. 일회성 `secret-init`이 UID 472·권한 600으로 전용 볼륨에 준비하고 Grafana는 읽기 전용으로 마운트한다. 준비·기동·기존 계정의 암호 처리는 [개발 안내](../development/README.md#관측과-자동-기준선)를 따른다.

1단계 실험은 기본 실행과 별도의 Compose project `limited-ticketing-baseline`과 앱 18081·DB 15432·Redis 16379를 사용했다. `compose.baseline.yaml`이 DB/Redis 자원 제한을 추가하며 앱 JVM은 호스트에서 256~768MB로 실행했다. 이 조건은 [과거 실측](../evaluations/load-testing/2026-10-15-stage-1-baseline.md#환경과-방법)의 기록이다. 이후 작업과 측정은 [기존 로컬 환경](../performance/load-testing.md#기존-로컬-환경-준비)을 사용하며 별도 앱·DB·Redis를 생성하지 않는다. 과거 측정 포트를 현재 실행의 기본값으로 사용하지 않는다.
