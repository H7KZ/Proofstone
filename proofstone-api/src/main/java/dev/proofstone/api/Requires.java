package dev.proofstone.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the {@link Capability capabilities} a test needs (test-api spec section 10). It applies to a {@link CellTest}
 * method, or to a class for all of its tests. A cell that lacks one of them is SKIPPED with the reason
 * {@code missing capability X on cell}, and the test body does not run.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Requires {

    /** The capabilities the test needs, all of them. */
    Capability[] value();
}
