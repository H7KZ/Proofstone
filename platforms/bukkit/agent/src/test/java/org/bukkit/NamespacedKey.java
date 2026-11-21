package org.bukkit;

/** Test stand-in: the agent is built against Bukkit 1.8.8, which has no NamespacedKey (T-M11-05). */
public final class NamespacedKey {
    private final String text;

    public NamespacedKey(String namespace, String key) {
        this.text = namespace + ":" + key;
    }

    @Override
    public String toString() {
        return text;
    }
}
