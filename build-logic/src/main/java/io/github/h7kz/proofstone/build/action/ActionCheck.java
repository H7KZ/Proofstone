package io.github.h7kz.proofstone.build.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Lightweight schema check for the composite action at integrations/github-action/action.yml (T-M6-03). It runs inside
 * docsCheck, so it needs no runtime dependency and no network. Errors fail the build.
 *
 * <p>Rules: the action is composite with a name and description; every input is documented; the EULA input has no
 * default and is required; no step writes {@code PROOFSTONE_ACCEPT_EULA} as true; every shell step names its shell;
 * every third-party {@code uses:} is pinned to a full commit SHA with its release tag in a comment.
 */
public final class ActionCheck {

    /** The action file, relative to the repository root. */
    public static final String ACTION = "integrations/github-action/action.yml";

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final Pattern PINNED_USES = Pattern.compile("^\\s*(-\\s*)?uses:\\s+\\S+@[0-9a-f]{40}\\s+#\\s*v\\S+.*$");
    private static final Pattern ANY_USES = Pattern.compile("^\\s*(-\\s*)?uses:\\s+(\\S+)");
    private static final Pattern EULA_LITERAL = Pattern.compile("PROOFSTONE_ACCEPT_EULA\\s*[:=]\\s*\"?true");

    private ActionCheck() {
    }

    /** @return the errors; empty when the file is absent (fixture trees) or passes */
    public static List<String> check(Path root) throws IOException {
        Path file = root.resolve(ACTION);
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        return check(file, Files.readString(file));
    }

    static List<String> check(Path file, String text) throws IOException {
        List<String> errors = new ArrayList<>();
        String where = file.toString();
        JsonNode action = YAML.readTree(text);
        if (action == null || !action.isObject()) {
            return List.of(where + ": not a YAML mapping");
        }
        requireText(action, "name", where, errors);
        requireText(action, "description", where, errors);

        JsonNode runs = action.path("runs");
        if (!"composite".equals(runs.path("using").asText())) {
            errors.add(where + ": runs.using must be composite (a workflow is not an action)");
        }
        if (action.has("on") || action.has("jobs")) {
            errors.add(where + ": an action file must not declare triggers or jobs (no workflows, AGENTS.md section 4)");
        }

        JsonNode inputs = action.path("inputs");
        if (!inputs.isObject() || inputs.isEmpty()) {
            errors.add(where + ": inputs are missing");
        } else {
            inputs.fields().forEachRemaining(entry -> checkInput(entry.getKey(), entry.getValue(), where, errors));
        }

        JsonNode steps = runs.path("steps");
        if (!steps.isArray() || steps.isEmpty()) {
            errors.add(where + ": runs.steps is missing");
        } else {
            for (int i = 0; i < steps.size(); i++) {
                JsonNode step = steps.get(i);
                String label = where + ": step " + (i + 1);
                if (!step.hasNonNull("name")) {
                    errors.add(label + " has no name");
                }
                if (step.has("run") && !step.hasNonNull("shell")) {
                    errors.add(label + " runs a script without a shell");
                }
                if (step.has("uses") && step.hasNonNull("run")) {
                    errors.add(label + " has both uses and run");
                }
            }
        }

        List<String> lines = text.lines().toList();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String at = where + ":" + (i + 1);
            if (line.trim().startsWith("#")) {
                continue;
            }
            if (ANY_USES.matcher(line).find() && !PINNED_USES.matcher(line).matches()) {
                errors.add(at + ": uses must be pinned to a 40-character commit SHA with '# v<tag>' after it");
            }
            if (EULA_LITERAL.matcher(line).find()) {
                errors.add(at + ": PROOFSTONE_ACCEPT_EULA must never be set to true in the action (forwarded input only)");
            }
        }
        return errors;
    }

    private static void checkInput(String name, JsonNode input, String where, List<String> errors) {
        String at = where + ": input " + name;
        if (!input.path("description").isTextual() || input.path("description").asText().isBlank()) {
            errors.add(at + " has no description");
        }
        if (name.equals("proofstone-accept-eula")) {
            if (input.has("default")) {
                errors.add(at + " must not have a default (the EULA is never defaulted)");
            }
            if (!input.path("required").asBoolean(false)) {
                errors.add(at + " must be required");
            }
        }
        if (input.has("default") && input.path("default").isBoolean()) {
            errors.add(at + " has a boolean default; write it as a string so it cannot be read as an acceptance");
        }
    }

    private static void requireText(JsonNode node, String field, String where, List<String> errors) {
        if (!node.path(field).isTextual() || node.path(field).asText().isBlank()) {
            errors.add(where + ": " + field + " is missing");
        }
    }
}
