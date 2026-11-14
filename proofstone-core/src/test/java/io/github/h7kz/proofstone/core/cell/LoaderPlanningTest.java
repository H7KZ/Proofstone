package io.github.h7kz.proofstone.core.cell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.h7kz.proofstone.core.versions.LoaderPlatform;
import io.github.h7kz.proofstone.core.versions.LoaderRow;
import io.github.h7kz.proofstone.core.versions.Status;
import io.github.h7kz.proofstone.core.versions.VersionsCatalog;
import io.github.h7kz.proofstone.core.versions.VersionsException;
import io.github.h7kz.proofstone.core.versions.VersionsFixture;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Loader cells (ADR-0017, T-M8-04): schema 4 loading, validation, the planner and the matrix, on a small fixture. */
class LoaderPlanningTest {

    static final String SHA = "cd".repeat(32);

    @TempDir
    Path dir;

    /** Schema 4 with a paper platform (as the proxy fixture) and the given loaders. */
    static String yaml(String loaders) {
        return TopologyPlanningTest.yaml("").replace("schema: 3", "schema: 4") + loaders;
    }

    static String both() {
        return """
                loaders:
                  fabric:
                    host: { image: "itzg/minecraft-server", digests: { java25: "sha256:25" } }
                    rows:
                      - { mc: "26.3", loader: "0.20.1", installer: "1.1.2", sha256: "SHA", vanilla-sha256: "TODO",
                          fabric-api: { version: "0.166.2+26.3", sha256: "TODO" }, protocol: 777, status: experimental,
                          native-drivers: [mcpl], observed: "spike", notes: "n" }
                  neoforge:
                    host: { image: "itzg/minecraft-server", digests: { java25: "sha256:25" } }
                    rows:
                      - { mc: "26.3", loader: "26.3.0.103", sha256: "SHA", vanilla-sha256: "TODO", protocol: 777,
                          status: experimental, native-drivers: [mcpl] }
                """.replace("SHA", SHA);
    }

    /** The fabric row with every pin set and status required. */
    static String pinnedRequired() {
        return both().replaceFirst("vanilla-sha256: \"TODO\"", "vanilla-sha256: \"" + SHA + "\"")
                .replace("sha256: \"TODO\" }", "sha256: \"" + SHA + "\" }")
                .replaceFirst("status: experimental", "status: required");
    }

    private VersionsCatalog load(String yaml) throws IOException {
        return VersionsFixture.load(dir, yaml);
    }

    private CellPlanner planner(String loaders) throws IOException {
        return new CellPlanner(load(yaml(loaders)));
    }

    private void rejects(String yaml, String... fragments) {
        assertThatThrownBy(() -> load(yaml)).isInstanceOf(VersionsException.class).hasMessageContainingAll(fragments);
    }

    // ---- catalog ----

    @Test
    void schemaFourLoadsLoaderRowsAndKeepsPlatformsAsTheyWere() throws IOException {
        VersionsCatalog catalog = load(yaml(both()));

        assertThat(catalog.platformIds()).containsExactly("paper");
        assertThat(catalog.proxyIds()).isEmpty();
        assertThat(catalog.loaderIds()).containsExactly("fabric", "neoforge");
        LoaderPlatform fabric = catalog.loader("fabric");
        assertThat(fabric.host().digest("java25")).isEqualTo("sha256:25");
        LoaderRow row = fabric.rows().get(0);
        assertThat(row.mc()).isEqualTo("26.3");
        assertThat(row.loader()).isEqualTo("0.20.1");
        assertThat(row.installer()).isEqualTo("1.1.2");
        assertThat(row.sha256()).isEqualTo(SHA);
        assertThat(row.vanillaSha256()).as("TODO is stored as null").isNull();
        assertThat(row.fabricApi().version()).isEqualTo("0.166.2+26.3");
        assertThat(row.fabricApi().sha256()).isNull();
        assertThat(row.protocol()).isEqualTo(777);
        assertThat(row.status()).isEqualTo(Status.EXPERIMENTAL);
        assertThat(row.nativeDrivers()).containsExactly("mcpl");
        assertThat(catalog.loader("neoforge").rows().get(0).installer()).isNull();
        assertThat(catalog.loader("neoforge").rows().get(0).fabricApi()).isNull();
    }

    @Test
    void aSchemaThreeFileIsAValidSchemaFourFileAndHasNoLoaders() throws IOException {
        assertThat(load(TopologyPlanningTest.yaml("")).loaderIds()).isEmpty();
        assertThat(load(yaml("")).loaderIds()).isEmpty();
    }

