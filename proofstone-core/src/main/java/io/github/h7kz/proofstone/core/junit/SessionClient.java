package io.github.h7kz.proofstone.core.junit;

import io.github.h7kz.proofstone.api.BlockPos;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.ItemView;
import io.github.h7kz.proofstone.api.Position;
import io.github.h7kz.proofstone.api.Window;
import io.github.h7kz.proofstone.core.await.Await;
import io.github.h7kz.proofstone.core.driver.ClientSession;
import io.github.h7kz.proofstone.core.driver.Face;
import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** A {@link Client} over a driver session; closes with the test. */
final class SessionClient implements Client, AutoCloseable {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private final String name;
    private final ClientSession session;
    /** How this client switches servers; {@code null} on a single-server cell. */
    private final Switcher switcher;

    /** A topology's server switch for one client (TopologyInvocation). */
    interface Switcher {
        void transfer(String player, ClientSession session, String target);
    }

    SessionClient(String name, ClientSession session) {
        this(name, session, null);
    }

    SessionClient(String name, ClientSession session, Switcher switcher) {
        this.name = name;
        this.session = session;
        this.switcher = switcher;
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
    public void transferTo(String target) {
        if (switcher == null) {
            throw new IllegalStateException("transferTo(\"" + target + "\") needs a topology cell (a proxy and its "
                    + "backends); this is a single-server cell, so there is no server to switch to (ADR-0016 section 5)");
        }
        switcher.transfer(name, session, target);
    }

    /** The driver's packet transcript, for the repro bundle. */
    List<String> transcript() {
        return session.transcript();
    }

    @Override
    public void close() {
        session.close();
    }
}
