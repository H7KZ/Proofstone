package io.github.h7kz.proofstone.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** One platform and the versions to run on it; repeat it for more platforms (test-api spec §1). */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(Matrices.class)
public @interface Matrix {

    Platform platform();

    /** Exact versions from {@code versions.yml}, or {@code latest}. An unknown version is an error. */
    String[] versions();
}
