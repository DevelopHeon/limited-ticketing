package com.example.limitedticketing.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class LayeringArchitectureTests {

    private static final JavaClasses classes = new ClassFileImporter()
        .withImportOption(new ImportOption.DoNotIncludeTests())
        .importPackages("com.example.limitedticketing");

    @Test
    void domainDoesNotDependOnFrameworkOrPersistenceImplementations() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .and().doNotHaveSimpleName("package-info")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
                "org.hibernate.."
            )
            .check(classes);
    }

    @Test
    void applicationDoesNotDependOnInfrastructureImplementations() {
        noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("com.example.limitedticketing..infrastructure..")
            .check(classes);
    }

    @Test
    void presentationAndPublicPortDtosDoNotExposeDomainTypes() {
        noClasses()
            .that().resideInAnyPackage("..presentation..", "..application.port.in.dto..")
            .should().dependOnClassesThat().resideInAPackage("com.example.limitedticketing..domain..")
            .check(classes);
    }

    @Test
    void presentationDoesNotBypassThePublicApplicationPort() {
        noClasses()
            .that().resideInAPackage("..presentation..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..application.command..",
                "..application.query..",
                "..application.support..",
                "com.example.limitedticketing..infrastructure.."
            )
            .check(classes);
    }

    @Test
    void publicPortDtosDoNotDependOnInternalApplicationLayers() {
        noClasses()
            .that().resideInAPackage("..application.port.in.dto..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.example.limitedticketing..application.command..",
                "com.example.limitedticketing..application.query..",
                "com.example.limitedticketing..application.support..",
                "com.example.limitedticketing..infrastructure.."
            )
            .check(classes);
    }
}
