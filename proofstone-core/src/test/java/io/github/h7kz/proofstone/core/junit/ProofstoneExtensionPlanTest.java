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
        return ProofstoneExtension.planCells(type, matrices(type), CellSelector.parse(selector, "-Dtest"), planner,
                Scope.PR, VersionFamilies.none());
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
}
