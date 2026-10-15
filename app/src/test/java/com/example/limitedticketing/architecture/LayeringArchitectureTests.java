package com.example.limitedticketing.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("모듈 내부 패키지 의존 방향 검증")
class LayeringArchitectureTests {

    private static final String ROOT = "com.example.limitedticketing";
    private static final JavaClasses classes = new ClassFileImporter()
        .withImportOption(new ImportOption.DoNotIncludeTests())
        .importPackages("com.example.limitedticketing");

    @Test
    @DisplayName("실행 모듈의 HTTP 어댑터도 공개 애플리케이션 계약을 통해 업무를 호출한다")
    void appHttpAdaptersAlsoEnterThroughPublicApplicationContracts() {
        noClasses()
            .that().resideInAPackage(ROOT + ".presentation..")
            .should().dependOnClassesThat().resideInAnyPackage(
                ROOT + "..domain..", ROOT + "..infrastructure..",
                ROOT + "..application.command..", ROOT + "..application.query..",
                ROOT + "..application.support..", ROOT + "..application.port.out.."
            )
            .because("The app assembly HTTP adapters must not bypass business module Facades")
            .check(classes);
    }

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @ValueSource(strings = {"member", "sale", "inventory", "purchase", "payment", "purchasewaiting"})
    @DisplayName("도메인은 모듈 내부 외부 계층과 프레임워크에 의존하지 않는다")
    void domainDoesNotDependOnOuterLayersOrFrameworks(String module) {
        String base = ROOT + "." + module;
        noClasses()
            .that().resideInAPackage(base + ".domain..")
            .and().doNotHaveSimpleName("package-info")
            .should().dependOnClassesThat().resideInAnyPackage(
                base + ".application..", base + ".presentation..", base + ".infrastructure..",
                "org.springframework..", "jakarta.persistence..", "org.hibernate.."
            )
            .because("Domain rules must not depend on use cases, adapters or persistence frameworks")
            .check(classes);
    }

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @ValueSource(strings = {"member", "sale", "inventory", "purchase", "payment", "purchasewaiting"})
    @DisplayName("애플리케이션은 모듈 내부 표현 계층과 인프라 구현에 의존하지 않는다")
    void applicationDoesNotDependOnPresentationOrInfrastructure(String module) {
        String base = ROOT + "." + module;
        noClasses()
            .that().resideInAPackage(base + ".application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                base + ".presentation..", base + ".infrastructure.."
            )
            .because("Use cases must call domain repositories or output ports instead of adapters")
            .check(classes);
    }

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @ValueSource(strings = {"sale", "purchase", "payment", "purchasewaiting"})
    @DisplayName("표현 계층은 공개 Facade를 우회해 모듈 내부 구현에 접근하지 않는다")
    void presentationDoesNotBypassThePublicApplicationPort(String module) {
        String base = ROOT + "." + module;
        noClasses()
            .that().resideInAPackage(base + ".presentation..")
            .should().dependOnClassesThat().resideInAnyPackage(
                base + ".domain..", base + ".infrastructure..",
                base + ".application.command..", base + ".application.query..",
                base + ".application.support..", base + ".application.port.out.."
            )
            .because("HTTP adapters must use the public Facade and its DTOs")
            .check(classes);
    }

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @ValueSource(strings = {"member", "sale", "inventory", "purchase", "payment", "purchasewaiting"})
    @DisplayName("공개 DTO는 모듈 내부 도메인과 구현 타입을 노출하지 않는다")
    void publicPortDtosDoNotExposeInternalTypes(String module) {
        String base = ROOT + "." + module;
        noClasses()
            .that().resideInAPackage(base + ".application.port.in.dto..")
            .should().dependOnClassesThat().resideInAnyPackage(
                base + ".domain..", base + ".infrastructure..", base + ".presentation..",
                base + ".application.command..", base + ".application.query..",
                base + ".application.support..", base + ".application.port.out.."
            )
            .because("Public contracts must remain independent of internal models and implementation DTOs")
            .check(classes);
    }

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @ValueSource(strings = {"member", "sale", "inventory", "purchase", "payment", "purchasewaiting"})
    @DisplayName("내부 유스케이스는 자신의 공개 입력 포트에 역방향으로 의존하지 않는다")
    void internalApplicationDoesNotDependOnItsOwnInboundPort(String module) {
        String base = ROOT + "." + module;
        noClasses()
            .that().resideInAnyPackage(
                base + ".application.command..", base + ".application.query..", base + ".application.support.."
            )
            .should().dependOnClassesThat().resideInAPackage(base + ".application.port.in..")
            .because("The public Facade delegates to internal use cases; internal use cases must not call it back")
            .check(classes);
    }

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @ValueSource(strings = {"member", "sale", "inventory", "purchase", "payment", "purchasewaiting"})
    @DisplayName("인프라 구현은 모듈 내부 HTTP 표현 계층에 의존하지 않는다")
    void infrastructureDoesNotDependOnPresentation(String module) {
        String base = ROOT + "." + module;
        noClasses()
            .that().resideInAPackage(base + ".infrastructure..")
            .should().dependOnClassesThat().resideInAPackage(base + ".presentation..")
            .because("Persistence and background adapters must not depend on HTTP contracts")
            .check(classes);
    }
}
