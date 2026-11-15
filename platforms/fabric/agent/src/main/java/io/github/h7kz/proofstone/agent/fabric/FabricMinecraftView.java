package io.github.h7kz.proofstone.agent.fabric;

import io.github.h7kz.proofstone.agent.vanilla.MinecraftView;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.minecraft.server.MinecraftServer;

/**
 * The real server behind {@link FabricView}: the shared vanilla reads ({@link MinecraftView}) plus the two answers that
 * need the Fabric loader (the mod list) or the pack repository (the data packs). Runs on the server thread.
 */
final class FabricMinecraftView extends MinecraftView implements FabricView {

    FabricMinecraftView(MinecraftServer server) {
        super(server, "Fabric");
    }

    /**
     * The mods loaded from the mods folder, told apart by their jar's path: not the game, Java or the loader (the
     * loader reports itself as an ordinary mod of type {@code fabric}, docs/platforms/fabric.md), and not a mod nested
     * in another jar (Fabric API's modules).
     */
    @Override
    public List<Map<String, Object>> mods() {
        Path folder = FabricLoader.getInstance().getGameDir().resolve("mods").toAbsolutePath().normalize();
        List<ModContainer> top = new ArrayList<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            if (mod.getOrigin().getKind() == ModOrigin.Kind.PATH && mod.getOrigin().getPaths().stream()
                    .anyMatch(path -> path.toAbsolutePath().normalize().startsWith(folder))) {
                top.add(mod);
            }
        }
        top.sort(Comparator.comparing(mod -> mod.getMetadata().getId()));
        List<Map<String, Object>> out = new ArrayList<>();
        for (ModContainer mod : top) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", mod.getMetadata().getId());
            entry.put("version", mod.getMetadata().getVersion().getFriendlyString());
            out.add(entry);
        }
        return out;
    }

    @Override
    public Map<String, Object> dataPacks() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", new ArrayList<>(server.getPackRepository().getSelectedIds()));
        out.put("available", new ArrayList<>(server.getPackRepository().getAvailableIds()));
        return out;
    }
}
