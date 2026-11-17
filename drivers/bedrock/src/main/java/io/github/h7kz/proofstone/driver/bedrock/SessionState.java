package io.github.h7kz.proofstone.driver.bedrock;

import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.JoinStage;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.Message;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.Position;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.Slot;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * Everything one Bedrock session knows, as the server sent it: the join stage, received lines, the inventory, the
 * position, and why it ended. It applies no prediction. Waits are on a condition with the remaining time, never a
 * sleep (ADR-0007); every change signals it.
 */
final class SessionState {

    /** Why a session cannot continue: an RPC error code and its text. */
    record Failure(String code, String message) {
    }

    /** The outcome of the join wait. */
    record JoinResult(boolean spawned, Failure failure, JoinStage stage, String lastPacket) {
    }

    private static final int MAX_SLOTS = 1024;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    private final List<Message> received = new ArrayList<>();
    private final List<Long> unconsumed = new ArrayList<>();
    private final List<String> transcript = new ArrayList<>();
    private final long startNanos = System.nanoTime();
    private JoinStage stage = JoinStage.RAKNET_CONNECT;
    private String lastPacket = "none";
    private boolean spawned;
    private Failure failure;
    private List<Slot> inventory = List.of();
    private Position position;

    void stage(JoinStage next) {
        locked(() -> {
            stage = next;
            changed.signalAll();
        });
    }

    JoinStage stage() {
        return locked(() -> stage);
    }

    void packet(String name) {
        locked(() -> {
            lastPacket = name;
            transcript.add((TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)) + " ms " + name);
        });
    }

    void note(String line) {
        locked(() -> transcript.add(line));
    }

    void spawned() {
        locked(() -> {
            spawned = true;
            changed.signalAll();
        });
    }

    /** The first failure wins: a later one is a consequence of it. */
    void fail(String code, String message) {
        locked(() -> {
            if (failure == null) {
                failure = new Failure(code, message);
                transcript.add("FAILED " + code + ": " + message);
            }
            changed.signalAll();
        });
    }

    Failure failure() {
        return locked(() -> failure);
    }

    void message(String text, boolean system) {
        locked(() -> {
            long seq = received.size();
            received.add(new Message(seq, text, system));
            unconsumed.add(seq);
            changed.signalAll();
        });
    }

    void inventory(List<Slot> slots) {
        locked(() -> inventory = List.copyOf(slots));
    }

    void inventorySlot(Slot slot) {
        locked(() -> {
            if (slot.slot() < 0 || slot.slot() >= MAX_SLOTS) {
                throw new IllegalArgumentException("inventory slot " + slot.slot() + " is outside 0 to " + (MAX_SLOTS - 1));
            }
            List<Slot> next = new ArrayList<>(inventory);
            while (next.size() <= slot.slot()) {
                next.add(new Slot(next.size(), "minecraft:air", 0, 0));
            }
            next.set(slot.slot(), slot);
            inventory = List.copyOf(next);
        });
    }

    List<Slot> inventory() {
        return locked(() -> inventory);
    }

    void position(Position next) {
        locked(() -> position = next);
    }

    Position position() {
        return locked(() -> position);
    }

    List<String> transcript() {
        return locked(() -> List.copyOf(transcript));
    }

    List<Message> messagesSince(long since) {
        return locked(() -> received.stream().filter(m -> m.seq() >= since).toList());
    }

    /** Waits until the session spawned, failed or the time ran out; never partial. */
    JoinResult awaitJoin(Duration timeout) {
        return locked(() -> {
            long remaining = timeout.toNanos();
            while (!spawned && failure == null && remaining > 0) {
                remaining = awaitNanos(remaining);
            }
            return new JoinResult(spawned, failure, stage, lastPacket);
        });
    }

    /**
     * Consumes the first unconsumed line matching the pattern, waiting for one. A line that arrived before the call
     * counts. Returns {@code null} on timeout; a failure ends the wait and is read with {@link #failure()}.
     */
    Message awaitMessage(Pattern pattern, Duration timeout) {
        return locked(() -> {
            long remaining = timeout.toNanos();
            while (true) {
                for (int i = 0; i < unconsumed.size(); i++) {
                    Message candidate = received.get(Math.toIntExact(unconsumed.get(i)));
                    if (pattern.matcher(candidate.text()).find()) {
                        unconsumed.remove(i);
                        return candidate;
                    }
                }
                if (failure != null || remaining <= 0) {
                    return null;
                }
                remaining = awaitNanos(remaining);
            }
        });
    }

    /** The unconsumed lines, for a timeout message. */
    List<String> unconsumedText() {
        return locked(() -> unconsumed.stream().map(seq -> received.get(Math.toIntExact(seq)).text()).toList());
    }

    private long awaitNanos(long nanos) {
        try {
            return changed.awaitNanos(nanos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting", e);
        }
    }

    private void locked(Runnable action) {
        locked(() -> {
            action.run();
            return null;
        });
    }

    private <T> T locked(java.util.function.Supplier<T> action) {
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    /** The RPC error for a failure that ended the session. */
    static BedrockRpc.RpcError error(Failure failure) {
        return new BedrockRpc.RpcError(failure.code(), failure.message(), null, null);
    }
}
