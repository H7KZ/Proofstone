package org.bukkit.persistence;

import java.util.Set;
import org.bukkit.NamespacedKey;

/** Test stand-in with the real method names and erased signatures (Bukkit 1.14+). */
public interface PersistentDataContainer {
    Set<NamespacedKey> getKeys();

    boolean has(NamespacedKey key, PersistentDataType type);

    Object get(NamespacedKey key, PersistentDataType type);
}
