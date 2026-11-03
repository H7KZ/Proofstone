// In-server agents (Paper, Folia, Spigot, Purpur). They run on old JVMs, so the bytecode is Java 8.
// Everything the agent bundles is shaded into dev.proofstone.shaded.* (AGENTS.md §3, ADR-0007).
// Module build files add their own relocate rules.
plugins {
    id("proofstone.java-host")
    id("com.gradleup.shadow")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
}
