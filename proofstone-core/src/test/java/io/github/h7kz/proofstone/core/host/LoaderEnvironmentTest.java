package io.github.h7kz.proofstone.core.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.core.cell.CellPlanner;
import io.github.h7kz.proofstone.core.cell.LoaderCell;
import io.github.h7kz.proofstone.core.cell.Scope;
import io.github.h7kz.proofstone.core.host.LoaderEnvironment.Download;
import io.github.h7kz.proofstone.core.host.LoaderEnvironment.PinnedFile;
import io.github.h7kz.proofstone.core.versions.Status;
import io.github.h7kz.proofstone.core.versions.VersionsCatalog;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The rendered environment, pins, copy list and install hosts of a loader cell: no Docker (ADR-0017 sections 3 and 6). */
class LoaderEnvironmentTest {

    private static final Path ROOT = Path.of(System.getProperty("proofstone.repoRoot"));
    private static final VersionsCatalog CATALOG = VersionsCatalog.load(ROOT.resolve("versions.yml"),
            ROOT.resolve("proofstone-protocol/schemas/versions.schema.json"));
    private static final LoaderCell FABRIC = cell("fabric");
    private static final LoaderCell NEOFORGE = cell("neoforge");
    private static final String AGENT_PORT_VALUE = "25580";

    private static LoaderCell cell(String loader) {
        return new CellPlanner(CATALOG).planLoaders(loader, List.of("26.3"), Scope.NIGHTLY).get(0);
    }

    @Test
    void theFabricEnvironmentIsOfflineAndReadsTheLauncherFromTheCopy() {
        Map<String, String> env = LoaderEnvironment.env(FABRIC, "tok", 25580);

        assertThat(env).containsEntry("TYPE", "FABRIC")
                .containsEntry("VERSION", "26.3")
                .containsEntry("ONLINE_MODE", "FALSE")
                .containsEntry("ENFORCE_SECURE_PROFILE", "FALSE")
                .containsEntry("LEVEL_TYPE", "minecraft:flat")
                .containsEntry("EULA", "TRUE")
                .containsEntry("PROOFSTONE_AGENT_TOKEN", "tok")
                .containsEntry("PROOFSTONE_AGENT_PORT", AGENT_PORT_VALUE)
                .containsEntry("FABRIC_LAUNCHER",
                        "/pins/fabric-server-mc.26.3-loader.0.20.1-launcher.1.1.2.jar")
                .doesNotContainKey("NEOFORGE_INSTALLER");
    }

    @Test
    void theNeoForgeEnvironmentReadsTheInstallerFromTheCopy() {
        Map<String, String> env = LoaderEnvironment.env(NEOFORGE, "tok", 25580);

        assertThat(env).containsEntry("TYPE", "NEOFORGE")
                .containsEntry("ONLINE_MODE", "FALSE")
                .containsEntry("NEOFORGE_INSTALLER", "/pins/neoforge-26.3.0.103-installer.jar")
                .doesNotContainKey("FABRIC_LAUNCHER");
    }

    @Test
    void everyLoaderCellPinsTheSameWorldSeedAsPaper() {
        // T-M8-21: the same world every boot, so a zombie village near spawn cannot reach the log guard. 1234567 has its
        // nearest village 353 blocks from spawn on Fabric; Paper has run with it since M1.
        for (LoaderCell cell : List.of(FABRIC, NEOFORGE)) {
            assertThat(LoaderEnvironment.env(cell, "t", 1)).containsEntry("SEED", PaperHost.SEED);
        }
        assertThat(PaperHost.SEED).isEqualTo("1234567");
    }

    @Test
    void noEnvironmentValueIsAnUnpinnedVersionOrLatest() {
        for (LoaderCell cell : List.of(FABRIC, NEOFORGE)) {
            assertThat(LoaderEnvironment.env(cell, "t", 1).values()).noneMatch(value -> value.contains("latest")
                    || value.contains("+"));
        }
    }

