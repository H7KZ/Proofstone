package io.github.h7kz.proofstone.api;

/** Event probes of the server oracle (test-api spec section 3). */
public interface Events {

    /**
     * Listens for a server event class. The agent loads the class through each installed plugin's classloader, so a
     * plugin's own event is addressed by its full name.
     *
     * @throws RuntimeException when no loaded class has that name or it is not a Bukkit event
     */
    EventProbe subscribe(String className);
}
