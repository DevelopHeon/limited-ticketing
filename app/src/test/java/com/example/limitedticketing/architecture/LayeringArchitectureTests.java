package com.example.limitedticketing.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.scheduling.annotation.Scheduled;

@DisplayName("모듈 내부 Port와 UseCase 및 Adapter 의존 방향 검증")
class LayeringArchitectureTests {

    private static final String ROOT = "com.example.limitedticketing";
    private static final JavaClasses importedClasses = new ClassFileImporter()
        .withImportOption(new ImportOption.DoNotIncludeTests())
        .importPackages(ROOT);

    @Test
    @DisplayName("스케줄 트리거는 infrastructure의 입력 Scheduler Adapter에 선언한다")
    void scheduledTriggersBelongToInputAdapters() {
        methods().that().areAnnotatedWith(Scheduled.class)
            .should().beDeclaredInClassesThat().resideInAPackage("..infrastructure.adapter.in.scheduler..")
            .because("실행 주기는 입력 Adapter, 업무와 트랜잭션은 Application이 소유한다")
            .check(importedClasses);
    }

    @Test
    @DisplayName("실행 앱의 공통 HTTP 기능은 업무 모듈 내부 구현을 직접 호출하지 않는다")
    void appHttpAdaptersDoNotBypassBusinessContracts() {
        noClasses().that().resideInAPackage(ROOT + ".presentation..")
            .should().dependOnClassesThat().resideInAnyPackage(
                ROOT + "..domain..", ROOT + "..infrastructure..", ROOT + "..application.service..",
                ROOT + "..application.facade..", ROOT + "..application.usecase..", ROOT + "..application.port.out.."
            )
            .because("app의 공통 HTTP 기능도 업무 모듈의 공개 Port를 사용해야 한다")
            .check(importedClasses);
    }

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @ValueSource(strings = {"member", "sale", "inventory", "purchase", "payment", "purchasewaiting"})
    @DisplayName("Domain은 Application과 Adapter 및 영속성 프레임워크에 의존하지 않는다")
    void domainDoesNotDependOnOuterLayersOrFrameworks(String module) {
        String base = ROOT + "." + module;
        noClasses().that().resideInAPackage(base + ".domain..")
            .and().doNotHaveSimpleName("package-info")
            .should().dependOnClassesThat().resideInAnyPackage(
                base + ".application..", base + ".infrastructure..", base + ".presentation..",
                "org.springframework..", "jakarta.persistence..", "org.hibernate.."
            )
            .because("Domain 상태 규칙은 외부 실행 및 저장 기술과 독립적이어야 한다")
            .check(importedClasses);
    }

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @ValueSource(strings = {"member", "sale", "inventory", "purchase", "payment", "purchasewaiting"})
    @DisplayName("Application은 모듈 내부 Adapter 구현에 의존하지 않는다")
    void applicationDoesNotDependOnAdapters(String module) {
        String base = ROOT + "." + module;
        noClasses().that().resideInAPackage(base + ".application..")
            .should().dependOnClassesThat().resideInAnyPackage(base + ".infrastructure..", base + ".presentation..")
            .because("Application은 구체 Adapter 대신 Output Port를 호출해야 한다")
            .check(importedClasses);
    }

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @ValueSource(strings = {"sale", "purchase", "payment", "purchasewaiting"})
    @DisplayName("입력 Adapter는 Input Port를 우회해 내부 업무와 출력 계약을 호출하지 않는다")
    void inputAdaptersOnlyEnterThroughInputPorts(String module) {
        String base = ROOT + "." + module;
        noClasses().that().resideInAPackage(base + ".infrastructure.adapter.in..")
            .should().dependOnClassesThat().resideInAnyPackage(
                base + ".domain..", base + ".application.service..", base + ".application.facade..",
                base + ".application.usecase..", base + ".application.port.out..", base + ".infrastructure.adapter.out.."
            )
            .because("Controller와 Scheduler는 Input Port 및 외부 Input/Output DTO로 코어에 진입한다")
            .check(importedClasses);
    }

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @ValueSource(strings = {"member", "sale", "inventory", "purchase", "payment", "purchasewaiting"})
    @DisplayName("입력 Port와 DTO는 Domain 및 내부 구현 타입을 노출하지 않는다")
    void inputContractsDoNotExposeInternalTypes(String module) {
        String base = ROOT + "." + module;
        noClasses().that().resideInAPackage(base + ".application.port.in..")
            .and().doNotHaveSimpleName("package-info")
            .should().dependOnClassesThat().resideInAnyPackage(
                base + ".domain..", base + ".infrastructure..", base + ".application.service..",
                base + ".application.facade..", base + ".application.usecase..", base + ".application.port.out.."
            )
            .because("외부 진입 계약은 모듈 내부 모델과 구현 DTO를 노출하지 않는다")
            .check(importedClasses);
    }

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @ValueSource(strings = {"member", "sale", "inventory", "purchase", "payment", "purchasewaiting"})
    @DisplayName("내부 UseCase와 DTO는 Input Port 및 진입 구현에 역방향으로 의존하지 않는다")
    void useCasesDoNotCallBackIntoEntryContracts(String module) {
        String base = ROOT + "." + module;
        noClasses().that().resideInAPackage(base + ".application.usecase..")
            .should().dependOnClassesThat().resideInAnyPackage(
                base + ".application.port.in..", base + ".application.facade..", base + ".application.service.."
            )
            .because("내부 UseCase는 코어 업무 계약이며 진입 Port나 구현체를 호출하지 않는다")
            .check(importedClasses);
    }

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @ValueSource(strings = {"member", "sale", "inventory", "purchase", "payment", "purchasewaiting"})
    @DisplayName("내부 업무 Service는 자신의 입력 Port를 역호출하지 않는다")
    void coreServicesDoNotCallBackIntoInputPorts(String module) {
        String base = ROOT + "." + module;
        noClasses().that().resideInAPackage(base + ".application.service..")
            .and(DescribedPredicate.describe("Input Port 구현 진입 Service를 제외한 내부 업무 Service", type ->
                type.getAllRawInterfaces().stream().noneMatch(contract ->
                    contract.getPackageName().startsWith(base + ".application.port.in"))))
            .should().dependOnClassesThat().resideInAnyPackage(base + ".application.port.in..", base + ".application.facade..")
            .because("진입 Service의 Port 구현은 허용하지만 내부 업무 Service는 UseCase로 실행한다")
            .check(importedClasses);
    }

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @ValueSource(strings = {"member", "sale", "inventory", "purchase", "payment", "purchasewaiting"})
    @DisplayName("Service 간 업무 호출은 구체 구현이 아닌 내부 UseCase를 사용한다")
    void servicesDoNotDependOnOtherServiceImplementations(String module) {
        String base = ROOT + "." + module;
        noClasses().that().resideInAPackage(base + ".application.service")
            .and().haveSimpleNameEndingWith("Service")
            .should().dependOnClassesThat(DescribedPredicate.describe("같은 모듈의 구체 Service", type ->
                type.getPackageName().equals(base + ".application.service") && type.getSimpleName().endsWith("Service")))
            .because("내부 record와 commit 콜백은 Service 구현이 아니며 다른 업무 구현은 UseCase를 통해 호출한다")
            .check(importedClasses);
    }

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @ValueSource(strings = {"member", "sale", "inventory", "purchase", "payment", "purchasewaiting"})
    @DisplayName("입력 Port 구현은 PortService로 통일하고 별도 Facade를 두지 않는다")
    void inputPortsAreImplementedByPortServices(String module) {
        String base = ROOT + "." + module;
        classes().that().resideInAPackage(base + ".application..")
            .and(DescribedPredicate.describe("자기 모듈의 Input Port 구현", type ->
                type.getAllRawInterfaces().stream().anyMatch(contract ->
                    contract.getPackageName().startsWith(base + ".application.port.in"))))
            .should().haveSimpleNameEndingWith("PortService")
            .andShould().resideInAPackage(base + ".application.service")
            .because("진입 연결과 UseCase 조율은 PortService가 함께 맡는다")
            .check(importedClasses);
    }

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @ValueSource(strings = {"member", "sale", "inventory", "purchase", "payment", "purchasewaiting"})
    @DisplayName("PortService는 Repository와 Output Port 및 구현 Service를 직접 호출하지 않는다")
    void portServicesOnlyExecuteThroughUseCases(String module) {
        String base = ROOT + "." + module;
        noClasses().that().resideInAPackage(base + ".application..")
            .and(DescribedPredicate.describe("자기 모듈의 Input Port 구현", type ->
                type.getAllRawInterfaces().stream().anyMatch(contract ->
                    contract.getPackageName().startsWith(base + ".application.port.in"))))
            .should().dependOnClassesThat().resideInAnyPackage(
                base + ".domain.repository..", base + ".application.port.out..",
                base + ".application.service..", base + ".application.facade..", base + ".infrastructure.."
            )
            .because("PortService는 DTO를 변환하고 내부 UseCase를 조율하며 저장소와 외부 기능은 업무 Service에 위임한다")
            .check(importedClasses);
    }

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @ValueSource(strings = {"member", "sale", "inventory", "purchase", "payment", "purchasewaiting"})
    @DisplayName("PortService는 Aggregate 대신 내부 결과 DTO를 변환한다")
    void portServicesDoNotManipulateAggregates(String module) {
        String base = ROOT + "." + module;
        noClasses().that().resideInAPackage(base + ".application.service")
            .and().haveSimpleNameEndingWith("PortService")
            .should().dependOnClassesThat(DescribedPredicate.describe("Domain의 Aggregate와 상태 객체", type ->
                type.getPackageName().startsWith(base + ".domain.model") && !type.isEnum()))
            .because("상태값의 DTO 변환은 허용하지만 Aggregate의 조회와 변경은 내부 업무 Service가 맡는다")
            .check(importedClasses);
    }

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @ValueSource(strings = {"member", "sale", "inventory", "purchase", "payment", "purchasewaiting"})
    @DisplayName("출력 Adapter는 입력 Adapter와 모듈 내부 업무 구현을 호출하지 않는다")
    void outputAdaptersDoNotDependOnInputOrApplicationImplementations(String module) {
        String base = ROOT + "." + module;
        noClasses().that().resideInAPackage(base + ".infrastructure.adapter.out..")
            .should().dependOnClassesThat().resideInAnyPackage(
                base + ".infrastructure.adapter.in..", base + ".application.service..",
                base + ".application.facade..", base + ".application.usecase..", base + ".application.port.in.."
            )
            .because("출력 Adapter는 자신의 Output Port를 구현하고 외부 기술과 통신한다")
            .check(importedClasses);
    }

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @ValueSource(strings = {"member", "sale", "inventory", "purchase", "payment", "purchasewaiting"})
    @DisplayName("Input과 Output Port는 구현 클래스가 아닌 interface다")
    void portsAreInterfaces(String module) {
        String base = ROOT + "." + module;
        classes().that().resideInAnyPackage(base + ".application.port.in..", base + ".application.port.out..")
            .and().resideOutsideOfPackages(base + ".application.port.in.dto..", base + ".application.port.in.worker.dto..", base + ".application.port.out.dto..")
            .and().doNotHaveSimpleName("package-info")
            .should().beInterfaces()
            .because("Port의 계약과 진입 Service 또는 Adapter의 구현을 분리한다")
            .check(importedClasses);
    }

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @ValueSource(strings = {"member", "sale", "inventory", "purchase", "payment", "purchasewaiting"})
    @DisplayName("내부 UseCase는 interface로 선언한다")
    void useCasesAreInterfaces(String module) {
        classes().that().resideInAPackage(ROOT + "." + module + ".application.usecase")
            .and().doNotHaveSimpleName("package-info")
            .should().beInterfaces()
            .because("업무 호출은 UseCase 계약을 사용하고 Service는 이를 구현한다")
            .check(importedClasses);
    }

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @ValueSource(strings = {"member", "sale", "inventory", "purchase", "payment", "purchasewaiting"})
    @DisplayName("이전 presentation과 command query support 및 facade 패키지는 남기지 않는다")
    void legacyPackagesAreRemoved(String module) {
        String base = ROOT + "." + module;
        classes().that().resideInAPackage(base + "..")
            .should().resideOutsideOfPackages(
                base + ".presentation..", base + ".application.command..", base + ".application.query..",
                base + ".application.support..", base + ".application.facade.."
            )
            .because("합의한 패키지 경계를 전체 모듈에 적용하고 이전 구현 경로를 함께 정리한다")
            .check(importedClasses);
    }

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @ValueSource(strings = {"member", "sale", "inventory", "purchase", "payment", "purchasewaiting"})
    @DisplayName("Aggregate Repository는 Domain의 추상 interface다")
    void aggregateRepositoriesAreDomainInterfaces(String module) {
        classes().that().resideInAPackage(ROOT + "." + module + ".domain.repository..")
            .and().haveSimpleNameEndingWith("Repository")
            .should().beInterfaces()
            .because("Aggregate 영속성 계약은 Domain이 소유하고 infrastructure에서 직접 구현한다")
            .check(importedClasses);
    }
}
