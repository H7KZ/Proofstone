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

    // ---- a join that fails names its hop (T-M9-11) ----

    private static final String SERVER_LOG = "[18:03:37 WARN]: [Geyser-Spigot] Exception caught in session of unknown (pre-login)\n"
            + "java.lang.AssertionError: Unable to fetch discovery data from https://client.discovery.minecraft-services.net/api\n"
            + "Caused by: java.net.UnknownHostException: client.discovery.minecraft-services.net\n";
    private static final String JOIN = "Bedrock join of PSConfB failed: DISCONNECTED: the connection closed: disconnect.timeout "
            + "(at join stage LOGIN_SENT)";

    @Test
    void aJoinThatFailedWhileTheStandInAnsweredNoNameBlamesTheStandInAndQuotesTheServersOwnLine() {
        String reason = GeyserConformance.joinFailure(JOIN, StandIn.NAMES, Set.of(), SERVER_LOG);

        assertThat(reason).contains("the discovery stand-in answered none of the two expected names")
                .contains(StandIn.DISCOVERY_NAME).contains(StandIn.AUTH_NAME)
                .contains("hop: server and driver host to the stand-in")
                .contains("Unable to fetch discovery data from https://client.discovery.minecraft-services.net/api")
                .contains(JOIN);
    }

    @Test
    void aJoinThatFailedWithOneNameMissingNamesOnlyTheMissingOne() {
        String reason = GeyserConformance.joinFailure(JOIN, StandIn.NAMES, Set.of(StandIn.DISCOVERY_NAME), "");

        assertThat(reason).contains("answered only [" + StandIn.DISCOVERY_NAME + "]").contains("missing [" + StandIn.AUTH_NAME + "]")
                .contains("hop: server and driver host to the stand-in").doesNotContain("Unable to fetch");
    }

    @Test
    void floodgatesOwnRefusalWithBothNamesAnsweredBlamesTheKeyBetweenGeyserAndFloodgate() {
        String message = "Bedrock join of PSConfB failed: DISCONNECTED: Please connect through the official Geyser (at join stage "
                + "START_GAME)";

        String reason = GeyserConformance.joinFailure(message, StandIn.NAMES, StandIn.NAMES, "");

        assertThat(reason).contains("Floodgate refused the data Geyser sent").contains("does not match")
                .contains("hop: Geyser to Floodgate on the server").contains("Please connect through the official Geyser")
                .contains("START_GAME");
    }

    @Test
    void geysersOwnRefusalOfTheProtocolNamesTheDriverToGeyserHopWithNothingAnsweredAndNothingAsked() {
        String message = "Bedrock join of PSConfB failed: DISCONNECTED: Outdated Geyser proxy! This server supports the following "
                + "Bedrock versions: 26.40, 26.63 (at join stage NETWORK_SETTINGS)";

        String reason = GeyserConformance.joinFailure(message, Set.of(), Set.of(), "");

        assertThat(reason).contains("Geyser refused the Bedrock protocol the driver sent")
                .contains("hop: driver host to Geyser on the server (protocol number)")
                .contains("Outdated Geyser proxy! This server supports the following Bedrock versions").contains("NETWORK_SETTINGS")
                .doesNotContain("stand-in answered");
        assertThat(GeyserConformance.joinFailure(message.replace("Geyser proxy", "Bedrock client"), Set.of(), Set.of(), ""))
                .contains("hop: driver host to Geyser on the server (protocol number)");
    }

    @Test
    void anyOtherJoinFailureKeepsItsMessageAndGuessesNoHop() {
        String reason = GeyserConformance.joinFailure("Bedrock join of PSConfB failed: DISCONNECTED: nope", StandIn.NAMES, StandIn.NAMES, SERVER_LOG);

        assertThat(reason).isEqualTo("the Bedrock conformance client could not join: Bedrock join of PSConfB failed: DISCONNECTED: nope");
    }

    @Test
    void aJoinThatDiedBeforeAnyLoginSaysNothingAboutTheStandIn() {
        String reason = GeyserConformance.joinFailure("RakNet connect failed", Set.of(), Set.of(), "");

        assertThat(reason).isEqualTo("the Bedrock conformance client could not join: RakNet connect failed");
    }
}
