package io.github.h7kz.proofstone.core.host;

import io.github.h7kz.proofstone.core.cell.LoaderCell;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * What a loader cell needs, as plain values: the environment of the container, the pinned downloads, the copy list and
 * the hosts an install may reach (ADR-0017 sections 3 and 6). No Docker here, so each of it is a unit test.
 */
final class LoaderEnvironment {

    /** Where the pinned loader jar is copied. The image's installer reads it from here, never from the network. */
    static final String PINS_DIR = "/pins/";
    /** The image syncs {@code /mods} into {@code /data/mods} as the server user. */
    static final String MODS_DIR = "/mods/";
    /** The image syncs {@code /config} into {@code /data/config}; a copy straight into {@code /data/config} is not synced. */
    static final String CONFIG_DIR = "/config/";
    static final String AGENT_JAR_NAME = "proofstone-agent.jar";

    /**
     * Hosts the harness JVM downloads the pins from, before the copy (sha256 checked there). Not the container.
     */
    static final Set<String> HARNESS_DOWNLOAD_HOSTS = Set.of("meta.fabricmc.net", "maven.fabricmc.net",
            "maven.neoforged.net");

    /**
     * ADR-0017 section 6: the hosts a cold start's install fetches from inside the container, the one listed exception
     * to "no Mojang dependency in runs". Measured on a cold start whose only route out was a logging proxy
     * (scripts/spike/install-hosts.sh; docs/platforms/fabric.md and neoforge.md, "Install hosts"). A host not on the list is a finding, never a silent addition.
     */
    static final Set<String> FABRIC_INSTALL_HOSTS = Set.of("launchermeta.mojang.com", "maven.fabricmc.net",
            "meta.fabricmc.net", "piston-data.mojang.com", "piston-meta.mojang.com");
    static final Set<String> NEOFORGE_INSTALL_HOSTS = Set.of("libraries.minecraft.net", "maven.neoforged.net",
            "mirrors.neoforged.net", "neoforged.net", "piston-data.mojang.com", "piston-meta.mojang.com");

    private LoaderEnvironment() {
    }

    /** One file the harness downloads, checks and copies in. */
    record Download(String name, String url, String sha256, String containerPath) {
    }

    /** A download that is on the host now. */
    record PinnedFile(Download download, Path file) {
    }

    /** {@code FABRIC} or {@code NEOFORGE}, the itzg {@code TYPE}. */
    static String type(LoaderCell cell) {
        return switch (cell.loader()) {
            case "fabric", "neoforge" -> cell.loader().toUpperCase(Locale.ROOT);
            default -> throw new PaperHostException("no loader host for loader \"" + cell.loader() + "\" in "
                    + cell.identity());
        };
    }

    /**
     * The container environment. Offline mode, so no session server is called (ADR-0017 section 6); the loader and the
     * Fabric API come from the copies, so the image fetches no loader of its own choosing.
     */
    static Map<String, String> env(LoaderCell cell, String token, int agentPort) {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("EULA", "TRUE");
        env.put("TYPE", type(cell));
        env.put("VERSION", cell.mc());
        Download loader = downloads(cell).get(0);
        env.put("fabric".equals(cell.loader()) ? "FABRIC_LAUNCHER" : "NEOFORGE_INSTALLER", loader.containerPath());
        env.put("ONLINE_MODE", "FALSE");
        env.put("ENFORCE_SECURE_PROFILE", "FALSE");
        env.put("LEVEL_TYPE", PaperHost.levelType(cell.mc()));
        env.put("SEED", PaperHost.SEED);
        env.put("SPAWN_PROTECTION", "0");
        env.put("DIFFICULTY", "peaceful");
        env.put("VIEW_DISTANCE", "4");
        env.put("ENABLE_RCON", "false");
        env.put("PROOFSTONE_AGENT_TOKEN", token);
        env.put("PROOFSTONE_AGENT_PORT", Integer.toString(agentPort));
        return env;
    }

