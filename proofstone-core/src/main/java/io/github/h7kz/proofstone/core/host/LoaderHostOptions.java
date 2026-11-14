package io.github.h7kz.proofstone.core.host;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Inputs of one {@link LoaderHost}.
 *
 * @param agentJar the shaded mod agent jar of the cell's loader
 * @param artefactBase directory the mod globs are relative to
 * @param modGlobs mod jars under test, copied into {@code /mods} next to the agent
 * @param eulaAccepted whether the user accepted the Minecraft EULA ({@link Eula#accepted()})
 * @param bootTimeout how long to wait for {@code Done (} in the log, a host finding when it passes. Never raised to hide a
 *     slow start (ADR-0017, M8 flake rule 4); the measured boot time is {@link LoaderHost#bootTime()}.
 * @param agentTimeout how long to wait for agent health once the server is done
 * @param cacheDir where the pinned downloads are kept between runs, each reused only when its sha256 matches
 * @param files container path (under {@code /config/}) to content, copied in before start owned by the server user
 */
public record LoaderHostOptions(
        Path agentJar,
        Path artefactBase,
        List<String> modGlobs,
        boolean eulaAccepted,
        Duration bootTimeout,
        Duration agentTimeout,
        Path cacheDir,
        Map<String, String> files) {

    /** The T-M8-05 and T-M8-06 boot timeout; a cold start with an install took about 20 s there. */
    public static final Duration BOOT_TIMEOUT = Duration.ofMinutes(5);

    public LoaderHostOptions {
        modGlobs = List.copyOf(modGlobs);
        files = Map.copyOf(files);
    }

    public static LoaderHostOptions of(Path agentJar, boolean eulaAccepted, Path cacheDir) {
        return new LoaderHostOptions(agentJar, agentJar.toAbsolutePath().getParent(), List.of(), eulaAccepted,
                BOOT_TIMEOUT, Duration.ofSeconds(30), cacheDir, Map.of());
    }

    public LoaderHostOptions withMods(Path base, List<String> globs) {
        return new LoaderHostOptions(agentJar, base, globs, eulaAccepted, bootTimeout, agentTimeout, cacheDir, files);
    }

    public LoaderHostOptions withFiles(Map<String, String> containerFiles) {
        return new LoaderHostOptions(agentJar, artefactBase, modGlobs, eulaAccepted, bootTimeout, agentTimeout, cacheDir,
                containerFiles);
    }

    public LoaderHostOptions withAgentTimeout(Duration timeout) {
        return new LoaderHostOptions(agentJar, artefactBase, modGlobs, eulaAccepted, bootTimeout, timeout, cacheDir, files);
    }

    public LoaderHostOptions withBootTimeout(Duration timeout) {
        return new LoaderHostOptions(agentJar, artefactBase, modGlobs, eulaAccepted, timeout, agentTimeout, cacheDir, files);
    }

    /** The files are not in the text: a config file may hold a secret. */
    @Override
    public String toString() {
        return "LoaderHostOptions[agentJar=" + agentJar + ", mods=" + modGlobs + ", files=" + files.keySet() + "]";
    }
}
