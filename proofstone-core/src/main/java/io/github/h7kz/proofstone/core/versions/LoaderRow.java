package io.github.h7kz.proofstone.core.versions;

import java.util.List;

/**
 * One loader row (ADR-0017 section 3). Pins that are still {@code TODO} in the file are {@code null} here.
 *
 * @param mc Minecraft version
 * @param loader the loader version (NeoForge: the installer version too)
 * @param installer Fabric: the installer version the launcher jar is built for; {@code null} on NeoForge
 * @param sha256 the jar the host copies in (Fabric: the server launcher, NeoForge: the installer), or {@code null}
 * @param vanillaSha256 the vanilla server jar the loader fetches at install, or {@code null} while {@code TODO}
 * @param fabricApi the pinned Fabric API every Fabric row carries; {@code null} on NeoForge
 * @param java Java image override, or {@code null} when the {@code java-rules} decide
 * @param protocol protocol number, or {@code null} when not recorded
 * @param status {@code required}, {@code experimental} or {@code retired}
 * @param nativeDrivers drivers that speak this version natively
 * @param observed free-text last observation
 * @param notes free-text notes
 */
public record LoaderRow(String mc, String loader, String installer, String sha256, String vanillaSha256,
        FabricApi fabricApi, String java, Integer protocol, Status status, List<String> nativeDrivers,
        String observed, String notes) {

    /**
     * The Fabric API pin of a Fabric row (ADR-0017 section 5).
     *
     * @param version the Fabric API version, for example {@code 0.166.2+26.3}
     * @param sha256 the jar checksum, or {@code null} while {@code TODO}
     */
    public record FabricApi(String version, String sha256) {
    }
}
