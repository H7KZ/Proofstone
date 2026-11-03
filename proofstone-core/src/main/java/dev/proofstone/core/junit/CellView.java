package dev.proofstone.core.junit;

import dev.proofstone.core.cell.Cell;

/** The API view of a planned cell. Bridging is not available yet, so it is never bridged. */
record CellView(Cell cell) implements dev.proofstone.api.Cell {

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
        return false;
    }
}
