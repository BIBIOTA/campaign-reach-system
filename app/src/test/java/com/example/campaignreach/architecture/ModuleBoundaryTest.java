package com.example.campaignreach.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Architecture guard for the modular monolith boundaries (design.md §3, §11.3).
 *
 * <p>{@code campaign} and {@code reach} are independent bounded modules. They may
 * each depend on {@code shared} (the shared kernel) but MUST NOT import each
 * other's packages — they communicate only through {@code shared.event}.
 */
class ModuleBoundaryTest {

    private static final String ROOT = "com.example.campaignreach";

    private static JavaClasses importedClasses;

    @BeforeAll
    static void importClasses() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    @Test
    void campaignMustNotDependOnReach() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..campaign..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..reach..")
                .because("campaign and reach must communicate only through shared.event (design.md §3)");

        rule.check(importedClasses);
    }

    @Test
    void reachMustNotDependOnCampaign() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..reach..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..campaign..")
                .because("campaign and reach must communicate only through shared.event (design.md §3)");

        rule.check(importedClasses);
    }
}
