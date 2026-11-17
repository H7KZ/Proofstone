package io.github.h7kz.proofstone.core.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.core.cell.Cell;
import io.github.h7kz.proofstone.core.cell.GeyserCell;
import io.github.h7kz.proofstone.core.host.standin.StandIn;
import io.github.h7kz.proofstone.core.versions.Status;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;

/** Rendered config, pins, key redaction, copy list and name guard of a Geyser cell (T-M9-06). No Docker. */
class GeyserHostUnitTest {

    @TempDir
    Path dir;

    private static final String SHA = "ab".repeat(32);

    private static GeyserCell cell() {
        Cell backend = new Cell("paper", "26.3", 244, SHA, "java25", Status.EXPERIMENTAL, "itzg/minecraft-server",
                "sha256:" + "cd".repeat(32), List.of());
        return new GeyserCell("geyser@2.12.1+paper@26.3", "2.12.1", 1303, SHA,
                new GeyserCell.Floodgate("2.2.5", 141, SHA), 2225, "java25", Status.EXPERIMENTAL, Status.EXPERIMENTAL,
                "itzg/minecraft-server", "sha256:" + "cd".repeat(32), backend, List.of());
    }

    // ---- rendered config ----

    @Test
    void renderChangesExactlyTheFiveKeysOfThePinnedDefault() throws IOException {
        String template = new String(GeyserConfig.class.getResourceAsStream("geyser-2.12.1-b1303-config.yml").readAllBytes());
        List<String> before = template.lines().toList();
        List<String> after = GeyserConfig.render("2.12.1", 1303).lines().toList();
        assertThat(after).hasSameSizeAs(before);
        List<String> changed = new ArrayList<>();
        for (int i = 0; i < before.size(); i++) {
            if (!before.get(i).equals(after.get(i))) {
                changed.add(after.get(i).strip());
            }
        }
        assertThat(changed).containsExactly("webrtc-port: 19133", "transport: both", "stun-discovery: false",
                "auth-type: floodgate", "validate-bedrock-login: false");
    }

    @Test
    void aTemplateWithoutAKeyOrWithAKeyTwiceFailsNamingIt() {
        assertThatThrownBy(() -> GeyserConfig.render("bedrock:\n  transport: x\n", "9", 1))
                .isInstanceOf(PaperHostException.class).hasMessageContaining("bedrock.webrtc-port");
        String twice = "bedrock:\n  transport: a\n  transport: b\n  webrtc-port: 0\n  signaling:\n    stun-discovery: true\n"
                + "java:\n  auth-type: online\nadvanced:\n  bedrock:\n    validate-bedrock-login: true\n";
        assertThatThrownBy(() -> GeyserConfig.render(twice, "9", 1)).hasMessageContaining("twice");
    }

    @Test
    void aBuildWithoutATemplateFailsLoudly() {
        assertThatThrownBy(() -> GeyserConfig.render("2.12.1", 9999)).isInstanceOf(PaperHostException.class)
                .hasMessageContaining("geyser-2.12.1-b9999-config.yml");
    }

    // ---- pins ----

    @Test
    void jarUrlsNameVersionAndBuildAndAMismatchFailsAndLeavesNothing() throws IOException {
        assertThat(GeyserJars.url("geyser", "2.12.1", 1303).toString())
                .isEqualTo("https://download.geysermc.org/v2/projects/geyser/versions/2.12.1/builds/1303/downloads/spigot");
        GeyserJars.Fetcher wrong = uri -> new ByteArrayInputStream(new byte[] {1, 2, 3});
        assertThatThrownBy(() -> GeyserJars.geyser(cell(), dir, wrong)).isInstanceOf(PaperHostException.class)
                .hasMessageContaining("the row pins " + SHA);
        try (var files = Files.list(dir)) {
            assertThat(files.toList()).isEmpty();
        }
    }

    @Test
    void aCheckedCachedJarIsNotFetchedAgainAndAStaleOneIsReplaced() throws IOException {
        byte[] bytes = {9, 9, 9};
        String sha = java.util.HexFormat.of().formatHex(sha(bytes));
        List<URI> asked = new ArrayList<>();
        GeyserJars.Fetcher fetcher = uri -> {
            asked.add(uri);
            return new ByteArrayInputStream(bytes);
        };
        Path first = GeyserJars.fetch(URI.create("https://x/y"), "a.jar", sha, dir, fetcher);
        GeyserJars.fetch(URI.create("https://x/y"), "a.jar", sha, dir, fetcher);
        assertThat(asked).hasSize(1);
        Files.write(first, new byte[] {0});
        GeyserJars.fetch(URI.create("https://x/y"), "a.jar", sha, dir, fetcher);
        assertThat(asked).hasSize(2);
        assertThat(Files.readAllBytes(first)).isEqualTo(bytes);
    }

