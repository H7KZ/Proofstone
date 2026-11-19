package io.github.h7kz.proofstone.core.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;
import io.github.h7kz.proofstone.core.cell.Cell;
import io.github.h7kz.proofstone.core.cell.CellPlanner;
import io.github.h7kz.proofstone.core.cell.CellSelector;
import io.github.h7kz.proofstone.core.cell.GeyserCell;
import io.github.h7kz.proofstone.core.cell.LoaderCell;
import io.github.h7kz.proofstone.core.cell.Scope;
import io.github.h7kz.proofstone.core.cell.VersionFamilies;
import io.github.h7kz.proofstone.core.versions.VersionsCatalog;
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

    private static final String LOADERS = """
            loaders:
              fabric:
                host: { image: "itzg/minecraft-server", digests: { java25: "sha256:a25" } }
                rows:
                  - { mc: "26.3", loader: "0.20.1", installer: "1.1.2", sha256: "SHA", vanilla-sha256: "SHA",
                      fabric-api: { version: "0.166.2+26.3", sha256: "SHA" }, protocol: 777, status: experimental,
                      native-drivers: [mcpl] }
              neoforge:
                host: { image: "itzg/minecraft-server", digests: { java25: "sha256:a25" } }
                rows:
                  - { mc: "26.3", loader: "26.3.0.103", sha256: "SHA", vanilla-sha256: "SHA", protocol: 777,
                      status: experimental, native-drivers: [mcpl] }
            """.replace("SHA", "cd".repeat(32));

    private ProofstoneExtension.Plan planLoader(Class<?> type, String selector) throws IOException {
        String yaml = """
                schema: 4
                java-rules:
                  - { range: ">=1.20.5 <26", image: java21 }
                  - { range: ">=26", image: java25 }
                drivers:
                  mcpl: { library: "g:a", version: "1.0", protocol: "26.3" }
                platforms:
                  paper:
                    host: { image: "itzg/minecraft-server", digests: { java21: "sha256:21", java25: "sha256:25" } }
                    rows:
                      - { mc: "26.3", build: 244, sha256: "a2", protocol: 777, status: required }
                """ + LOADERS;
        CellPlanner planner = new CellPlanner(VersionsFixture.load(dir, yaml));
        return ProofstoneExtension.plan(type, matrices(type), CellSelector.parse(selector, "-Dtest"), planner,
                Scope.PR, VersionFamilies.none());
    }

    @Matrix(platform = Platform.PAPER, versions = {"26.3"})
    @Matrix(platform = Platform.FABRIC, versions = {"26.3"})
    @Matrix(platform = Platform.NEOFORGE, versions = {"26.3"})
    static class PaperAndLoaders {
    }

    @Matrix(platform = Platform.FABRIC, versions = {"26.3"})
    static class OnlyFabric {
    }

    @Test
    void aLoaderRowIsExperimentalSoWithoutASelectorItsMatrixIsNotPlanned() throws IOException {
        // Loader rows run only when -Dproofstone.cells names them: the default run never boots an experimental loader.
        ProofstoneExtension.Plan plan = planLoader(PaperAndLoaders.class, null);

        assertThat(plan.cells()).extracting(Cell::platform).containsExactly("paper");
        assertThat(plan.loaders()).isEmpty();
    }

    @Test
    void aPartlySelectedClassKeepsItsUnselectedRowsOutOfTheJunitReport() throws IOException {
        // T-M4-13 review: a normal selector run (paper@26.3 over a class that also declares loaders) must stay skipped="0"
        // (AGENTS.md section 4). The unselected rows are said on stderr and are kept on the plan only for the zero-row error.
        ProofstoneExtension.Plan plan = planLoader(PaperAndLoaders.class, null);

        assertThat(plan.cells()).extracting(Cell::platform).containsExactly("paper");
        assertThat(plan.notSelected()).extracting(ProofstoneExtension.NotSelected::what)
                .containsExactlyInAnyOrder("fabric", "neoforge");
        assertThat(ProofstoneExtension.notSelectedInvocations(plan)).isEmpty();
    }

    @Test
    void aClassWithNoSelectedRowFailsByNameAndListsEveryRowTheSelectorLeftOut() {
        // The silent-vanish hole (robustness R15, X1): nothing selected is an error that names each unselected row, not a pass.
        assertThatThrownBy(() -> planLoader(OnlyFabric.class, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(OnlyFabric.class.getName())
                .hasMessageContaining("NOT SELECTED: declares fabric, an experimental loader row");
        assertThatThrownBy(() -> plan(Declared.class, "folia@latest"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NOT SELECTED: declares paper, which -Dproofstone.cells or PROOFSTONE_CELLS does not name");
    }

    @Test
    void aClassWhoseEveryRowIsSelectedHasNoNotSelectedRow() throws IOException {
        assertThat(planLoader(PaperAndLoaders.class, "paper@26.3,fabric@26.3,neoforge@26.3").notSelected()).isEmpty();
    }

    @Test
    void aLoaderSelectorPlansTheLoaderCellAndNoPaperCell() throws IOException {
        ProofstoneExtension.Plan plan = planLoader(PaperAndLoaders.class, "fabric@26.3");

        assertThat(plan.cells()).isEmpty();
        assertThat(plan.loaders()).extracting(LoaderCell::identity).containsExactly("fabric@26.3");
    }

    @Test
    void theNeoForgeSelectorPlansTheNeoForgeCell() throws IOException {
        assertThat(planLoader(PaperAndLoaders.class, "neoforge@26.3").loaders())
                .extracting(LoaderCell::identity).containsExactly("neoforge@26.3");
    }

    @Test
    void aPaperSelectorLeavesTheLoaderMatrixUnplanned() throws IOException {
        ProofstoneExtension.Plan plan = planLoader(PaperAndLoaders.class, "paper@26.3");

        assertThat(plan.cells()).extracting(Cell::platform).containsExactly("paper");
        assertThat(plan.loaders()).isEmpty();
    }

    @Test
    void aLoaderOnlyClassWithoutALoaderSelectorFailsByNameInsteadOfRunningNothing() {
        assertThatThrownBy(() -> planLoader(OnlyFabric.class, null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(OnlyFabric.class.getName());
    }

    @Test
    void aLoaderSelectorNamingARowThatDoesNotExistFailsLoudly() {
        assertThatThrownBy(() -> planLoader(OnlyFabric.class, "fabric@1.21.11"))
                .isInstanceOf(io.github.h7kz.proofstone.core.versions.VersionsException.class)
                .hasMessageContaining("1.21.11");
    }

    @Test
    void aPaperClassIsNotRunOnALoaderSelectionAndFailsByName() {
        assertThatThrownBy(() -> planLoader(Declared.class, "fabric@26.3"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(Declared.class.getName());
    }

    @Matrix(platform = Platform.GEYSER, versions = {"latest"})
    static class OnGeyser {
    }

    @Matrix(platform = Platform.GEYSER, versions = {"latest"})
    @Matrix(platform = Platform.PAPER, versions = {"26.3"})
    static class OnGeyserAndPaper {
    }

    @Matrix(platform = Platform.GEYSER, versions = {"latest"})
    @Proofstone(mods = "build/libs/m.jar")
    static class GeyserWithMods {
    }

    @Matrix(platform = Platform.GEYSER, versions = {"latest"})
    @Proofstone(datapacks = "build/libs/d.zip")
    static class GeyserWithDatapacks {
    }

    /** The repository's own versions.yml: its geyser row is the one the real-server runs use (ADR-0018 section 8). */
    private ProofstoneExtension.Plan planGeyser(Class<?> type, String selector) {
        Path root = Path.of(System.getProperty("proofstone.repoRoot"));
        VersionsCatalog catalog = VersionsCatalog.load(root.resolve("versions.yml"),
                root.resolve("proofstone-protocol/schemas/versions.schema.json"));
        return ProofstoneExtension.plan(type, matrices(type), CellSelector.parse(selector, "-Dtest"),
                new CellPlanner(catalog), Scope.PR, VersionFamilies.none());
    }

    @Test
    void aGeyserMatrixPlansTheNamedGeyserCellAndNothingElse() {
        ProofstoneExtension.Plan plan = planGeyser(OnGeyser.class, "geyser@2.12.1+paper@26.3");

        assertThat(plan.geysers()).extracting(GeyserCell::identity).containsExactly("geyser@2.12.1+paper@26.3");
        assertThat(plan.cells()).isEmpty();
        assertThat(plan.topologies()).isEmpty();
        assertThat(plan.loaders()).isEmpty();
        // The lifecycle runs the Paper backend, whose own platform stays paper.
        assertThat(plan.geysers().get(0).backend().platform()).isEqualTo("paper");
    }

    @Test
    void aGeyserRowIsExperimentalSoWithoutASelectorTheClassFailsByNameInsteadOfRunningIt() {
        // The row is experimental (ADR-0018 section 9): a default run never boots it, and says so on stderr.
        assertThatThrownBy(() -> planGeyser(OnGeyser.class, null)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(OnGeyser.class.getName()).hasMessageContaining("has no cell to run");
    }

    @Test
    void aPlainPaperSelectionOfAClassThatAlsoDeclaresGeyserPlansOnlyThePaperCell() {
        ProofstoneExtension.Plan plan = planGeyser(OnGeyserAndPaper.class, "paper@26.3");

        assertThat(plan.cells()).extracting(Cell::platform).containsExactly("paper");
        assertThat(plan.geysers()).isEmpty();
    }

    @Test
    void aGeyserEntryNoMatrixPlansIsNotPlannedByAPaperOnlyClass() {
        assertThatThrownBy(() -> planGeyser(Declared.class, "geyser@2.12.1+paper@26.3"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(Declared.class.getName());
    }

    @Test
    void modsAndDatapacksOnAGeyserClassAreAnInvalidDeclarationNamingTheClass() {
        assertThatThrownBy(() -> planGeyser(GeyserWithMods.class, "geyser@2.12.1+paper@26.3"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("INVALID test declaration")
                .hasMessageContaining(GeyserWithMods.class.getName()).hasMessageContaining("mods");
        assertThatThrownBy(() -> planGeyser(GeyserWithDatapacks.class, "geyser@2.12.1+paper@26.3"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("INVALID test declaration")
                .hasMessageContaining("datapacks");
    }

    @Test
    void aDifferentialRunTheFlakeDetectorAnotherDriverAndABridgedJoinAreRefusedNamingTheCell() {
        String cell = "geyser@2.12.1+paper@26.3";
        assertThat(GeyserRun.refusal(cell, false, 1, "mcpl", "mcpl", false)).isEmpty();
        assertThat(GeyserRun.refusal(cell, true, 1, "mcpl", "mcpl", false)).hasValueSatisfying(
                reason -> assertThat(reason).contains(cell).contains("differential").contains("ADR-0018"));
        assertThat(GeyserRun.refusal(cell, false, 3, "mcpl", "mcpl", false)).hasValueSatisfying(
                reason -> assertThat(reason).contains(cell).contains("flake detector"));
        assertThat(GeyserRun.refusal(cell, false, 1, "mineflayer-ref", "mcpl", false)).hasValueSatisfying(
                reason -> assertThat(reason).contains(cell).contains("mineflayer-ref"));
        assertThat(GeyserRun.refusal(cell, false, 1, "mcpl", "mcpl", true)).hasValueSatisfying(
                reason -> assertThat(reason).contains(cell).contains("ViaProxy").contains("ADR-0018 section 3"));
    }
}