    @Test
    void proxiesAndLoadersCoexistInSchemaFour() throws IOException {
        VersionsCatalog catalog = load(TopologyPlanningTest.yaml(TopologyPlanningTest.both())
                .replace("schema: 3", "schema: 4") + both());

        assertThat(catalog.proxyIds()).containsExactly("velocity", "bungeecord");
        assertThat(catalog.loaderIds()).containsExactly("fabric", "neoforge");
    }

    @Test
    void loadersNeedSchemaFour() {
        rejects(yaml(both()).replace("schema: 4", "schema: 3"), "loaders", "schema: 4", "ADR-0017");
        rejects(yaml(both()).replace("schema: 4", "schema: 2"), "loaders", "schema: 4");
    }

    @Test
    void anUnknownLoaderIdNamesTheOnesProofstoneRuns() {
        rejects(yaml(both().replace("neoforge:", "quilt:")), "quilt", "fabric, neoforge");
    }

    @Test
    void aLoaderIdThatIsAlsoAPlatformId() {
        rejects(TopologyPlanningTest.yaml("").replace("schema: 3", "schema: 4").replace("platforms:\n",
                "platforms:\n  fabric:\n    host: { image: \"x\", digests: { java25: \"sha256:25\" } }\n"
                        + "    rows: []\n") + both(), "fabric", "also a platform");
    }

    @Test
    void duplicateMcWithinALoader() {
        String row = "      - { mc: \"26.3\", loader: \"26.3.0.103\", sha256: \"" + SHA
                + "\", vanilla-sha256: \"TODO\", protocol: 777,\n          status: experimental, native-drivers: [mcpl] }\n";
        String twice = both().replace(row, row + row.replace("26.3.0.103", "26.3.0.104"));
        rejects(yaml(twice), "neoforge", "\"26.3\" appears twice");
    }

    @Test
    void everyRowNeedsItsPins() {
        rejects(yaml(both().replace(" loader: \"0.20.1\",", "")), "loader");
        rejects(yaml(both().replaceFirst(" sha256: \"" + SHA + "\",", "")), "sha256");
        rejects(yaml(both().replaceFirst(" vanilla-sha256: \"TODO\",", "")), "vanilla-sha256");
    }

    @Test
    void aFabricRowNeedsItsInstallerAndFabricApi() {
        rejects(yaml(both().replace(" installer: \"1.1.2\",", "")), "fabric", "26.3", "installer");
        rejects(yaml(both().replace("fabric-api: { version: \"0.166.2+26.3\", sha256: \"TODO\" }, ", "")),
                "fabric", "26.3", "fabric-api");
    }

    @Test
    void aRequiredRowMustNotContainTodo() {
        rejects(yaml(both().replaceFirst("status: experimental", "status: required")), "fabric", "required", "TODO");
    }

    @Test
    void aRequiredRowIsAcceptedWhenEveryPinIsSet() throws IOException {
        assertThat(load(yaml(pinnedRequired())).loader("fabric").rows().get(0).status()).isEqualTo(Status.REQUIRED);
    }

    @Test
    void hostDigestsMustHaveTheRowsJavaTag() {
        rejects(yaml(both().replaceFirst("digests: \\{ java25: \"sha256:25\" \\}", "digests: { java21: \"sha256:21\" }")),
                "fabric", "26.3", "java25");
    }

    @Test
    void aBridgedLoaderRowIsRefusedAndNamesTheAdr() {
        // The driver speaks 26.3 (protocol 777); a 1.21.11 loader row (protocol 774) would need a bridge.
        String bridged = both().replace("mc: \"26.3\", loader: \"0.20.1\"", "mc: \"1.21.11\", loader: \"0.20.1\"")
                .replaceFirst("protocol: 777", "protocol: 774");
        rejects(yaml(bridged), "fabric@1.21.11", "bridged", "ADR-0017");
    }

    @Test
    void aMissingDriverProtocolCannotJudgeNativeRows() {
        rejects(yaml(both()).replace("  mcpl: { library: \"g:a\", version: \"1.0\", protocol: \"26.3\" }\n",
                "  mcpl: { library: \"g:a\", version: \"1.0\" }\n"), "drivers.mcpl.protocol");
    }

