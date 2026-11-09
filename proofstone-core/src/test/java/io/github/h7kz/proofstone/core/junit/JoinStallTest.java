package io.github.h7kz.proofstone.core.junit;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.core.driver.JoinTimeoutException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The server's view of a stalled join (T-M6-14): composition only, no server. */
class JoinStallTest {

    private static JoinTimeoutException timeout() {
        return new JoinTimeoutException("timed out joining T1 -> h:1 after PT30S; stalled at LOGIN_START_SENT_NO_REPLY (...)",
                null, "T1", "LOGIN_START_SENT_NO_REPLY", "775", "none");
    }

    @Test
    void aServerThatLoggedTheLoginIsReportedAsHavingSeenIt() {
        List<String> during = List.of("[10:00:01 INFO]: UUID of player T1 is 0000", "[10:00:01 INFO]: Other[/1.2.3.4] x");
        JoinStall stall = JoinStall.of(timeout(), "server log", during, "tail line");

        assertThat(stall.loginStartSeen()).isTrue();
        assertThat(stall.suffix()).contains("server login start seen: yes");
        assertThat(stall.report()).contains("server login start seen: yes")
                .contains("UUID of player T1").doesNotContain("Other[/1.2.3.4]");
    }

    @Test
    void aServerThatLoggedNothingAboutThePlayerIsReportedAsNotHavingSeenIt() {
        JoinStall stall = JoinStall.of(timeout(), "server log", List.of("[10:00:01 INFO]: unrelated"), "tail line");

        assertThat(stall.loginStartSeen()).isFalse();
        assertThat(stall.suffix())
                .contains("server login start seen: no (no line of the server log since the join began names T1)");
    }

    @Test
    void theReportCarriesTheClientsViewAndTheServerLogTail() {
        String report = JoinStall.of(timeout(), "server log", List.of(), "line a\nline b").report();

        assertThat(report).contains("player: T1")
                .contains("stalled stage: LOGIN_START_SENT_NO_REPLY")
                .contains("protocol version offered: 775")
                .contains("last packet received: none")
                .contains("server log tail at the timeout:\nline a\nline b");
    }

    @Test
    void aPlayerNameThatIsAPrefixOfAnotherIsNotCounted() {
        // PSConf and T1 style names are distinct, but a name inside a longer word must not count as the login.
        JoinStall stall = JoinStall.of(timeout(), "server log", List.of("[INFO]: T10 joined the game"), "");

        assertThat(stall.loginStartSeen()).isFalse();
    }
}
