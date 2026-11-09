package io.github.h7kz.proofstone.core.junit;

import io.github.h7kz.proofstone.core.driver.JoinTimeoutException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The server's side of a join that timed out (T-M6-14), put beside the client's. "Login start seen" means a line of
 * the server log, written since the join began, names the player: that is the first thing the server logs about a
 * login, and it does not depend on the exact wording of one Paper version. This is evidence of the stall, not a
 * reason for it, and it is never treated as one.
 *
 * @param loginStartSeen whether any log line since the join began names the player
 * @param named the log lines that name the player (at most {@link #MAX_NAMED})
 */
record JoinStall(JoinTimeoutException timeout, String source, boolean loginStartSeen, List<String> named,
        String logTail) {

    static final int MAX_NAMED = 10;

    /**
     * @param source what the lines came from, for the text: {@code server log}, or {@code server and ViaProxy logs}
     * @param duringJoin the log lines added since the join began
     * @param logTail the last lines of the server log at the timeout
     */
    static JoinStall of(JoinTimeoutException timeout, String source, List<String> duringJoin, String logTail) {
        // A name inside a longer word (T10 for T1) is another player.
        Pattern name = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(timeout.player()) + "(?![A-Za-z0-9_])");
        List<String> named = duringJoin.stream().filter(line -> name.matcher(line).find()).limit(MAX_NAMED).toList();
        return new JoinStall(timeout, source, !named.isEmpty(), named, logTail);
    }

    /** The text appended to the INVALID or failure message. */
    String suffix() {
        return "; server login start seen: " + (loginStartSeen ? "yes"
                : "no (no line of the " + source + " since the join began names " + timeout.player() + ")");
    }

    /** The bundle's {@code join-stall.txt}, also written to stderr. */
    String report() {
        StringBuilder text = new StringBuilder("join stall (T-M6-14)\n")
                .append("player: ").append(timeout.player()).append('\n')
                .append("stalled stage: ").append(timeout.stage()).append('\n')
                .append("protocol version offered: ").append(timeout.protocolOffered()).append('\n')
                .append("last packet received: ").append(timeout.lastReceived()).append('\n')
                .append("server login start seen: ").append(loginStartSeen ? "yes" : "no").append('\n');
        if (!named.isEmpty()) {
            text.append("lines of the ").append(source).append(" naming the player:\n");
            named.forEach(line -> text.append("  ").append(line).append('\n'));
        }
        return text.append("server log tail at the timeout:\n").append(logTail).append('\n').toString();
    }
}
