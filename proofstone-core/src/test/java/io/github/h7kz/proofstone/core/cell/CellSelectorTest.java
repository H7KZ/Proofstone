package io.github.h7kz.proofstone.core.cell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.core.versions.VersionsException;
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
    // Topology selectors (ADR-0016 section 1): proxy@version+platform@version.

    @Test
    void aTopologyEntryIsKeptApartFromTheSingleServerEntries() {
        CellSelector selector = CellSelector.parse("paper@26.3,velocity@4.3.1+paper@26.3", SOURCE);

        assertThat(selector.isSet()).isTrue();
        assertThat(selector.versions("paper")).containsExactly("26.3");
        assertThat(selector.selects("velocity")).isFalse();
        assertThat(selector.topologies()).singleElement().satisfies(topology -> {
            assertThat(topology.proxy()).isEqualTo("velocity");
            assertThat(topology.proxyVersion()).isEqualTo("4.3.1");
            assertThat(topology.backend()).isEqualTo("paper");
            assertThat(topology.backendVersion()).isEqualTo("26.3");
        });
    }

    @Test
    void aTopologyAloneIsASelectorThatIsSet() {
        assertThat(CellSelector.parse("bungeecord@2111+paper@26.3", SOURCE).isSet()).isTrue();
        assertThat(CellSelector.none().topologies()).isEmpty();
    }

    @Test
    void latestAndAllWorkOnEitherHalfAndDuplicatesCollapse() {
        CellSelector selector = CellSelector.parse(
                "velocity@latest+paper@all,velocity@all+paper@latest,velocity@latest+paper@all", SOURCE);

        assertThat(selector.topologies()).hasSize(2);
    }

    @Test
    void aMalformedTopologyEntryIsAnErrorThatQuotesIt() {
        for (String bad : List.of("velocity@4.3.1+", "+paper@26.3", "velocity@+paper@26.3", "velocity+paper@26.3",
                "velocity@4.3.1+paper", "velocity@4.3.1+paper@", "velocity@4.3.1++paper@26.3",
                "velocity@4.3.1+paper@26.3+folia@26.3", "Velocity@4.3.1+paper@26.3", "velocity@4.3.1 + paper@26.3",
                "velocity@4.3.1+paper@26.x", "velocity@4.3.1+@26.3", "@4.3.1+paper@26.3", "velocity@4.3.1+paper@26.3+")) {
            assertThatThrownBy(() -> CellSelector.parse(bad, SOURCE))
                    .as(bad)
                    .isInstanceOf(VersionsException.class)
                    .hasMessageContaining("\"" + bad.trim() + "\"");
        }
    }

    @Test
    void aTopologyEntryNamesItsFormInTheError() {
        assertThatThrownBy(() -> CellSelector.parse("velocity@4.3.1+", SOURCE))
                .isInstanceOf(VersionsException.class)
                .hasMessageContaining("proxy@version+platform@version");
    }

    @Test
    void anEmptyEntryNextToATopologyStillFails() {
        assertThatThrownBy(() -> CellSelector.parse("velocity@4.3.1+paper@26.3,", SOURCE))
                .isInstanceOf(VersionsException.class)
                .hasMessageContaining("empty entry");
    }

    @Test
    void topologiesReturnAnImmutableList() {
        List<CellSelector.Topology> topologies = CellSelector.parse("velocity@4.3.1+paper@26.3", SOURCE).topologies();

        assertThatThrownBy(() -> topologies.add(topologies.get(0))).isInstanceOf(UnsupportedOperationException.class);
    }

    // ---- loader entries (ADR-0017 section 2, T-M8-04) ----

    @Test
    void aLoaderEntryIsASingleServerIdentityOfItsOwn() {
        CellSelector selector = CellSelector.parse("fabric@26.3", SOURCE);

        assertThat(selector.isSet()).isTrue();
        assertThat(selector.loaders()).containsExactly(new CellSelector.LoaderEntry("fabric", "26.3"));
        assertThat(selector.loaders().get(0).entry()).isEqualTo("fabric@26.3");
        assertThat(selector.selects("fabric")).as("a loader is not a platform").isFalse();
        assertThat(selector.topologies()).isEmpty();
    }

    @Test
    void loaderEntriesMixWithPlatformsAndKeepOrderWithoutDuplicates() {
        CellSelector selector = CellSelector.parse("paper@latest, neoforge@all,fabric@26.3,fabric@26.3,fabric@latest",
                SOURCE);

        assertThat(selector.versions("paper")).containsExactly("latest");
        assertThat(selector.loaders()).extracting(CellSelector.LoaderEntry::entry)
                .containsExactly("neoforge@all", "fabric@26.3", "fabric@latest");
    }

    @Test
    void aSelectorNamingOnlyALoaderStillCountsAsSet() {
        assertThat(CellSelector.parse("neoforge@latest", SOURCE).isSet()).isTrue();
    }

    @Test
    void malformedLoaderEntriesFailLoudlyWithTheEntryQuoted() {
        for (String bad : List.of("fabric@", "fabric", "fabric@26.x", "fabric@26.3.", "Fabric@26.3", "fabric@@26.3",
                "fabric@26.3@1", "neoforge@v26.3", "fabric@LATEST", "@26.3", "fabric@26.3,,neoforge@26.3")) {
            assertThatThrownBy(() -> CellSelector.parse(bad, SOURCE))
                    .as(bad)
                    .isInstanceOf(VersionsException.class);
        }
        assertThatThrownBy(() -> CellSelector.parse("fabric@26.x", SOURCE))
                .hasMessageContaining("\"fabric@26.x\"").hasMessageContaining("platform@version");
    }

    @Test
    void aLoaderIsNeverAHalfOfATopologyEntry() {
        for (String bad : List.of("fabric@26.3+paper@26.3", "velocity@4.3.1+fabric@26.3", "neoforge@26.3+paper@26.3",
                "velocity@4.3.1+neoforge@latest")) {
            assertThatThrownBy(() -> CellSelector.parse(bad, SOURCE))
                    .as(bad)
                    .isInstanceOf(VersionsException.class)
                    .hasMessageContainingAll("\"" + bad + "\"", "loader", "single-server", "ADR-0017");
        }
    }

    @Test
    void loadersReturnAnImmutableList() {
        List<CellSelector.LoaderEntry> loaders = CellSelector.parse("fabric@26.3", SOURCE).loaders();

        assertThatThrownBy(() -> loaders.add(loaders.get(0))).isInstanceOf(UnsupportedOperationException.class);
        assertThat(CellSelector.none().loaders()).isEmpty();
    }

    @Test
    void anUnknownLoaderIdIsThePlannersErrorNotASilentSkip() {
        // "quilt" is not a loader id the selector knows, so it parses as a platform entry; the planner refuses it.
        CellSelector selector = CellSelector.parse("quilt@26.3", SOURCE);

        assertThat(selector.loaders()).isEmpty();
        assertThat(selector.selects("quilt")).isTrue();
    }

    // ---- Geyser entries (ADR-0018 section 8, T-M9-04) ----

    @Test
    void aGeyserEntryIsAnIdentityOfItsOwnKeptApartFromTopologies() {
        CellSelector selector = CellSelector.parse("geyser@2.12.1+paper@26.3", SOURCE);

        assertThat(selector.isSet()).isTrue();
        assertThat(selector.geysers()).containsExactly(new CellSelector.GeyserEntry("2.12.1", "paper", "26.3"));
        assertThat(selector.geysers().get(0).entry()).isEqualTo("geyser@2.12.1+paper@26.3");
        assertThat(selector.topologies()).as("a Geyser cell is not a topology cell").isEmpty();
        assertThat(selector.selects("geyser")).isFalse();
        assertThat(selector.loaders()).isEmpty();
    }

    @Test
    void geyserEntriesTakeLatestAndAllAndMixWithOtherEntriesWithoutDuplicates() {
        CellSelector selector = CellSelector.parse(
                "paper@latest, geyser@latest+paper@latest,velocity@4.3.1+paper@26.3,geyser@all+paper@all,"
                        + "geyser@latest+paper@latest,fabric@26.3", SOURCE);

        assertThat(selector.versions("paper")).containsExactly("latest");
        assertThat(selector.geysers()).extracting(CellSelector.GeyserEntry::entry)
                .containsExactly("geyser@latest+paper@latest", "geyser@all+paper@all");
        assertThat(selector.topologies()).extracting(CellSelector.Topology::entry)
                .containsExactly("velocity@4.3.1+paper@26.3");
        assertThat(selector.loaders()).extracting(CellSelector.LoaderEntry::entry).containsExactly("fabric@26.3");
    }

    @Test
    void geyserEntriesReturnAnImmutableList() {
        List<CellSelector.GeyserEntry> geysers = CellSelector.parse("geyser@2.12.1+paper@26.3", SOURCE).geysers();

        assertThatThrownBy(() -> geysers.add(geysers.get(0))).isInstanceOf(UnsupportedOperationException.class);
        assertThat(CellSelector.none().geysers()).isEmpty();
    }

    @Test
    void aGeyserIdOnTheRightOfThePlusIsRefusedNamingTheAdr() {
        for (String bad : List.of("paper@26.3+geyser@2.12.1", "velocity@4.3.1+geyser@2.12.1", "fabric@26.3+geyser@latest",
                "geyser@2.12.1+geyser@2.12.1")) {
            assertThatThrownBy(() -> CellSelector.parse(bad, SOURCE))
                    .as(bad)
                    .isInstanceOf(VersionsException.class)
                    .hasMessageContainingAll("\"" + bad + "\"", "geyser", "left of", "ADR-0018");
        }
    }

    @Test
    void aGeyserEntryWithoutAPairingIsRefused() {
        assertThatThrownBy(() -> CellSelector.parse("geyser@2.12.1", SOURCE))
                .isInstanceOf(VersionsException.class)
                .hasMessageContainingAll("\"geyser@2.12.1\"", "geyser@version+paper@version", "ADR-0018");
    }

    @Test
    void aLoaderIsNeverTheBackendOfAGeyserEntry() {
        assertThatThrownBy(() -> CellSelector.parse("geyser@2.12.1+fabric@26.3", SOURCE))
                .isInstanceOf(VersionsException.class).hasMessageContainingAll("\"geyser@2.12.1+fabric@26.3\"", "loader");
    }

    @Test
    void malformedGeyserEntriesFailLoudlyWithTheEntryQuoted() {
        for (String bad : List.of("geyser@+paper@26.3", "geyser@2.12.1+paper", "geyser@2.12.1+paper@", "geyser@2.12.1+@26.3",
                "geyser@2.12.x+paper@26.3", "Geyser@2.12.1+paper@26.3", "geyser@2.12.1+paper@26.3+paper@26.3",
                "geyser@2.12.1+Paper@26.3", "geyser@2.12.1++paper@26.3", "geyser@2.12.1+paper@v26.3",
                "geyser@LATEST+paper@26.3", "geyser@2.12.1+paper@26.3,", ",geyser@2.12.1+paper@26.3")) {
            assertThatThrownBy(() -> CellSelector.parse(bad, SOURCE))
                    .as(bad)
                    .isInstanceOf(VersionsException.class);
        }
        assertThatThrownBy(() -> CellSelector.parse("geyser@2.12.x+paper@26.3", SOURCE))
                .hasMessageContainingAll("\"geyser@2.12.x+paper@26.3\"", "geyser@version+paper@version");
    }
}
