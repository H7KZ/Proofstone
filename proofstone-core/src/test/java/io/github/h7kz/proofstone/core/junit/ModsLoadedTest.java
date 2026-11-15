package io.github.h7kz.proofstone.core.junit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Loaded or loud for mods (ADR-0017 section 4, T-M8-13): every declared id must be in {@code GET /v1/mods}. */
class ModsLoadedTest {

    private static JsonNode reply(String json) throws Exception {
        return new ObjectMapper().readTree(json);
    }

    @Test
    void aDeclaredModTheAgentListsIsLoaded() throws Exception {
        JsonNode mods = reply("{\"mods\":[{\"id\":\"fabric-api\",\"version\":\"1\"},{\"id\":\"proofstone_example\","
                + "\"version\":\"0.0.1\"}]}");

        assertThat(ModsLoaded.invalid(List.of("proofstone_example"), mods, "fabric@26.3", "tail")).isNull();
    }

    @Test
    void aDeclaredModTheAgentDoesNotListIsInvalidNamingItWithTheListAndTheLog() throws Exception {
        JsonNode mods = reply("{\"mods\":[{\"id\":\"fabric-api\",\"version\":\"1\"}]}");

        String message = ModsLoaded.invalid(List.of("proofstone_example", "fabric-api"), mods, "fabric@26.3", "the log tail");

        assertThat(message).startsWith("INVALID test declaration").contains("proofstone_example")
                .contains("did not load on fabric@26.3").contains("[fabric-api]").contains("the log tail");
    }

    @Test
    void aReplyWithNoModListIsInvalidNotAPass() throws Exception {
        assertThat(ModsLoaded.invalid(List.of("proofstone_example"), reply("{}"), "neoforge@26.3", "tail"))
                .startsWith("INVALID test declaration").contains("no mods list");
        assertThat(ModsLoaded.invalid(List.of("proofstone_example"), null, "neoforge@26.3", "tail"))
                .startsWith("INVALID test declaration").contains("no mods list");
    }

    @Test
    void noDeclaredModNeedsNoCheck() {
        assertThat(ModsLoaded.invalid(List.of(), null, "fabric@26.3", "tail")).isNull();
    }

    @Test
    void aControlRunInstallsNoDeclaredMod() {
        assertThat(ModsLoaded.globsToInstall(new String[] {"build/my-mod.jar"}, false)).containsExactly("build/my-mod.jar");
        assertThat(ModsLoaded.globsToInstall(new String[] {"build/my-mod.jar"}, true)).isEmpty();
        assertThat(ModsLoaded.globsToInstall(new String[] {}, false)).isEmpty();
    }
}
