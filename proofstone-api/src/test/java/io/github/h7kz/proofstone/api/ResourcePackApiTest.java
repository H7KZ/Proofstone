package io.github.h7kz.proofstone.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** T-M8-10: the resource-pack declarations are additive and have no default answer (ADR-0017 section 7). */
class ResourcePackApiTest {

    @ServerResourcePack("packs/one.zip")
    static class Defaults {
    }

    @Test
    void aDeclaredPackIsOptionalByDefaultWithNoPromptAndTheFilesOwnHash() {
        ServerResourcePack pack = Defaults.class.getAnnotation(ServerResourcePack.class);
        assertThat(pack.required()).isFalse();
        assertThat(pack.prompt()).isEmpty();
        assertThat(pack.sha1()).isEmpty();
    }

    @Test
    void theAnswerGoesOnTheClassOrTheMethodAndHasExactlyTheThreeVanillaChoices() {
        assertThat(ResourcePackAnswer.class.getAnnotation(Target.class).value())
                .containsExactlyInAnyOrder(ElementType.TYPE, ElementType.METHOD);
        assertThat(ResourcePackAnswer.Answer.values()).containsExactly(ResourcePackAnswer.Answer.ACCEPT,
                ResourcePackAnswer.Answer.DECLINE, ResourcePackAnswer.Answer.FAIL_DOWNLOAD);
    }

    @Test
    void aCustomClientFailsLoudlyInsteadOfReportingNoPacks() {
        Client custom = new Client() {
            public String name() { return "x"; }
            public void command(String c) { }
            public String awaitMessage(java.util.regex.Pattern p) { return ""; }
            public Position position() { return null; }
            public Position awaitPosition(String d, java.util.function.Predicate<Position> p) { return null; }
            public Window openBlock(BlockPos pos) { return null; }
            public List<ItemView> inventory() { return List.of(); }
            public int experienceLevel() { return 0; }
        };
        assertThatThrownBy(custom::resourcePacks).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void aPushIsASnapshotItsStatusesCannotChangeUnderTheTest() {
        List<String> statuses = new ArrayList<>(List.of("ACCEPTED"));
        ResourcePackPush push = new ResourcePackPush("id", "http://127.0.0.1:1/p.zip", "ab", true, null,
                ResourcePackAnswer.Answer.ACCEPT, statuses, null, false);
        statuses.add("DOWNLOADED");
        assertThat(push.statuses()).containsExactly("ACCEPTED");
        assertThat(Set.of(Capability.values())).contains(Capability.RESOURCE_PACKS);
    }
}
