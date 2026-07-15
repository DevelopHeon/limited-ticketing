package com.example.limitedticketing;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.docs.Documenter;

class ModularityDocumentationTests {

    @Test
    void writesApplicationModuleDocumentation() {
        new Documenter(ModularityTestSupport.applicationModules()).writeDocumentation();
    }
}
