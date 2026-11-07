package dev.proofstone.core.host;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.testcontainers.containers.Network;

/**
 * Inputs of one {@link PaperHost}.
 *
 * @param agentJar the shaded agent plugin jar
 * @param artefactBase directory the globs are relative to
 * @param artefactGlobs plugin jars under test, copied into {@code /plugins} next to the agent
 * @param eulaAccepted whether the user accepted the Minecraft EULA ({@link Eula#accepted()})
 * @param bootTimeout how long to wait for {@code Done (} in the log
 * @param agentTimeout how long to wait for agent health once the server is done
 * @param applyGameRules run the game-rule setup through the agent command endpoint (on by default).
 * @param debugPorts publish the agent port to the host. Off by default (T-M1-07).
 * @param network the cell network the server joins as {@link PaperHost#NETWORK_ALIAS}, or {@code null} for the
 *     default one; set when a ViaProxy container must reach the server by name (T-M3-03)
 */
public record PaperHostOptions(
        Path agentJar,
        Path artefactBase,
        List<String> artefactGlobs,
        boolean eulaAccepted,
        Duration bootTimeout,
        Duration agentTimeout,
        boolean applyGameRules,
        boolean debugPorts,
        Network network) {

    public static PaperHostOptions of(Path agentJar, boolean eulaAccepted) {
        return new PaperHostOptions(agentJar, agentJar.toAbsolutePath().getParent(), List.of(), eulaAccepted,
                Duration.ofMinutes(3), Duration.ofSeconds(30), true, false, null);
    }

    public PaperHostOptions withArtefacts(Path base, List<String> globs) {
        return new PaperHostOptions(agentJar, base, globs, eulaAccepted, bootTimeout, agentTimeout,
                applyGameRules, debugPorts, network);
    }

    public PaperHostOptions withApplyGameRules(boolean apply) {
        return new PaperHostOptions(agentJar, artefactBase, artefactGlobs, eulaAccepted, bootTimeout,
                agentTimeout, apply, debugPorts, network);
    }

    public PaperHostOptions withDebugPorts(boolean debug) {
        return new PaperHostOptions(agentJar, artefactBase, artefactGlobs, eulaAccepted, bootTimeout, agentTimeout,
                applyGameRules, debug, network);
    }

    public PaperHostOptions withAgentTimeout(Duration timeout) {
        return new PaperHostOptions(agentJar, artefactBase, artefactGlobs, eulaAccepted, bootTimeout, timeout,
                applyGameRules, debugPorts, network);
    }

    public PaperHostOptions withNetwork(Network cellNetwork) {
        return new PaperHostOptions(agentJar, artefactBase, artefactGlobs, eulaAccepted, bootTimeout, agentTimeout,
                applyGameRules, debugPorts, cellNetwork);
    }
}
