package com.example.limitedticketing.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.docs.Documenter;

class ModularityDocumentationTests {

    @Test
    void writesApplicationModuleDocumentation() {
        new Documenter(ModularityTestSupport.applicationModules()).writeDocumentation();
    }
}
