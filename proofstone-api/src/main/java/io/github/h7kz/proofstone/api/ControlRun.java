package io.github.h7kz.proofstone.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares what a control run (test-api and self-tests spec section 2, {@code -Dproofstone.controlRun=true}) must
 * show for a test. In a control run the plugin jars are not installed, so a passing test is {@code VACUOUS}, which
 * fails the run. A test that declares {@link Expectation#EXPECTED_PASS} is a genuine invariant instead, such as
 * "vanilla behaviour is unchanged when the feature is disabled": its pass is reported with the reason and is not
 * vacuous. This is rare on purpose.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ControlRun {

    /** What the test expects of a control run. */
    Expectation value();

    /** Why the test passes without the plugin. Required: a blank reason fails the test, it is never ignored. */
    String reason();

    enum Expectation {
        /** The test is expected to pass without the plugin, and its pass is reported with the reason. */
        EXPECTED_PASS
    }
}
