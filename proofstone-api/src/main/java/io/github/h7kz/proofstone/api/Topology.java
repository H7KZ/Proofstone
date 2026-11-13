package io.github.h7kz.proofstone.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

/**
 * A topology cell: one proxy and its named backends (ADR-0016, test-api spec section 12). A test gets one by declaring a
 * {@code Topology} parameter on a topology cell, planned from a {@link Matrix} that names a proxy platform such as
 * {@link Platform#VELOCITY}. The injected plain {@link Agent} is the default backend's agent (the first of
 * {@link #servers()}, where the client lands); every other agent is reached here.
 *
 * <p>A client moves between backends with {@link Client#transferTo(String)}, a server switch.
 */
public interface Topology {

    /**
     * The agent of one backend: its own game state, exactly as on a single-server cell.
     *
     * @throws IllegalArgumentException when no backend has this name, with the names there are
     */
    Agent server(String name);

    /**
     * The proxy's agent. It runs proxy console commands and observes proxy events and logs. A proxy holds no game state,
     * so {@link Agent#player(String)} on it fails loudly; read a player through {@link #server(String)}.
     */
    Agent proxy();

    /** The backend names in order; the first is the proxy's default server. */
    List<String> servers();

    /**
     * Marks a test class as a topology test and names its backends (ADR-0016 section 1). Its tests need
     * {@link Capability#TOPOLOGY}, so on a single-server cell they are SKIPPED with {@code CAPABILITY_MISSING}, never run
     * and never a pass. Without it, a topology cell runs the default backends {@code lobby} and {@code survival}.
     *
     * <p>It is nested here because an annotation and an interface cannot share the name {@code Topology} in one package.
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface Backends {

        /**
         * The backend names in order, each matching {@code [a-z][a-z0-9]{0,15}}, unique and not {@code proxy}. The first
         * is the proxy's default server. A name that breaks the rule fails the class before anything starts.
         */
        String[] value() default {"lobby", "survival"};
    }
}
