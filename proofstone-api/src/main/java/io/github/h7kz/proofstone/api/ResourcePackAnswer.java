package io.github.h7kz.proofstone.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * How every client of the test answers a resource-pack push (ADR-0017 section 7). The push arrives during the join,
 * before the test body runs, so the answer is declared here, on the class or on the method (the method wins). There is
 * no default: a push with no declared answer ends the join with an error naming the URL, never a silent accept.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ResourcePackAnswer {

    /** The answer to every push. */
    Answer value();

    /** The answers a vanilla client can give, with the statuses it then sends (client-driver spec section 2b). */
    enum Answer {
        /**
         * {@code ACCEPTED}, then the download from the harness source, then {@code DOWNLOADED} and
         * {@code SUCCESSFULLY_LOADED} when the sha1 matches, or {@code FAILED_DOWNLOAD} when it does not. A pack from any
         * other URL is never downloaded: accepting it is an error naming the URL.
         */
        ACCEPT,
        /** {@code DECLINED}. */
        DECLINE,
        /** {@code ACCEPTED}, then {@code FAILED_DOWNLOAD}, without downloading. */
        FAIL_DOWNLOAD
    }
}
