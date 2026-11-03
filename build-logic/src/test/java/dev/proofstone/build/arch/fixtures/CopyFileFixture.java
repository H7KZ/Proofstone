package dev.proofstone.build.arch.fixtures;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.MountableFile;

// Passes NO_BIND_MOUNTS: the input is copied into the container (AGENTS.md §3).
public class CopyFileFixture {
    public void copy(GenericContainer<?> container) {
        container.withCopyFileToContainer(MountableFile.forHostPath("/tmp/host"), "/srv");
    }
}
