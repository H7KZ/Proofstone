package io.github.h7kz.proofstone.core.junit;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.h7kz.proofstone.core.await.Await;
import io.github.h7kz.proofstone.core.await.AwaitTimeoutException;
import io.github.h7kz.proofstone.core.host.TopologyHost;
import io.github.h7kz.proofstone.core.verdict.ConformanceResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The per-cell check of a topology cell until P01 to P05 exist (T-M7-11, T-M7-12). It is a readiness check, not those
 * scenarios: the proxy agent lists exactly the topology's backends and its own ping reaches each one. A red makes every
 * test on the cell INVALID, as conformance does (ADR-0016 section 7). Its result is cached per cell per run.
 */
final class TopologyConformance {

    static final String NAME = "T00 proxy lists every backend online";
    private static final Duration WAIT = Duration.ofSeconds(20);

    private TopologyConformance() {
    }

    static ConformanceResult run(String identity, TopologyHost topology) {
        Set<String> expected = new TreeSet<>(topology.backendNames());
        try {
            Await.until(NAME, () -> servers(topology), seen -> seen.equals(expected.stream()
                    .map(name -> name + " online").toList()), WAIT);
            System.err.println("PROOFSTONE CONFORMANCE " + identity + ": " + NAME + " passed (P01 to P05 arrive with "
                    + "T-M7-11 and T-M7-12)");
            return ConformanceResult.pass(identity);
        } catch (AwaitTimeoutException | IllegalStateException e) {
            return new ConformanceResult(identity, false, NAME + " failed: " + e.getMessage());
        }
    }

    /** {@code <name> online|offline} per server the proxy agent lists, sorted by name. */
    private static List<String> servers(TopologyHost topology) {
        JsonNode servers = topology.proxy().request("GET", "/v1/proxy/servers", null).get("servers");
        if (servers == null || !servers.isArray()) {
            throw new IllegalStateException("the proxy agent's /v1/proxy/servers reply has no servers list");
        }
        List<String> seen = new ArrayList<>();
        for (JsonNode server : servers) {
            seen.add(server.path("name").asText() + (server.path("online").asBoolean(false) ? " online" : " offline"));
        }
        seen.sort(null);
        return seen;
    }
}
