package io.github.h7kz.proofstone.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a {@link CellExpectation} for the tests of a class, or for one {@link CellTest} method (test-api spec section
 * 9). The expectation runs on every cell of the test, after the test body, so it can tell a cell apart from the others.
 * When a class and a method both declare one, both run.
 *
 * <p>The class must be public, with a public no-argument constructor, so the harness can create it for each check.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Expect {

    /** The expectation to check on each cell. */
    Class<? extends CellExpectation> value();
}
