package dev.proofstone.api;

import dev.proofstone.api.spi.OracleWaiter;
import dev.proofstone.api.spi.OracleWaiter.Outcome;
import dev.proofstone.api.spi.OracleWaiter.Side;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Dual-oracle assertions (ADR-0003, test-api spec section 5). Each check waits for the server oracle (agent) and
 * the client oracle (driver) together. Both matching passes. Exactly one matching throws {@link DisagreeException}.
 * Neither matching, or an oracle that never answered, throws a plain {@link AssertionError}; nothing passes or
 * skips quietly. Every failure shows both observed values and the time waited.
 */
public final class DualAssert {

    /** The wait for both oracles (spec section 5). */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private static final List<String> SERVER_ONLY_REASONS = new CopyOnWriteArrayList<>();

    private DualAssert() {
    }

    /** Assertions about an item in the player's inventory, checked on both oracles. */
    public static ItemAssert item(PlayerHandle player, Client client) {
        return item(player, client, loadWaiter());
    }

    static ItemAssert item(PlayerHandle player, Client client, OracleWaiter waiter) {
        return new ItemAssert(Objects.requireNonNull(player, "player"), Objects.requireNonNull(client, "client"),
                waiter);
    }

    /** Assertions about the experience level, checked on both oracles. */
    public static LevelAssert level(PlayerHandle player, Client client) {
        return new LevelAssert(Objects.requireNonNull(player, "player"), Objects.requireNonNull(client, "client"),
                loadWaiter());
    }

    /** Every reason given to {@code serverOnly} so far, for the report (ADR-0003). */
    public static List<String> serverOnlyReasons() {
        return List.copyOf(SERVER_ONLY_REASONS);
    }

    private static OracleWaiter loadWaiter() {
        for (OracleWaiter waiter : ServiceLoader.load(OracleWaiter.class)) {
            return waiter;
        }
        throw new IllegalStateException("no OracleWaiter on the classpath: proofstone-core provides it, add it to the "
                + "test runtime");
    }

    /** Checks the experience level: the server's {@code level} and the client's must both equal the expected value. */
    public static final class LevelAssert {
        private final PlayerHandle player;
        private final Client client;
        private final OracleWaiter waiter;

        private LevelAssert(PlayerHandle player, Client client, OracleWaiter waiter) {
            this.player = player;
            this.client = client;
            this.waiter = waiter;
        }

        public LevelAssert isEqualTo(int expected) {
            Outcome<Integer, Integer> outcome = waiter.untilBoth("experience level " + expected,
                    () -> player.state().level(), client::experienceLevel,
                    (server, clientLevel) -> Integer.valueOf(expected).equals(server)
                            && Integer.valueOf(expected).equals(clientLevel),
                    DEFAULT_TIMEOUT);
            if (outcome.matched()) {
                return this;
            }
            String server = outcome.server().present() ? String.valueOf(outcome.server().value())
                    : "no answer (" + outcome.server().error() + ")";
            String clientView = outcome.client().present() ? String.valueOf(outcome.client().value())
                    : "no answer (" + outcome.client().error() + ")";
            String body = "experience level: expected " + expected + " after " + outcome.elapsed().toMillis()
                    + " ms\n  server oracle (agent):  " + server + "\n  client oracle (driver): " + clientView;
            if (!outcome.server().present() || !outcome.client().present()) {
                throw new AssertionError("ORACLE MISSING, not a pass: " + body);
            }
            boolean serverOk = Integer.valueOf(expected).equals(outcome.server().value());
            boolean clientOk = Integer.valueOf(expected).equals(outcome.client().value());
            if (serverOk != clientOk) {
                throw new DisagreeException("DISAGREE: " + body, server, clientView);
            }
            throw new AssertionError("FAIL: " + body);
        }
    }

    /** Checks on the first inventory item an {@code inInventory} predicate selects. */
    public static final class ItemAssert {
        private final PlayerHandle player;
        private final Client client;
        private final OracleWaiter waiter;
        private Predicate<ItemView> selector;
        private String selected = "the selected item";

        private ItemAssert(PlayerHandle player, Client client, OracleWaiter waiter) {
            this.player = player;
            this.client = client;
            this.waiter = waiter;
        }

        /** Selects the item and asserts that both oracles have one. */
        public ItemAssert inInventory(Predicate<ItemView> predicate) {
            return inInventory("matching the predicate", predicate);
        }

