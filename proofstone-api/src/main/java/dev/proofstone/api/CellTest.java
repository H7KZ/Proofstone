package dev.proofstone.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.TestTemplate;

/**
 * A test that runs once per cell of the class's {@link Matrix}, displayed as {@code paper@26.3}. It is a JUnit
 * {@link TestTemplate}. Parameters may be {@link Cell}, {@link Agent} and {@link Client}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@TestTemplate
public @interface CellTest {
}
