package io.github.h7kz.proofstone.core.junit;

import io.github.h7kz.proofstone.api.Capability;
import io.github.h7kz.proofstone.core.cell.Cell;
import java.util.Set;

/**
 * The API view of a planned cell. {@code bridged} is true when ViaProxy sits between the driver and the server.
 * {@code capabilities} is what the cell holds, read from the agent at readiness (test-api spec section 10).
 */
record CellView(Cell cell, boolean bridged, Set<Capability> capabilities) implements io.github.h7kz.proofstone.api.Cell {

    CellView {
        capabilities = Set.copyOf(capabilities);
    }

    @Override
    public String platform() {
        return cell.platform();
    }

    @Override
    public String version() {
        return cell.mc();
    }

    @Override
    public String javaTag() {
        return cell.java();
    }

    @Override
    public boolean bridged() {
        return bridged;
    }
}