    @Test
    void anUnknownLoaderLookupNamesTheKnownOnes() throws IOException {
        VersionsCatalog withLoaders = load(yaml(both()));
        assertThatThrownBy(() -> withLoaders.loader("quilt")).isInstanceOf(VersionsException.class)
                .hasMessageContainingAll("quilt", "fabric", "neoforge");
        VersionsCatalog without = load(yaml(""));
        assertThatThrownBy(() -> without.loader("fabric")).isInstanceOf(VersionsException.class)
                .hasMessageContaining("versions.yml has no loaders");
    }

    // ---- planner ----

    @Test
    void anExactIdentityPlansOneLoaderCellWithEveryPin() throws IOException {
        List<LoaderCell> cells = planner(both()).planLoaders("fabric", List.of("26.3"), Scope.PR);

        assertThat(cells).singleElement().satisfies(cell -> {
            assertThat(cell.identity()).isEqualTo("fabric@26.3");
            assertThat(cell.loader()).isEqualTo("fabric");
            assertThat(cell.mc()).isEqualTo("26.3");
            assertThat(cell.loaderVersion()).isEqualTo("0.20.1");
            assertThat(cell.installer()).isEqualTo("1.1.2");
            assertThat(cell.sha256()).isEqualTo(SHA);
            assertThat(cell.vanillaSha256()).isNull();
            assertThat(cell.fabricApi().version()).isEqualTo("0.166.2+26.3");
            assertThat(cell.fabricApi().sha256()).isNull();
            assertThat(cell.java()).isEqualTo("java25");
            assertThat(cell.status()).isEqualTo(Status.EXPERIMENTAL);
            assertThat(cell.hostImage()).isEqualTo("itzg/minecraft-server");
            assertThat(cell.hostDigest()).isEqualTo("sha256:25");
        });
        assertThat(planner(both()).planLoaders("neoforge", List.of("26.3"), Scope.PR).get(0).identity())
                .isEqualTo("neoforge@26.3");
    }

    @Test
    void anExactIdentityRunsInAnyScopeLikeAnExactPlatformVersion() throws IOException {
        assertThat(planner(both()).planLoaders("fabric", List.of("26.3"), Scope.NIGHTLY)).hasSize(1);
    }

    @Test
    void aTodoOnTheJarPinFailsLoudlyNamingIt() throws IOException {
        // sha256 is the one pin the planner needs; the other TODOs fail at the host check (ADR-0017 section 3).
        CellPlanner todo = new CellPlanner(load(yaml(both()).replace("sha256: \"" + SHA + "\", vanilla-sha256",
                "sha256: \"TODO\", vanilla-sha256")));
        assertThatThrownBy(() -> todo.planLoaders("fabric", List.of("26.3"), Scope.PR))
                .isInstanceOf(VersionsException.class).hasMessageContainingAll("fabric@26.3", "TODO pins", "sha256");
    }

    @Test
    void latestIsTheHighestRequiredRowAndNeverAnExperimentalOne() throws IOException {
        assertThatThrownBy(() -> planner(both()).planLoaders("fabric", List.of("latest"), Scope.PR))
                .isInstanceOf(VersionsException.class)
                .hasMessageContainingAll("latest", "fabric", "no required row", "26.3");
        assertThat(planner(pinnedRequired()).planLoaders("fabric", List.of("latest"), Scope.PR).get(0).identity())
                .isEqualTo("fabric@26.3");
    }

    @Test
    void allIsScopedLikeSingleServerRows() throws IOException {
        CellPlanner planner = planner(both());

        assertThatThrownBy(() -> planner.planLoaders("fabric", List.of("all"), Scope.PR))
                .isInstanceOf(VersionsException.class).hasMessageContainingAll("fabric", "no row in scope", "PR");
        assertThat(planner.planLoaders("fabric", List.of("all"), Scope.NIGHTLY)).extracting(LoaderCell::identity)
                .containsExactly("fabric@26.3");
    }

    @Test
    void duplicatesCollapseAndAnEmptyRequestFails() throws IOException {
        CellPlanner planner = planner(both());

        assertThat(planner.planLoaders("neoforge", List.of("26.3", "26.3", "all"), Scope.NIGHTLY)).hasSize(1);
        assertThatThrownBy(() -> planner.planLoaders("neoforge", List.of(), Scope.PR))
                .isInstanceOf(VersionsException.class).hasMessageContaining("no versions requested for neoforge");
    }

    @Test
    void anUnknownVersionListsTheValidOnes() throws IOException {
        assertThatThrownBy(() -> planner(both()).planLoaders("fabric", List.of("1.21.11"), Scope.PR))
                .isInstanceOf(VersionsException.class).hasMessageContainingAll("unknown version", "1.21.11", "fabric",
                        "valid: 26.3");
    }

