package dev.proofstone.core.junit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.proofstone.api.PluginHandle;
import dev.proofstone.core.host.PaperHost;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** A {@link PluginHandle} over the agent's {@code /v1/plugins/{id}/config} endpoints. */
final class HostPlugin implements PluginHandle {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final PaperHost host;
    private final String name;
    private final Set<String> patched;

    /** @param patched the plugins this test changed; the lifecycle restores each one after the test */
    HostPlugin(PaperHost host, String name, Set<String> patched) {
        this.host = host;
        this.name = name;
        this.patched = patched;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Object config(String path) {
        JsonNode reply = host.request("GET", base() + "?path=" + URLEncoder.encode(path, StandardCharsets.UTF_8),
                null);
        JsonNode value = reply.get("value");
        if (value == null) {
            throw new IllegalStateException("agent config reply for " + name + " " + path + " has no value: " + reply);
        }
        return JSON.convertValue(value, Object.class);
    }

    @Override
    public PluginHandle patchConfig(String path, Object value) {
        // Record first: a patch that fails half way may still have taken the snapshot, and restoring is safe.
        patched.add(name);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("path", path);
        body.put("value", value);
        host.request("PATCH", base(), body);
        return this;
    }

    /** Puts the config back to the first-patch snapshot. */
    static void restore(PaperHost host, String name) {
        host.request("POST", "/v1/plugins/" + name + "/config/restore", Map.of());
    }

    private String base() {
        return "/v1/plugins/" + name + "/config";
    }
}
