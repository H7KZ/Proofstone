package io.github.h7kz.proofstone.core.host;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The hop of a topology join that did not complete, decided from the logs written since the join began (ADR-0016
 * section 9): no proxy line naming the player means client to proxy; a proxy line but no {@code logged in} line for the
 * player on the target backend means proxy to that backend. Evidence of where it stopped, never a reason why.
 *
 * @param name the hop, as the failure message says it
 * @param evidence the log line the decision rests on, or what was missing
 */
public record JoinHop(String name, String evidence) {

    /**
     * @param proxyLines the proxy log lines since the join began
     * @param backendLines each backend's log lines since the join began
     * @param target the backend the proxy should have sent the player to
     */
    public static JoinHop of(String player, List<String> proxyLines, Map<String, List<String>> backendLines,
            String target) {
        if (!backendLines.containsKey(target)) {
            throw new IllegalArgumentException("unknown target backend \"" + target + "\"; backends are "
                    + backendLines.keySet());
        }
        // A name inside a longer word (T10 for T1) is another player, as in JoinStall.
        Pattern name = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(player) + "(?![A-Za-z0-9_])");
        String proxyLine = proxyLines.stream().filter(line -> name.matcher(line).find()).findFirst().orElse(null);
        if (proxyLine == null) {
            return new JoinHop("client to proxy", "no proxy log line since the join began names " + player);
        }
        String login = backendLines.get(target).stream()
                .filter(line -> line.contains("logged in") && name.matcher(line).find()).findFirst().orElse(null);
        if (login == null) {
            return new JoinHop("proxy to backend " + target, "proxy logged \"" + proxyLine + "\", backend " + target
                    + " logged no \"logged in\" line for " + player);
        }
        return new JoinHop("after both hops (backend " + target + " logged the player in)", login);
    }
}
