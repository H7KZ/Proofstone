package io.github.h7kz.proofstone.core.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.core.cell.Cell;
import io.github.h7kz.proofstone.core.cell.CellPlanner;
import io.github.h7kz.proofstone.core.cell.CellSelector;
import io.github.h7kz.proofstone.core.cell.Scope;
import io.github.h7kz.proofstone.core.cell.VersionFamilies;
import io.github.h7kz.proofstone.core.versions.VersionsFixture;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import io.github.h7kz.proofstone.api.Topology;
import io.github.h7kz.proofstone.core.cell.TopologyCell;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.commons.support.AnnotationSupport;

/**
 * The zero-cell decision of {@link ProofstoneExtension}, without JUnit or Docker. A selector that names none of a
 * class's platforms must fail the class by name (PR #35 added the error by hand; this test keeps it).
 */
class ProofstoneExtensionPlanTest {

    @TempDir
    Path dir;

    @Matrix(platform = Platform.PAPER, versions = {"1.10.2"})
    static class Declared {
    }

    private List<Matrix> matrices(Class<?> type) {
        return AnnotationSupport.findRepeatableAnnotations(type, Matrix.class);
    }

    private List<Cell> plan(Class<?> type, String selector) throws IOException {
        CellPlanner planner = new CellPlanner(VersionsFixture.load(dir, VersionsFixture.valid()));
        return ProofstoneExtension.plan(type, matrices(type), CellSelector.parse(selector, "-Dtest"), planner,
                Scope.PR, VersionFamilies.none()).cells();
    }

    @Test
    void withoutSelectorTheMatrixVersionsRun() throws IOException {
        assertThat(plan(Declared.class, null)).extracting(Cell::mc).containsExactly("1.10.2");
    }

    @Test
    void aTopologyEntryNextToASingleServerEntryLeavesTheSingleServerCellsAsTheyWere() throws IOException {
        assertThat(plan(Declared.class, "paper@1.20.4,velocity@4.3.1+paper@26.3")).extracting(Cell::mc)
                .containsExactly("1.20.4");
    }

    @Test
    void aTopologyEntryAloneFailsTheClassByNameInsteadOfRunningNothing() {
        assertThatThrownBy(() -> plan(Declared.class, "velocity@4.3.1+paper@26.3"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(Declared.class.getName());
    }

    @Test
    void selectorReplacesTheMatrixVersionsOfThePlatformItNames() throws IOException {
        assertThat(plan(Declared.class, "paper@1.20.4")).extracting(Cell::mc).containsExactly("1.20.4");
    }

    @Matrix(platform = Platform.PAPER, versions = {"1.10.2"})
    @Matrix(platform = Platform.FOLIA, versions = {"1.21.11"})
    static class PaperAndFolia {
    }

    @Test
    void aPlatformSpecificClassThatAlsoDeclaresPaperPlansOneCellForAPaperSelection() throws IOException {
        // The Folia-only tests (C-F01, C-F02) declare Paper too and skip there through @Requires, so paper@... plans a cell.
        assertThat(plan(PaperAndFolia.class, "paper@1.20.4")).extracting(Cell::platform).containsExactly("paper");
    }

    @Test
    void selectorNamingOnlyAnotherPlatformFailsTheClassByNameAndSelector() throws IOException {
        assertThatThrownBy(() -> plan(Declared.class, "folia@latest"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(Declared.class.getName())
                .hasMessageContaining("has no cell to run")
                .hasMessageContaining("-Dproofstone.cells or PROOFSTONE_CELLS");
    }

    private static final String PROXIES = """
            proxies:
              velocity:
                host: { image: "itzg/mc-proxy", digests: { java25: "sha256:a25" } }
                forwarding: modern
                rows:
                  - { version: "4.3.1", build: 45, sha256: "SHA", java: java25, status: experimental,
                      backends: [ { platform: paper, mc: "26.3", status: experimental } ] }
            """.replace("SHA", "ab".repeat(32));

    private ProofstoneExtension.Plan planTopology(Class<?> type, String selector) throws IOException {
        String yaml = """
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
                """ + PROXIES;
        CellPlanner planner = new CellPlanner(VersionsFixture.load(dir, yaml));
        return ProofstoneExtension.plan(type, matrices(type), CellSelector.parse(selector, "-Dtest"), planner,
                Scope.PR, VersionFamilies.none());
    }

    @Matrix(platform = Platform.VELOCITY, versions = {"latest"})
    static class OnVelocity {
    }

    @Matrix(platform = Platform.VELOCITY, versions = {"4.3.1"})
    @Topology.Backends({"hub", "games", "pvp"})
    static class NamedBackends {
    }

    @Matrix(platform = Platform.VELOCITY, versions = {"4.3.1"})
    @Topology.Backends({"lobby", "proxy"})
    static class ReservedBackend {
    }

    @Test
    void aProxyMatrixPlansATopologyCellOnPaperLatestWithTheDefaultBackends() throws IOException {
        ProofstoneExtension.Plan plan = planTopology(OnVelocity.class, null);

        assertThat(plan.cells()).isEmpty();
        assertThat(plan.topologies()).extracting(TopologyCell::identity).containsExactly("velocity@4.3.1+paper@26.3");
        assertThat(plan.topologies().get(0).backendNames()).containsExactly("lobby", "survival");
    }

    @Test
    void topologyBackendsNameTheBackendsInOrder() throws IOException {
        assertThat(planTopology(NamedBackends.class, null).topologies().get(0).backendNames())
                .containsExactly("hub", "games", "pvp");
    }

    @Test
    void aReservedBackendNameFailsTheClassBeforeAnythingStarts() {
        assertThatThrownBy(() -> planTopology(ReservedBackend.class, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reserved");
    }

    @Test
    void aTopologySelectorReplacesTheProxyMatrixVersions() throws IOException {
        assertThat(planTopology(OnVelocity.class, "velocity@4.3.1+paper@26.3").topologies())
                .extracting(TopologyCell::identity).containsExactly("velocity@4.3.1+paper@26.3");
    }

    @Test
    void aSingleServerSelectorDoesNotSelectTheProxyMatrixAndTheClassFailsByName() {
        assertThatThrownBy(() -> planTopology(OnVelocity.class, "paper@26.3"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(OnVelocity.class.getName());
    }
}
