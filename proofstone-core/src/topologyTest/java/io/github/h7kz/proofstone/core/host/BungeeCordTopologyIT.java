package io.github.h7kz.proofstone.core.host;

/** BungeeCord build 2111, legacy forwarding, two paper@26.3 backends. */
class BungeeCordTopologyIT extends TopologyContract {

    @Override
    ProxyHostOptions proxyOptions() {
        return Topologies.bungeeCord();
    }
}
