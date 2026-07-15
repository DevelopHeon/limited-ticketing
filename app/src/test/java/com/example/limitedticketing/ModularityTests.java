package com.example.limitedticketing;

import org.junit.jupiter.api.Test;

class ModularityTests {

    @Test
    void verifiesApplicationModuleDependencies() {
        ModularityTestSupport.applicationModules().verify();
    }
}
