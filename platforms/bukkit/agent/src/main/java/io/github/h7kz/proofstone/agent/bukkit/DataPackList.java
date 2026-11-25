package io.github.h7kz.proofstone.agent.bukkit;

import io.github.h7kz.proofstone.agent.protocol.AgentException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;

/**
 * The server's data packs through Paper's {@code Server#getDatapackManager} (the interface {@code io.papermc.paper.datapack.DatapackManager}, Paper API 26.3) (ADR-0017 section 8, protocol 1.3). The agent
 * is built against the 1.8.8 API, which has no such manager, so it is read by reflection: where the method is missing the
 * agent does not report the {@code datapacks} token and never serves the endpoint. Read-only: it lists, never enables,
 * disables or reloads a pack (AGENTS.md section 3).
 */
final class DataPackList {

    private static final String MANAGER_CLASS = "io.papermc.paper.datapack.DatapackManager";

    private DataPackList() {
    }

    /**
     * A Paper API class, loaded through the server's own class loader. {@code Class.forName(name)} would use the agent's
     * plugin loader, and on paper@1.18.2 that falls into the global lookup over every plugin loader, which reaches a
     * disabled plugin's closed jar and fails with {@code zip file closed} (T-M12-03, T-M6-111).
     */
    private static Class<?> serverClass(String name) throws ClassNotFoundException {
        return Class.forName(name, false, Bukkit.getServer().getClass().getClassLoader());
    }

    /** Whether this server's API lists data packs. */
    static boolean supported() {
        try {
            find(Bukkit.getServer().getClass(), "getDatapackManager");
            Class<?> type = serverClass(MANAGER_CLASS);
            find(type, "getPacks");
            find(type, "getEnabledPacks");
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** The first public method of {@code type} with one of the names, whatever its parameters. */
    private static Method find(Class<?> type, String... names) throws NoSuchMethodException {
        for (String name : names) {
            for (Method method : type.getMethods()) {
                if (method.getName().equals(name)) {
                    return method;
                }
            }
        }
        throw new NoSuchMethodException(type.getName() + " has none of " + java.util.Arrays.toString(names));
    }

    /** {@code {enabled, available}} (datapacks.json), on the server's thread. */
    static Map<String, Object> read() {
        try {
            Object manager = find(Bukkit.getServer().getClass(), "getDatapackManager")
                    .invoke(Bukkit.getServer());
            Class<?> type = serverClass(MANAGER_CLASS);
            Method available = find(type, "getPacks");
            Method enabled = find(type, "getEnabledPacks");
            Map<String, Object> out = new LinkedHashMap<String, Object>();
            out.put("enabled", ids((Collection<?>) enabled.invoke(manager)));
            out.put("available", ids((Collection<?>) available.invoke(manager)));
            return out;
        } catch (InvocationTargetException e) {
            throw failed(e.getCause() == null ? e : e.getCause());
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw failed(e);
        }
    }

    private static List<String> ids(Collection<?> packs) throws ReflectiveOperationException {
        List<String> ids = new ArrayList<String>();
        for (Object pack : packs) {
            // The pack's name is the id the server names it by: vanilla, file/x.zip.
            ids.add(String.valueOf(nameMethod(pack.getClass()).invoke(pack)));
        }
        return ids;
    }

    /**
     * {@code getName()} as a public {@code String} method of a public interface the pack implements. The pack's own class
     * is a server internal that reflection may not call, and the public type's name differs between Paper versions (1.18.2
     * has no {@code DiscoveredDatapack}), so it is found by walking the interfaces, not by name. Interfaces come before
     * the classes so an unrelated public {@code getName()} on an implementation class cannot win.
     */
    private static Method nameMethod(Class<?> type) throws NoSuchMethodException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Class<?> iface : c.getInterfaces()) {
                Method found = publicName(iface);
                if (found != null) {
                    return found;
                }
            }
        }
        Method own = publicName(type);
        if (own != null) {
            return own;
        }
        throw new NoSuchMethodException(type.getName() + " has no public String getName() on a public type");
    }

    private static Method publicName(Class<?> type) {
        if (!java.lang.reflect.Modifier.isPublic(type.getModifiers())) {
            return null;
        }
        try {
            Method method = type.getMethod("getName");
            return method.getReturnType() == String.class ? method : null;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static AgentException failed(Throwable cause) {
        return new AgentException(500, "INTERNAL", "cannot list the data packs: " + cause, null);
    }
}
