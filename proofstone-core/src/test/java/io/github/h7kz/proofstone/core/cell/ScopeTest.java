package io.github.h7kz.proofstone.core.cell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.core.versions.VersionsException;
import org.junit.jupiter.api.Test;

class ScopeTest {

    @Test
    void missingScopeIsThePullRequestScope() {
        assertThat(Scope.parse(null, "-Dproofstone.scope")).isEqualTo(Scope.PR);
        assertThat(Scope.parse(" ", "-Dproofstone.scope")).isEqualTo(Scope.PR);
    }

    @Test
    void namesAreCaseInsensitive() {
        assertThat(Scope.parse("pr", "x")).isEqualTo(Scope.PR);
        assertThat(Scope.parse("PR", "x")).isEqualTo(Scope.PR);
        assertThat(Scope.parse("Nightly", "x")).isEqualTo(Scope.NIGHTLY);
    }

    @Test
    void unknownScopeIsAnErrorNamingTheSourceAndTheValidValues() {
        assertThatThrownBy(() -> Scope.parse("daily", "PROOFSTONE_SCOPE"))
                .isInstanceOf(VersionsException.class)
                .hasMessageContaining("PROOFSTONE_SCOPE")
                .hasMessageContaining("\"daily\"")
                .hasMessageContaining("pr")
                .hasMessageContaining("nightly");
    }
}
