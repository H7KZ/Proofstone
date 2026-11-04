package dev.proofstone.core.junit;

import dev.proofstone.api.BlockPos;
import dev.proofstone.api.Client;
import dev.proofstone.api.ItemView;
import dev.proofstone.api.Position;
import dev.proofstone.api.Window;
import dev.proofstone.core.await.Await;
import dev.proofstone.core.driver.ClientSession;
import dev.proofstone.core.driver.Face;
import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** A {@link Client} over a driver session; closes with the test. */
final class SessionClient implements Client, AutoCloseable {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private final String name;
    private final ClientSession session;

    SessionClient(String name, ClientSession session) {
        this.name = name;
        this.session = session;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void command(String commandWithoutSlash) {
        session.command(commandWithoutSlash);
    }

    @Override
    public String awaitMessage(Pattern pattern) {
        return session.awaitMessage(pattern, DEFAULT_TIMEOUT).text();
    }

    @Override
    public Position position() {
        double[] at = session.position();
        return at == null ? null : new Position(at[0], at[1], at[2]);
    }

    @Override
    public Position awaitPosition(String description, Predicate<Position> predicate) {
        return Await.until(name + " position: " + description, this::position,
                at -> at != null && predicate.test(at), DEFAULT_TIMEOUT);
    }

    @Override
    public Window openBlock(BlockPos pos) {
        session.useBlock(pos, Face.UP);
        return new SessionWindow(session.awaitWindow(DEFAULT_TIMEOUT));
    }

    @Override
    public List<ItemView> inventory() {
        return session.inventory().itemViews();
    }

    @Override
    public int experienceLevel() {
        return session.experienceLevel();
    }

    @Override
    public void close() {
        session.close();
    }
}
