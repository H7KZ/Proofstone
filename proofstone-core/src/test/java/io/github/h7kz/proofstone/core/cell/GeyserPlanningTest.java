package io.github.h7kz.proofstone.core.cell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.h7kz.proofstone.core.versions.GeyserRow;
import io.github.h7kz.proofstone.core.versions.Status;
import io.github.h7kz.proofstone.core.versions.VersionsCatalog;
import io.github.h7kz.proofstone.core.versions.VersionsException;
import io.github.h7kz.proofstone.core.versions.VersionsFixture;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Geyser cells (ADR-0018, T-M9-04): schema 5 loading, validation, the planner and the matrix, on a small fixture. */
class GeyserPlanningTest {

    static final String SHA = "ef".repeat(32);
    static final String SNAPSHOT = "3.0.0.Beta15-20261116.161745-6";
    static final String PAIRING = "{ platform: paper, mc: \"26.3\", status: experimental }";

    @TempDir
    Path dir;

    /** Schema 5 on the topology fixture's paper rows (1.21.11 protocol 774, 26.3 protocol 777), plus the bedrock driver. */
    static String yaml(String geyser) {
        return TopologyPlanningTest.yaml("").replace("schema: 3", "schema: 5").replace("\nplatforms:\n",
                "\n  bedrock: { library: \"org.cloudburstmc.protocol:bedrock-connection\", version: \"" + SNAPSHOT
                        + "\", protocol: 2225, game-version: \"1.26.60\" }\nplatforms:\n") + geyser;
    }

    static String row() {
        return """
                geyser:
                  rows:
                    - version: "2.12.1"
                      build: 1303
                      sha256: "SHA"
                      floodgate: { version: "2.2.5", build: 141, sha256: "SHA" }
                      bedrock-protocol: 2225
                      java: java25
                      status: experimental
                      backends:
                        - { platform: paper, mc: "26.3", status: experimental }
                      observed: "spike"
                      notes: "n"
                """.replace("SHA", SHA);
    }

    /** The row and its pairing as a promotion leaves them; the driver pin is still a snapshot. */
    static String requiredRow() {
        return row().replace("status: experimental", "status: required");
    }

    /** A promoted file: required row and pairing, and the driver is a release. */
    static String promoted() {
        return yaml(requiredRow()).replace(SNAPSHOT, "3.0.0");
    }

    private VersionsCatalog load(String yaml) throws IOException {
        return VersionsFixture.load(dir, yaml);
    }

    private CellPlanner planner(String geyser) throws IOException {
        return new CellPlanner(load(yaml(geyser)));
    }

    private void rejects(String yaml, String... fragments) {
        assertThatThrownBy(() -> load(yaml)).isInstanceOf(VersionsException.class).hasMessageContainingAll(fragments);
    }

    // ---- catalog ----

    @Test
    void schemaFiveLoadsTheRowAndTheDriverPinAndKeepsPlatformsAsTheyWere() throws IOException {
        VersionsCatalog catalog = load(yaml(row()));

        assertThat(catalog.platformIds()).containsExactly("paper");
        assertThat(catalog.proxyIds()).isEmpty();
        assertThat(catalog.loaderIds()).isEmpty();
        assertThat(catalog.geyserRows()).hasSize(1);
        GeyserRow geyser = catalog.geyserRows().get(0);
        assertThat(geyser.version()).isEqualTo("2.12.1");
        assertThat(geyser.build()).isEqualTo(1303);
        assertThat(geyser.sha256()).isEqualTo(SHA);
        assertThat(geyser.floodgate().version()).isEqualTo("2.2.5");
        assertThat(geyser.floodgate().build()).isEqualTo(141);
        assertThat(geyser.floodgate().sha256()).isEqualTo(SHA);
        assertThat(geyser.bedrockProtocol()).isEqualTo(2225);
        assertThat(geyser.java()).isEqualTo("java25");
        assertThat(geyser.status()).isEqualTo(Status.EXPERIMENTAL);
        assertThat(geyser.backends()).singleElement().satisfies(pairing -> {
            assertThat(pairing.platform()).isEqualTo("paper");
            assertThat(pairing.mc()).isEqualTo("26.3");
            assertThat(pairing.status()).isEqualTo(Status.EXPERIMENTAL);
        });
        assertThat(catalog.bedrockDriver()).isPresent().get().satisfies(driver -> {
            assertThat(driver.version()).isEqualTo(SNAPSHOT);
            assertThat(driver.protocol()).isEqualTo(2225);
            assertThat(driver.gameVersion()).isEqualTo("1.26.60");
        });
    }

