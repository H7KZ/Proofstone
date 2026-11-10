package io.github.h7kz.proofstone.core.cell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.core.versions.Status;
import io.github.h7kz.proofstone.core.versions.VersionsCatalog;
import io.github.h7kz.proofstone.core.versions.VersionsException;
import io.github.h7kz.proofstone.core.versions.VersionsFixture;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Topology cells (ADR-0016, T-M7-08): the planner, the matrix and the identity, on a small schema 3 fixture. */
class TopologyPlanningTest {

    static final String SHA = "ab".repeat(32);
    static final List<String> DEFAULT_BACKENDS = List.of("lobby", "survival");

    @TempDir
    Path dir;

    /** The schema 2 part is the seed file's shape; a test changes only the proxies. */
    static String yaml(String proxies) {
        return """
                schema: 3
                java-rules:
                  - { range: ">=1.20.5 <26", image: java21 }
                  - { range: ">=26", image: java25 }
                drivers:
                  mcpl: { library: "g:a", version: "1.0", protocol: "26.3" }
                platforms:
                  paper:
                    host: { image: "itzg/minecraft-server", digests: { java21: "sha256:21", java25: "sha256:25" } }
                    rows:
                      - { mc: "1.21.11", build: 132, sha256: "a1", protocol: 774, status: required }
                      - { mc: "26.3", build: 244, sha256: "a2", protocol: 777, status: required }
                """ + proxies;
    }

    static String both() {
        return """
                proxies:
                  velocity:
                    host: { image: "itzg/mc-proxy", digests: { java25: "sha256:a25" } }
                    forwarding: modern
                    rows:
                      - { version: "4.3.1", build: 45, sha256: "SHA", java: java25, status: experimental,
                          backends: [ { platform: paper, mc: "26.3", status: experimental } ] }
                  bungeecord:
                    host: { image: "itzg/mc-proxy", digests: { java21: "sha256:b21" } }
                    forwarding: legacy
                    rows:
                      - { version: "2111", build: 2111, sha256: "SHA", java: java21, status: experimental,
                          backends: [ { platform: paper, mc: "26.3", status: experimental } ] }
                """.replace("SHA", SHA);
    }

    private CellPlanner planner(String proxies) throws IOException {
        return new CellPlanner(VersionsFixture.load(dir, yaml(proxies)));
    }

    @Test
    void anExactIdentityPlansOneTopologyCellWithEveryPin() throws IOException {
        TopologyCell cell = planner(both()).planTopology("velocity", "4.3.1", "paper", "26.3", Scope.NIGHTLY,
                DEFAULT_BACKENDS);

        assertThat(cell.identity()).isEqualTo("velocity@4.3.1+paper@26.3");
        assertThat(cell.status()).isEqualTo(Status.EXPERIMENTAL);
        assertThat(cell.proxy().id()).isEqualTo("velocity");
        assertThat(cell.proxy().version()).isEqualTo("4.3.1");
        assertThat(cell.proxy().build()).isEqualTo(45);
        assertThat(cell.proxy().sha256()).isEqualTo(SHA);
        assertThat(cell.proxy().java()).isEqualTo("java25");
        assertThat(cell.proxy().forwarding()).isEqualTo("modern");
        assertThat(cell.proxy().hostImage()).isEqualTo("itzg/mc-proxy");
        assertThat(cell.proxy().hostDigest()).isEqualTo("sha256:a25");
        assertThat(cell.backend().platform()).isEqualTo("paper");
        assertThat(cell.backend().mc()).isEqualTo("26.3");
        assertThat(cell.backend().build()).isEqualTo(244);
        assertThat(cell.backendNames()).containsExactly("lobby", "survival");
    }

