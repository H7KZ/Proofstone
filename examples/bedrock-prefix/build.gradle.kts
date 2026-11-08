// Example plugin for the simulated Bedrock tier (T-M5-06). A test-only Bukkit plugin, built as
// build/libs/proofstone-bedrock-prefix.jar from these sources and never committed (AGENTS.md section 3). The conformance
// module copies the jar into the cell through @Proofstone(plugins). Java 8 bytecode, like the other Bukkit fixtures.
plugins {
    id("proofstone.java-host")
}

dependencies {
    compileOnly(libs.spigot.api) {
        isTransitive = false
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
}

tasks.named<Jar>("jar") {
    archiveFileName.set("proofstone-bedrock-prefix.jar")
}