    @Test
    void aSchemaFourFileIsAValidSchemaFiveFileAndHasNoGeyser() throws IOException {
        VersionsCatalog catalog = load(TopologyPlanningTest.yaml("").replace("schema: 3", "schema: 4"));

        assertThat(catalog.geyserRows()).isEmpty();
        assertThat(catalog.bedrockDriver()).isEmpty();
        assertThat(load(yaml("")).geyserRows()).isEmpty();
    }

    @Test
    void loadersAndProxiesStillLoadInSchemaFive() throws IOException {
        VersionsCatalog catalog = load(
                yaml(TopologyPlanningTest.both() + LoaderPlanningTest.both() + row()));

        assertThat(catalog.proxyIds()).containsExactly("velocity", "bungeecord");
        assertThat(catalog.loaderIds()).containsExactly("fabric", "neoforge");
        assertThat(catalog.geyserRows()).hasSize(1);
    }

    @Test
    void geyserNeedsSchemaFive() {
        rejects(yaml(row()).replace("schema: 5", "schema: 4"), "geyser", "schema: 5", "ADR-0018");
        rejects(yaml(row()).replace("schema: 5", "schema: 2"), "geyser", "schema: 5");
        rejects(yaml("").replace("schema: 5", "schema: 4"), "drivers.bedrock", "schema: 5");
    }

    @Test
    void geyserRowsNeedTheBedrockDriverAndItsFields() {
        rejects(yaml(row()).replaceFirst("  bedrock: [^\n]*\n", ""), "geyser", "drivers.bedrock");
        rejects(yaml(row()).replace(", protocol: 2225, game-version", ", game-version"), "protocol");
        rejects(yaml(row()).replace("library: \"org.cloudburstmc.protocol:bedrock-connection\", ", ""), "library");
    }

    @Test
    void geyserIsAReservedId() {
        rejects(TopologyPlanningTest.yaml("").replace("schema: 3", "schema: 5").replace("platforms:\n",
                "platforms:\n  geyser:\n    host: { image: \"x\", digests: { java25: \"sha256:25\" } }\n"
                        + "    rows: []\n"), "geyser", "reserved");
    }

    @Test
    void duplicateVersionsAndDuplicatePairings() {
        String second = row().replace("geyser:\n  rows:\n", "").replace("build: 1303", "build: 1304");
        rejects(yaml(row() + second), "\"2.12.1\" appears twice");
        rejects(yaml(row().replace(PAIRING, PAIRING + "\n        - " + PAIRING)), "paper@26.3", "listed twice");
    }

    @Test
    void everyRowNeedsItsPins() {
        rejects(yaml(row().replace("      build: 1303\n", "")), "build");
        rejects(yaml(row().replaceFirst("      sha256: \"" + SHA + "\"\n", "")), "sha256");
        rejects(yaml(row().replace("      floodgate: { version: \"2.2.5\", build: 141, sha256: \"" + SHA + "\" }\n", "")),
                "floodgate");
        rejects(yaml(row().replace("build: 141, ", "")), "build");
        rejects(yaml(row().replace(", sha256: \"" + SHA + "\" }", " }")), "sha256");
        rejects(yaml(row().replace("      java: java25\n", "")), "java");
        rejects(yaml(row().replace("      bedrock-protocol: 2225\n", "")), "bedrock-protocol");
        rejects(yaml(row().replaceFirst("sha256: \"" + SHA + "\"", "sha256: \"abc\"")), "sha256");
    }