    @Test
    void bungeeCordsVersionIsTheBuildNumberAsAString() throws IOException {
        TopologyCell cell = planner(both()).planTopology("bungeecord", "2111", "paper", "26.3", Scope.NIGHTLY,
                DEFAULT_BACKENDS);

        assertThat(cell.identity()).isEqualTo("bungeecord@2111+paper@26.3");
        assertThat(cell.proxy().forwarding()).isEqualTo("legacy");
        assertThat(cell.proxy().hostDigest()).isEqualTo("sha256:b21");
    }

    @Test
    void latestOnBothHalvesResolvesLikeThePlatformAndWarnsOnExperimental() throws IOException {
        TopologyCell cell = planner(both()).planTopology("velocity", "latest", "paper", "latest", Scope.PR,
                DEFAULT_BACKENDS);

        assertThat(cell.identity()).isEqualTo("velocity@4.3.1+paper@26.3");
        assertThat(cell.notes()).anyMatch(note -> note.contains("latest") && note.contains("experimental"));
    }

    @Test
    void allIsScopedLikeSingleServerRows() throws IOException {
        CellPlanner planner = planner(both());

        assertThat(planner.planTopologies("velocity", List.of("all"), "paper", List.of("all"), Scope.NIGHTLY,
                DEFAULT_BACKENDS)).extracting(TopologyCell::identity).containsExactly("velocity@4.3.1+paper@26.3");
        assertThatThrownBy(() -> planner.planTopologies("velocity", List.of("all"), "paper", List.of("all"),
                Scope.PR, DEFAULT_BACKENDS))
                .isInstanceOf(VersionsException.class)
                .hasMessageContaining("velocity")
                .hasMessageContaining("no topology cell in scope");
    }

    @Test
    void aRequiredPairingIsInPrScope() throws IOException {
        String required = both().replace("status: experimental", "status: required");
        List<TopologyCell> cells = planner(required).planTopologies("velocity", List.of("all"), "paper",
                List.of("all"), Scope.PR, DEFAULT_BACKENDS);

        assertThat(cells).extracting(TopologyCell::identity).containsExactly("velocity@4.3.1+paper@26.3");
        assertThat(cells.get(0).status()).isEqualTo(Status.REQUIRED);
    }

    @Test
    void theStatusIsTheWeakestOfProxyRowPairingAndBackendRow() throws IOException {
        // Proxy row required, pairing experimental, backend row required: experimental.
        String proxyRequired = both().replaceFirst("status: experimental,", "status: required,");

        TopologyCell cell = planner(proxyRequired).planTopology("velocity", "4.3.1", "paper", "26.3", Scope.NIGHTLY,
                DEFAULT_BACKENDS);

        assertThat(cell.status()).isEqualTo(Status.EXPERIMENTAL);
    }

    @Test
    void anUnknownProxyFailsAndNamesTheKnownOnes() {
        assertThatThrownBy(() -> planner(both()).planTopology("waterfall", "615", "paper", "26.3", Scope.NIGHTLY,
                DEFAULT_BACKENDS))
                .isInstanceOf(VersionsException.class)
                .hasMessageContaining("unknown proxy \"waterfall\"")
                .hasMessageContaining("velocity")
                .hasMessageContaining("bungeecord");
    }

    @Test
    void anUnknownProxyVersionFailsAndListsTheRows() {
        assertThatThrownBy(() -> planner(both()).planTopology("velocity", "3.5.1", "paper", "26.3", Scope.NIGHTLY,
                DEFAULT_BACKENDS))
                .isInstanceOf(VersionsException.class)
                .hasMessageContaining("unknown version \"3.5.1\" for proxy velocity")
                .hasMessageContaining("4.3.1");
    }

    @Test
    void aBackendThatIsNotAListedPairingFailsAndListsThePairings() {
        assertThatThrownBy(() -> planner(both()).planTopology("velocity", "4.3.1", "paper", "1.21.11",
                Scope.NIGHTLY, DEFAULT_BACKENDS))
                .isInstanceOf(VersionsException.class)
                .hasMessageContaining("velocity@4.3.1")
                .hasMessageContaining("paper@1.21.11")
                .hasMessageContaining("paper@26.3");
        assertThatThrownBy(() -> planner(both()).planTopology("velocity", "4.3.1", "folia", "26.3", Scope.NIGHTLY,
                DEFAULT_BACKENDS))
                .isInstanceOf(VersionsException.class)
                .hasMessageContaining("folia@26.3");
    }

