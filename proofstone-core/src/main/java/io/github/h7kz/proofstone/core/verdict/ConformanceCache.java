package io.github.h7kz.proofstone.core.verdict;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Runs the conformance suite once per cell per run and keeps the result (self-tests section 1, spec "cached per cell
 * per CI run"). Every plugin test on the cell reads the same result, so a conformance run is never repeated.
 *
 * <p>A supplier that throws, or returns nothing, is an error, and nothing is cached for that cell.
 */
public final class ConformanceCache {

    private final Map<String, ConformanceResult> results = new ConcurrentHashMap<>();

    /**
     * @param cell the cell's display id
     * @param conformance runs the suite on the cell; called at most once per cell
     * @return the cached result for the cell
     * @throws IllegalStateException when the supplier returns nothing or a result for another cell
     */
    public ConformanceResult get(String cell, Supplier<ConformanceResult> conformance) {
        if (cell == null || cell.isBlank()) {
            throw new IllegalArgumentException("cell must not be blank");
        }
        Objects.requireNonNull(conformance, "conformance");
        return results.computeIfAbsent(cell, key -> {
            ConformanceResult result = conformance.get();
            if (result == null) {
                throw new IllegalStateException("conformance returned no result for " + key + ": not a pass");
            }
            if (!result.cell().equals(key)) {
                throw new IllegalStateException("conformance for " + result.cell() + " returned for " + key);
            }
            return result;
        });
    }
}
