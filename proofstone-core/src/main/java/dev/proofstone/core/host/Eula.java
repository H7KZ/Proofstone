package dev.proofstone.core.host;

import java.util.Map;

/**
 * The Minecraft EULA gate. The harness never accepts the EULA for anyone: servers start only when the user
 * sets {@value #VARIABLE}{@code =true} in the environment of the test JVM.
 */
public final class Eula {

    public static final String VARIABLE = "PROOFSTONE_ACCEPT_EULA";

    private Eula() {
    }

    public static boolean accepted() {
        return accepted(System.getenv());
    }

    static boolean accepted(Map<String, String> environment) {
        return "true".equalsIgnoreCase(environment.get(VARIABLE));
    }
}
