package io.github.h7kz.proofstone.agent.vanilla;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The shared module reads the vanilla server for both loaders' agents, so it must name no loader API at all and none that
 * registers a command or payload, decides an event or mixes in (AGENTS.md section 3, ADR-0017 section 5). Each agent's
 * own {@code ObserveOnlyTest} scans these classes too, with its loader's list; this one guards the module on its own.
 */
class ObserveOnlyTest {

    private static final List<String> FORBIDDEN = List.of(
            "net/fabricmc", "net/neoforged", "org/spongepowered/asm/mixin", "Mixin", "setCanceled", "setResult",
            "RegisterCommandsEvent", "CommandRegistrationCallback", "PayloadRegistrar", "ServerPlayNetworking",
            "DeferredRegister", "SubscribeEvent");

    @Test
    void noClassOfTheSharedModuleNamesLoaderApiOrApiThatChangesTheServer() throws Exception {
        Map<String, String> classes = ClassScan.classesOf(MinecraftView.class);
        assertThat(classes.keySet()).as("the scan must reach the module's classes")
                .anyMatch(name -> name.endsWith("/vanilla/MinecraftView.class"))
                .anyMatch(name -> name.endsWith("/vanilla/Values.class"));
        classes.forEach((name, content) -> {
            for (String forbidden : FORBIDDEN) {
                assertThat(content).as(name + " must not refer to " + forbidden).doesNotContain(forbidden);
            }
        });
    }

    @Test
    void theScanFindsANameThatIsThere() throws Exception {
        // A scan that can find nothing proves nothing: this one must see a name the module really uses.
        String content = ClassScan.classesOf(MinecraftView.class).get(
                MinecraftView.class.getName().replace('.', '/') + ".class");
        assertThat(content).contains("net/minecraft/server/MinecraftServer");
    }
}
