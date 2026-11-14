// SPIKE (T-M8-01). Loaded only when Fabric API's lifecycle module is present.
package proofstone.spike;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

final class SpikeFabricEvents {
    private SpikeFabricEvents() {
    }

    static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> SpikeHttp.mark("SERVER_STARTING"));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> SpikeHttp.ready("SERVER_STARTED"));
    }
}