    @Test
    void theFabricPinsAreTheLauncherAndFabricApiWithTheirSha256FromVersionsYml() {
        List<Download> downloads = LoaderEnvironment.downloads(FABRIC);

        assertThat(downloads).hasSize(2);
        assertThat(downloads.get(0).url()).isEqualTo(
                "https://meta.fabricmc.net/v2/versions/loader/26.3/0.20.1/1.1.2/server/jar");
        assertThat(downloads.get(0).sha256()).isEqualTo(FABRIC.sha256()).hasSize(64);
        assertThat(downloads.get(0).containerPath()).startsWith("/pins/");
        assertThat(downloads.get(1).url()).isEqualTo("https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/"
                + "0.166.2%2B26.3/fabric-api-0.166.2%2B26.3.jar");
        assertThat(downloads.get(1).sha256()).isEqualTo(FABRIC.fabricApi().sha256()).hasSize(64);
        assertThat(downloads.get(1).containerPath()).isEqualTo("/mods/fabric-api-0.166.2+26.3.jar");
    }

    @Test
    void theNeoForgePinIsTheInstallerOnly() {
        List<Download> downloads = LoaderEnvironment.downloads(NEOFORGE);

        assertThat(downloads).hasSize(1);
        assertThat(downloads.get(0).url()).isEqualTo("https://maven.neoforged.net/releases/net/neoforged/neoforge/"
                + "26.3.0.103/neoforge-26.3.0.103-installer.jar");
        assertThat(downloads.get(0).sha256()).isEqualTo(NEOFORGE.sha256());
    }

    @Test
    void everyRowPinsTheVanillaJarWithAFullSha256() {
        assertThat(FABRIC.vanillaSha256()).matches("[0-9a-f]{64}");
        assertThat(NEOFORGE.vanillaSha256()).matches("[0-9a-f]{64}");
    }

    @Test
    void aFabricApiPinThatIsTodoFailsLoudlyNamingIt() {
        LoaderCell unpinned = withFabricApi(new LoaderCell.FabricApi("0.166.2+26.3", null));

        assertThatThrownBy(() -> LoaderEnvironment.downloads(unpinned)).isInstanceOf(PaperHostException.class)
                .hasMessageContaining("fabric@26.3").hasMessageContaining("Fabric API sha256 is TODO");
    }

    @Test
    void theCopyListHoldsTheLoaderFabricApiAgentAndModsAndNothingElse() {
        List<PinnedFile> pins = LoaderEnvironment.downloads(FABRIC).stream()
                .map(download -> new PinnedFile(download, Path.of("cache", download.name()))).toList();

        Map<String, Path> copies = LoaderEnvironment.jarCopies(pins, Path.of("agent-all.jar"),
                List.of(Path.of("libs", "example-mod.jar")));

        assertThat(copies.keySet()).containsExactly(
                "/pins/fabric-server-mc.26.3-loader.0.20.1-launcher.1.1.2.jar",
                "/mods/fabric-api-0.166.2+26.3.jar",
                "/mods/proofstone-agent.jar",
                "/mods/example-mod.jar");
        assertThat(copies.get("/mods/proofstone-agent.jar")).isEqualTo(Path.of("agent-all.jar"));
    }

    @Test
    void theNeoForgeCopyListHasNoFabricApi() {
        List<PinnedFile> pins = LoaderEnvironment.downloads(NEOFORGE).stream()
                .map(download -> new PinnedFile(download, Path.of("cache", download.name()))).toList();

        assertThat(LoaderEnvironment.jarCopies(pins, Path.of("agent-all.jar"), List.of()).keySet()).containsExactly(
                "/pins/neoforge-26.3.0.103-installer.jar", "/mods/proofstone-agent.jar");
    }

    @Test
    void twoFilesForOneDestinationAreAnErrorNamingBoth() {
        assertThatThrownBy(() -> LoaderEnvironment.jarCopies(List.of(), Path.of("agent-all.jar"),
                List.of(Path.of("a", "proofstone-agent.jar"))))
                .isInstanceOf(PaperHostException.class).hasMessageContaining("/mods/proofstone-agent.jar")
                .hasMessageContaining("agent-all.jar");
    }

