package com.example.limitedticketing.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.docs.Documenter;

@DisplayName("코드 기반 모듈 문서 생성 검증")
class ModularityDocumentationTests {

    @Test
    @DisplayName("감지된 모듈과 공개 계약으로 아키텍처 문서를 생성한다")
    void writesApplicationModuleDocumentation() {
        new Documenter(ModularityTestSupport.applicationModules()).writeDocumentation();
    }
}