    private static byte[] sha(byte[] bytes) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- key ----

    @Test
    void theKeyIs16RandomBytesPerCellAndIsRedactedInEveryForm() {
        FloodgateKey key = FloodgateKey.generate();
        assertThat(key.bytes()).hasSize(16).isNotEqualTo(FloodgateKey.generate().bytes());
        assertThat(key.toString()).doesNotContain(key.forms().get(0));
        String text = String.join(" | ", key.forms());
        String redacted = key.redact(text);
        for (String form : key.forms()) {
            assertThat(redacted).doesNotContain(form);
        }
        assertThat(redacted).contains(FloodgateKey.REDACTED);
    }

    // ---- copy list ----

    @Test
    void theServerGetsTheCopyListTheAdrNamesAndNothingIsMountedOrPublished() throws Exception {
        Path agent = Files.write(dir.resolve("agent.jar"), new byte[] {1});
        Path geyser = Files.write(dir.resolve("Geyser-Spigot-2.12.1-b1303.jar"), new byte[] {2});
        Path floodgate = Files.write(dir.resolve("floodgate-spigot-2.2.5-b141.jar"), new byte[] {3});
        PaperHost host = new PaperHost(cell().backend(), PaperHostOptions.of(agent, true));
        host.configure(List.of(), null);
        GenericContainer<?> container = host.container();
        GeyserHost.prepareServer(container, new GeyserHost.ServerInputs(geyser, floodgate,
                GeyserConfig.render("2.12.1", 1303), FloodgateKey.generate(), null, null, new byte[] {4}, "172.18.0.2"));

        List<String> copied = new ArrayList<>(container.getCopyToFileContainerPathMap().values());
        var method = GenericContainer.class.getDeclaredMethod("getCopyToTransferableContainerPathMap");
        method.setAccessible(true);
        copied.addAll(((java.util.Map<?, String>) method.invoke(container)).values());
        assertThat(copied).contains("/plugins/Geyser-Spigot-2.12.1-b1303.jar", "/plugins/floodgate-spigot-2.2.5-b141.jar",
                "/plugins/Geyser-Spigot/config.yml", "/plugins/floodgate/key.pem", "/stub/cacerts", "/data/bukkit.yml");
        assertThat(container.getBinds()).isEmpty();
        assertThat(container.getEnvMap().get("JAVA_TOOL_OPTIONS"))
                .isEqualTo("-Djavax.net.ssl.trustStore=/stub/cacerts -Djavax.net.ssl.trustStorePassword=changeit");
        // Paper's throttle is off in every cell's bukkit.yml (T-M1-10); the server publishes the game port only.
        assertThat(PaperHost.configCopies(java.util.Map.of()).get("/data/bukkit.yml").getBytes())
                .asString().contains("connection-throttle: -1");
        assertThat(container.getExposedPorts()).containsExactly(PaperHost.GAME_PORT);
    }

    @Test
    void theKeyMismatchFaultCopiesADecoyKeyAndAFloodgateConfigThatReadsItAndChangesNothingElse() throws Exception {
        Path agent = Files.write(dir.resolve("agent.jar"), new byte[] {1});
        Path jar = floodgateJarWithConfig();
        PaperHost host = new PaperHost(cell().backend(), PaperHostOptions.of(agent, true));
        host.configure(List.of(), null);
        GenericContainer<?> container = host.container();
        FloodgateKey key = FloodgateKey.generate();
        FloodgateKey decoy = FloodgateKey.generate();
        String config = FloodgateConfig.withKeyFile(jar, GeyserHost.DECOY_KEY_FILE);
        GeyserHost.prepareServer(container, new GeyserHost.ServerInputs(jar, jar, GeyserConfig.render("2.12.1", 1303), key, decoy,
                config, new byte[] {4}, "172.18.0.2"));

        var method = GenericContainer.class.getDeclaredMethod("getCopyToTransferableContainerPathMap");
        method.setAccessible(true);
        assertThat(((java.util.Map<?, String>) method.invoke(container)).values())
                .contains("/plugins/floodgate/key.pem", "/plugins/floodgate/decoy-key.pem", "/plugins/floodgate/config.yml");
        assertThat(key.bytes()).isNotEqualTo(decoy.bytes());
        assertThat(config).contains("key-file-name: decoy-key.pem").doesNotContain("key-file-name: key.pem")
                .doesNotContain("${metrics.uuid}").contains("invalid-key: Please connect through the official Geyser");
    }

