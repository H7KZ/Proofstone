package io.github.h7kz.proofstone.build.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

// Each action rule has a failing fixture. The real action file must pass.
class ActionCheckTest {

    private static final String VALID = """
            name: "Proofstone"
            description: "d"
            inputs:
              proofstone-accept-eula:
                description: "must be true"
                required: true
            runs:
              using: "composite"
              steps:
                - name: step
                  shell: bash
                  run: echo ok
                - name: pinned
                  uses: actions/cache@55cc8345863c7cc4c66a329aec7e433d2d1c52a9 # v6.1.0
            """;

    private static List<String> errorsFor(String text) throws IOException {
        return ActionCheck.check(Path.of("fixture/action.yml"), text);
    }

    @Test
    void validActionPasses() throws IOException {
        assertThat(errorsFor(VALID)).isEmpty();
    }

    @Test
    void eulaWithDefaultFails() throws IOException {
        String text = VALID.replace("required: true", "required: true\n    default: \"true\"");
        assertThat(String.join("\n", errorsFor(text))).contains("must not have a default");
    }

    @Test
    void eulaNotRequiredFails() throws IOException {
        String text = VALID.replace("required: true", "required: false");
        assertThat(String.join("\n", errorsFor(text))).contains("must be required");
    }

    @Test
    void eulaSetToTrueInStepFails() throws IOException {
        String text = VALID.replace("run: echo ok", "run: echo ok\n      env:\n        PROOFSTONE_ACCEPT_EULA: true");
        assertThat(String.join("\n", errorsFor(text))).contains("must never be set to true");
    }

    @Test
    void unpinnedUsesFails() throws IOException {
        String text = VALID.replace("actions/cache@55cc8345863c7cc4c66a329aec7e433d2d1c52a9 # v6.1.0", "actions/cache@v4");
        assertThat(String.join("\n", errorsFor(text))).contains("40-character commit SHA");
    }

    @Test
    void workflowTriggerFails() throws IOException {
        String text = "on: push\n" + VALID;
        assertThat(String.join("\n", errorsFor(text))).contains("must not declare triggers");
    }

    @Test
    void shellMissingFails() throws IOException {
        String text = VALID.replace("shell: bash\n      run", "run");
        assertThat(text).as("fixture edit applied").isNotEqualTo(VALID);
        assertThat(String.join("\n", errorsFor(text))).contains("without a shell");
    }

    @Test
    void realActionFilePasses() throws IOException {
        assertThat(ActionCheck.check(Path.of(".."))).isEmpty();
    }
}
