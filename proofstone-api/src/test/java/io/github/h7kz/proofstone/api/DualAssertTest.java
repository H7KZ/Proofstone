package io.github.h7kz.proofstone.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.api.spi.OracleWaiter;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class DualAssertTest {

    /** Probes each side once. A probe that throws is "no answer", as in the real waiter. */
    private static final OracleWaiter ONCE = new OracleWaiter() {
        @Override
        public <S, C> Outcome<S, C> untilBoth(String description, Supplier<S> server, Supplier<C> client,
                BiPredicate<S, C> ok, Duration timeout) {
            Side<S> s = probe(server);
            Side<C> c = probe(client);
            boolean matched = s.present() && c.present() && ok.test(s.value(), c.value());
            return new Outcome<>(matched, Duration.ofMillis(7), s, c);
        }

        private <T> Side<T> probe(Supplier<T> supplier) {
            try {
                return new Side<>(true, supplier.get(), null);
            } catch (RuntimeException e) {
                return new Side<>(false, null, e.toString());
            }
        }
    };

    private static final ItemView BOOK_5 = new ItemView("minecraft:enchanted_book", 1, Map.of(),
            Map.of("minecraft:sharpness", 5), null, List.of(), 0, 0, null);
    private static final ItemView BOOK_4 = new ItemView("minecraft:enchanted_book", 1, Map.of(),
            Map.of("minecraft:sharpness", 4), null, List.of(), 0, 0, null);

    @Test
    void bothOraclesMatchingPasses() {
        DualAssert.item(server(BOOK_5), client(BOOK_5), ONCE)
                .inInventory(i -> i.is("enchanted_book"))
                .storedEnchants(Map.of("minecraft:sharpness", 5));
    }

    @Test
    void oneOracleMatchingIsADisagreementShowingBothValues() {
        assertThatThrownBy(() -> item(server(BOOK_5), client(BOOK_4)).storedEnchants(Map.of("minecraft:sharpness", 5)))
                .isInstanceOfSatisfying(DisagreeException.class, e -> {
                    assertThat(e.getMessage()).startsWith("DISAGREE").contains("expected {minecraft:sharpness=5}")
                            .contains("server oracle (agent):  {minecraft:sharpness=5}")
                            .contains("client oracle (driver): {minecraft:sharpness=4}").contains("7 ms");
                    assertThat(e.serverObserved()).contains("sharpness=5");
                    assertThat(e.clientObserved()).contains("sharpness=4");
                });
    }

    @Test
    void neitherMatchingIsAPlainFailureNotADisagreement() {
        assertThatThrownBy(() -> item(server(BOOK_4), client(BOOK_4)).storedEnchants(Map.of("minecraft:sharpness", 5)))
                .isInstanceOf(AssertionError.class).isNotInstanceOf(DisagreeException.class)
                .hasMessageStartingWith("FAIL").hasMessageContaining("sharpness=4");
    }

    @Test
    void anItemOnlyOneSideHasIsADisagreement() {
        assertThatThrownBy(() -> item(server(BOOK_5), client()))
                .isInstanceOf(DisagreeException.class).hasMessageContaining("no matching item among []");
    }

    @Test
    void anOracleThatDoesNotAnswerFailsLoudly() {
        Client broken = new FakeClient(null) {
            @Override
            public List<ItemView> inventory() {
                throw new IllegalStateException("not synced yet");
            }
        };
        assertThatThrownBy(() -> DualAssert.item(server(BOOK_5), broken, ONCE).inInventory(i -> true))
                .isInstanceOf(AssertionError.class).isNotInstanceOf(DisagreeException.class)
                .hasMessageStartingWith("ORACLE MISSING").hasMessageContaining("not synced yet")
                .hasMessageContaining("server oracle (agent)");
    }

    @Test
    void aCheckBeforeSelectingAnItemIsAnError() {
        assertThatThrownBy(() -> DualAssert.item(server(BOOK_5), client(BOOK_5), ONCE).amount(1))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("inInventory");
    }

    @Test
    void serverOnlyNeedsAReasonAndRecordsIt() {
        DualAssert.ItemAssert selected = item(server(BOOK_5), client(BOOK_5));
        assertThatThrownBy(() -> selected.serverOnly(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> selected.serverOnly(null)).isInstanceOf(IllegalArgumentException.class);

        selected.serverOnly("PDC is not sent to clients");

        assertThat(DualAssert.serverOnlyReasons()).contains("PDC is not sent to clients");
    }

    @Test
    void serverOnlyPdcChecksTheServerAndNamesTheReasonOnFailure() {
        ItemView tagged = new ItemView("minecraft:stick", 1, Map.of(), Map.of(), null, List.of(), 0, 0, null,
                Map.of("myplugin:key", "v"), null);
        ItemView plain = new ItemView("minecraft:stick", 1, Map.of(), Map.of(), null, List.of(), 0, 0, null);
        DualAssert.item(server(tagged), client(plain), ONCE).inInventory(i -> i.is("stick"))
                .serverOnly("PDC is not sent to clients").pdc("myplugin:key", "v");

        assertThatThrownBy(() -> DualAssert.item(server(tagged), client(plain), ONCE)
                .inInventory(i -> i.is("stick")).serverOnly("PDC is not sent to clients")
                .pdc("myplugin:key", "other"))
                .isInstanceOf(AssertionError.class).hasMessageContaining("server only: PDC is not sent to clients")
                .hasMessageContaining("expected other").hasMessageContaining("{myplugin:key=v}");
    }

    private static DualAssert.ItemAssert item(PlayerHandle server, Client client) {
        return DualAssert.item(server, client, ONCE).inInventory(i -> i.is("enchanted_book"));
    }

    private static PlayerHandle server(ItemView... items) {
        return new FakePlayer(items);
    }

    private static Client client(ItemView... items) {
        return new FakeClient(List.of(items));
    }

    private static final class FakePlayer implements PlayerHandle {
        private final ItemView[] items;

        FakePlayer(ItemView[] items) {
            this.items = items;
        }

        @Override
        public String name() {
            return "Alex";
        }

        @Override
        public PlayerHandle reset() {
            return this;
        }

        @Override
        public PlayerHandle level(int level) {
            return this;
        }

        @Override
        public PlayerHandle give(Item item) {
            return this;
        }

        @Override
        public PlayerState state() {
            Map<Integer, ItemView> inventory = new java.util.HashMap<>();
            for (int i = 0; i < items.length; i++) {
                inventory.put(i, items[i]);
            }
            return new PlayerState("Alex", 0, 0, "survival", 20, inventory, ItemView.EMPTY);
        }

        @Override
        public PlayerState awaitState(String description, Predicate<PlayerState> predicate) {
            return state();
        }
    }

    private static class FakeClient implements Client {
        private final List<ItemView> items;

        FakeClient(List<ItemView> items) {
            this.items = items;
        }

        @Override
        public String name() {
            return "Alex";
        }

        @Override
        public void command(String commandWithoutSlash) {
        }

        @Override
        public String awaitMessage(Pattern pattern) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Position position() {
            return null;
        }

        @Override
        public Position awaitPosition(String description, Predicate<Position> predicate) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Window openBlock(BlockPos pos) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int experienceLevel() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ItemView> inventory() {
            return items;
        }
    }
}
