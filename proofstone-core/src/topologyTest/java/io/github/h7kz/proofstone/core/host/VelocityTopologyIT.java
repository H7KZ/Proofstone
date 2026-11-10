package io.github.h7kz.proofstone.core.host;

/** Velocity 4.3.1 build 45, modern forwarding, two paper@26.3 backends. */
class VelocityTopologyIT extends TopologyContract {

    @Override
    ProxyHostOptions proxyOptions() {
        return Topologies.velocity();
    }
}
