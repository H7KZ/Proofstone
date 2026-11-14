// SPIKE (T-M8-01). NeoForge mod entry: dedicated server only, no payload registered, so nothing is asked of the client.
// The server-started event is on the game bus (NeoForge.EVENT_BUS), listened to at the lowest priority, read-only.
package proofstone.spike;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(value = "proofstone_spike", dist = Dist.DEDICATED_SERVER)
public final class SpikeNeo {
    public SpikeNeo(IEventBus modBus) {
        SpikeHttp.start("neoforge");
        if ("1".equals(System.getenv("PROOFSTONE_SPIKE_CONTENT"))) {
            // Only with PROOFSTONE_SPIKE_CONTENT=1: one item, the smallest "content mod" (see SpikeFabricContent).
            DeferredRegister.Items items = DeferredRegister.createItems("proofstone_spike");
            items.registerSimpleItem("probe_item");
            items.register(modBus);
            System.out.println("[proofstone-spike] registering item proofstone_spike:probe_item");
        }
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ServerStartingEvent.class,
                event -> SpikeHttp.mark("ServerStartingEvent"));
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ServerStartedEvent.class,
                event -> SpikeHttp.ready("ServerStartedEvent"));
    }
}
