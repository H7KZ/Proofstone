package dev.proofstone.core.junit;

import dev.proofstone.api.ItemView;
import dev.proofstone.api.spi.OracleWaiter;
import dev.proofstone.core.verdict.Attempt;
import dev.proofstone.core.verdict.CellResult;
import dev.proofstone.core.verdict.CellRun;
import dev.proofstone.core.verdict.ConformanceResult;
import dev.proofstone.core.verdict.VerdictEngine;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The differential run (self-tests section 3, ADR-0004): each test runs with the primary driver (mcpl) and with the
 * reference driver (mineflayer-ref) on the same server, and the client-oracle observations of the two runs are
 * compared assertion point by assertion point.
 *
 * <p>The two drivers run one after the other in one JVM, on one cell and one server. The primary runs first and
 * the reference second, which compares with the primary's evidence and reports a mismatch as {@code DISAGREE}.
 * They never run in parallel, so each test starts from the server state the previous one left behind, reset as usual.
 *
 * <p>Only the client oracle is compared, and only the fields in {@link #canonicalItem}: the pinned list. Timing-dependent
 * fields (positions while moving, packet times) are not in it, and the transcripts are written to the bundle but never
 * compared.
 */
final class DifferentialRun {

    /** The switch that turns on the differential run. The Gradle build forwards it to the test JVM. */
    static final String PROPERTY = "proofstone.differential";
    /** The primary driver: the one every ordinary run uses (ADR-0004). */
    static final String PRIMARY = "mcpl";
    /** The reference driver, native on the row it names in versions.yml (max-native). */
    static final String REFERENCE = "mineflayer-ref";
    /** Both drivers of a differential run, in run order. The second one compares. */
    static final List<String> DRIVERS = List.of(PRIMARY, REFERENCE);

    private static final ThreadLocal<List<Observation>> OBSERVATIONS = new ThreadLocal<>();
    /** The first arrival of a pair, waiting for the second one, by test and cell. */
    private static final Map<String, Evidence> WAITING = new ConcurrentHashMap<>();

    private DifferentialRun() {
    }

    /** One client-oracle observation at one assertion point: what the assertion waited on, and what the client showed. */
    record Observation(String point, String value) {
    }

    /** What one driver's run observed, in assertion order, plus its client transcript (not compared). */
    record Evidence(String driver, List<Observation> observations, List<String> transcript) {
        Evidence {
            observations = List.copyOf(observations);
            transcript = List.copyOf(transcript);
        }
    }

    /** The first point where the two runs differ, with both evidence sets. */
    record Disagreement(Evidence first, Evidence second, String detail) {
    }

    /** @return whether the differential run is on; a value other than {@code true} or {@code false} is an error */
    static boolean enabled() {
        return parse(System.getProperty(PROPERTY));
    }

    static boolean parse(String value) {
        if (value == null || value.equals("false")) {
            return false;
        }
        if (value.equals("true")) {
            return true;
        }
        throw new IllegalStateException("-D" + PROPERTY + " must be true or false, not \"" + value + "\"");
    }

    /** Whether the reference driver speaks the cell's row natively, so it can join it without a bridge. */
    static boolean referenceNative(String cellMc, String referenceProtocol) {
        return cellMc.equals(referenceProtocol);
    }

    /** Starts recording the client-oracle observations of one test on this thread. */
    static void begin() {
        OBSERVATIONS.set(new ArrayList<>());
    }

    /** Stops recording and returns what the test observed, in order; empty when nothing was recording. */
    static List<Observation> end() {
        List<Observation> recorded = OBSERVATIONS.get();
        OBSERVATIONS.remove();
        return recorded == null ? List.of() : List.copyOf(recorded);
    }

    /**
     * Called by the waiter at every dual-oracle assertion point, with the client side of its outcome. Only the client
     * side is recorded: the server oracle is not compared between drivers. Does nothing when no test is recording.
     */
    static void record(String point, OracleWaiter.Side<?> client) {
        List<Observation> recorded = OBSERVATIONS.get();
        if (recorded != null) {
            recorded.add(new Observation(point, render(client)));
        }
    }

    /** The client side as text: the value in the pinned form, or the reason there was no answer. */
    static String render(OracleWaiter.Side<?> client) {
        if (!client.present()) {
            return "no answer (" + client.error() + ")";
        }
        Object value = client.value();
        if (value instanceof List<?> list) {
            StringBuilder out = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                Object slot = list.get(i);
                if (i > 0) {
                    out.append(", ");
                }
                out.append(i).append('=');
                out.append(slot instanceof ItemView item ? (item.isEmpty() ? "-" : canonicalItem(item))
                        : String.valueOf(slot));
            }
            return out.append(']').toString();
        }
        return value instanceof ItemView item ? canonicalItem(item) : String.valueOf(value);
    }

    /**
     * The pinned comparison list for an item (self-tests section 3). Compared: type, amount, enchants, stored enchants,
     * name, lore, damage and repair cost. Not compared: custom data, PDC and the raw form, because each is one-sided
     * (ItemView). Adding a field is a deliberate change here, reviewed like a schema change.
     */
    static String canonicalItem(ItemView item) {
        return item.type() + " x" + item.amount() + " enchants=" + new TreeMap<>(item.enchants())
                + " storedEnchants=" + new TreeMap<>(item.storedEnchants()) + " name=" + item.name()
                + " lore=" + item.lore() + " damage=" + item.damage() + " repairCost=" + item.repairCost();
    }

    /**
     * The comparator. Walks both observation lists in order and reports the first point that differs, a missing
     * point included.
     *
     * @return the disagreement, or {@code null} when every point matches
     */
    static Disagreement compare(Evidence first, Evidence second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        int count = Math.max(first.observations().size(), second.observations().size());
        for (int i = 0; i < count; i++) {
            Observation a = i < first.observations().size() ? first.observations().get(i) : null;
            Observation b = i < second.observations().size() ? second.observations().get(i) : null;
            if (!Objects.equals(a, b)) {
                String detail = "client observations differ at assertion " + (i + 1) + ":\n  " + first.driver()
                        + ": " + describe(a) + "\n  " + second.driver() + ": " + describe(b);
                return new Disagreement(first, second, detail);
            }
        }
        return null;
    }

    private static String describe(Observation observation) {
        return observation == null ? "no assertion point" : observation.point() + " => " + observation.value();
    }

    /**
     * Pairs the runs of one test on one cell. The first arrival waits; the second one, from the other driver, compares.
     * A repeat from the same driver replaces the waiting evidence, so a run that never paired cannot leak into the next.
     *
     * @return the disagreement when the second arrival differs from the first, otherwise {@code null}
     */
    static Disagreement arrive(String key, Evidence evidence) {
        Evidence waiting = WAITING.get(key);
        if (waiting == null || waiting.driver().equals(evidence.driver())) {
            WAITING.put(key, evidence);
            return null;
        }
        WAITING.remove(key);
        return compare(waiting, evidence);
    }

    /**
     * A differential run must observe the client at least once. A test with no {@code DualAssert} compares nothing, so
     * two empty lists would agree for no reason (self-tests section 3, robustness X3).
     *
     * @return the failure of a run that recorded no client observation, otherwise {@code null}
     */
    static AssertionError unobserved(String test, String cell, Evidence evidence) {
        if (!evidence.observations().isEmpty()) {
            return null;
        }
        return new AssertionError("FAIL " + test + " on " + cell + ": the " + evidence.driver()
                + " run recorded no client observation, so the differential run compared nothing; add a DualAssert");
    }

    /** The failure of a disagreeing test: its verdict from the engine, then the first point that differs. */
    static AssertionError failure(String test, String cell, Disagreement disagreement,
            ConformanceResult conformance) {
        CellResult result = VerdictEngine.resolve(
                new CellRun(test, cell, List.of(Attempt.pass()), false, true), conformance);
        return new AssertionError(result.verdict() + " " + test + " on " + cell + ": " + result.reason() + "\n"
                + disagreement.detail());
    }

    /**
     * Writes both drivers' observations and transcripts into {@code <bundle>/differential/}, with the agent token
     * redacted. A disagreement is then reproducible from the bundle alone.
     */
    static void writeEvidence(Path bundle, Disagreement disagreement, String secret) {
        Path out = bundle.resolve("differential");
        try {
            Files.createDirectories(out);
            for (Evidence evidence : List.of(disagreement.first(), disagreement.second())) {
                StringBuilder observations = new StringBuilder();
                for (Observation observation : evidence.observations()) {
                    observations.append(describe(observation)).append('\n');
                }
                write(out.resolve(evidence.driver() + "-observations.txt"), observations.toString(), secret);
                write(out.resolve(evidence.driver() + "-transcript.jsonl"),
                        String.join("\n", evidence.transcript()) + "\n", secret);
            }
            write(out.resolve("disagreement.txt"), disagreement.detail() + "\n", secret);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write the differential evidence to " + out, e);
        }
    }

    private static void write(Path file, String text, String secret) throws IOException {
        Files.writeString(file, BundleWriter.redact(text, secret), StandardCharsets.UTF_8);
    }
}