    /**
     * The pinned downloads, loader first. A Fabric API pin that is {@code TODO} in {@code versions.yml} (null on the cell)
     * fails the cell loudly naming it; the planner already refused a TODO loader sha256.
     */
    static List<Download> downloads(LoaderCell cell) {
        List<Download> list = new ArrayList<>();
        if ("fabric".equals(cell.loader())) {
            String name = "fabric-server-mc." + cell.mc() + "-loader." + cell.loaderVersion() + "-launcher."
                    + cell.installer() + ".jar";
            list.add(new Download(name, "https://meta.fabricmc.net/v2/versions/loader/" + cell.mc() + "/"
                    + cell.loaderVersion() + "/" + cell.installer() + "/server/jar", cell.sha256(), PINS_DIR + name));
            if (cell.fabricApi() == null || cell.fabricApi().sha256() == null) {
                throw new PaperHostException(cell.identity() + ": the Fabric API sha256 is TODO in versions.yml. A Fabric "
                        + "cell never runs an unpinned Fabric API (ADR-0017 section 5); pin it from a real download.");
            }
            String version = cell.fabricApi().version();
            String escaped = version.replace("+", "%2B");
            String apiName = "fabric-api-" + version + ".jar";
            list.add(new Download(apiName, "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/" + escaped
                    + "/fabric-api-" + escaped + ".jar", cell.fabricApi().sha256(), MODS_DIR + apiName));
        } else if ("neoforge".equals(cell.loader())) {
            String name = "neoforge-" + cell.loaderVersion() + "-installer.jar";
            list.add(new Download(name, "https://maven.neoforged.net/releases/net/neoforged/neoforge/"
                    + cell.loaderVersion() + "/" + name, cell.sha256(), PINS_DIR + name));
        } else {
            type(cell);
        }
        return List.copyOf(list);
    }

    /**
     * Every jar copied in, by container path: the pinned downloads, the agent, then the mods under test. Two files with the
     * same destination are an error naming both, not a silent overwrite.
     */
    static Map<String, Path> jarCopies(List<PinnedFile> pins, Path agentJar, List<Path> mods) {
        Map<String, Path> copies = new LinkedHashMap<>();
        pins.forEach(pin -> put(copies, pin.download().containerPath(), pin.file()));
        put(copies, MODS_DIR + AGENT_JAR_NAME, agentJar);
        mods.forEach(mod -> put(copies, MODS_DIR + mod.getFileName(), mod));
        return copies;
    }

    private static void put(Map<String, Path> copies, String containerPath, Path file) {
        Path earlier = copies.put(containerPath, file);
        if (earlier != null) {
            throw new PaperHostException("two files are copied to " + containerPath + ": " + earlier + " and " + file);
        }
    }

    /**
     * Config files, by container path. They must sit under {@code /config/}: the image syncs that to {@code /data/config}
     * as the server user, and a path under {@code /data/config} is never synced (T-M8-02). Owned by uid and gid 1000.
     */
    static Map<String, ServerFile> configCopies(Map<String, String> files) {
        Map<String, ServerFile> copies = new LinkedHashMap<>();
        files.forEach((path, content) -> {
            if (!path.startsWith(CONFIG_DIR) || path.contains("..")) {
                throw new PaperHostException("config file " + path + " must be under " + CONFIG_DIR
                        + " (the image syncs it to /data/config), not a path of the host's choosing");
            }
            copies.put(path, ServerFile.of(content));
        });
        return copies;
    }

    /** Where the vanilla server jar sits in the container after an install (spike export, T-M8-01). */
    static String vanillaJarPath(LoaderCell cell) {
        return "fabric".equals(cell.loader())
                ? "/data/.fabric/server/" + cell.mc() + "-server.jar"
                : "/data/libraries/net/minecraft/server/" + cell.mc() + "/server-" + cell.mc() + ".jar";
    }

    /** The log lines that mean the loader gave up, so the boot has failed now and not at the timeout (ADR-0017 section 5). */
    static List<String> bootFailureMarkers(LoaderCell cell) {
        return switch (type(cell)) {
            case "FABRIC" -> List.of("Failed to start the minecraft server");
            default -> List.of("Failed to start FML");
        };
    }

    /** The first line of {@code log} that is a boot failure for this loader, or {@code null}. */
    static String bootFailure(LoaderCell cell, String log) {
        List<String> markers = bootFailureMarkers(cell);
        for (String line : log.split("\\R")) {
            for (String marker : markers) {
                if (line.contains(marker)) {
                    return line.strip();
                }
            }
        }
        return null;
    }

    /** The hosts the container's install may reach (ADR-0017 section 6), sorted. */
    static Set<String> installHosts(LoaderCell cell) {
        return new TreeSet<>("fabric".equals(cell.loader()) ? FABRIC_INSTALL_HOSTS : NEOFORGE_INSTALL_HOSTS);
    }
}
