package com.example.limitedticketing.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

@DisplayName("모듈 간 공개 계약과 의존 관계 검증")
class ModularityTests {

    @Test
    @DisplayName("모든 업무 모듈은 허용된 공개 계약만 참조하고 순환 의존하지 않는다")
    void modulesOnlyUseDeclaredPublicApisAndHaveNoCycles() {
        ApplicationModules modules = ModularityTestSupport.applicationModules();

        assertThat(modules.stream().map(module -> module.getIdentifier().toString()))
            .as("All business modules and the shared contracts must be included in boundary verification")
            .containsExactlyInAnyOrder("shared", "member", "sale", "inventory", "purchase", "payment", "purchasewaiting");
        modules.verify();
    }
}
