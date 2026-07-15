package com.example.limitedticketing.architecture;

import org.junit.jupiter.api.Test;

class ModularityTests {

    @Test
    void verifiesApplicationModuleDependencies() {
        ModularityTestSupport.applicationModules().verify();
    }
}