    @Test
    void theFloodgateConfigOfTheFaultNeedsTheJarsOwnTemplate() throws IOException {
        Path empty = dir.resolve("empty.jar");
        try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(Files.newOutputStream(empty))) {
            out.putNextEntry(new java.util.zip.ZipEntry("plugin.yml"));
            out.write(1);
            out.closeEntry();
        }
        assertThatThrownBy(() -> FloodgateConfig.withKeyFile(empty, "x.pem")).isInstanceOf(PaperHostException.class)
                .hasMessageContaining("config.yml");
    }

    private Path floodgateJarWithConfig() throws IOException {
        Path jar = dir.resolve("floodgate.jar");
        try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new java.util.zip.ZipEntry("config.yml"));
            out.write(("key-file-name: key.pem\ndisconnect:\n  invalid-key: Please connect through the official Geyser\nmetrics:\n"
                    + "  enabled: true\n  uuid: ${metrics.uuid}\nconfig-version: 3\n").getBytes());
            out.closeEntry();
        }
        return jar;
    }

    @Test
    void theStandInAbsentFaultOnlyAddsTheRefuseArgumentToTheStandInsCommand() {
        assertThat(StandInHost.command(false)).hasSize(4).doesNotContain(StandIn.REFUSE_NAMES);
        assertThat(StandInHost.command(true)).hasSize(5).endsWith(StandIn.REFUSE_NAMES);
    }

    @Test
    void cellJsonSaysOfflineStub() throws Exception {
        String json = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(cell()).toString();
        assertThat(json).contains("\"bedrockAuth\":\"offline-stub\"");
    }

    // ---- trust store ----

    @Test
    void theTrustStoreHoldsTheImageCertificatesAndTheStandInCertificate() throws Exception {
        Path cacerts = Path.of(System.getProperty("java.home"), "lib", "security", "cacerts");
        CellTrust trust = CellTrust.generate();
        byte[] store = trust.trustStore(Files.readAllBytes(cacerts));
        KeyStore result = KeyStore.getInstance("PKCS12");
        result.load(new ByteArrayInputStream(store), StandIn.STORE_PASSWORD.toCharArray());
        KeyStore source = KeyStore.getInstance(cacerts.toFile(), StandIn.STORE_PASSWORD.toCharArray());
        assertThat(result.size()).isEqualTo(source.size() + 1);
        assertThat(result.isCertificateEntry("proofstone-stand-in")).isTrue();
        String text = result.getCertificate("proofstone-stand-in").toString();
        assertThat(text).contains(StandIn.DISCOVERY_NAME).contains(StandIn.AUTH_NAME);
        // The stand-in's keystore holds the private key; the trust store never does.
        KeyStore stub = KeyStore.getInstance("PKCS12");
        stub.load(new ByteArrayInputStream(trust.stubKeystore()), StandIn.STORE_PASSWORD.toCharArray());
        assertThat(stub.isKeyEntry("proofstone-stand-in")).isTrue();
        assertThat(result.isKeyEntry("proofstone-stand-in")).isFalse();
    }

    // ---- name guard ----

    @Test
    void theGuardRefusesXboxAndMicrosoftAccountNamesAndAllowsTheTwoStandInNames() {
        String clean = "DNS A client.discovery.minecraft-services.net\nSTUB answered client.discovery.minecraft-services.net\n"
                + "DNS AAAA authorization.franchise.minecraft-services.net\nDNS A fill.papermc.io\nSTUB GET x/y";
        assertThat(NameGuard.violations(clean)).isEmpty();
        assertThat(NameGuard.namesAsked(clean)).containsExactly("client.discovery.minecraft-services.net",
                "authorization.franchise.minecraft-services.net", "fill.papermc.io");
        for (String bad : List.of("login.live.com", "user.auth.xboxlive.com", "x.xboxlive.cn", "www.xbox.com",
                "login.microsoftonline.com", "other.minecraft-services.net")) {
            assertThat(NameGuard.violations("DNS A " + bad)).as(bad).hasSize(1);
        }
        assertThatThrownBy(() -> NameGuard.require("DNS A login.live.com", "tail")).hasMessageContaining("login.live.com")
                .hasMessageContaining("tail");
    }

    @Test
    void answeredNamesAreTheStubLines() {
        String log = "STUB answered a.b\nDNS A c.d\nSTUB empty AAAA a.b\nSTUB answered e.f\nSTUB answered a.b";
        assertThat(NameGuard.namesAnswered(log).stream().collect(Collectors.toList())).containsExactly("a.b", "e.f");
    }
}
