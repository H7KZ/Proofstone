package io.github.h7kz.proofstone.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * How long a server lives (test-api spec section 1). Without this annotation the mode is {@link Mode#PER_CLASS}.
 * Every test also gets a reset player, restored config and a fresh client, in both modes.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Isolation {

    Mode value();

    enum Mode {
        /** One server per class and cell, shared by its tests. */
        PER_CLASS,
        /** A new server per test. Slow: use it only for state that reset and restore cannot clear. */
        PER_TEST
    }
}