    @Test
    void aRequiredRowHasNoTodo() {
        rejects(yaml(requiredRow().replace("build: 141", "build: TODO")), "required row must not contain TODO");
        rejects(yaml(requiredRow().replaceFirst("sha256: \"" + SHA + "\"", "sha256: \"TODO\"")),
                "required row must not contain TODO");
    }

    @Test
    void anExperimentalRowMayHoldATodoPin() throws IOException {
        VersionsCatalog catalog = load(yaml(row().replaceFirst("sha256: \"" + SHA + "\"", "sha256: \"TODO\"")));

        assertThat(catalog.geyserRows().get(0).sha256()).isNull();
    }

    @Test
    void theJavaTagNeedsAHostDigestOnThePaperPlatform() {
        rejects(yaml(row().replace("java: java25", "java: java17")), "java17", "host.digests");
    }

    @Test
    void aPairingNamesAnExistingPaperRowOnly() {
        rejects(yaml(row().replace("mc: \"26.3\", status", "mc: \"9.9\", status")), "paper@9.9", "not a row");
        rejects(yaml(row().replace("platform: paper", "platform: folia")), "folia", "only paper");
    }

    @Test
    void aBedrockProtocolThatIsNotTheDriversIsRefused() {
        rejects(yaml(row().replace("bedrock-protocol: 2225", "bedrock-protocol: 2193")), "bedrock-protocol", "2193",
                "2225");
    }

    @Test
    void aBridgedPairingIsRefusedNamingTheAdr() {
        // paper 1.21.11 is protocol 774: the mcpl driver speaks 777 (26.3), so the pairing would need ViaProxy.
        rejects(yaml(row().replace("mc: \"26.3\", status", "mc: \"1.21.11\", status")),
                "geyser@2.12.1+paper@1.21.11", "bridged", "ADR-0018");
    }

    @Test
    void aRequiredPairingNeedsARequiredGeyserRow() {
        String pairingOnly = row().replace(PAIRING, PAIRING.replace("experimental", "required"));

        rejects(yaml(pairingOnly).replace(SNAPSHOT, "3.0.0"), "geyser@2.12.1+paper@26.3", "required Geyser row");
    }

    @Test
    void aRequiredPairingNeedsAReleaseDriverNotASnapshot() {
        rejects(yaml(requiredRow()), "geyser@2.12.1+paper@26.3", "snapshot", "ADR-0018");
        rejects(yaml(requiredRow()).replace(SNAPSHOT, "3.0.0-SNAPSHOT"), "snapshot");
    }

    @Test
    void aRequiredPairingOnAReleaseDriverLoads() throws IOException {
        VersionsCatalog catalog = load(promoted());

        assertThat(catalog.geyserRows().get(0).backends().get(0).status()).isEqualTo(Status.REQUIRED);
    }

    // ---- planner ----

    @Test
    void anExactRowAndPairingPlanOneCell() throws IOException {
        GeyserCell cell = planner(row()).planGeysers(List.of("2.12.1"), "paper", List.of("26.3"), Scope.PR).get(0);

        assertThat(cell.identity()).isEqualTo("geyser@2.12.1+paper@26.3");
        assertThat(cell.version()).isEqualTo("2.12.1");
        assertThat(cell.build()).isEqualTo(1303);
        assertThat(cell.sha256()).isEqualTo(SHA);
        assertThat(cell.floodgate().version()).isEqualTo("2.2.5");
        assertThat(cell.floodgate().build()).isEqualTo(141);
        assertThat(cell.floodgate().sha256()).isEqualTo(SHA);
        assertThat(cell.bedrockProtocol()).isEqualTo(2225);
        assertThat(cell.java()).isEqualTo("java25");
        assertThat(cell.hostImage()).isEqualTo("itzg/minecraft-server");
        assertThat(cell.hostDigest()).isEqualTo("sha256:25");
        assertThat(cell.backend().platform()).isEqualTo("paper");
        assertThat(cell.backend().mc()).isEqualTo("26.3");
        assertThat(cell.status()).isEqualTo(Status.EXPERIMENTAL);
        assertThat(GeyserCell.identity("2.12.1", "paper", "26.3")).isEqualTo(cell.identity());
    }

