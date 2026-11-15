package io.github.h7kz.proofstone.core.junit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The loaded-or-loud check of declared data packs (ADR-0017 section 4), without a server. */
class DataPackLoadTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void everyDeclaredPackEnabledIsLoaded() throws IOException {
        assertThat(DataPackLoad.invalid(List.of("file/x.zip"), JSON.readTree(
                "{\"enabled\":[\"vanilla\",\"file/x.zip\"],\"available\":[\"vanilla\",\"file/x.zip\"]}"),
                "paper@26.3", "log")).isNull();
    }

    @Test
    void aPackThatIsOnlyAvailableIsNamedAsNotLoaded() throws IOException {
        String message = DataPackLoad.invalid(List.of("file/x.zip", "file/y.zip"), JSON.readTree(
                "{\"enabled\":[\"vanilla\",\"file/y.zip\"],\"available\":[\"vanilla\",\"file/x.zip\",\"file/y.zip\"]}"),
                "paper@26.3", "the log tail");
        assertThat(message).startsWith("INVALID").contains("file/x.zip").contains("paper@26.3").contains("the log tail")
                .doesNotContain("declared data pack file/y.zip");
    }

    @Test
    void aReplyWithoutAnEnabledListIsAnErrorNotAPass() throws IOException {
        assertThat(DataPackLoad.invalid(List.of("file/x.zip"), JSON.readTree("{}"), "fabric@26.3", "log"))
                .contains("file/x.zip").contains("no enabled list");
    }

    private static final Map<String, List<String>> PACKS = Map.of("file/broken.zip", List.of("proofstone_broken"));
    private static final String PAPER_LOG = String.join("\n",
            "[04:11:04 INFO]: Found new data pack file/broken.zip, loading it automatically",
            "[04:11:04 ERROR]: No key layers in MapLike[{}]",
            "[04:11:04 ERROR]: Failed to load function proofstone_broken:hello",
            "java.util.concurrent.CompletionException: java.lang.IllegalArgumentException: Whilst parsing command on line 2: "
                    + "Unknown or incomplete command. See below for error at position 0: <--[HERE]",
            "\tat java.base/java.util.concurrent.CompletableFuture.wrapInCompletionException(Unknown Source)",
            "[04:11:06 INFO]: Done (5.522s)! For help, type \"help\"");

    @Test
    void anErrorLoggedForAFunctionOfThePackNamesThePackAndQuotesTheServer() {
        String message = DataPackLoad.errorsLogged(PACKS, PAPER_LOG, "paper@26.3");

        assertThat(message).startsWith("INVALID").contains("file/broken.zip").contains("paper@26.3")
                .contains("Failed to load function proofstone_broken:hello").contains("Whilst parsing command on line 2")
                .doesNotContain("No key layers").doesNotContain("\tat ");
    }

    @Test
    void aLoaderLogLineAtErrorLevelCountsToo() {
        String log = "[15Nov2026 04:11:04.123] [Worker-Main-3/ERROR] [net.minecraft.server.ServerFunctionLibrary/]: "
                + "Failed to load function proofstone_broken:hello";

        assertThat(DataPackLoad.errorsLogged(PACKS, log, "fabric@26.3")).startsWith("INVALID").contains("file/broken.zip");
        assertThat(DataPackLoad.errorsLogged(PACKS,
                "[04:11:04] [Worker-Main-3/WARN] (Minecraft) Skipping recipe proofstone_broken:two_dirt", "fabric@26.3"))
                .startsWith("INVALID");
    }

    @Test
    void anInfoLineOrAnotherNamespaceOrNoLogIsNotAnError() {
        assertThat(DataPackLoad.errorsLogged(PACKS, "[04:11:04 INFO]: Loaded proofstone_broken:hello", "paper@26.3")).isNull();
        assertThat(DataPackLoad.errorsLogged(PACKS, "[04:11:04 ERROR]: Failed to load function other_pack:hello", "paper@26.3"))
                .isNull();
        assertThat(DataPackLoad.errorsLogged(PACKS, "", "paper@26.3")).isNull();
        assertThat(DataPackLoad.errorsLogged(Map.of(), PAPER_LOG, "paper@26.3")).isNull();
    }

    @Test
    void noDeclaredPackNeedsNoCheck() throws IOException {
        assertThat(DataPackLoad.invalid(List.of(), JSON.readTree("{}"), "paper@26.3", "log")).isNull();
    }
}
