package io.github.h7kz.proofstone.core.cell;

import io.github.h7kz.proofstone.core.versions.Status;
import java.util.List;

/**
 * One loader cell (glossary: Cell, ADR-0017 section 2), resolved from {@code versions.yml} schema 4: a single server on a
 * Fabric or NeoForge row. Single-server Paper-family code never sees this type: {@link Cell} is unchanged.
 *
 * <p>The planner refuses a {@code TODO} in {@code sha256}, the one pin it needs to name the jar. A {@code TODO} in
 * {@code vanillaSha256} or the Fabric API checksum stays {@code null} here, and the host fails the cell loudly at that
 * check (ADR-0017 section 3), never skips it.
 *
 * @param identity {@code <loader>@<mc>}, used unchanged for the selector, the matrix, the report, {@code cell.json} and
 *     the bundle directory; it never contains {@code +}
 * @param loader loader id, {@code fabric} or {@code neoforge}
 * @param mc Minecraft version
 * @param loaderVersion the loader version (NeoForge: the installer version)
 * @param installer Fabric: the installer version of the launcher jar; {@code null} on NeoForge
 * @param sha256 the jar the host copies in (Fabric launcher, NeoForge installer)
 * @param vanillaSha256 the vanilla server jar's checksum, or {@code null} while {@code TODO}
 * @param fabricApi the pinned Fabric API of a Fabric row, or {@code null} on NeoForge
 * @param java Java image, for example {@code java25}
 * @param status row status
 * @param hostImage host image repository
 * @param hostDigest host image digest for {@code java}
 * @param notes warnings
 */
public record LoaderCell(
        String identity,
        String loader,
        String mc,
        String loaderVersion,
        String installer,
        String sha256,
        String vanillaSha256,
        FabricApi fabricApi,
        String java,
        Status status,
        String hostImage,
        String hostDigest,
        List<String> notes) {

    public LoaderCell {
        notes = List.copyOf(notes);
    }

    /**
     * The Fabric API pin of a Fabric cell.
     *
     * @param version the Fabric API version
     * @param sha256 the jar checksum, or {@code null} while {@code TODO}
     */
    public record FabricApi(String version, String sha256) {
    }

    /** {@code <loader>@<mc>} (ADR-0017 section 2). */
    public static String identity(String loader, String mc) {
        return loader + "@" + mc;
    }
}
