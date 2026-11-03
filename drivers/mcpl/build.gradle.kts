plugins {
    id("proofstone.java-host")
    id("proofstone.integration-test")
}

dependencies {
    implementation(project(":proofstone-core"))
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
