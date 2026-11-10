package io.github.h7kz.proofstone.core.host;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
 * @param network the cell network the server joins as {@code networkAlias}, or {@code null} for the
 *     default one; set when a ViaProxy container must reach the server by name (T-M3-03)
 * @param networkAlias the server's name on {@code network}: {@link PaperHost#NETWORK_ALIAS}, or
 *     {@code server-<name>} for a topology backend (ADR-0016 section 1)
 * @param files container path to content, copied in before start (a topology backend's forwarding settings,
 *     TopologyConfig); empty for a single server. The content may hold a secret, so it is never logged.
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
        Network network,
        String networkAlias,
        Map<String, String> files) {

    public PaperHostOptions {
        files = Map.copyOf(files);
    }

    public static PaperHostOptions of(Path agentJar, boolean eulaAccepted) {
        return new PaperHostOptions(agentJar, agentJar.toAbsolutePath().getParent(), List.of(), eulaAccepted,
                Duration.ofMinutes(3), Duration.ofSeconds(30), true, false, null, PaperHost.NETWORK_ALIAS, Map.of());
    }

    public PaperHostOptions withArtefacts(Path base, List<String> globs) {
        return new PaperHostOptions(agentJar, base, globs, eulaAccepted, bootTimeout, agentTimeout,
                applyGameRules, debugPorts, network, networkAlias, files);
    }

    public PaperHostOptions withApplyGameRules(boolean apply) {
        return new PaperHostOptions(agentJar, artefactBase, artefactGlobs, eulaAccepted, bootTimeout,
                agentTimeout, apply, debugPorts, network, networkAlias, files);
    }

    public PaperHostOptions withDebugPorts(boolean debug) {
        return new PaperHostOptions(agentJar, artefactBase, artefactGlobs, eulaAccepted, bootTimeout, agentTimeout,
                applyGameRules, debug, network, networkAlias, files);
    }

    public PaperHostOptions withAgentTimeout(Duration timeout) {
        return new PaperHostOptions(agentJar, artefactBase, artefactGlobs, eulaAccepted, bootTimeout, timeout,
                applyGameRules, debugPorts, network, networkAlias, files);
    }

    public PaperHostOptions withBootTimeout(Duration timeout) {
        return new PaperHostOptions(agentJar, artefactBase, artefactGlobs, eulaAccepted, timeout, agentTimeout,
                applyGameRules, debugPorts, network, networkAlias, files);
    }

    public PaperHostOptions withNetwork(Network cellNetwork) {
        return withNetwork(cellNetwork, PaperHost.NETWORK_ALIAS);
    }

    /** Joins {@code cellNetwork} under {@code alias} (a topology backend's {@code server-<name>}). */
    public PaperHostOptions withNetwork(Network cellNetwork, String alias) {
        return new PaperHostOptions(agentJar, artefactBase, artefactGlobs, eulaAccepted, bootTimeout, agentTimeout,
                applyGameRules, debugPorts, cellNetwork, alias, files);
    }

    /** Copies {@code containerFiles} (path to content) in before start. */
    public PaperHostOptions withFiles(Map<String, String> containerFiles) {
        return new PaperHostOptions(agentJar, artefactBase, artefactGlobs, eulaAccepted, bootTimeout, agentTimeout,
                applyGameRules, debugPorts, network, networkAlias, containerFiles);
    }

    /** The files are not in the text: a topology backend's hold the forwarding secret (ADR-0016 section 8). */
    @Override
    public String toString() {
        return "PaperHostOptions[agentJar=" + agentJar + ", artefacts=" + artefactGlobs + ", network=" + network
                + ", networkAlias=" + networkAlias + ", files=" + files.keySet() + "]";
    }
}
