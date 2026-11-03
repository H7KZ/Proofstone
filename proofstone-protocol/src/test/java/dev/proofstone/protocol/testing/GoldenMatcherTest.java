package dev.proofstone.protocol.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GoldenMatcherTest {

    private static final String UUID = "069a79f4-44e9-4726-a5be-fca90e38aaf5";

    @Test
    void identicalJsonHasNoMismatches() {
        String json = "{\"name\":\"Alex\",\"capabilities\":[\"command\"]}";

        assertThat(GoldenMatcher.mismatches(json, json)).isEmpty();
    }

    @Test
    void uuidPlaceholderAcceptsWellFormedUuid() {
        assertThat(GoldenMatcher.mismatches("{\"uuid\":\"<uuid>\"}", "{\"uuid\":\"" + UUID + "\"}")).isEmpty();
    }

    @Test
    void uuidPlaceholderRejectsMalformedUuid() {
        assertThat(GoldenMatcher.mismatches("{\"uuid\":\"<uuid>\"}", "{\"uuid\":\"nope\"}"))
                .containsExactly("$.uuid: expected <uuid> but was \"nope\"");
    }

    @Test
    void stringPlaceholderAcceptsAnyStringButNotANumber() {
        assertThat(GoldenMatcher.mismatches("{\"raw\":\"<string>\"}", "{\"raw\":\"anything\"}")).isEmpty();
        assertThat(GoldenMatcher.mismatches("{\"raw\":\"<string>\"}", "{\"raw\":3}"))
                .containsExactly("$.raw: expected <string> but was 3");
    }

    @Test
    void timePlaceholderRequiresAnInstant() {
        assertThat(GoldenMatcher.mismatches("{\"time\":\"<time>\"}", "{\"time\":\"2026-11-03T12:00:00Z\"}")).isEmpty();
        assertThat(GoldenMatcher.mismatches("{\"time\":\"<time>\"}", "{\"time\":\"yesterday\"}"))
                .containsExactly("$.time: expected <time> but was \"yesterday\"");
    }

    @Test
    void requestIdPlaceholderRequiresNonBlankString() {
        assertThat(GoldenMatcher.mismatches("{\"id\":\"<requestId>\"}", "{\"id\":\"req-1\"}")).isEmpty();
        assertThat(GoldenMatcher.mismatches("{\"id\":\"<requestId>\"}", "{\"id\":\" \"}"))
                .containsExactly("$.id: expected <requestId> but was \" \"");
    }

    @Test
    void mismatchMessageShowsNestedJsonPath() {
        String expected = "{\"data\":{\"inventory\":{\"0\":{\"amount\":1}}}}";
        String actual = "{\"data\":{\"inventory\":{\"0\":{\"amount\":2}}}}";

        assertThat(GoldenMatcher.mismatches(expected, actual))
                .containsExactly("$.data.inventory.0.amount: expected 1 but was 2");
    }

    @Test
    void arrayMismatchShowsIndex() {
        assertThat(GoldenMatcher.mismatches("{\"c\":[\"a\",\"b\"]}", "{\"c\":[\"a\",\"x\"]}"))
                .containsExactly("$.c[1]: expected \"b\" but was \"x\"");
    }

    @Test
    void arrayLengthMismatchIsReported() {
        assertThat(GoldenMatcher.mismatches("{\"c\":[1,2]}", "{\"c\":[1]}"))
                .containsExactly("$.c: expected 2 items but was 1");
    }

    @Test
    void missingAndUnexpectedFieldsAreReported() {
        assertThat(GoldenMatcher.mismatches("{\"name\":\"Alex\"}", "{\"extra\":1}"))
                .containsExactlyInAnyOrder("$.name: missing", "$.extra: unexpected 1");
    }

    @Test
    void numbersCompareByValue() {
        assertThat(GoldenMatcher.mismatches("{\"health\":20.0}", "{\"health\":20}")).isEmpty();
    }

    @Test
    void nullMustBeNull() {
        assertThat(GoldenMatcher.mismatches("{\"cursor\":null}", "{\"cursor\":\"x\"}"))
                .containsExactly("$.cursor: expected null but was \"x\"");
    }

    @Test
    void unknownPlaceholderFailsLoudly() {
        assertThatThrownBy(() -> GoldenMatcher.mismatches("{\"x\":\"<bogus>\"}", "{\"x\":\"1\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("<bogus>");
    }

    @Test
    void emptyOrUnparsableActualFailsLoudly() {
        assertThatThrownBy(() -> GoldenMatcher.mismatches("{}", ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GoldenMatcher.mismatches("{}", "not json"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void concreteReplacesPlaceholdersWithValidSamples() {
        String concrete = GoldenMatcher.concrete("{\"uuid\":\"<uuid>\",\"raw\":\"<string>\"}");

        assertThat(concrete).doesNotContain("<");
        assertThat(GoldenMatcher.mismatches("{\"uuid\":\"<uuid>\",\"raw\":\"<string>\"}", concrete)).isEmpty();
    }
}
