package io.github.h7kz.proofstone.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * A {@link Client} or {@link Agent} that cannot place, break or read blocks must say so (T-M11-04): returning quietly
 * would let a test read "nothing happened" as a pass.
 */
class ClientBlockDefaultTest {

    private final Client client = new Client() {
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
        public Position awaitPosition(String description, java.util.function.Predicate<Position> predicate) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Window openBlock(BlockPos pos) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ItemView> inventory() {
            return List.of();
        }

        @Override
        public int experienceLevel() {
            return 0;
        }
    };

    private final Agent agent = new Agent() {
        @Override
        public String command(String command) {
            return "";
        }

        @Override
        public PlayerHandle player(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PluginHandle plugin(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Events events() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> capabilities() {
            return List.of();
        }
    };

    @Test
    void placeBlockFailsLoudlyOnAClientThatDoesNotOverrideIt() {
        assertThatThrownBy(() -> client.placeBlock(new BlockPos(0, 63, 0), Face.UP))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("cannot place blocks");
    }

    @Test
    void breakBlockFailsLoudlyOnAClientThatDoesNotOverrideIt() {
        assertThatThrownBy(() -> client.breakBlock(new BlockPos(0, 64, 1)))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("cannot break blocks");
    }

    @Test
    void blockTypeFailsLoudlyOnAnAgentThatDoesNotOverrideIt() {
        assertThatThrownBy(() -> agent.blockType("world", new BlockPos(0, 64, 1)))
                .isInstanceOf(UnsupportedOperationException.class).hasMessage("this agent cannot read blocks");
    }
}
