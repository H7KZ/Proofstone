package io.github.h7kz.proofstone.agent.neoforge;

import io.github.h7kz.proofstone.agent.vanilla.MinecraftView;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.IModInfo;

/**
 * The real server behind the agent: the shared vanilla reads ({@link MinecraftView}) plus the one answer that needs the
 * NeoForge loader, the mod list. Runs on the server thread.
 */
final class NeoForgeMinecraftView extends MinecraftView {

    NeoForgeMinecraftView(MinecraftServer server) {
        super(server, "NeoForge");
    }

    /**
     * The mods loaded from the mods folder, told apart by their jar's path as on Fabric: not the game ({@code minecraft})
     * or NeoForge itself ({@code neoforge}), which FML lists as mods from the libraries, and not a jar-in-jar mod, which
     * FML loads from its cache. The same rule on both loaders, so the loaded-or-loud check reads one list.
     */
    @Override
    public List<Map<String, Object>> mods() {
        Path folder = FMLPaths.MODSDIR.get().toAbsolutePath().normalize();
        List<IModInfo> top = new ArrayList<>();
        for (IModInfo mod : ModList.get().getMods()) {
            Path jar = mod.getOwningFile().getFile().getFilePath();
            if (jar != null && jar.toAbsolutePath().normalize().startsWith(folder)) {
                top.add(mod);
            }
        }
        top.sort(Comparator.comparing(IModInfo::getModId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (IModInfo mod : top) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", mod.getModId());
            entry.put("version", mod.getVersion().toString());
            out.add(entry);
        }
        return out;
    }
}
