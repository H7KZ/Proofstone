package io.github.h7kz.proofstone.build.arch;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import io.github.h7kz.proofstone.build.arch.fixtures.BindMountFixture;
import io.github.h7kz.proofstone.build.arch.fixtures.CleanFixture;
import io.github.h7kz.proofstone.build.arch.fixtures.CopyFileFixture;
import io.github.h7kz.proofstone.build.arch.fixtures.RepeatedTestFixture;
import io.github.h7kz.proofstone.build.arch.fixtures.RetryExtensionFixture;
import io.github.h7kz.proofstone.build.arch.fixtures.SleepFixture;
import io.github.h7kz.proofstone.conformance.flake.FlakeFixture;
import io.github.h7kz.proofstone.core.await.Await;
import org.junit.jupiter.api.Test;

// Each hard rule has one failing fixture and one passing fixture (AGENTS.md §3).
class HardRulesTest {

    @Test
    void noSleepFailsOnThreadAndTimeUnitSleep() {
        assertViolation(HardRules.NO_SLEEP, SleepFixture.class);
    }

    @Test
    void noSleepPassesOnAwaitItself() {
        assertNoViolation(HardRules.NO_SLEEP, Await.class, CleanFixture.class);
    }

    @Test
    void noBindMountsFailsOnBindMode() {
        assertViolation(HardRules.NO_BIND_MOUNTS, BindMountFixture.class);
    }

    @Test
    void noBindMountsPassesOnCopyIntoContainer() {
        assertNoViolation(HardRules.NO_BIND_MOUNTS, CopyFileFixture.class);
    }

    @Test
    void noRetriesFailsOnRepeatedTestOutsideFlake() {
        assertViolation(HardRules.NO_RETRIES, RepeatedTestFixture.class);
    }

    @Test
    void noRetriesFailsOnRetryExtensionOutsideFlake() {
        assertViolation(HardRules.NO_RETRIES, RetryExtensionFixture.class);
    }

    @Test
    void noRetriesPassesInsideFlake() {
        assertNoViolation(HardRules.NO_RETRIES, FlakeFixture.class, CleanFixture.class);
    }

    private static void assertViolation(ArchRule rule, Class<?>... classes) {
        assertThat(rule.evaluate(new ClassFileImporter().importClasses(classes)).hasViolation())
                .as("%s should flag %s", rule.getDescription(), classes[0].getSimpleName())
                .isTrue();
    }

    private static void assertNoViolation(ArchRule rule, Class<?>... classes) {
        assertThat(rule.evaluate(new ClassFileImporter().importClasses(classes)).hasViolation())
                .as("%s should pass for %s", rule.getDescription(), classes[0].getSimpleName())
                .isFalse();
    }
}
