package dev.proofstone.core.verdict;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class JunitXmlTest {

    @Test
    void xmlMatchesTheGoldenSnapshot() throws IOException {
        String actual = JunitXml.render(ReportWriterTest.fixture());

        assertThat(actual).isEqualTo(ReportWriterTest.golden("junit-golden.xml"));
    }

    @Test
    void everyNonGreenVerdictIsAFailureAndSkipsStayVisible() {
        String xml = JunitXml.render(ReportWriterTest.fixture());

        // Five non-green verdicts (every verdict except PASS and SKIPPED) are failures; the skip is a skip.
        assertThat(xml).contains("failures=\"5\" errors=\"0\" skipped=\"1\"");
        assertThat(xml).contains("<failure message=\"FLAKY: verdicts differ across 5 runs: PASS, FAIL\" "
                + "type=\"FLAKY\"/>");
        assertThat(xml).contains("<skipped message=\"CAPABILITY_MISSING: grindstone arrived in 1.14\"/>");
    }

    @Test
    void attributeTextIsEscapedAndXmlForbiddenCharactersAreReplaced() {
        assertThat(JunitXml.escape("a<b>&\"'\u0001c")).isEqualTo("a&lt;b&gt;&amp;&quot;&apos;�c");
    }
}
