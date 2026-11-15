package io.github.h7kz.proofstone.core.junit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
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

    @Test
    void noDeclaredPackNeedsNoCheck() throws IOException {
        assertThat(DataPackLoad.invalid(List.of(), JSON.readTree("{}"), "paper@26.3", "log")).isNull();
    }
}
