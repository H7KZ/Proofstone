package io.github.h7kz.proofstone.core.host;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.github.dockerjava.api.model.Container;
import io.github.h7kz.proofstone.core.cell.Cell;
import io.github.h7kz.proofstone.core.cell.CellPlanner;
import io.github.h7kz.proofstone.core.versions.VersionsCatalog;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.testcontainers.DockerClientFactory;

/** What the topology tests share: the backend row, the proxy pins and the "nothing of ours left" check. */
final class Topologies {

    static final List<String> BACKENDS = List.of("lobby", "survival");
    /** The Java the itzg/mc-proxy tags run (docs/platforms/velocity.md, bungeecord.md); checked in the container. */
    private static final int VELOCITY_JAVA = 25;
    private static final int BUNGEE_JAVA = 21;

    private Topologies() {
    }

    /** {@code paper@26.3} exactly as versions.yml pins it: the native row of ADR-0016 section 3. */
    static Cell backendCell() {
        Path root = Path.of(property("proofstone.repoRoot"));
        VersionsCatalog catalog = VersionsCatalog.load(root.resolve("versions.yml"),
                root.resolve("proofstone-protocol/schemas/versions.schema.json"));
        return new CellPlanner(catalog).plan("paper", List.of("26.3")).get(0);
    }

    static void requireEula() {
        assumeTrue(Eula.accepted(), "Minecraft EULA not accepted: set " + Eula.VARIABLE + "=true");
    }

    static PaperHostOptions backendOptions() {
        return PaperHostOptions.of(Path.of(property("proofstone.agentJar")), true);
    }

    static ProxyHostOptions velocity() {
        return ProxyHostOptions.of(ProxyKind.VELOCITY, property("proofstone.velocity.image"),
                property("proofstone.velocity.version"), property("proofstone.velocity.build"),
                property("proofstone.velocity.sha256"), VELOCITY_JAVA, Path.of(property("proofstone.velocity.agentJar")));
    }

    static ProxyHostOptions bungeeCord() {
        String build = property("proofstone.bungee.build");
        return ProxyHostOptions.of(ProxyKind.BUNGEECORD, property("proofstone.bungee.image"), build, build,
                property("proofstone.bungee.sha256"), BUNGEE_JAVA, Path.of(property("proofstone.bungee.agentJar")));
    }

    /**
     * The containers of this test JVM's Testcontainers session that still run, except its Ryuk reaper. Containers named
     * {@code ohlidame-*} are another project's and are never touched or counted.
     */
    static List<String> ourRunningContainers() {
        List<Container> running = DockerClientFactory.instance().client().listContainersCmd()
                .withLabelFilter(Map.of("org.testcontainers.sessionId", DockerClientFactory.SESSION_ID)).exec();
        return running.stream()
                .filter(c -> !c.getImage().contains("ryuk"))
                .map(c -> Arrays.toString(c.getNames()) + " " + c.getImage())
                .filter(name -> !name.contains("ohlidame-"))
                .toList();
    }

    /** Every running container's name, for the evidence ({@code docker ps}). */
    static List<String> dockerPs() {
        return DockerClientFactory.instance().client().listContainersCmd().exec().stream()
                .map(c -> String.join(",", c.getNames())).toList();
    }

    private static String property(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("system property " + name + " is not set (run through :topologyTest)");
        }
        return value;
    }
}