    @Test
    void theCellStatusIsTheWeakestOfRowPairingAndBackend() throws IOException {
        String retiredPairing = row().replace(PAIRING, PAIRING.replace("experimental", "retired"));

        assertThat(planner(retiredPairing).planGeysers(List.of("2.12.1"), "paper", List.of("26.3"), Scope.PR).get(0)
                .status()).isEqualTo(Status.RETIRED);
    }

    @Test
    void latestOnTheLeftIsTheHighestRequiredRowAndAnErrorWithNone() throws IOException {
        assertThatThrownBy(() -> planner(row()).planGeysers(List.of("latest"), "paper", List.of("26.3"), Scope.NIGHTLY))
                .isInstanceOf(VersionsException.class).hasMessageContainingAll("latest", "geyser", "no required row");
        assertThat(new CellPlanner(load(promoted())).planGeysers(List.of("latest"), "paper", List.of("latest"), Scope.PR))
                .extracting(GeyserCell::identity).containsExactly("geyser@2.12.1+paper@26.3");
    }

    @Test
    void latestOnTheRightResolvesAsForPaperAndMustBeListed() throws IOException {
        // paper latest is 26.3 (the highest required row) and the row lists it.
        assertThat(planner(row()).planGeysers(List.of("2.12.1"), "paper", List.of("latest"), Scope.PR))
                .extracting(GeyserCell::identity).containsExactly("geyser@2.12.1+paper@26.3");
        // A row that lists only 1.21.11 (made native by its protocol) is not paired with the latest.
        String other = yaml(row().replace("mc: \"26.3\"", "mc: \"1.21.11\"")).replace("protocol: 774", "protocol: 777");
        assertThatThrownBy(() -> new CellPlanner(load(other)).planGeysers(List.of("2.12.1"), "paper",
                List.of("latest"), Scope.PR))
                .isInstanceOf(VersionsException.class)
                .hasMessageContainingAll("geyser@2.12.1", "not paired with paper@26.3", "paper@1.21.11");
    }

    @Test
    void allFollowsTheScopeRule() throws IOException {
        CellPlanner planner = planner(row());

        assertThatThrownBy(() -> planner.planGeysers(List.of("all"), "paper", List.of("all"), Scope.PR))
                .isInstanceOf(VersionsException.class).hasMessageContaining("no geyser cell in scope");
        assertThat(planner.planGeysers(List.of("all"), "paper", List.of("all"), Scope.NIGHTLY))
                .extracting(GeyserCell::identity).containsExactly("geyser@2.12.1+paper@26.3");
        assertThat(planner.planGeysers(List.of("2.12.1"), "paper", List.of("26.3"), Scope.PR))
                .as("an exact cell runs in any scope").hasSize(1);
    }

    @Test
    void anUnknownVersionAnUnlistedPairingAndAnEmptyRequestAreErrors() throws IOException {
        CellPlanner planner = planner(row());

        assertThatThrownBy(() -> planner.planGeysers(List.of("9.9.9"), "paper", List.of("26.3"), Scope.PR))
                .isInstanceOf(VersionsException.class)
                .hasMessageContainingAll("unknown version \"9.9.9\"", "geyser", "valid: 2.12.1");
        assertThatThrownBy(() -> planner.planGeysers(List.of("2.12.1"), "paper", List.of("1.21.11"), Scope.PR))
                .isInstanceOf(VersionsException.class)
                .hasMessageContainingAll("not paired with paper@1.21.11", "paper@26.3");
        assertThatThrownBy(() -> planner.planGeysers(List.of("2.12.1"), "folia", List.of("26.3"), Scope.PR))
                .isInstanceOf(VersionsException.class).hasMessageContainingAll("folia", "paper");
        assertThatThrownBy(() -> planner.planGeysers(List.of(), "paper", List.of("26.3"), Scope.PR))
                .isInstanceOf(VersionsException.class).hasMessageContaining("no versions requested");
        assertThatThrownBy(() -> planner.planGeysers(List.of("2.12.1"), "paper", List.of(), Scope.PR))
                .isInstanceOf(VersionsException.class).hasMessageContaining("no versions requested");
    }

