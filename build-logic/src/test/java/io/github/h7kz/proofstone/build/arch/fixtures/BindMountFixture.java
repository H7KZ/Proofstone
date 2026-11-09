package io.github.h7kz.proofstone.build.arch.fixtures;

import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;

// Violates NO_BIND_MOUNTS: a host path is bind-mounted into the container.
public class BindMountFixture {
    public void mount(GenericContainer<?> container) {
        container.withFileSystemBind("/tmp/host", "/srv", BindMode.READ_ONLY);
    }
}
