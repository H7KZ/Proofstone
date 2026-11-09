package io.github.h7kz.proofstone.api;

/** Config access for one plugin on the server (test-api spec section 3). */
public interface PluginHandle {

    /** The plugin name, the {@code {id}} of the agent endpoints. */
    String name();

    /** The config value at a dotted path. A path that does not exist is an error. */
    Object config(String path);

    /**
     * Sets a config value. The agent snapshots the config on the first patch, and the framework restores it after
     * the test, so the next test sees the original.
     */
    PluginHandle patchConfig(String path, Object value);
}
