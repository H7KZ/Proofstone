package io.github.h7kz.proofstone.core.host;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Inputs of one {@link GeyserHost}.
 *
 * @param driverHostJar the shaded Bedrock driver host jar ({@code :drivers:bedrock:shadowJar})
 * @param downloads where the pinned Geyser and Floodgate jars are cached after their sha256 is checked; never committed
 * @param auxTimeout how long each of the stand-in, Geyser's ready line, the {@code floodgate} token and the driver host
 *     may take to become ready
 * @param fetcher where jar bytes come from; the official download API unless a test replaces it
 * @param transport how Bedrock clients reach Geyser: {@code both} (the driver joins over RakNet, the default) or {@code nethernet} (ADR-0021)
 */
public record GeyserHostOptions(Path driverHostJar, Path downloads, Duration auxTimeout, GeyserJars.Fetcher fetcher,
        GeyserTransport transport) {

    public static GeyserHostOptions of(Path driverHostJar) {
        return new GeyserHostOptions(driverHostJar, Path.of("build", "proofstone", "downloads"), Duration.ofSeconds(60),
                GeyserJars.http(), GeyserTransport.fromSystemProperty());
    }

    public GeyserHostOptions withDownloads(Path directory) {
        return new GeyserHostOptions(driverHostJar, directory, auxTimeout, fetcher, transport);
    }

    public GeyserHostOptions withTransport(GeyserTransport value) {
        return new GeyserHostOptions(driverHostJar, downloads, auxTimeout, fetcher, value);
    }
}
