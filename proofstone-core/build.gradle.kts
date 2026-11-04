plugins {
    id("proofstone.java-host")
    id("proofstone.integration-test")
}

dependencies {
    // The driver SPI exposes api types (Click, BlockPos, ItemView), so drivers see them through core.
    api(project(":proofstone-api"))
    // The JUnit extension (T-M1-10); its annotations live in proofstone-api.
    implementation(platform(libs.junit.bom))
    implementation(libs.junit.jupiter.api)
    implementation(project(":proofstone-protocol"))
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.json.schema.validator)
    implementation(libs.testcontainers)
}

tasks.test {
    // The seed versions.yml and its schema sit at the repo root (ADR-0008). Tests read them from here.
    systemProperty("proofstone.repoRoot", rootProject.projectDir.absolutePath)
}

// The Docker tests boot the real agent jar. It is built, never committed (AGENTS.md §3).
val agentJar = rootProject.layout.projectDirectory.file("platforms/bukkit/agent/build/libs/agent-all.jar")

tasks.named<Test>("integrationTest") {
    // Path-based, so this project does not need the agent project configured first.
    dependsOn(":platforms:bukkit:agent:shadowJar")
    systemProperty("proofstone.repoRoot", rootProject.projectDir.absolutePath)
    systemProperty("proofstone.agentJar", agentJar.asFile.absolutePath)
}
