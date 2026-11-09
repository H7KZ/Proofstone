package io.github.h7kz.proofstone.protocol;

/** A position and rotation in one world. */
public record Location(String world, double x, double y, double z, double yaw, double pitch) {
}
