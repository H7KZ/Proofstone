package io.github.h7kz.proofstone.core.verdict;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ConformanceCacheTest {

    @Test
    void conformanceRunsOncePerCell() {
        ConformanceCache cache = new ConformanceCache();
        AtomicInteger runs = new AtomicInteger();

        for (int i = 0; i < 3; i++) {
            ConformanceResult result = cache.get("paper@1.8.8", () -> {
                runs.incrementAndGet();
                return ConformanceResult.pass("paper@1.8.8");
            });
            assertThat(result.passed()).isTrue();
        }
        assertThat(runs).hasValue(1);
    }

    @Test
    void eachCellHasItsOwnRun() {
        ConformanceCache cache = new ConformanceCache();
        AtomicInteger runs = new AtomicInteger();

        cache.get("paper@1.8.8", () -> {
            runs.incrementAndGet();
            return ConformanceResult.pass("paper@1.8.8");
        });
        ConformanceResult other = cache.get("paper@1.21.11", () -> {
            runs.incrementAndGet();
            return new ConformanceResult("paper@1.21.11", false, "C05 xp mismatch");
        });

        assertThat(runs).hasValue(2);
        assertThat(other.passed()).isFalse();
    }

    @Test
    void aMissingResultIsAnErrorAndIsNotCached() {
        ConformanceCache cache = new ConformanceCache();

        assertThatThrownBy(() -> cache.get("paper@1.8.8", () -> null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a pass");
        // Nothing was cached, so the next request runs the suite again.
        assertThat(cache.get("paper@1.8.8", () -> ConformanceResult.pass("paper@1.8.8")).passed()).isTrue();
    }

    @Test
    void aResultForAnotherCellIsAnError() {
        ConformanceCache cache = new ConformanceCache();

        assertThatThrownBy(() -> cache.get("paper@1.8.8", () -> ConformanceResult.pass("paper@1.21.11")))
                .isInstanceOf(IllegalStateException.class);
    }
}
