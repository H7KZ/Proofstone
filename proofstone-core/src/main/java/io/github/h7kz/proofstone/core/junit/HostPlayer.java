package io.github.h7kz.proofstone.core.junit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.h7kz.proofstone.api.Item;
import io.github.h7kz.proofstone.api.ItemView;
import io.github.h7kz.proofstone.api.PlayerHandle;
import io.github.h7kz.proofstone.api.PlayerState;
import io.github.h7kz.proofstone.core.await.Await;
import io.github.h7kz.proofstone.core.host.PaperHost;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** A {@link PlayerHandle} over the agent's {@code /v1/players} endpoints. */
final class HostPlayer implements PlayerHandle {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final PaperHost host;
    private final String name;

    HostPlayer(PaperHost host, String name) {
        this.host = host;
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public PlayerHandle reset() {
        host.request("POST", path("/reset"), Map.of());
        return this;
    }

    @Override
    public PlayerHandle level(int level) {
        host.request("PATCH", path(""), Map.of("level", level));
        return this;
    }

    @Override
    public PlayerHandle give(Item item) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", item.type());
        body.put("amount", item.amount());
        if (!item.enchants().isEmpty()) {
            body.put("enchants", item.enchants());
        }
        if (!item.storedEnchants().isEmpty()) {
            body.put("storedEnchants", item.storedEnchants());
        }
        if (item.name() != null) {
            body.put("name", item.name());
        }
        if (!item.lore().isEmpty()) {
            body.put("lore", item.lore());
        }
        if (item.damage() > 0) {
            body.put("damage", item.damage());
        }
        host.request("POST", path("/give"), body);
        return this;
    }

    @Override
    public PlayerState state() {
        io.github.h7kz.proofstone.protocol.PlayerState wire;
        try {
            wire = JSON.treeToValue(host.request("GET", path(""), null), io.github.h7kz.proofstone.protocol.PlayerState.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("unparsable player state of " + name, e);
        }
        Map<Integer, ItemView> inventory = new LinkedHashMap<>();
        wire.inventory().forEach((slot, item) -> inventory.put(Integer.valueOf(slot), view(item)));
        return new PlayerState(wire.name(), wire.level(), wire.exp(), wire.gamemode(), wire.health(), inventory,
                wire.cursor() == null ? ItemView.EMPTY : view(wire.cursor()));
    }

    @Override
    public PlayerState awaitState(String description, Predicate<PlayerState> predicate) {
        return Await.until(name + " state: " + description, this::state, predicate, SessionWindow.DEFAULT_TIMEOUT);
    }

    private String path(String suffix) {
        return "/v1/players/" + name + suffix;
    }

    /** The wire item as a view. Fields the agent leaves out are the neutral value, never a guess. */
    static ItemView view(io.github.h7kz.proofstone.protocol.Item item) {
        return new ItemView(item.type(), item.amount(),
                item.enchants() == null ? Map.of() : item.enchants(),
                item.storedEnchants() == null ? Map.of() : item.storedEnchants(),
                item.name(), item.lore() == null ? List.of() : item.lore(),
                item.damage() == null ? 0 : item.damage(), item.repairCost() == null ? 0 : item.repairCost(),
                null, item.pdc(), item.raw());
    }
}
