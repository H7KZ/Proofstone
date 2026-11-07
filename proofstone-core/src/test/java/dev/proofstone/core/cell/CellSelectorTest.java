package dev.proofstone.core.cell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.proofstone.core.versions.VersionsException;
import java.util.List;
import org.junit.jupiter.api.Test;

class CellSelectorTest {

    private static final String SOURCE = "-Dproofstone.cells";

    @Test
    void blankOrMissingSelectorIsNotSet() {
        assertThat(CellSelector.parse(null, SOURCE).isSet()).isFalse();
        assertThat(CellSelector.parse("", SOURCE).isSet()).isFalse();
        assertThat(CellSelector.parse("   ", SOURCE).isSet()).isFalse();
    }

    @Test
    void exactVersionSelectsThatPlatform() {
        CellSelector selector = CellSelector.parse("paper@1.8.8", SOURCE);

        assertThat(selector.isSet()).isTrue();
        assertThat(selector.selects("paper")).isTrue();
        assertThat(selector.versions("paper")).containsExactly("1.8.8");
    }

    @Test
    void latestAndAllAreKeptAsTokensForThePlanner() {
        assertThat(CellSelector.parse("paper@latest", SOURCE).versions("paper")).containsExactly("latest");
        assertThat(CellSelector.parse("paper@all", SOURCE).versions("paper")).containsExactly("all");
    }

    @Test
    void severalEntriesGroupByPlatformInOrder() {
        CellSelector selector = CellSelector.parse("paper@1.8.8,folia@latest,paper@1.12.2", SOURCE);

        assertThat(selector.versions("paper")).containsExactly("1.8.8", "1.12.2");
        assertThat(selector.versions("folia")).containsExactly("latest");
    }

    @Test
    void duplicatesCollapseAndSurroundingWhitespaceIsIgnored() {
        CellSelector selector = CellSelector.parse(" paper@1.8.8 , paper@1.8.8 ", SOURCE);

        assertThat(selector.versions("paper")).containsExactly("1.8.8");
    }

    @Test
    void unnamedPlatformIsNotSelectedAndHasNoVersions() {
        CellSelector selector = CellSelector.parse("paper@latest", SOURCE);

        assertThat(selector.selects("folia")).isFalse();
        assertThat(selector.versions("folia")).isEmpty();
    }

    @Test
    void noneIsNotSetAndSelectsNothing() {
        assertThat(CellSelector.none().isSet()).isFalse();
        assertThat(CellSelector.none().selects("paper")).isFalse();
    }

    @Test
    void entryWithoutAtSignIsAnErrorThatShowsTheForm() {
        assertThatThrownBy(() -> CellSelector.parse("paper", SOURCE))
                .isInstanceOf(VersionsException.class)
                .hasMessageContaining("\"paper\"")
                .hasMessageContaining("platform@version");
    }

    @Test
    void emptyPlatformIsAnError() {
        assertThatThrownBy(() -> CellSelector.parse("@1.8.8", SOURCE))
                .isInstanceOf(VersionsException.class)
                .hasMessageContaining("\"@1.8.8\"");
    }

    @Test
    void emptyVersionIsAnError() {
        assertThatThrownBy(() -> CellSelector.parse("paper@", SOURCE))
                .isInstanceOf(VersionsException.class)
                .hasMessageContaining("\"paper@\"");
    }

    @Test
    void emptyEntryIsAnErrorInTheMiddleAndAtTheEnd() {
        assertThatThrownBy(() -> CellSelector.parse("paper@1.8.8,,folia@latest", SOURCE))
                .isInstanceOf(VersionsException.class)
                .hasMessageContaining("empty entry");
        assertThatThrownBy(() -> CellSelector.parse("paper@1.8.8,", SOURCE))
                .isInstanceOf(VersionsException.class)
                .hasMessageContaining("empty entry");
    }

    @Test
    void upperCasePlatformIsAnError() {
        assertThatThrownBy(() -> CellSelector.parse("Paper@latest", SOURCE))
                .isInstanceOf(VersionsException.class)
                .hasMessageContaining("\"Paper@latest\"");
    }

    @Test
    void twoAtSignsAreAnError() {
        assertThatThrownBy(() -> CellSelector.parse("paper@1.8.8@latest", SOURCE))
                .isInstanceOf(VersionsException.class)
                .hasMessageContaining("\"paper@1.8.8@latest\"");
    }

    @Test
    void wildcardsAndRangesAreErrors() {
        assertThatThrownBy(() -> CellSelector.parse("paper@*", SOURCE)).isInstanceOf(VersionsException.class);
        assertThatThrownBy(() -> CellSelector.parse("paper@1.8.x", SOURCE)).isInstanceOf(VersionsException.class);
    }

    @Test
    void errorNamesTheSourceSoTheUserKnowsWhichSettingIsWrong() {
        assertThatThrownBy(() -> CellSelector.parse("paper", "PROOFSTONE_CELLS"))
                .isInstanceOf(VersionsException.class)
                .hasMessageContaining("PROOFSTONE_CELLS");
    }

    @Test
    void versionsReturnsAnImmutableList() {
        List<String> versions = CellSelector.parse("paper@latest", SOURCE).versions("paper");

        assertThatThrownBy(() -> versions.add("1.8.8")).isInstanceOf(UnsupportedOperationException.class);
    }
}
