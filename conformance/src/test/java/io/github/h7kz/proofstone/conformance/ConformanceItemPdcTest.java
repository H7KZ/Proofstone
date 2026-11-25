package io.github.h7kz.proofstone.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.Cell;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.ItemView;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.PlayerHandle;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;
import java.util.Map;

/**
 * C-I01 (specs/self-tests.md section 1, T-M11-05): {@code ItemView.pdc} carries the server's persistent data container. The
 * stick is given by the vanilla {@code give} command with the value under {@code PublicBukkitValues}, the compound Bukkit
 * keeps its container in, so the read goes through the server's own PDC and no fixture plugin. Every common type comes back
 * as its Java type, a nested container as a map, a tag Bukkit cannot read as the {@code unsupported} marker, and an item
 * with no data has none.
 */
@Proofstone
@Matrix(platform = Platform.PAPER, versions = {"latest", "1.18.2", "1.17.1", "1.16.5"})
@Matrix(platform = Platform.FOLIA, versions = {"latest"})
@Matrix(platform = Platform.PURPUR, versions = {"latest"})
@Matrix(platform = Platform.SPIGOT, versions = {"latest"})
class ConformanceItemPdcTest {

    /** The same compound in SNBT, which every version from 1.13 reads. */
    private static final String PDC = "PublicBukkitValues:{"
            + "\"proofstone:b\":3b,\"proofstone:s\":2s,\"proofstone:i\":70000,\"proofstone:l\":9000000000L,"
            + "\"proofstone:f\":1.5f,\"proofstone:d\":2.5d,\"proofstone:t\":\"hello\","
            + "\"proofstone:ba\":[B;1b,-2b],\"proofstone:ia\":[I;3,4],\"proofstone:la\":[L;5L,9000000000L],"
            + "\"proofstone:c\":{\"proofstone:n\":7b},\"proofstone:odd\":[1,2]}";

    @CellTest
    void theServerReadsEveryCommonPdcTypeAndMarksTheOnesItCannotRead(Cell cell, Agent agent, Client client) {
        PlayerHandle player = agent.player(client).reset();
        player.awaitState("empty inventory", state -> state.inventory().isEmpty());

        agent.command("give " + client.name() + " minecraft:stick" + (components(cell.version())
                ? "[custom_data={" + PDC + "}]" : "{" + PDC + "}"));
        agent.command("give " + client.name() + " minecraft:apple");
        Map<Integer, ItemView> inventory = player.awaitState("the stick and the apple",
                state -> state.inventory().size() == 2).inventory();

        ItemView stick = inventory.values().stream().filter(item -> item.is("stick")).findFirst().orElseThrow();
        ItemView apple = inventory.values().stream().filter(item -> item.is("apple")).findFirst().orElseThrow();
        Map<String, Object> pdc = stick.pdc();
        assertThat(pdc).as("the stick's pdc").isNotNull();
        assertThat(pdc).containsEntry("proofstone:b", (byte) 3).containsEntry("proofstone:s", (short) 2)
                .containsEntry("proofstone:i", 70000).containsEntry("proofstone:l", 9000000000L)
                .containsEntry("proofstone:f", 1.5f).containsEntry("proofstone:d", 2.5d)
                .containsEntry("proofstone:t", "hello")
                .containsEntry("proofstone:c", Map.of("proofstone:n", (byte) 7));
        assertThat((byte[]) pdc.get("proofstone:ba")).containsExactly(1, -2);
        assertThat((int[]) pdc.get("proofstone:ia")).containsExactly(3, 4);
        assertThat((long[]) pdc.get("proofstone:la")).containsExactly(5L, 9000000000L);
        assertThat(pdc.get("proofstone:odd")).as("a list tag is reported, not dropped").isInstanceOf(Map.class);
        assertThat(new java.util.ArrayList<Object>(((Map<?, ?>) pdc.get("proofstone:odd")).keySet())).containsExactly("unsupported");
        assertThat(pdc).hasSize(12);
        assertThat(apple.pdc()).as("an item with no data has no pdc").isNull();
    }

    /** Item components replaced item NBT in 1.20.5. Versions are {@code 1.x.y} or the year-based {@code 26.x}. */
    private static boolean components(String version) {
        String[] part = version.split("[.]");
        int major = Integer.parseInt(part[0]);
        if (major != 1) {
            return true;
        }
        int minor = Integer.parseInt(part[1]);
        int patch = part.length > 2 ? Integer.parseInt(part[2]) : 0;
        return minor > 20 || (minor == 20 && patch >= 5);
    }
}