    @Test
    void anEmptyBackendListFails() {
        assertThatThrownBy(() -> planner(both()).planTopology("velocity", "4.3.1", "paper", "26.3", Scope.NIGHTLY,
                List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one backend");
    }

    @Test
    void aDuplicateReservedOrMalformedBackendNameFails() throws IOException {
        CellPlanner planner = planner(both());
        assertThatThrownBy(() -> planner.planTopology("velocity", "4.3.1", "paper", "26.3", Scope.NIGHTLY,
                List.of("lobby", "lobby")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate backend name \"lobby\"");
        assertThatThrownBy(() -> planner.planTopology("velocity", "4.3.1", "paper", "26.3", Scope.NIGHTLY,
                List.of("proxy")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reserved");
        assertThatThrownBy(() -> planner.planTopology("velocity", "4.3.1", "paper", "26.3", Scope.NIGHTLY,
                List.of("Lobby")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("must match");
    }

    @Test
    void aTodoPinIsRefusedByNameLikeASingleServerRow() {
        String todo = both().replaceFirst("sha256: \"" + SHA + "\"", "sha256: \"TODO\"");

        assertThatThrownBy(() -> planner(todo).planTopology("velocity", "4.3.1", "paper", "26.3", Scope.NIGHTLY,
                DEFAULT_BACKENDS))
                .isInstanceOf(VersionsException.class)
                .hasMessageContaining("velocity@4.3.1")
                .hasMessageContaining("has TODO pins")
                .hasMessageContaining("sha256");
    }

    @Test
    void theMatrixListsTopologyCellsAfterTheSingleServerCells() throws IOException {
        VersionsCatalog catalog = VersionsFixture.load(dir, yaml(both()));

        CiMatrix.Plan nightly = CiMatrix.plan(catalog, Scope.NIGHTLY);
        var include = CiMatrix.matrix(nightly.cells(), nightly.topologies()).get("include");
        List<String> cells = new ArrayList<>();
        include.forEach(entry -> cells.add(entry.get("cell").asText()));

        assertThat(cells).containsExactly("paper@1.21.11", "paper@26.3", "velocity@4.3.1+paper@26.3",
                "bungeecord@2111+paper@26.3");
        assertThat(include.get(2).get("platform").asText()).isEqualTo("velocity");
        assertThat(include.get(2).get("status").asText()).isEqualTo("experimental");
        assertThat(include.get(2).get("slug").asText()).isEqualTo("velocity-4.3.1_paper-26.3");
    }

    @Test
    void prScopeLeavesOutExperimentalPairingsAndSaysWhy() throws IOException {
        VersionsCatalog catalog = VersionsFixture.load(dir, yaml(both()));

        CiMatrix.Plan pr = CiMatrix.plan(catalog, Scope.PR);

        assertThat(pr.topologies()).isEmpty();
        assertThat(pr.skipped()).containsKeys("velocity", "bungeecord");
        assertThat(pr.skipped().get("velocity")).contains("no required");
        assertThat(pr.cells()).extracting(c -> c.platform() + "@" + c.mc()).containsExactly("paper@1.21.11",
                "paper@26.3");
    }

    @Test
    void aSchemaTwoFileWithNoProxiesPlansNoTopology() throws IOException {
        VersionsCatalog catalog = VersionsFixture.load(dir, VersionsFixture.valid());

        assertThat(catalog.proxyIds()).isEmpty();
        assertThat(CiMatrix.plan(catalog, Scope.NIGHTLY).topologies()).isEmpty();
    }
}
