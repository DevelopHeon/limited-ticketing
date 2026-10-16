# 테스트 책임과 검증

모듈러 모놀리스에서도 각 업무 모듈에 Domain·Application Service 단위 테스트를 둔다. 실행 모듈 `app`은 실제 저장소와 모듈 연결을 사용하는 통합 테스트를 맡는다. 클래스가 존재한다는 사실보다 업무 규칙과 실패 시 부수효과를 검증한다.

## 자동 검증

```bash
./gradlew check
```

| 검증 | 목적 |
|---|---|
| Checkstyle | 모든 모듈의 main/test에서 `var`와 `setXxx` 메서드 선언 금지 |
| `ModularityTests` | 전체 업무 모듈 감지, Named Interface를 통한 공개 계약 사용, 금지된 모듈 참조와 순환 의존 검증 |
| `LayeringArchitectureTests` | 각 업무 모듈 내부의 Domain → 외부 계층, Application → 어댑터 역방향 의존 금지, Presentation의 Facade 우회와 공개 DTO의 내부 타입 노출 검증 |
| Domain 단위 테스트 | 회원 구매 자격, 판매 시간 경계, 재고 수량 보존, 구매·결제·대기 요청의 허용 상태 전이 검증 |
| Application Service 단위 테스트 | 각 업무 모듈에서 실제 Domain 객체와 고정 Clock을 사용해 구매 멱등성, 결제 소유권·만료, 대기 결과 분류와 시스템 오류 전파 검증 |
| `PurchaseConcurrencyIntegrationTest` | 동시 예약에서 정확한 재고 수량과 활성 구매 제약 검증 |
| `PurchaseLifecycleConcurrencyIntegrationTest` | 동일 구매의 동시 승인·취소 시 상태 전이 단일 성공과 다른 회원의 예약 재고 보호 |
| `PurchaseReservationIntegrationTest` | 구매 예약·취소·만료, 소유권과 재고 반환 검증 |
| `PaymentLifecycleIntegrationTest` | 결제 실패·재시도·승인, 소유권·만료와 구매 및 재고 전이 검증 |
| `SaleProductManagementIntegrationTest` | 판매 생성, 상태 전이, 재고 생성, 목록/상세 조회 검증 |
| `ObservabilityIntegrationTest` | 실제 HTTP의 지표 접근 401/403/200, 업무 API 인증 유지, histogram·JVM·Hikari 노출 검증 |
| `ReservationQueueIntegrationTest` | Redis ZSET과 PostgreSQL에서 대기열 멱등성, 중복 차단, 예약/품절, 복구 검증 |

`ModularityDocumentationTests`는 Spring Modulith `Documenter`로 코드 기준의 모듈 문서를 생성한다.

```text
app/build/spring-modulith-docs/
├── components.puml
├── module-*.puml
├── module-*.adoc
└── all-docs.adoc
```

Checkstyle 규칙은 [Checkstyle 설정](../../config/checkstyle/checkstyle.xml)이 소유한다. `setXxx` 금지는 프로젝트가 선언한 메서드 이름에 적용하며 외부 라이브러리 메서드 호출은 검사하지 않는다. 의미 있는 업무 동사인지 여부는 코드 리뷰에서 확인한다.

Domain 및 Application Service 단위 테스트는 각 업무 모듈이 소유한다. Service 단위 테스트는 Spring Context 없이 Repository·다른 모듈의 Facade·출력 Port를 대체하고 업무 분기와 허용·금지되는 부수효과를 검증한다. HTTP, 보안, 모듈 연결, 실제 DB·Redis와 트랜잭션·동시성은 실행 모듈 `app`의 통합 테스트에서 검증한다. 단위 테스트의 Mock으로 DB 정합성과 롤백을 검증했다고 간주하지 않는다. 모든 테스트 클래스와 테스트 메서드는 한국어 `@DisplayName`으로 검증하는 정책을 설명한다.

새 모듈, Named Interface, 모듈 간 의존성을 변경할 때는 `./gradlew check`를 실행해 코드 규칙, 아키텍처, 업무 동작과 모듈 문서 생성을 함께 검증한다. `./gradlew test`는 Checkstyle을 실행하지 않는다. 부하 테스트 절차는 [부하 테스트 가이드](../performance/load-testing.md), 남은 검증과 확장 계획은 [로드맵](../plans/roadmap.md)에서 관리한다.

테스트만 실행하려면 `./gradlew test`를 사용한다. 테스트 클래스와 메서드의 `@DisplayName`은 검증하는 업무 규칙을 한국어로 설명한다.

## 부하 평가 도구의 계약 검사

Python unittest는 원본 덮어쓰기·불완전 결과·재고/구매 연결·DB 동시 실행·정리 소유권·지연 판정을, Node test는 실제 k6가 사용하는 응답 분류와 fixture 중복을 검사한다. 테스트는 한국어 설명을 제공한다. 실제 DB/Redis/HTTP/k6 연결은 Smoke와 최종 SQL 검증으로 확인한다.

```bash
python3 -m unittest discover -s load-test/k6 -p test_run_baseline.py
node --test load-test/k6/baseline-contract.test.mjs
```

전체 Java 규칙·아키텍처·업무 테스트는 기존처럼 `./gradlew check`로 실행한다.

## 관측 비밀값 준비 검사

실제 임시 디렉터리에서 비밀번호 최초 생성·기존 값 보존·700/600 권한·출력 비노출·빈 파일 거부를 검사한다. Docker 기동·로그인·컨테이너 UID 접근은 실제 관측 서비스로 별도 확인한다.

```bash
python3 -m unittest discover -s observability/tests -p test_setup.py
```
