plugins {
    id("proofstone.java-host")
    id("proofstone.integration-test")
}

dependencies {
    implementation(project(":proofstone-core"))
    // Frames and replies are JSON (JSON-RPC 2.0). The same pinned Jackson as proofstone-core.
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    // The integration test builds the sidecar image and starts it on the cell network (no bind mounts, ADR-0005).
    "integrationTestImplementation"(libs.testcontainers)
}

// The Docker test boots the real agent jar next to the server. It is built, never committed (AGENTS.md §3).
val agentJar = rootProject.layout.projectDirectory.file("platforms/bukkit/agent/build/libs/agent-all.jar")

tasks.named<Test>("integrationTest") {
    dependsOn(":platforms:bukkit:agent:shadowJar")
    systemProperty("proofstone.repoRoot", rootProject.projectDir.absolutePath)
    systemProperty("proofstone.agentJar", agentJar.asFile.absolutePath)
}
