package io.github.h7kz.proofstone.core.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** The Geyser cell's guards over its whole life: names and aux logs, per test and at close (T-M10-06). No Docker. */
class CellGuardsTest {

    private final LogBuffer standIn = new LogBuffer();
    private final LogBuffer driver = new LogBuffer();

    private CellGuards guards(boolean refusalExpected) {
        return new CellGuards(standIn, driver, refusalExpected);
    }

    @Test
    void aCleanCellPassesEveryCheck() {
        CellGuards guards = guards(false);
        standIn.append("DNS A client.discovery.minecraft-services.net\nSTUB answered client.discovery.minecraft-services.net");
        driver.append("bedrock driver host ready: RPC v1");

        assertThatCode(guards::requireClean).doesNotThrowAnyException();
        assertThatCode(guards::requireClean).doesNotThrowAnyException();
    }

    @Test
    void aDeniedNameAskedAfterTheFirstCheckFailsTheTestThatAskedItAndOnlyThatOne() {
        CellGuards guards = guards(false);
        standIn.append("DNS A client.discovery.minecraft-services.net");
        guards.requireClean();
        standIn.append("DNS A login.live.com");

        assertThatThrownBy(guards::requireClean).hasMessageContaining("login.live.com");
        // The next test did not ask it, so it is not failed again for the same line.
        assertThatCode(guards::requireClean).doesNotThrowAnyException();
    }

    @Test
    void anErrorInTheDriverHostLogFailsAndNamesTheContainer() {
        CellGuards guards = guards(false);
        driver.append("Exception in thread \"main\" java.lang.IllegalStateException: boom");

        assertThatThrownBy(guards::requireClean).isInstanceOf(PaperHostException.class)
                .hasMessageContaining("driver host").hasMessageContaining("Exception in thread");
    }

    @Test
    void aStubRefusedLineInTheStandInLogFailsUnlessTheFaultIsOn() {
        standIn.append("STUB refused client.discovery.minecraft-services.net");

        assertThatThrownBy(guards(false)::requireClean).hasMessageContaining("stand-in").hasMessageContaining("STUB refused");
        assertThatCode(guards(true)::requireClean).doesNotThrowAnyException();
    }

    @Test
    void bothProblemsAreReportedTogether() {
        CellGuards guards = guards(false);
        standIn.append("DNS A login.live.com\nERROR stand-in broke");
        driver.append("ERROR driver broke");

        assertThatThrownBy(guards::requireClean).hasMessageContaining("login.live.com").hasMessageContaining("stand-in broke")
                .hasMessageContaining("driver broke");
    }
}
