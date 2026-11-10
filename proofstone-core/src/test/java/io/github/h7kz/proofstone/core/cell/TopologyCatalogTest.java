package io.github.h7kz.proofstone.core.cell;

import static io.github.h7kz.proofstone.core.cell.TopologyPlanningTest.SHA;
import static io.github.h7kz.proofstone.core.cell.TopologyPlanningTest.both;
import static io.github.h7kz.proofstone.core.cell.TopologyPlanningTest.yaml;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.core.versions.ProxyPlatform;
import io.github.h7kz.proofstone.core.versions.ProxyRow;
import io.github.h7kz.proofstone.core.versions.Status;
import io.github.h7kz.proofstone.core.versions.VersionsCatalog;
import io.github.h7kz.proofstone.core.versions.VersionsException;
import io.github.h7kz.proofstone.core.versions.VersionsFixture;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Schema 3 loading and the validation rules of docs/specs/versions-yml.md ("Schema 3: proxies"). */
class TopologyCatalogTest {

    @TempDir
    Path dir;

    private VersionsCatalog load(String yaml) throws IOException {
        return VersionsFixture.load(dir, yaml);
    }

    private void rejects(String yaml, String... fragments) {
        assertThatThrownBy(() -> load(yaml)).isInstanceOf(VersionsException.class).hasMessageContainingAll(fragments);
    }

    @Test
    void schemaThreeLoadsProxiesAndKeepsPlatformsAsTheyWere() throws IOException {
        VersionsCatalog catalog = load(yaml(both()));

        assertThat(catalog.platformIds()).containsExactly("paper");
        assertThat(catalog.proxyIds()).containsExactly("velocity", "bungeecord");
        ProxyPlatform velocity = catalog.proxy("velocity");
        assertThat(velocity.forwarding()).isEqualTo("modern");
        assertThat(velocity.host().digest("java25")).isEqualTo("sha256:a25");
        ProxyRow row = velocity.rows().get(0);
        assertThat(row.version()).isEqualTo("4.3.1");
        assertThat(row.build()).isEqualTo(45);
        assertThat(row.sha256()).isEqualTo(SHA);
        assertThat(row.java()).isEqualTo("java25");
        assertThat(row.status()).isEqualTo(Status.EXPERIMENTAL);
        assertThat(row.backends()).singleElement().satisfies(pairing -> {
            assertThat(pairing.platform()).isEqualTo("paper");
            assertThat(pairing.mc()).isEqualTo("26.3");
            assertThat(pairing.status()).isEqualTo(Status.EXPERIMENTAL);
        });
    }

    @Test
    void aSchemaTwoFileIsStillValidAndHasNoProxies() throws IOException {
        assertThat(load(VersionsFixture.valid()).proxyIds()).isEmpty();
    }

    @Test
    void aSchemaThreeFileWithoutProxiesIsValid() throws IOException {
        assertThat(load(VersionsFixture.valid().replace("schema: 2", "schema: 3")).proxyIds()).isEmpty();
    }

    @Test
    void proxiesNeedSchemaThree() {
        rejects(yaml(both()).replace("schema: 3", "schema: 2"), "proxies", "schema: 3");
    }

    @Test
    void anUnknownProxyId() {
        rejects(yaml(both().replace("velocity:", "waterfall:")), "waterfall", "velocity, bungeecord");
    }

    @Test
    void aProxyIdThatIsAlsoAPlatformId() {
        String clash = yaml(both()).replace("platforms:\n", "platforms:\n  velocity:\n"
                + "    host: { image: \"x\", digests: { java25: \"sha256:25\" } }\n    rows: []\n");
        rejects(clash, "velocity", "also a platform");
    }

    @Test
    void duplicateVersionsWithinAProxy() {
        String twice = both().replace("      - { version: \"4.3.1\", build: 45,",
                "      - { version: \"4.3.1\", build: 44, sha256: \"" + SHA + "\", java: java25, status: experimental,\n"
                        + "          backends: [ { platform: paper, mc: \"26.3\", status: experimental } ] }\n"
                        + "      - { version: \"4.3.1\", build: 45,");
        rejects(yaml(twice), "velocity", "\"4.3.1\" appears twice");
    }

    @Test
    void javaIsRequiredOnEveryRow() {
        rejects(yaml(both().replace(" java: java25,", "")), "java");
    }

    @Test
    void hostDigestsMustHaveTheRowsJavaTag() {
        rejects(yaml(both().replace("digests: { java25: \"sha256:a25\" }", "digests: { java21: \"sha256:a21\" }")),
                "velocity", "4.3.1", "java25");
    }

    @Test
    void aRequiredRowMustNotContainTodo() {
        String todo = both().replace("status: experimental", "status: required")
                .replaceFirst("sha256: \"" + SHA + "\"", "sha256: \"TODO\"");
        rejects(yaml(todo), "velocity", "required", "TODO");
    }

    @Test
    void aBackendMustNameAnExistingRow() {
        rejects(yaml(both().replace("paper, mc: \"26.3\"", "paper, mc: \"9.9\"")), "velocity", "paper@9.9");
        rejects(yaml(both().replace("platform: paper, mc: \"26.3\"", "platform: spigot, mc: \"26.3\"")),
                "velocity", "spigot@26.3");
    }

    @Test
    void aPairingMayNotBeListedTwice() {
        String twice = both().replace("backends: [ { platform: paper, mc: \"26.3\", status: experimental } ] }\n"
                        + "  bungeecord",
                "backends: [ { platform: paper, mc: \"26.3\", status: experimental },\n"
                        + "                      { platform: paper, mc: \"26.3\", status: experimental } ] }\n"
                        + "  bungeecord");
        rejects(yaml(twice), "velocity", "paper@26.3", "twice");
    }

    @Test
    void aRequiredPairingNeedsARequiredProxyRowAndARequiredBackendRow() {
        String pairingRequiredOnly = both().replaceFirst("(backends: \\[ \\{ platform: paper, mc: \"26.3\", status:) "
                + "experimental", "$1 required");
        rejects(yaml(pairingRequiredOnly), "velocity@4.3.1+paper@26.3", "required", "proxy row");
        String backendExperimental = both().replace("status: experimental", "status: required");
        rejects(yaml(backendExperimental).replace("{ mc: \"26.3\", build: 244, sha256: \"a2\", protocol: 777, "
                + "status: required }", "{ mc: \"26.3\", build: 244, sha256: \"a2\", protocol: 777, status: "
                + "experimental }"), "paper@26.3", "backend row");
    }

    @Test
    void aBridgedPairingIsRefusedAndNamesTheAdr() {
        // The driver speaks 26.3 (protocol 777); a backend on 1.21.11 (protocol 774) would need a bridge.
        String bridged = both().replace("paper, mc: \"26.3\"", "paper, mc: \"1.21.11\"");
        rejects(yaml(bridged), "velocity@4.3.1+paper@1.21.11", "bridged", "ADR-0016");
    }

    @Test
    void forwardingMustBeModernOrLegacyAndSupportedByTheProxy() {
        rejects(yaml(both().replace("forwarding: modern", "forwarding: bungeeguard")), "forwarding");
        rejects(yaml(both().replace("forwarding: legacy", "forwarding: modern")), "bungeecord", "legacy");
    }

    @Test
    void aMissingDriverProtocolCannotJudgeNativePairings() {
        rejects(yaml(both()).replace("  mcpl: { library: \"g:a\", version: \"1.0\", protocol: \"26.3\" }\n",
                "  mcpl: { library: \"g:a\", version: \"1.0\" }\n"), "drivers.mcpl.protocol");
    }
}
