package io.github.h7kz.proofstone.core.versions;

import java.util.List;

/**
 * One Geyser row of {@code versions.yml} schema 5 (ADR-0018 section 8). Pins that are still {@code TODO} in the file are
 * {@code null} here.
 *
 * @param version Geyser's own version as a string; the cell identity uses it
 * @param build the {@code download.geysermc.org/v2} build, or {@code null} while {@code TODO}
 * @param sha256 Geyser-Spigot jar checksum, or {@code null} while {@code TODO}
 * @param floodgate the Floodgate build this Geyser build is run with
 * @param bedrockProtocol the Bedrock protocol the driver and this build are run with
 * @param java Java tag of the stand-in and the driver host; required, its digest is the backend platform's
 * @param status {@code required}, {@code experimental} or {@code retired}
 * @param backends the Paper rows this build is paired with, each with its own status
 * @param observed free-text last observation
 * @param notes free-text notes
 */
public record GeyserRow(String version, Integer build, String sha256, Floodgate floodgate, int bedrockProtocol,
        String java, Status status, List<Pairing> backends, String observed, String notes) {

    /**
     * The Floodgate pin of a Geyser row.
     *
     * @param version Floodgate's version
     * @param build the download build, or {@code null} while {@code TODO}
     * @param sha256 jar checksum, or {@code null} while {@code TODO}
     */
    public record Floodgate(String version, Integer build, String sha256) {
    }

    /**
     * One backend row a Geyser row is paired with.
     *
     * @param platform backend platform id; {@code paper} only in M9
     * @param mc the backend row's Minecraft version
     * @param status the pairing's own status
     */
    public record Pairing(String platform, String mc, Status status) {
    }
}
