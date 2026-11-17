package io.github.h7kz.proofstone.core.junit;

import io.github.h7kz.proofstone.core.cell.GeyserCell;
import io.github.h7kz.proofstone.core.host.GeyserHost;
import io.github.h7kz.proofstone.core.host.PaperHostException;
import io.github.h7kz.proofstone.core.host.standin.StandIn;
import io.github.h7kz.proofstone.core.verdict.ConformanceResult;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The first cached check of a Geyser cell (ADR-0018 sections 4 and 9, T-M9-08): one Bedrock client joins, and afterwards the
 * discovery stand-in must have answered exactly the two expected Microsoft names and the name guard must be clean. A red is a
 * failed conformance, so every test on the cell is INVALID. Geyser asks for the two names at its first login of each server start,
 * so the check needs a join; it is the cell's own, and the C-G scenarios then run on a cell known to be offline-stub.
 */
final class GeyserConformance {

    /** The Bedrock client the check joins with. Tests use their own names. */
    static final String CLIENT = "PSConfB";

    private GeyserConformance() {
    }

    /**
     * @param join joins a Bedrock client under the given name and returns it once the agent lists it
     * @param awaitGone waits, on the agent, until the server no longer lists {@code .name}
     */
    static ConformanceResult run(String cell, GeyserHost host, Function<String, SessionClient> join,
            Consumer<String> awaitGone) {
        // Before the join, so what the join logs is this check's own.
        long window = ErrorLogGuard.cursor(host.server());
        SessionClient client;
        try {
            client = join.apply(CLIENT);
        } catch (RuntimeException | AssertionError e) {
            String message = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
            return new ConformanceResult(cell, false,
                    joinFailure(message, host.namesAsked(), host.namesAnswered(), host.server().logs().all()));
        }
        String logged = null;
        try {
            ErrorLogGuard.check(host.server(), window, ErrorLogGuard.Patterns.of(GeyserCell.ID));
        } catch (AssertionError e) {
            logged = e.getMessage();
        }
        try {
            client.close();
            awaitGone.accept("." + CLIENT);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
            return new ConformanceResult(cell, false, "the Bedrock conformance client could not leave: " + message);
        }
        if (logged != null) {
            return new ConformanceResult(cell, false, "the server logged errors during the Bedrock join: " + logged);
        }
        String guard = null;
        try {
            host.requireNameGuardClean();
        } catch (PaperHostException e) {
            guard = e.getMessage();
        }
        return judgeNames(cell, host.namesAnswered(), guard);
    }

    /** Floodgate's default text for data it cannot decrypt (its {@code disconnect.invalid-key}); the client gets it as the kick. */
    static final String FLOODGATE_INVALID_KEY = "Please connect through the official Geyser";
    /** Geyser's own disconnect texts for a Bedrock protocol outside its range (geyser.md, "Negative controls", T-M10-05). */
    private static final String[] PROTOCOL_REFUSALS = {"Outdated Geyser proxy!", "Outdated Bedrock client!"};
    private static final String DISCOVERY_FAILURE = "Unable to fetch discovery data";

    /**
     * Why the Bedrock conformance client could not join, naming the hop when the evidence says which it is (T-M9-11). A client that
     * only sees {@code disconnect.timeout} is not a reason: the stand-in's answers and the server's own log line are.
     *
     * @param joinMessage what the driver reported
     * @param asked every name the stand-in was asked
     * @param answered the names the stand-in answered itself
     * @param serverLog the server's log, for the line that shows the failed fetch
     */
    static String joinFailure(String joinMessage, Set<String> asked, Set<String> answered, String serverLog) {
        Set<String> missing = new TreeSet<>(StandIn.NAMES);
        missing.removeAll(answered);
        boolean fetchFailed = serverLog.lines().anyMatch(line -> line.contains(DISCOVERY_FAILURE));
        // Blame the stand-in only on evidence: a name it was asked and did not answer, or Geyser's own failed fetch. A join that
        // died before any login (nothing asked) says nothing about the stand-in.
        if (!missing.isEmpty() && (fetchFailed || missing.stream().anyMatch(asked::contains))) {
            String answeredText = answered.isEmpty() ? "none of the two expected names"
                    : "only " + new TreeSet<>(answered) + " of them";
            StringBuilder reason = new StringBuilder("the discovery stand-in answered ").append(answeredText).append(" (missing ")
                    .append(missing).append("), so Geyser's login fetch could not complete; hop: server and "
                            + "driver host to the stand-in");
            serverLog.lines().filter(line -> line.contains(DISCOVERY_FAILURE)).findFirst()
                    .ifPresent(line -> reason.append("; the server logged: ").append(line.strip()));
            return reason.append("; the join said: ").append(joinMessage).toString();
        }
        for (String refusal : PROTOCOL_REFUSALS) {
            if (joinMessage.contains(refusal)) {
                return "Geyser refused the Bedrock protocol the driver sent: its range does not include it; "
                        + "hop: driver host to Geyser on the server (protocol number); the join said: " + joinMessage;
            }
        }
        if (joinMessage.contains(FLOODGATE_INVALID_KEY)) {
            return "Floodgate refused the data Geyser sent: the key Geyser encrypts with does not match the key Floodgate holds; "
                    + "hop: Geyser to Floodgate on the server; the join said: " + joinMessage;
        }
        return "the Bedrock conformance client could not join: " + joinMessage;
    }

    /**
     * @param answered the names the stand-in answered itself
     * @param guardFailure what the name guard refused, or {@code null} when it is clean
     */
    static ConformanceResult judgeNames(String cell, Set<String> answered, String guardFailure) {
        Set<String> missing = new TreeSet<>(StandIn.NAMES);
        missing.removeAll(answered);
        Set<String> unexpected = new TreeSet<>(answered);
        unexpected.removeAll(StandIn.NAMES);
        if (!missing.isEmpty() || !unexpected.isEmpty()) {
            return new ConformanceResult(cell, false, "the stand-in must be asked exactly the two expected names "
                    + new TreeSet<>(StandIn.NAMES) + " and answered " + new TreeSet<>(answered)
                    + (missing.isEmpty() ? "" : "; missing " + missing)
                    + (unexpected.isEmpty() ? "" : "; unexpected " + unexpected));
        }
        if (guardFailure != null) {
            return new ConformanceResult(cell, false, "the name guard is not clean: " + guardFailure);
        }
        return ConformanceResult.pass(cell);
    }
}
