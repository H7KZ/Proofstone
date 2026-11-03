// Conformance fixture plugin (T-M2-03): a test-only Bukkit plugin with a config.yml and a reload hook. Built as
// build/libs/proofstone-fixture.jar from these sources, never committed (AGENTS.md §3). PaperHost copies the jar into
// the container. Java 8 bytecode, like the agent, so it loads on the same servers.
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
    archiveFileName.set("proofstone-fixture.jar")
}
