package io.github.h7kz.proofstone.driver.bedrock;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** The host jar carries one package of core and the library; anything else it used would be missing at run time. */
class BoundaryTest {

    private static final String RPC = "io.github.h7kz.proofstone.core.driver.bedrock.rpc..";

    @Test
    void theHostUsesOnlyTheRpcContractOfCore() {
        noClasses().that().resideInAPackage("io.github.h7kz.proofstone.driver.bedrock..")
                .should().dependOnClassesThat(JavaClass.Predicates.resideInAPackage("io.github.h7kz.proofstone.core..")
                        .and(DescribedPredicate.not(JavaClass.Predicates.resideInAPackage(RPC))))
                .check(new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("io.github.h7kz.proofstone.driver.bedrock"));
    }

    @Test
    void theContractUsesOnlyTheJdkAndJackson() {
        noClasses().that().resideInAPackage(RPC)
                .should().dependOnClassesThat().resideInAnyPackage("org.testcontainers..", "io.github.h7kz.proofstone.api..",
                        "io.github.h7kz.proofstone.protocol..", "io.github.h7kz.proofstone.core.driver")
                .check(new ClassFileImporter().importPackages("io.github.h7kz.proofstone.core.driver.bedrock.rpc"));
    }
}
