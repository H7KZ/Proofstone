package dev.proofstone.core.cell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.proofstone.core.versions.Status;
import dev.proofstone.core.versions.VersionsCatalog;
import dev.proofstone.core.versions.VersionsException;
import dev.proofstone.core.versions.VersionsFixture;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CellPlannerTest {

    @TempDir
    Path dir;

    private CellPlanner planner(String yaml) throws IOException {
        return new CellPlanner(VersionsFixture.load(dir, yaml));
    }

    @Test
    void exactVersionBecomesCellWithPinsAndJavaTag() throws IOException {
        List<Cell> cells = planner(VersionsFixture.valid()).plan("paper", List.of("1.10.2"));

        assertThat(cells).hasSize(1);
        Cell cell = cells.get(0);
        assertThat(cell.platform()).isEqualTo("paper");
        assertThat(cell.mc()).isEqualTo("1.10.2");
        assertThat(cell.build()).isEqualTo(2);
        assertThat(cell.sha256()).isEqualTo("a2");
        assertThat(cell.hostImage()).isEqualTo("itzg/minecraft-server");
        assertThat(cell.hostDigest()).isEqualTo("sha256:08");
        assertThat(cell.status()).isEqualTo(Status.REQUIRED);
        assertThat(cell.java()).isEqualTo("java8");
        assertThat(cell.notes()).isEmpty();
    }

    @Test
    void latestPicksHighestRequiredRowNumerically() throws IOException {
        // 1.10.2 beats 1.9.4 numerically; 26.3 is the highest overall but only experimental.
        List<Cell> cells = planner(VersionsFixture.valid()).plan("paper", List.of("latest"));

        assertThat(cells).extracting(Cell::mc).containsExactly("1.20.4");
        assertThat(cells.get(0).notes()).isEmpty();
    }

    @Test
    void latestFallsBackToHighestExperimentalWithWarningWhenNoRequiredRows() throws IOException {
        String yaml = VersionsFixture.valid().replace("status: required", "status: experimental");

        Cell cell = planner(yaml).plan("paper", List.of("latest")).get(0);

        assertThat(cell.mc()).isEqualTo("26.3");
        assertThat(cell.notes()).singleElement().asString().contains("no required row");
    }

    @Test
    void javaRulesResolveEdgesAndRowOverrideWins() throws IOException {
        CellPlanner planner = planner(VersionsFixture.valid());

        assertThat(planner.plan("paper", List.of("1.17.1")).get(0).java()).isEqualTo("java16");
        assertThat(planner.plan("paper", List.of("1.20.4")).get(0).java()).isEqualTo("java17");
        assertThat(planner.plan("paper", List.of("1.20.5")).get(0).java()).isEqualTo("java21");
        assertThat(planner.plan("paper", List.of("26.1")).get(0).java()).isEqualTo("java25");
        assertThat(planner.plan("paper", List.of("26.3")).get(0).java()).isEqualTo("java25");
        assertThat(planner.plan("paper", List.of("1.16.5")).get(0).java()).isEqualTo("java11");
    }

    @Test
    void retiredRowIsAllowedOnlyWhenRequestedExplicitlyAndIsNoted() throws IOException {
        Cell cell = planner(VersionsFixture.valid()).plan("paper", List.of("1.8.8")).get(0);

        assertThat(cell.status()).isEqualTo(Status.RETIRED);
        assertThat(cell.notes()).singleElement().asString().contains("retired");
    }

    @Test
    void unknownVersionFailsAndListsValidVersions() throws IOException {
        CellPlanner planner = planner(VersionsFixture.valid());

        assertThatThrownBy(() -> planner.plan("paper", List.of("1.99")))
                .isInstanceOf(VersionsException.class)
                .hasMessageContaining("1.99")
                .hasMessageContaining("1.20.4");
    }

    @Test
    void unknownPlatformFailsAndListsPlatforms() throws IOException {
        CellPlanner planner = planner(VersionsFixture.valid());

        assertThatThrownBy(() -> planner.plan("folia", List.of("latest")))
                .isInstanceOf(VersionsException.class)
                .hasMessageContaining("folia")
                .hasMessageContaining("paper");
    }

    @Test
    void emptyRequestFails() throws IOException {
        CellPlanner planner = planner(VersionsFixture.valid());

        assertThatThrownBy(() -> planner.plan("paper", List.of()))
                .isInstanceOf(VersionsException.class);
    }

    @Test
    void chosenRowWithTodoPinFailsBeforeAnyContainerStarts() throws IOException {
        // Was the seed row 1.21.11. version-watch (T-M3-01) pins every seed row, so the TODO now comes from this fixture.
        String yaml = VersionsFixture.valid().replace("sha256: \"a8\"", "sha256: TODO");

        assertThatThrownBy(() -> planner(yaml).plan("paper", List.of("26.3")))
                .isInstanceOf(VersionsException.class)
                .hasMessageContaining("TODO")
                .hasMessageContaining("sha256");
    }

    @Test
    void eachCellTakesTheHostDigestOfItsOwnJavaTag() throws IOException {
        List<Cell> cells = planner(VersionsFixture.valid()).plan("paper", List.of("1.10.2", "1.17.1", "1.20.4", "26.3"));

        assertThat(cells).extracting(Cell::java).containsExactly("java8", "java16", "java17", "java25");
        assertThat(cells).extracting(Cell::hostDigest)
                .containsExactly("sha256:08", "sha256:16", "sha256:17", "sha256:25");
    }

    @Test
    void todoDigestForTheCellsJavaTagFailsAndNamesTheTag() throws IOException {
        String yaml = VersionsFixture.valid().replace("java17: \"sha256:17\"", "java17: TODO");

        assertThatThrownBy(() -> planner(yaml).plan("paper", List.of("1.20.4")))
                .isInstanceOf(VersionsException.class)
                .hasMessageContaining("host digest for java17");
        // Another tag is unaffected: there is no fallback and no cross-talk.
        assertThat(planner(yaml).plan("paper", List.of("26.3"))).hasSize(1);
    }

    @Test
    void allInPrScopeTakesOnlyRequiredRowsAndNeverRetired()throws IOException {
        List<Cell> cells = planner(VersionsFixture.valid()).plan("paper", List.of("all"), Scope.PR, VersionFamilies.none());

        assertThat(cells).extracting(Cell::mc).containsExactly("1.9.4", "1.10.2", "1.20.4");
    }

    @Test
    void allInNightlyScopeTakesRequiredAndExperimentalButNeverRetired() throws IOException {
        List<Cell> cells = planner(VersionsFixture.valid())
                .plan("paper", List.of("all"), Scope.NIGHTLY, VersionFamilies.none());

        assertThat(cells).extracting(Cell::mc)
                .containsExactly("1.9.4", "1.10.2", "1.16.5", "1.17.1", "1.20.4", "1.20.5", "26.1", "26.3");
    }

    @Test
    void allMergesWithExplicitVersionsWithoutDuplicatingCells() throws IOException {
        List<Cell> cells = planner(VersionsFixture.valid())
                .plan("paper", List.of("all", "1.8.8", "1.9.4"), Scope.PR, VersionFamilies.none());

        assertThat(cells).extracting(Cell::mc).containsExactly("1.9.4", "1.10.2", "1.20.4", "1.8.8");
    }

    @Test
    void experimentalRowIsRunInPrOnlyWhenNamedExplicitly() throws IOException {
        CellPlanner planner = planner(VersionsFixture.valid());

        assertThat(planner.plan("paper", List.of("26.3"), Scope.PR, VersionFamilies.none()))
                .extracting(Cell::mc).containsExactly("26.3");
    }

    @Test
    void familiesAddTheNewestVersionOfEachFamilyInPrAndEveryFamilyVersionInNightly() throws IOException {
        VersionsCatalog catalog = VersionsFixture.load(dir, VersionsFixture.valid());
        CellPlanner planner = new CellPlanner(catalog);
        VersionFamilies families = VersionFamilies.parse(
                "version-families:\n  paper:\n    netty: [\"1.9.4\", \"1.16.5\"]\n", catalog);

        assertThat(planner.plan("paper", List.of("all"), Scope.PR, families))
                .extracting(Cell::mc).containsExactly("1.9.4", "1.10.2", "1.16.5", "1.20.4");
        assertThat(planner.plan("paper", List.of("all"), Scope.NIGHTLY, families))
                .extracting(Cell::mc)
                .containsExactly("1.9.4", "1.10.2", "1.16.5", "1.17.1", "1.20.4", "1.20.5", "26.1", "26.3");
    }

    @Test
    void familyVersionThatIsNotARowOfThePlatformFailsWhenTheFileIsRead() throws IOException {
        VersionsCatalog catalog = VersionsFixture.load(dir, VersionsFixture.valid());

        assertThatThrownBy(() -> VersionFamilies.parse(
                "version-families:\n  paper:\n    netty: [\"1.99\"]\n", catalog))
                .isInstanceOf(VersionsException.class)
                .hasMessageContaining("1.99");
    }

    @Test
    void allFailsWhenNoRowIsInScope() throws IOException {
        String yaml = VersionsFixture.valid().replace("status: required", "status: experimental");

        assertThatThrownBy(() -> planner(yaml).plan("paper", List.of("all"), Scope.PR, VersionFamilies.none()))
                .isInstanceOf(VersionsException.class)
                .hasMessageContaining("all")
                .hasMessageContaining("paper")
                .hasMessageContaining("PR");
    }

    @Test
    void allFailsOnAnUnpinnedRowThatIsInScope() throws IOException {
        String yaml = VersionsFixture.valid()
                .replace("build: 1, sha256: \"a1\", status: required", "build: 1, sha256: TODO, status: experimental");

        assertThatThrownBy(() -> planner(yaml).plan("paper", List.of("all"), Scope.NIGHTLY, VersionFamilies.none()))
                .isInstanceOf(VersionsException.class)
                .hasMessageContaining("paper@1.9.4")
                .hasMessageContaining("sha256");
        // Outside PR scope the same row is not selected, so it does not fail the PR run.
        assertThat(planner(yaml).plan("paper", List.of("all"), Scope.PR, VersionFamilies.none()))
                .extracting(Cell::mc).containsExactly("1.10.2", "1.20.4");
    }

    @Test
    void noJavaRuleMatchFails() throws IOException {
        String yaml = VersionsFixture.valid()
                .replace("  - { range: \"<=1.16.5\", image: java8 }\n", "")
                .replace("  - { range: \"1.17.x\", image: java16 }\n", "")
                .replace("  - { range: \">=1.20.5 <26\", image: java21 }\n", "")
                .replace("  - { range: \">=26\", image: java25 }\n", "");

        assertThatThrownBy(() -> planner(yaml).plan("paper", List.of("1.9.4")))
                .isInstanceOf(VersionsException.class)
                .hasMessageContaining("no java-rules entry matches 1.9.4");
    }
}
