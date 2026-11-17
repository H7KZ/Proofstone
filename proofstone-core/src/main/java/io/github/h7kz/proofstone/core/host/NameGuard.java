package io.github.h7kz.proofstone.core.host;

import io.github.h7kz.proofstone.core.host.standin.StandIn;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The name guard of a Geyser cell (ADR-0018 section 4): reads the names the stand-in was asked and fails on a Xbox or
 * Microsoft account host. The two stand-in names are expected. It checks the names the cell looked up; it cannot see
 * traffic to an address that was never looked up, so the claim is "no Microsoft or Xbox name is looked up", never "no
 * packet leaves".
 */
final class NameGuard {

    /** Domains whose names (and subdomains) must never be asked: Xbox Live, Microsoft account and Entra sign-in. */
    static final List<String> DENIED_DOMAINS = List.of("live.com", "xboxlive.com", "xboxlive.cn", "xbox.com",
            "microsoftonline.com", "msauth.net");
    /** The domain of the two expected names: any other name under it means the login code asked for something new. */
    private static final String STAND_IN_DOMAIN = "minecraft-services.net";

    private NameGuard() {
    }

    /** The names in a stand-in log ({@code DNS <type> <name>} lines), in first-asked order, each once. */
    static Set<String> namesAsked(String log) {
        Set<String> names = new LinkedHashSet<>();
        for (String line : log.split("\\R")) {
            String[] parts = line.strip().split(" ");
            if (parts.length == 3 && parts[0].equals("DNS") && !parts[1].equals("error")) {
                names.add(parts[2].toLowerCase(Locale.ROOT));
            }
        }
        return names;
    }

    /** The names the stand-in itself answered ({@code STUB answered <name>}), in first-asked order. */
    static Set<String> namesAnswered(String log) {
        Set<String> names = new LinkedHashSet<>();
        for (String line : log.split("\\R")) {
            if (line.startsWith("STUB answered ")) {
                names.add(line.substring("STUB answered ".length()).strip());
            }
        }
        return names;
    }

    /** The names asked that the guard refuses, each with its reason; empty when the log is clean. */
    static List<String> violations(String log) {
        List<String> found = new ArrayList<>();
        for (String name : namesAsked(log)) {
            for (String domain : DENIED_DOMAINS) {
                if (name.equals(domain) || name.endsWith("." + domain)) {
                    found.add(name + " (a Xbox or Microsoft account host: " + domain + ")");
                }
            }
            if ((name.equals(STAND_IN_DOMAIN) || name.endsWith("." + STAND_IN_DOMAIN)) && !StandIn.NAMES.contains(name)) {
                found.add(name + " (an unexpected name under " + STAND_IN_DOMAIN + ")");
            }
        }
        return found;
    }

    /** @throws PaperHostException when a refused name was asked, with the stand-in log tail */
    static void require(String log, String tail) {
        List<String> found = violations(log);
        if (!found.isEmpty()) {
            throw new PaperHostException("the Geyser cell looked up a name the guard refuses: " + found
                    + "\n--- stand-in log tail ---\n" + tail);
        }
    }
}
