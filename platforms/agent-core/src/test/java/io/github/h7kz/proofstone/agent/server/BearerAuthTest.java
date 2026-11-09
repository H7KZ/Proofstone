package io.github.h7kz.proofstone.agent.server;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BearerAuthTest {

    @Test
    void acceptsTheExactBearerToken() {
        assertThat(BearerAuth.authorised("Bearer s3cret", "s3cret")).isTrue();
    }

    @Test
    void rejectsMissingWrongOrUnprefixedTokens() {
        assertThat(BearerAuth.authorised(null, "s3cret")).isFalse();
        assertThat(BearerAuth.authorised("Bearer wrong", "s3cret")).isFalse();
        assertThat(BearerAuth.authorised("s3cret", "s3cret")).isFalse();
        assertThat(BearerAuth.authorised("Bearer s3cret ", "s3cret")).isFalse();
    }
}
