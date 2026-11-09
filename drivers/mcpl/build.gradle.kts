plugins {
    id("proofstone.java-host")
    id("proofstone.integration-test")
    id("proofstone.publish")
}

dependencies {
    implementation(project(":proofstone-core"))
    // ItemView, Click and BlockPos are public API types and live only there (AGENTS.md §6); the driver depends on api, not the reverse.
    implementation(project(":proofstone-api"))
    // The version is pinned in the catalog and mirrored in versions.yml (ADR-0004, ADR-0006).
    implementation(libs.mcprotocollib.protocol)
}

// The Docker test boots the real agent jar next to the server. It is built, never committed (AGENTS.md §3).
val agentJar = rootProject.layout.projectDirectory.file("platforms/bukkit/agent/build/libs/agent-all.jar")

tasks.named<Test>("integrationTest") {
    dependsOn(":platforms:bukkit:agent:shadowJar")
    systemProperty("proofstone.repoRoot", rootProject.projectDir.absolutePath)
    systemProperty("proofstone.agentJar", agentJar.asFile.absolutePath)
}

// MCProtocolLib 26.3 is published for Java 21+ only, so this module is the one host-side module above 17.
// Its dependents run the driver in a JVM that is 21 or newer (ADR-0004).
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// The item integration test (T-M2-06) reads the agent's JSON reply. Same pinned Jackson as proofstone-core, tests only.
dependencies {
    "integrationTestImplementation"(platform(libs.jackson.bom))
    "integrationTestImplementation"(libs.jackson.databind)
}
