package dev.proofstone.agent.platform;

/** Reads the Minecraft version out of {@code Bukkit.getBukkitVersion()}. */
public final class MinecraftVersion {

    private MinecraftVersion() {
    }

    /**
     * {@code 1.8.8-R0.1-SNAPSHOT} becomes {@code 1.8.8}. Paper 26.x puts its build into the first segment
     * ({@code 26.3.build.238-R0.1-SNAPSHOT}), and that suffix is not part of the Minecraft version.
     * An empty result is an error, never a guess.
     */
    public static String parse(String bukkitVersion) {
        String version = bukkitVersion.split("-")[0].replaceFirst("[.]build[.][0-9]+$", "");
        if (version.isEmpty()) {
            throw new IllegalStateException("cannot read the Minecraft version from \"" + bukkitVersion + "\"");
        }
        return version;
    }
}
