// SPIKE (T-M8-01). Fabric server entrypoint. Works with the loader alone; the server-started event needs Fabric API's
// lifecycle module, so it is registered only when that module is loaded.
package proofstone.spike;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class SpikeFabric implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        SpikeHttp.start("fabric");
        if ("1".equals(System.getenv("PROOFSTONE_SPIKE_CONTENT"))) {
            SpikeFabricContent.register();
        }
        if (FabricLoader.getInstance().isModLoaded("fabric-lifecycle-events-v1")) {
            SpikeFabricEvents.register();
        } else {
            System.out.println("[proofstone-spike] fabric-lifecycle-events-v1 absent: no server-started event");
        }
    }
}
