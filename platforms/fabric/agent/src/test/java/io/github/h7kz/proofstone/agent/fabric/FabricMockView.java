package io.github.h7kz.proofstone.agent.fabric;

import io.github.h7kz.proofstone.agent.vanilla.MockView;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** The shared in-memory server plus the Fabric row's mods and data packs. */
final class FabricMockView extends MockView implements FabricView {

    FabricMockView() {
        mods.add(mod("fabric-api", "0.166.2+26.3"));
        mods.add(mod("proofstone-agent", "0.1.0"));
    }

    @Override
    public Map<String, Object> dataPacks() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", Arrays.asList("vanilla", "fabric-convention-tags-v2", "file/x.zip"));
        out.put("available", Arrays.asList("vanilla", "fabric-convention-tags-v2", "file/x.zip"));
        return out;
    }
}
