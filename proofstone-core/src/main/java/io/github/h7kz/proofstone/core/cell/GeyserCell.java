package io.github.h7kz.proofstone.core.cell;

import io.github.h7kz.proofstone.core.versions.Status;
import java.util.List;

/**
 * One Geyser cell (glossary: Cell, ADR-0018 section 8), resolved from {@code versions.yml} schema 5: a Paper server with
 * Geyser and Floodgate, a Java client and a Bedrock one. It is not a {@link TopologyCell}: there is no proxy and no
 * backend list. Nothing that reads {@link Cell}, {@link TopologyCell} or {@link LoaderCell} sees it.
 *
 * <p>The planner refuses a {@code TODO} in a jar pin it needs to name a download, so the {@code null}-able fields of the
 * row are non-null here.
 *
 * @param identity {@code geyser@<version>+<backend>@<mc>}, used unchanged for the selector, the matrix, the report,
 *     {@code cell.json} and the bundle directory
 * @param version Geyser's version
 * @param build the Geyser download build
 * @param sha256 the Geyser-Spigot jar checksum
 * @param floodgate the Floodgate pin
 * @param bedrockProtocol the Bedrock protocol the cell is run with
 * @param java Java image of the stand-in and the driver host, for example {@code java25}
 * @param status the weakest of Geyser row, pairing and backend row
 * @param pairingStatus the pairing's own status
 * @param hostImage host image repository (the backend platform's)
 * @param hostDigest host image digest for {@code java}
 * @param backend the Paper cell the server runs
 * @param notes warnings
 */
public record GeyserCell(
        String identity,
        String version,
        int build,
        String sha256,
        Floodgate floodgate,
        int bedrockProtocol,
        String java,
        Status status,
        Status pairingStatus,
        String hostImage,
        String hostDigest,
        Cell backend,
        List<String> notes) {

    /** What {@code cell.json} says about Xbox authentication: it is off on every Geyser cell (ADR-0018 section 4). */
    public static final String BEDROCK_AUTH = "offline-stub";

    /** The reserved id left of the {@code +} (ADR-0018 section 8). */
    public static final String ID = "geyser";

    public GeyserCell {
        notes = List.copyOf(notes);
    }

    /**
     * The Floodgate pin of a Geyser cell.
     *
     * @param version Floodgate's version
     * @param build the download build
     * @param sha256 the jar checksum
     */
    public record Floodgate(String version, int build, String sha256) {
    }

    /** {@code "offline-stub"}: written into {@code cell.json}, the report and the display name, so no reader mistakes the cell for production auth. */
    @com.fasterxml.jackson.annotation.JsonProperty("bedrockAuth")
    public String bedrockAuth() {
        return BEDROCK_AUTH;
    }

    /** {@code geyser@<version>+<backend>@<mc>}. */
    public static String identity(String version, String backendPlatform, String backendMc) {
        return ID + "@" + version + "+" + backendPlatform + "@" + backendMc;
    }
}
