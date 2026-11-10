package io.github.h7kz.proofstone.core.host;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.github.dockerjava.api.model.Container;
import io.github.h7kz.proofstone.core.cell.CellPlanner;
import io.github.h7kz.proofstone.core.cell.Scope;
import io.github.h7kz.proofstone.core.cell.TopologyCell;
import io.github.h7kz.proofstone.core.versions.VersionsCatalog;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.testcontainers.DockerClientFactory;

/**
 * What the topology tests share: the topology cells planned from versions.yml schema 3 (the proxy pins and the backend
 * row are the file's, T-M7-08) and the "nothing of ours left" check.
 */
final class Topologies {

    static final List<String> BACKENDS = List.of("lobby", "survival");

    private Topologies() {
    }

    /**
     * The planned cell of {@code proxy} on its latest paired {@code paper} row, exactly as versions.yml pins it: the
     * native row of ADR-0016 section 3. Planned in nightly scope, so an experimental pairing runs here.
     */
    static TopologyCell cell(String proxy, List<String> backends) {
        Path root = Path.of(property("proofstone.repoRoot"));
        VersionsCatalog catalog = VersionsCatalog.load(root.resolve("versions.yml"),
                root.resolve("proofstone-protocol/schemas/versions.schema.json"));
        return new CellPlanner(catalog).planTopology(proxy, "latest", "paper", "latest", Scope.NIGHTLY, backends);
    }

    static TopologyCell velocity(List<String> backends) {
        return cell("velocity", backends);
    }

    static TopologyCell bungeeCord(List<String> backends) {
        return cell("bungeecord", backends);
    }

    static Path velocityAgentJar() {
        return Path.of(property("proofstone.velocity.agentJar"));
    }

    static Path bungeeAgentJar() {
        return Path.of(property("proofstone.bungee.agentJar"));
    }

    static void requireEula() {
        assumeTrue(Eula.accepted(), "Minecraft EULA not accepted: set " + Eula.VARIABLE + "=true");
    }

    static PaperHostOptions backendOptions() {
        return PaperHostOptions.of(Path.of(property("proofstone.agentJar")), true);
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
