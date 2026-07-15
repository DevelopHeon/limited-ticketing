package com.example.limitedticketing.architecture;

import com.example.limitedticketing.LimitedTicketingApplication;
import com.tngtech.archunit.core.domain.JavaClass;
import org.springframework.modulith.core.ApplicationModules;

final class ModularityTestSupport {

    private ModularityTestSupport() {
    }

    static ApplicationModules applicationModules() {
        return ApplicationModules.of(
            LimitedTicketingApplication.class,
            JavaClass.Predicates.resideInAnyPackage(
                "com.example.limitedticketing.configuration..",
                "com.example.limitedticketing.presentation.."
            )
        );
    }
}
