package io.github.h7kz.proofstone.core.junit;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.core.host.standin.StandIn;
import io.github.h7kz.proofstone.core.verdict.ConformanceResult;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The first cached check of a Geyser cell (T-M9-08): the stand-in's names and the name guard, judged from what they say. */
class GeyserConformanceTest {

    private static final String CELL = "geyser@2.12.1+paper@26.3";

    @Test
    void exactlyTheTwoExpectedNamesAnsweredAndACleanGuardPass() {
        assertThat(GeyserConformance.judgeNames(CELL, StandIn.NAMES, null).passed()).isTrue();
    }

    @Test
    void noNameAnsweredIsARedBecauseTheJoinNeverReachedTheStandIn() {
        ConformanceResult result = GeyserConformance.judgeNames(CELL, Set.of(), null);

        assertThat(result.passed()).isFalse();
        assertThat(result.detail()).contains("exactly the two expected names").contains(StandIn.DISCOVERY_NAME);
    }

    @Test
    void aMissingOrExtraAnsweredNameIsARedAndNamesWhatDiffers() {
        ConformanceResult missing = GeyserConformance.judgeNames(CELL, Set.of(StandIn.DISCOVERY_NAME), null);
        ConformanceResult extra = GeyserConformance.judgeNames(CELL, Set.of(StandIn.DISCOVERY_NAME, StandIn.AUTH_NAME,
                "login.live.com"), null);

        assertThat(missing.passed()).isFalse();
        assertThat(missing.detail()).contains("missing").contains(StandIn.AUTH_NAME);
        assertThat(extra.passed()).isFalse();
        assertThat(extra.detail()).contains("unexpected").contains("login.live.com");
    }

    @Test
    void aGuardViolationIsARedEvenWhenTheNamesAnsweredAreRight() {
        ConformanceResult result = GeyserConformance.judgeNames(CELL, StandIn.NAMES,
                "the Geyser cell looked up a name the guard refuses: [login.live.com]");

        assertThat(result.passed()).isFalse();
        assertThat(result.detail()).contains("name guard").contains("login.live.com");
    }
}
