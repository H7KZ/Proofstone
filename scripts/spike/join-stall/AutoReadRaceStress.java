import io.netty.channel.embedded.EmbeddedChannel;
import net.raphimc.viaproxy.proxy.util.ChannelUtil;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

// Same call pattern as ViaProxy's login on the server channel (CompressionPacketHandler + LoginPacketHandler + ConfigurationPacketHandler),
// against the UNMODIFIED ChannelUtil of the pinned image. A = the server-channel event loop, B = the client-channel event loop.
public class Stress {
    public static void main(String[] a) throws Exception {
        int n = Integer.parseInt(a[0]);
        ExecutorService ex = Executors.newFixedThreadPool(2);
        int stuck = 0, raceThrow = 0, ok = 0;
        for (int i = 0; i < n; i++) {
            EmbeddedChannel ch = new EmbeddedChannel();
            CountDownLatch d1 = new CountDownLatch(1), r1 = new CountDownLatch(1);
            AtomicBoolean thrown = new AtomicBoolean();
            // B: write listener of the forwarded compression packet, runs on the client channel's loop after A disabled
            Future<?> b = ex.submit(() -> {
                try { d1.await(); ChannelUtil.restoreAutoRead(ch); } catch (Throwable t) { thrown.set(true); }
                r1.countDown();
            });
            // A: S2C compression -> disable, then S2C LoginFinished -> disable, then (client acknowledged) -> restore
            Future<?> aF = ex.submit(() -> {
                ChannelUtil.disableAutoRead(ch);
                d1.countDown();
                ChannelUtil.disableAutoRead(ch);
                try { r1.await(); } catch (InterruptedException e) { }
                try { ChannelUtil.restoreAutoRead(ch); } catch (Throwable t) { thrown.set(true); }
            });
            aF.get(); b.get();
            if (!ch.config().isAutoRead()) stuck++; else if (thrown.get()) raceThrow++; else ok++;
            ch.finishAndReleaseAll();
        }
        ex.shutdown();
        System.out.println("iterations=" + n + " ok=" + ok + " autoReadRestoredAfterSwallowedRaceException=" + raceThrow + " STUCK_autoRead_false=" + stuck);
    }
}