    @Test
    void configFilesAreUnderConfigNotDataConfigAndOwnedByTheServerUser() {
        Map<String, ServerFile> copies = LoaderEnvironment.configCopies(Map.of("/config/example.properties", "a=b\n"));

        assertThat(copies).containsOnlyKeys("/config/example.properties");
        assertThat(copies.get("/config/example.properties").getBytes()).isEqualTo("a=b\n".getBytes());
        assertThatThrownBy(() -> LoaderEnvironment.configCopies(Map.of("/data/config/x.properties", "a")))
                .isInstanceOf(PaperHostException.class).hasMessageContaining("/data/config/x.properties");
        assertThatThrownBy(() -> LoaderEnvironment.configCopies(Map.of("/config/../etc/x", "a")))
                .isInstanceOf(PaperHostException.class);
        assertThat(ServerFile.SERVER_ID).isEqualTo(1000);
    }

    @Test
    void theVanillaJarIsLookedForWhereEachInstallPutsIt() {
        assertThat(LoaderEnvironment.vanillaJarPath(FABRIC)).isEqualTo("/data/.fabric/server/26.3-server.jar");
        assertThat(LoaderEnvironment.vanillaJarPath(NEOFORGE))
                .isEqualTo("/data/libraries/net/minecraft/server/26.3/server-26.3.jar");
    }

    @Test
    void aLoadersGiveUpLineIsABootFailureAtOnce() {
        String fabric = "[12:00:01] [main/INFO]: starting\n[12:00:02] [main/ERROR]: Failed to start the minecraft server\n";
        String neoforge = "[12:00:01] [main/ERROR]: Failed to start FML: boom\n";

        assertThat(LoaderEnvironment.bootFailure(FABRIC, fabric)).contains("Failed to start the minecraft server");
        assertThat(LoaderEnvironment.bootFailure(NEOFORGE, neoforge)).contains("Failed to start FML");
        // Each loader reads its own marker: the other's line is not its failure.
        assertThat(LoaderEnvironment.bootFailure(FABRIC, neoforge)).isNull();
        assertThat(LoaderEnvironment.bootFailure(NEOFORGE, fabric)).isNull();
        assertThat(LoaderEnvironment.bootFailure(FABRIC, "Done (3.1s)! For help, type \"help\"")).isNull();
    }

    @Test
    void theInstallMayReachOnlyTheListedMojangAndMavenHosts() {
        // ADR-0017 section 6. The list is measured (docs/platforms/fabric.md and neoforge.md, "Install hosts"); a new host
        // is a finding that changes this test, never a silent addition.
        assertThat(LoaderEnvironment.installHosts(FABRIC)).containsExactlyInAnyOrder(
                "launchermeta.mojang.com", "maven.fabricmc.net", "meta.fabricmc.net", "piston-data.mojang.com",
                "piston-meta.mojang.com");
        assertThat(LoaderEnvironment.installHosts(NEOFORGE)).containsExactlyInAnyOrder(
                "libraries.minecraft.net", "maven.neoforged.net", "mirrors.neoforged.net", "neoforged.net",
                "piston-data.mojang.com", "piston-meta.mojang.com");
        assertThat(LoaderEnvironment.HARNESS_DOWNLOAD_HOSTS).containsExactlyInAnyOrder("meta.fabricmc.net",
                "maven.fabricmc.net", "maven.neoforged.net");
    }

    @Test
    void everyDownloadUrlIsOnAHarnessDownloadHost() {
        for (LoaderCell cell : List.of(FABRIC, NEOFORGE)) {
            for (Download download : LoaderEnvironment.downloads(cell)) {
                assertThat(LoaderEnvironment.HARNESS_DOWNLOAD_HOSTS).contains(java.net.URI.create(download.url()).getHost());
            }
        }
    }

    private static LoaderCell withFabricApi(LoaderCell.FabricApi api) {
        return new LoaderCell(FABRIC.identity(), FABRIC.loader(), FABRIC.mc(), FABRIC.loaderVersion(), FABRIC.installer(),
                FABRIC.sha256(), FABRIC.vanillaSha256(), api, FABRIC.java(), Status.EXPERIMENTAL, FABRIC.hostImage(),
                FABRIC.hostDigest(), List.of());
    }
}