        /** As {@link #inInventory(Predicate)}, with a description that appears in failures. */
        public ItemAssert inInventory(String description, Predicate<ItemView> predicate) {
            this.selector = Objects.requireNonNull(predicate, "predicate");
            this.selected = "item " + description;
            verify("present in the inventory", item -> "present", "present");
            return this;
        }

        public ItemAssert amount(int expected) {
            verify("amount", ItemView::amount, expected);
            return this;
        }

        public ItemAssert enchants(Map<String, Integer> expected) {
            verify("enchants", ItemView::enchants, Map.copyOf(expected));
            return this;
        }

        public ItemAssert storedEnchants(Map<String, Integer> expected) {
            verify("storedEnchants", ItemView::storedEnchants, Map.copyOf(expected));
            return this;
        }

        /** The custom name as plain text. */
        public ItemAssert name(String expected) {
            verify("name", ItemView::name, expected);
            return this;
        }

        /** Leaves the dual oracle for state clients cannot see. The reason is mandatory and goes to the report. */
        public ServerOnly serverOnly(String reason) {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("serverOnly needs a non-blank reason (ADR-0003)");
            }
            SERVER_ONLY_REASONS.add(reason);
            return new ServerOnly(reason);
        }

        private void verify(String what, Function<ItemView, Object> field, Object expected) {
            if (selector == null) {
                throw new IllegalStateException("call inInventory first: there is no item selected");
            }
            Predicate<List<ItemView>> matches = items -> select(items)
                    .map(item -> Objects.equals(field.apply(item), expected)).orElse(false);
            Outcome<List<ItemView>, List<ItemView>> outcome = waiter.untilBoth(selected + " " + what,
                    () -> new ArrayList<>(player.state().inventory().values()), client::inventory,
                    (server, clientItems) -> matches.test(server) && matches.test(clientItems), DEFAULT_TIMEOUT);
            if (outcome.matched()) {
                return;
            }
            String header = what + " of " + selected + ": expected " + expected + " after "
                    + outcome.elapsed().toMillis() + " ms";
            String server = describe(outcome.server(), field);
            String clientView = describe(outcome.client(), field);
            String body = header + "\n  server oracle (agent):  " + server + "\n  client oracle (driver): "
                    + clientView;
            boolean serverOk = outcome.server().present() && matches.test(outcome.server().value());
            boolean clientOk = outcome.client().present() && matches.test(outcome.client().value());
            if (!outcome.server().present() || !outcome.client().present()) {
                throw new AssertionError("ORACLE MISSING, not a pass: " + body);
            }
            if (serverOk != clientOk) {
                throw new DisagreeException("DISAGREE: " + body, server, clientView);
            }
            throw new AssertionError("FAIL: " + body);
        }

        private Optional<ItemView> select(List<ItemView> items) {
            return items.stream().filter(item -> !item.isEmpty() && selector.test(item)).findFirst();
        }

        private String describe(Side<List<ItemView>> side, Function<ItemView, Object> field) {
            if (!side.present()) {
                return "no answer (" + side.error() + ")";
            }
            return select(side.value()).map(item -> String.valueOf(field.apply(item)) + " in " + item)
                    .orElse("no matching item among " + side.value().stream().filter(i -> !i.isEmpty()).toList());
        }

        /** Checks on the server oracle alone. */
        public final class ServerOnly {
            private final String reason;

            private ServerOnly(String reason) {
                this.reason = reason;
            }

            /** The reason given, as reported. */
            public String reason() {
                return reason;
            }

            /** Asserts a persistent data container value on the selected item. */
            public ServerOnly pdc(String key, Object expected) {
                if (selector == null) {
                    throw new IllegalStateException("call inInventory first: there is no item selected");
                }
                Predicate<List<ItemView>> matches = items -> select(items).map(ItemView::pdc)
                        .map(pdc -> Objects.equals(pdc.get(key), expected)).orElse(false);
                Outcome<List<ItemView>, String> outcome = waiter.untilBoth(selected + " pdc " + key,
                        () -> new ArrayList<>(player.state().inventory().values()), () -> reason,
                        (server, ignored) -> matches.test(server), DEFAULT_TIMEOUT);
                if (outcome.matched()) {
                    return this;
                }
                String seen = outcome.server().present()
                        ? select(outcome.server().value()).map(item -> "pdc=" + item.pdc() + " in " + item)
                                .orElse("no matching item")
                        : "no answer (" + outcome.server().error() + ")";
                throw new AssertionError("FAIL (server only: " + reason + "): pdc " + key + " of " + selected
                        + ": expected " + expected + " after " + outcome.elapsed().toMillis()
                        + " ms\n  server oracle (agent): " + seen);
            }
        }
    }
}
