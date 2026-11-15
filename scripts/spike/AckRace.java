// SPIKE (T-M7-18). Throwaway client for the first-login `moved too quickly!` diagnosis, not production code and not part
// of the build. Joins one server many times with raw MCProtocolLib and acknowledges the join teleport in one of two ways:
//   I (immediate): on the first ClientboundPlayerPositionPacket, as the mcpl driver does today (McplSession.applyPosition);
//   T (after tick): only after the first ClientboundSetHealthPacket, which the server sends from the player's first tick
//                   (ServerGamePacketListenerImpl.tickPlayer -> resetPosition, then ServerPlayer.doTick).
// Modes alternate I, T, I, T ... so warm-up affects both alike. Each join prints its timeline; the server log is read by
// scripts/spike/ack-race.sh for the WARN count per name. The sleeps here only pace a throwaway experiment.
//
// Run through scripts/spike/ack-race.sh:
//   java -cp <mcpl runtime classpath> scripts/spike/AckRace.java <host> <port> <joins per mode>

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.network.session.ClientNetworkSession;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetHealthPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;

public class AckRace {

    public static void main(String[] args) throws Exception {
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        int joins = Integer.parseInt(args[2]);
        for (int i = 1; i <= joins; i++) {
            join(host, port, String.format("AckI%02d", i), false);
            join(host, port, String.format("AckT%02d", i), true);
        }
    }

    private static void join(String host, int port, String name, boolean afterTick) throws Exception {
        long t0 = System.nanoTime();
        List<String> timeline = new ArrayList<>();
        CountDownLatch healthSeen = new CountDownLatch(1);
        ClientNetworkSession network = ClientNetworkSessionFactory.factory()
                .setAddress(host, port)
                .setProtocol(new MinecraftProtocol(name))
                .create();
        Object lock = new Object();
        ClientboundPlayerPositionPacket[] pending = new ClientboundPlayerPositionPacket[1];
        boolean[] acked = new boolean[1];
        int[] positions = new int[1];
        network.addListener(new SessionAdapter() {
            @Override
            public void packetReceived(Session session, Packet packet) {
                String at = ms(t0);
                if (packet instanceof ClientboundLoginPacket) {
                    timeline.add(at + " login");
                } else if (packet instanceof ClientboundPlayerPositionPacket move) {
                    positions[0]++;
                    timeline.add(at + " position#" + positions[0] + " " + move.getPosition());
                    synchronized (lock) {
                        if (afterTick && !acked[0]) {
                            pending[0] = move;
                            return;
                        }
                    }
                    ack(session, move, timeline, t0, positions[0] == 1);
                    synchronized (lock) {
                        acked[0] = true;
                    }
                } else if (packet instanceof ClientboundSetHealthPacket) {
                    if (healthSeen.getCount() == 1) {
                        timeline.add(at + " first SetHealth (first player tick)");
                        healthSeen.countDown();
                        ClientboundPlayerPositionPacket move;
                        synchronized (lock) {
                            move = pending[0];
                            pending[0] = null;
                            if (move != null) {
                                acked[0] = true;
                            }
                        }
                        if (move != null) {
                            ack(session, move, timeline, t0, true);
                        }
                    }
                }
            }
        });
        network.connect(false);
        healthSeen.await(20, TimeUnit.SECONDS);
        Thread.sleep(1500);
        network.disconnect("spike done");
        Thread.sleep(500);
        System.out.println("JOIN " + name + " mode=" + (afterTick ? "after-tick" : "immediate") + " positions="
                + positions[0] + " | " + String.join(" | ", timeline));
    }

    private static void ack(Session session, ClientboundPlayerPositionPacket move, List<String> timeline, long t0,
            boolean first) {
        // Absolute join position (no relative flags on the join teleport), as McplSession.applyPosition sends it.
        session.send(new ServerboundAcceptTeleportationPacket(move.getId(), move.getPosition().getX(),
                move.getPosition().getY(), move.getPosition().getZ(), move.getYRot(), move.getXRot()));
        timeline.add(ms(t0) + " -> accept id=" + move.getId());
        if (first) {
            session.send(ServerboundPlayerLoadedPacket.INSTANCE);
        }
    }

    private static String ms(long t0) {
        return String.format("%.1fms", (System.nanoTime() - t0) / 1e6);
    }
}
