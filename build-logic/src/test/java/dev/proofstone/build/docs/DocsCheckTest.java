package dev.proofstone.build.docs;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Each docsCheck rule has a failing fixture and a passing fixture. Fixtures are small trees under @TempDir.
class DocsCheckTest {

    @TempDir
    Path root;

    @Test
    void brokenFileLinkFails() throws IOException {
        write("docs/a.md", "See [missing](missing.md).\n");
        assertThat(DocsCheck.run(root).errors()).singleElement().asString().contains("missing.md");
    }

    @Test
    void validFileAndAnchorLinksPass() throws IOException {
        write("docs/a.md", "See [b](b.md#intro) and [self](#top), [web](https://example.com/x).\n\n# Top\n");
        write("docs/b.md", "# Title\n\n## Intro\n");
        assertThat(DocsCheck.run(root).errors()).isEmpty();
    }

    @Test
    void brokenAnchorFails() throws IOException {
        write("docs/a.md", "See [b](b.md#nope).\n");
        write("docs/b.md", "## Intro\n");
        assertThat(DocsCheck.run(root).errors()).singleElement().asString().contains("#nope");
    }

    @Test
    void linksInsideCodeFencesAreIgnored() throws IOException {
        write("docs/a.md", "```\n[missing](missing.md)\n```\n");
        assertThat(DocsCheck.run(root).errors()).isEmpty();
    }

    @Test
    void cardWithoutTestFirstFails() throws IOException {
        write("docs/plan/tasks/M0-foundation.md", card("T-M0-01", "- tier: haiku\n- depends: —\n- modules: a\n"
                + "- adrs: 0001\n- specs: x\n", sections()));
        assertThat(DocsCheck.run(root).errors()).singleElement().asString().contains("test-first");
    }

    @Test
    void cardWithoutVerifySectionFails() throws IOException {
        write("docs/plan/tasks/M0-foundation.md", card("T-M0-01", metadata("haiku"),
                "**Goal.**\n\n**Do.**\n\n**Don't.**\n\n**Acceptance.**\n"));
        assertThat(DocsCheck.run(root).errors()).singleElement().asString().contains("Verify.");
    }

    @Test
    void cardInWrongMilestoneFileFails() throws IOException {
        write("docs/plan/tasks/M1-walking-skeleton.md", card("T-M0-01", metadata("haiku"), sections()));
        assertThat(DocsCheck.run(root).errors()).singleElement().asString().contains("T-M0-01").contains("M1");
    }

    @Test
    void unknownTierFails() throws IOException {
        write("docs/plan/tasks/M0-foundation.md", card("T-M0-01", metadata("gpt"), sections()));
        assertThat(DocsCheck.run(root).errors()).singleElement().asString().contains("tier");
    }

    @Test
    void goodCardPasses() throws IOException {
        write("docs/plan/tasks/M0-foundation.md", card("T-M0-01", metadata("haiku"), sections()));
        assertThat(DocsCheck.run(root).errors()).isEmpty();
    }

    @Test
    void missingDependsIdFails() throws IOException {
        write("docs/plan/tasks/M0-foundation.md", card("T-M0-01", metadata("haiku").replace("—", "T-M0-99"), sections()));
        assertThat(DocsCheck.run(root).errors()).singleElement().asString().contains("T-M0-99");
    }

    @Test
    void emDashDependsPasses() throws IOException {
        write("docs/plan/tasks/M0-foundation.md", card("T-M0-01", metadata("haiku"), sections()));
        assertThat(DocsCheck.run(root).errors()).isEmpty();
    }

    @Test
    void letterSuffixedSplitCardPasses() throws IOException {
        write("docs/plan/tasks/M0-foundation.md", card("T-M0-01", metadata("haiku"), sections())
                + card("T-M0-01b", metadata("haiku").replace("—", "T-M0-01"), sections()));
        assertThat(DocsCheck.run(root).errors()).isEmpty();
    }

    @Test
    void avoidedGlossaryTermWarnsWithoutFailing() throws IOException {
        write("docs/glossary.md", "| Term | Meaning | Avoid saying |\n|---|---|---|\n"
                + "| **Agent** | The in-server part. | helper plugin, test plugin |\n");
        write("docs/a.md", "We install a helper plugin next to the jar.\n");
        DocsCheck.Report report = DocsCheck.run(root);
        assertThat(report.errors()).isEmpty();
        assertThat(report.warnings()).singleElement().asString().contains("helper plugin");
    }

    private void write(String relative, String content) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static String metadata(String tier) {
        return "- tier: " + tier + "\n- depends: —\n- modules: a\n- adrs: 0001\n- specs: x\n- test-first: yes\n";
    }

    private static String sections() {
        return "**Goal.**\n\n**Do.**\n\n**Don't.**\n\n**Acceptance.**\n\n**Verify.**\n";
    }

    private static String card(String id, String metadata, String sections) {
        return "# Milestone\n\n### " + id + " · Short title\n" + metadata + "\n" + sections;
    }
}
