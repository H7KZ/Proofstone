package io.github.h7kz.proofstone.protocol;

/**
 * Inline examples for wire protocol v1. T-M1-02 replaces these with the golden files.
 * Examples carry no JSON nulls: the round-trip test drops nulls, so a null example would not compare equal.
 */
final class ProtocolExamples {

    static final String HEALTH = """
        {"agentVersion":"0.1.0","protocol":"1.1","platform":"paper","mcVersion":"1.21.11","capabilities":["command","players"]}""";

    static final String ERROR = """
        {"code":"UNKNOWN_KEY","message":"unknown material","detail":{"key":"minecraft:foo"}}""";

    /** Enchant level 32767 is deliberately large: levels are never clamped (test-api.md §6). */
    static final String ITEM = """
        {"type":"minecraft:diamond_sword","amount":1,"name":"Blade","lore":["line one"],"enchants":{"minecraft:sharpness":32767},"storedEnchants":{"minecraft:mending":1},"damage":3,"repairCost":0,"pdc":{"proofstone:tag":"x"},"raw":"{}"}""";

    static final String PLAYER_STATE = """
        {"name":"Alex","uuid":"069a79f4-44e9-4726-a5be-fca90e38aaf5","level":30,"exp":0.5,"gamemode":"survival","health":20.0,"location":{"world":"world","x":0.5,"y":64.0,"z":0.5,"yaw":0.0,"pitch":0.0},"inventory":{"0":{"type":"minecraft:diamond_sword","amount":1,"enchants":{"minecraft:sharpness":32767}}},"cursor":{"type":"minecraft:stone","amount":64},"openWindow":{"type":"minecraft:chest","title":"Chest","slots":[{"type":"minecraft:dirt","amount":1},null]}}""";

    static final String COMMAND = """
        {"command":"version","timeoutMs":5000}""";

    static final String COMMAND_REPLY = """
        {"raw":"This server is running Paper"}""";

    static final String ENVELOPE_OK = """
        {"ok":true,"data":{"agentVersion":"0.1.0","protocol":"1.1","platform":"paper","mcVersion":"1.21.11","capabilities":[]}}""";

    static final String ENVELOPE_ERROR = """
        {"ok":false,"error":{"code":"TIMEOUT","message":"timed out"}}""";

    static final String BLOCK = """
        {"world":"world","x":0,"y":63,"z":0,"type":"minecraft:stone","state":{}}""";

    static final String BLOCK_WRITE = """
        {"type":"minecraft:anvil","state":{"facing":"west"}}""";

    static final String CONFIG_ENTRY = """
        {"path":"greeting","value":"hello"}""";

    static final String TICKS = """
        {"global":1234,"regions":{"r1":1234}}""";

    /** Event with a player, an item and a {$type, value} field (event.json). Schema-checked only: see ProbeEvent. */
    static final String EVENT = """
        {"class":"org.bukkit.event.player.PlayerJoinEvent","tick":1200,"thread":"Server thread","fields":{"getPlayer":{"name":"Alex"},"getJoinMessage":"joined","getItem":{"type":"minecraft:diamond_sword","amount":1},"getTime":{"$type":"java.time.Instant","value":"2026-11-03T12:00:00Z"}}}""";

    static final String LOG_PAGE = """
        {"records":[{"time":"2026-11-03T12:00:00Z","level":"WARN","logger":"org.bukkit","message":"boom","thrown":"java.lang.IllegalStateException: boom"}],"next":7}""";

    private ProtocolExamples() {
    }
}
