# 프로젝트 작업 규칙

- 기본 작업 위치는 기존 `limited-ticketing` checkout의 `master`다. 일반적인 기능 구현·수정·문서 작업은 이곳에서 직접 진행한다.
- worktree는 동일 프로젝트의 여러 branch를 병렬로 작업하거나, 그에 준하는 복잡한 작업에서 작업 영역 분리가 필요한 경우에만 사용한다. 일반적인 단계별 구현에는 사용하지 않는다.
- 앱·DB·Redis는 기존 로컬 환경을 사용한다. 별도 인스턴스나 Compose project를 임의로 만들지 않는다.
- 결과와 측정 원본은 기존 프로젝트의 `load-test/k6/results/`에 보존한다. 기존 원본을 덮어쓰거나 manifest·hash를 수정하지 않는다.
- 재현 가능한 부하 시나리오·실행/판정 도구·SQL·도구 테스트는 Git으로 관리한다. 실행 결과 JSON·CSV·로그와 비밀 설정은 Git에서 제외한다.
- 단계별로 구현·검증·한국어 commit/push 후 검토 대기한다. 다음 단계는 사용자의 명시적인 시작 요청 이후 진행한다.
- 테스트는 업무 규칙과 실패 조건을 검증한다. Java 테스트에는 한국어 `@DisplayName`을 작성한다.
- 코드 변경 후 `./gradlew check`로 ArchUnit·Spring Modulith·Checkstyle을 포함한 검증을 실행한다. 검사 완화·비활성화로 통과시키지 않는다.
- 실행과 검증 절차는 [개발 안내](docs/development/README.md), [테스트 기준](docs/development/testing.md), [부하 테스트](docs/performance/load-testing.md)를 따른다.