    @Test
    void anUnknownLoaderIsAnErrorNamingTheKnownOnes() throws IOException {
        CellPlanner planner = planner(both());

        assertThatThrownBy(() -> planner.planLoaders("quilt", List.of("26.3"), Scope.PR))
                .isInstanceOf(VersionsException.class).hasMessageContainingAll("unknown loader", "quilt", "fabric");
        assertThat(planner.isLoader("quilt")).isFalse();
        assertThat(planner.isLoader("paper")).isFalse();
        assertThat(planner.isLoader("fabric")).isTrue();
    }

    @Test
    void platformsPlanAsBeforeOnAFileWithLoaders() throws IOException {
        CellPlanner planner = planner(both());

        assertThat(planner.plan("paper", List.of("26.3"))).singleElement().satisfies(cell -> {
            assertThat(cell.platform()).isEqualTo("paper");
            assertThat(cell.mc()).isEqualTo("26.3");
        });
        assertThatThrownBy(() -> planner.plan("fabric", List.of("26.3"))).isInstanceOf(VersionsException.class)
                .hasMessageContaining("unknown platform \"fabric\"");
    }

    // ---- matrix ----

    @Test
    void prScopePlansNoExperimentalLoaderAndSaysWhy() throws IOException {
        CiMatrix.Plan plan = CiMatrix.plan(load(yaml(both())), Scope.PR);

        assertThat(plan.loaders()).isEmpty();
        assertThat(plan.skipped()).containsKeys("fabric", "neoforge");
        assertThat(plan.skipped().get("fabric")).contains("no required row");
        assertThat(plan.cells()).extracting(Cell::mc).containsExactly("1.21.11", "26.3");
        JsonNode include = CiMatrix.matrix(plan.cells(), plan.topologies(), plan.loaders()).get("include");
        assertThat(include).hasSize(2);
    }

    @Test
    void nightlyScopeAddsLoaderCellsAfterSingleServerAndTopologyCells() throws IOException {
        CiMatrix.Plan plan = CiMatrix.plan(load(TopologyPlanningTest.yaml(TopologyPlanningTest.both())
                .replace("schema: 3", "schema: 4") + both()), Scope.NIGHTLY);

        assertThat(plan.loaders()).extracting(LoaderCell::identity).containsExactly("fabric@26.3", "neoforge@26.3");
        JsonNode include = CiMatrix.matrix(plan.cells(), plan.topologies(), plan.loaders()).get("include");
        assertThat(include).hasSize(plan.cells().size() + plan.topologies().size() + 2);
        JsonNode fabric = include.get(include.size() - 2);
        assertThat(fabric.get("cell").asText()).isEqualTo("fabric@26.3");
        assertThat(fabric.get("platform").asText()).isEqualTo("fabric");
        assertThat(fabric.get("kind").asText()).isEqualTo("loader");
        assertThat(fabric.get("mc").asText()).isEqualTo("26.3");
        assertThat(fabric.get("loader").asText()).isEqualTo("0.20.1");
        assertThat(fabric.get("java").asText()).isEqualTo("java25");
        assertThat(fabric.get("status").asText()).isEqualTo("experimental");
        assertThat(fabric.get("slug").asText()).isEqualTo("fabric-26.3");
        assertThat(include.get(0).has("kind")).as("single-server entries are unchanged").isFalse();
    }

    @Test
    void aJarPinTodoSkipsTheLoaderWithItsReasonInTheMatrix() throws IOException {
        CiMatrix.Plan plan = CiMatrix.plan(load(yaml(both()).replaceFirst("sha256: \"" + SHA + "\", vanilla-sha256",
                "sha256: \"TODO\", vanilla-sha256")), Scope.NIGHTLY);

        assertThat(plan.loaders()).extracting(LoaderCell::identity).containsExactly("neoforge@26.3");
        assertThat(plan.skipped().get("fabric")).contains(CiMatrix.TODO_PINS);
    }

    @Test
    void aFileWithoutLoadersPlansTheMatrixItDidBefore() throws IOException {
        CiMatrix.Plan plan = CiMatrix.plan(load(yaml("")), Scope.NIGHTLY);

        assertThat(plan.loaders()).isEmpty();
        assertThat(CiMatrix.matrix(plan.cells()))
                .isEqualTo(CiMatrix.matrix(plan.cells(), plan.topologies(), plan.loaders()));
    }
}
