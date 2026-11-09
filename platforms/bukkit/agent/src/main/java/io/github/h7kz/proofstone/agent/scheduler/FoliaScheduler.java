package io.github.h7kz.proofstone.agent.scheduler;

import io.github.h7kz.proofstone.agent.protocol.AgentException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Routes agent work to Folia's region schedulers (docs/platforms/folia.md). Players go to their entity scheduler,
 * blocks to the region that owns their chunk, and everything else to the global region scheduler.
 *
 * <p>Folia's API is reached through reflection only, so the agent still compiles against the Bukkit 1.8.8 API and runs
 * on every row (ADR-0002). The scheduler classes are named by their Folia interfaces, and a server without them fails
 * the constructor, never a later call. Folia's thread checks stay on: every access happens on the thread that owns it.
 */
public final class FoliaScheduler implements Scheduler {

    private static final String PACKAGE = "io.papermc.paper.threadedregions.scheduler.";
    private static final long EVERY_TICK = 1L;

    private final Plugin plugin;
    private final Object global;
    private final Method globalRun;
    private final Method globalFixedRate;
    private final Object regions;
    private final Method regionRun;
    private final Method regionFixedRate;
    private final Method entityScheduler;
    private final Method entityRun;
    private final Method cancel;
    private final List<Object> repeating = new ArrayList<>();
    private final Map<String, AtomicLong> regionTicks = new ConcurrentHashMap<>();

    /** @throws IllegalStateException when the server is not Folia, or its scheduler API has no expected method */
    public FoliaScheduler(Plugin plugin) {
        this.plugin = plugin;
        try {
            Class<?> globalClass = Class.forName(PACKAGE + "GlobalRegionScheduler");
            Class<?> regionClass = Class.forName(PACKAGE + "RegionScheduler");
            Class<?> entityClass = Class.forName(PACKAGE + "EntityScheduler");
            Class<?> taskClass = Class.forName(PACKAGE + "ScheduledTask");
            Class<?> bukkit = Bukkit.class;
            Class<?> entity = Class.forName("org.bukkit.entity.Entity");
            this.global = bukkit.getMethod("getGlobalRegionScheduler").invoke(null);
            this.regions = bukkit.getMethod("getRegionScheduler").invoke(null);
            this.globalRun = globalClass.getMethod("run", Plugin.class, Consumer.class);
            this.globalFixedRate = globalClass.getMethod("runAtFixedRate", Plugin.class, Consumer.class,
                    long.class, long.class);
            this.regionRun = regionClass.getMethod("run", Plugin.class, Location.class, Consumer.class);
            this.regionFixedRate = regionClass.getMethod("runAtFixedRate", Plugin.class, Location.class,
                    Consumer.class, long.class, long.class);
            this.entityScheduler = entity.getMethod("getScheduler");
            this.entityRun = entityClass.getMethod("run", Plugin.class, Consumer.class, Runnable.class);
            this.cancel = taskClass.getMethod("cancel");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("the server has no Folia region scheduler API: " + e, e);
        }
    }

    @Override
    public void run(Affinity affinity, Runnable task, Consumer<AgentException> dropped) {
        switch (affinity.kind()) {
            case PLAYER:
                runOnPlayer(affinity.player(), task, dropped);
                return;
            case BLOCK:
                if (runOnRegion(affinity, task, dropped)) {
                    return;
                }
                // An unknown world has no region. The global thread runs the task, which answers INVALID_REQUEST.
                runGlobally(task, dropped);
                return;
            default:
                runGlobally(task, dropped);
        }
    }

    private void runOnPlayer(String name, Runnable task, Consumer<AgentException> dropped) {
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) {
            dropped.accept(new AgentException(404, "PLAYER_NOT_FOUND", "no player named " + name,
                    Collections.singletonMap("name", name)));
            return;
        }
        Object scheduler = invoke(entityScheduler, player);
        Consumer<Object> body = ignored -> task.run();
        Runnable retired = () -> dropped.accept(new AgentException(404, "PLAYER_NOT_FOUND",
                "player " + name + " left before the task could run", Collections.singletonMap("name", name)));
        if (invoke(entityRun, scheduler, plugin, body, retired) == null) {
            retired.run();
        }
    }

    /** @return {@code false} when the world is not loaded, so the caller falls back to the global thread */
    private boolean runOnRegion(Affinity affinity, Runnable task, Consumer<AgentException> dropped) {
        World world = Bukkit.getWorld(affinity.world());
        if (world == null) {
            return false;
        }
        Location at = new Location(world, affinity.x(), affinity.y(), affinity.z());
        ensureRegionTicker(world, affinity.x() >> 4, affinity.z() >> 4, at);
        Consumer<Object> body = ignored -> task.run();
        if (invoke(regionRun, regions, plugin, at, body) == null) {
            dropped.accept(new AgentException(503, "UNAVAILABLE",
                    "the region of " + affinity.world() + " is not accepting work", null));
        }
        return true;
    }

    private void runGlobally(Runnable task, Consumer<AgentException> dropped) {
        Consumer<Object> body = ignored -> task.run();
        if (invoke(globalRun, global, plugin, body) == null) {
            dropped.accept(new AgentException(503, "UNAVAILABLE", "the global region is not accepting work", null));
        }
    }

    @Override
    public void repeatGlobally(Runnable task) {
        Consumer<Object> body = ignored -> task.run();
        track(invoke(globalFixedRate, global, plugin, body, EVERY_TICK, EVERY_TICK));
    }

    /**
     * Counts the ticks of the region that owns one chunk. The counter runs on that region's own thread, so it counts
     * the region's ticks, not the global ones. Started by the first operation that touches the chunk.
     */
    private void ensureRegionTicker(World world, int chunkX, int chunkZ, Location at) {
        String key = world.getName() + ":" + chunkX + "," + chunkZ;
        AtomicLong counter = new AtomicLong();
        if (regionTicks.putIfAbsent(key, counter) != null) {
            return;
        }
        Consumer<Object> count = ignored -> counter.incrementAndGet();
        track(invoke(regionFixedRate, regions, plugin, at, count, EVERY_TICK, EVERY_TICK));
    }

    @Override
    public Map<String, Long> regionTicks() {
        Map<String, Long> out = new TreeMap<>();
        for (Map.Entry<String, AtomicLong> entry : regionTicks.entrySet()) {
            out.put(entry.getKey(), entry.getValue().get());
        }
        return out;
    }

    @Override
    public void close() {
        synchronized (repeating) {
            for (Object task : repeating) {
                invoke(cancel, task);
            }
            repeating.clear();
        }
    }

    private void track(Object task) {
        if (task == null) {
            throw new IllegalStateException("Folia refused a repeating task: the scheduler returned null");
        }
        synchronized (repeating) {
            repeating.add(task);
        }
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot call Folia " + method.getName(), e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Folia " + method.getName() + " failed: " + e.getCause(), e.getCause());
        }
    }
}