    @Test
    void aTodoInTheGeyserOrFloodgateJarIsNotPlanned() throws IOException {
        assertThatThrownBy(() -> planner(row().replaceFirst("sha256: \"" + SHA + "\"", "sha256: \"TODO\""))
                .planGeysers(List.of("2.12.1"), "paper", List.of("26.3"), Scope.PR))
                .isInstanceOf(VersionsException.class).hasMessageContainingAll("TODO pins", "sha256");
        assertThatThrownBy(() -> planner(row().replace("build: 141", "build: TODO"))
                .planGeysers(List.of("2.12.1"), "paper", List.of("26.3"), Scope.PR))
                .isInstanceOf(VersionsException.class).hasMessageContainingAll("TODO pins", "floodgate build");
    }

    @Test
    void platformsPlanAsBeforeOnAFileWithGeyser() throws IOException {
        assertThat(planner(row()).plan("paper", List.of("26.3"))).singleElement()
                .satisfies(cell -> assertThat(cell.mc()).isEqualTo("26.3"));
    }

    // ---- matrix ----

    @Test
    void prScopePlansNoExperimentalGeyserAndSaysWhy() throws IOException {
        CiMatrix.Plan plan = CiMatrix.plan(load(yaml(row())), Scope.PR);

        assertThat(plan.geysers()).isEmpty();
        assertThat(plan.skipped().get("geyser")).contains("no required");
        assertThat(CiMatrix.matrix(plan.cells(), plan.topologies(), plan.loaders(), plan.geysers()).get("include"))
                .hasSize(2);
    }

    @Test
    void nightlyScopeAddsTheGeyserCellLast() throws IOException {
        String base = yaml("");
        CiMatrix.Plan plan = CiMatrix.plan(load(base + TopologyPlanningTest.both() + LoaderPlanningTest.both() + row()),
                Scope.NIGHTLY);

        assertThat(plan.geysers()).extracting(GeyserCell::identity).containsExactly("geyser@2.12.1+paper@26.3");
        JsonNode include = CiMatrix.matrix(plan.cells(), plan.topologies(), plan.loaders(), plan.geysers())
                .get("include");
        assertThat(include).hasSize(plan.cells().size() + plan.topologies().size() + plan.loaders().size() + 1);
        JsonNode geyser = include.get(include.size() - 1);
        assertThat(geyser.get("cell").asText()).isEqualTo("geyser@2.12.1+paper@26.3");
        assertThat(geyser.get("platform").asText()).isEqualTo("geyser");
        assertThat(geyser.get("kind").asText()).isEqualTo("geyser");
        assertThat(geyser.get("version").asText()).isEqualTo("2.12.1");
        assertThat(geyser.get("build").asInt()).isEqualTo(1303);
        assertThat(geyser.get("mc").asText()).isEqualTo("26.3");
        assertThat(geyser.get("java").asText()).isEqualTo("java25");
        assertThat(geyser.get("status").asText()).isEqualTo("experimental");
        assertThat(geyser.get("slug").asText()).isEqualTo("geyser-2.12.1-paper-26.3");
    }

    @Test
    void aTodoPinSkipsGeyserWithItsReason() throws IOException {
        CiMatrix.Plan plan = CiMatrix.plan(
                load(yaml(row().replaceFirst("sha256: \"" + SHA + "\"", "sha256: \"TODO\""))), Scope.NIGHTLY);

        assertThat(plan.geysers()).isEmpty();
        assertThat(plan.skipped().get("geyser")).contains(CiMatrix.TODO_PINS);
    }

    @Test
    void aFileWithoutGeyserPlansTheMatrixItDidBefore() throws IOException {
        CiMatrix.Plan plan = CiMatrix.plan(load(yaml("")), Scope.NIGHTLY);

        assertThat(plan.geysers()).isEmpty();
        assertThat(plan.skipped()).doesNotContainKey("geyser");
        assertThat(CiMatrix.matrix(plan.cells(), plan.topologies(), plan.loaders(), plan.geysers()))
                .isEqualTo(CiMatrix.matrix(plan.cells(), plan.topologies(), plan.loaders()));
    }
}
