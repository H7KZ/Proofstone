package io.github.h7kz.proofstone.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A server-sent resource pack under test (ADR-0017 section 7, test-api spec section 13). The harness serves the file from
 * its own JVM on {@code 127.0.0.1} and the server's {@code server.properties} pushes it to every client that joins. How a
 * client answers is declared with {@link ResourcePackAnswer}; with none declared, a push ends the join with an error.
 * Declaring a pack implies {@link Capability#RESOURCE_PACKS}: a cell without it is SKIPPED with the capability named. No
 * texture is unpacked or rendered.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ServerResourcePack {

    /** The pack, as a glob relative to the project directory that matches exactly one {@code .zip} file. */
    String value();

    /** Whether the server requires the pack ({@code require-resource-pack}): a client that declines is kicked. */
    boolean required() default false;

    /**
     * The prompt the server shows, as a JSON text component such as {@code {"text":"Please"}}; empty for none. A plain
     * string is refused as an invalid declaration, because the server drops it (docs/platforms/fabric.md, Gotchas).
     */
    String prompt() default "";

    /**
     * The sha1 the server pushes, in hex. Empty (the default) is the sha1 of the file. Set another value only to test a
     * wrong hash: a client that accepts then reports the mismatch in {@link ResourcePackPush#problem()}.
     */
    String sha1() default "";
}
