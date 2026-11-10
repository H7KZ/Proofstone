plugins {
    id("proofstone.java-host")
    id("proofstone.publish")
    id("proofstone.integration-test")
}

description = "Proofstone core: cell runner, JUnit extension, driver SPI and the verdict machinery."

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
    // Public: the driver SPI binds a driver to the cell network (ClientDriver.onNetwork).
    api(libs.testcontainers)
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

// Real-server topology tests (T-M7-07): a proxy and Paper backends on one network. Not part of `check`; run one proxy
// per invocation under scripts/dockerlock.sh (M7 flake rule). Its own source set, so `integrationTest` stays as it was.
val topologyTest = sourceSets.create("topologyTest") {
    compileClasspath += sourceSets["main"].output
    runtimeClasspath += sourceSets["main"].output
}
configurations["topologyTestImplementation"].extendsFrom(configurations["implementation"])
configurations["topologyTestRuntimeOnly"].extendsFrom(configurations["runtimeOnly"])

dependencies {
    "topologyTestImplementation"(platform(libs.junit.bom))
    "topologyTestImplementation"(libs.junit.jupiter)
    "topologyTestImplementation"(libs.assertj.core)
    "topologyTestRuntimeOnly"(libs.junit.platform.launcher)
    // The join-stall test finds the client driver through ServiceLoader, as the extension does. Runtime only: core
    // never compiles against a driver (README, Boundaries).
    "topologyTestRuntimeOnly"(project(":drivers:mcpl"))
}

// The mcpl driver is Java 21 bytecode, so the runtime classpath resolves for 21 and the tests run on JDK 21.
configurations.named("topologyTestRuntimeClasspath") {
    attributes.attribute(org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)
}

val topologyToolchains = extensions.getByType<JavaToolchainService>()

tasks.register<Test>("topologyTest") {
    group = "verification"
    description = "Real-server topology tests: a proxy and Paper backends. Needs Docker. Not part of check."
    testClassesDirs = topologyTest.output.classesDirs
    classpath = topologyTest.runtimeClasspath
    useJUnitPlatform()
    javaLauncher.set(topologyToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    outputs.upToDateWhen { false }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    // Path-based, so this project does not need the agent projects configured first.
    dependsOn(":platforms:bukkit:agent:shadowJar", ":platforms:velocity:agent:shadowJar",
        ":platforms:bungee:agent:shadowJar")
    val platforms = rootProject.layout.projectDirectory.dir("platforms")
    systemProperty("proofstone.repoRoot", rootProject.projectDir.absolutePath)
    systemProperty("proofstone.agentJar", agentJar.asFile.absolutePath)
    systemProperty("proofstone.velocity.agentJar", platforms.file("velocity/agent/build/libs/agent-all.jar").asFile.absolutePath)
    systemProperty("proofstone.bungee.agentJar", platforms.file("bungee/agent/build/libs/agent-all.jar").asFile.absolutePath)
    // The proxy pins are planned from versions.yml schema 3 by the tests themselves (T-M7-08, ADR-0016 section 2).
}

// AGENTS.md section 3 applies to these classes too: archTest scans them.
tasks.named("archTest") {
    dependsOn("topologyTestClasses")
    val dirs = topologyTest.output.classesDirs
    doLast {
        val violations = io.github.h7kz.proofstone.build.arch.HardRules.check(dirs.files.map { it.toPath() })
        if (violations.isNotEmpty()) {
            throw GradleException("archTest failed (topologyTest classes):\n" + violations.joinToString("\n"))
        }
    }
}
