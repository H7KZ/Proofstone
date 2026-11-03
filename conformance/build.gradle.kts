// Real-server conformance (specs/self-tests.md §1). The scenarios are JUnit tests on the newest Paper row. They boot a
// server, so they need Docker and the EULA, and `check` never runs them: `:conformance:smoke` is their gate.
import java.io.IOException
import java.util.concurrent.TimeUnit

plugins {
    id("proofstone.java-host")
}

dependencies {
    testImplementation(project(":proofstone-api"))
    // Registers the JUnit extension through its service file.
    testRuntimeOnly(project(":proofstone-core"))
    // The mcpl client driver, found through ServiceLoader. It needs Java 21+, hence the toolchain below.
    testRuntimeOnly(project(":drivers:mcpl"))
}

// MCProtocolLib is Java 21+ only, and the driver runs in the test JVM (ADR-0004).
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// Only archTest stays in `check`. The scenarios run in `smoke`, never through `test`.
tasks.named("check") {
    setDependsOn(listOf("archTest"))
}
tasks.test {
    enabled = false
}

val agentJar = rootProject.layout.projectDirectory.file("platforms/bukkit/agent/build/libs/agent-all.jar")

tasks.register<Test>("smoke") {
    group = "verification"
    description = "Boots one real Paper cell (newest row) and runs C01 and C02. Needs Docker and the EULA."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    // The agent jar is built, never committed (AGENTS.md §3).
    dependsOn(":platforms:bukkit:agent:shadowJar", "testClasses")
    systemProperty("proofstone.repoRoot", rootProject.projectDir.absolutePath)
    systemProperty("proofstone.agentJar", agentJar.asFile.absolutePath)
    // The result depends on Docker and the EULA, so always run when asked. A skip is never a pass (AGENTS.md §3).
    outputs.upToDateWhen { false }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    // Checked before the tests start, so a missing prerequisite is never a green run. In CI (CI=true) it fails the
    // task. Locally it skips the task and says why. The same two checks as the JUnit extension, which skips per test.
    onlyIf {
        val missing = mutableListOf<String>()
        if (!"true".equals(System.getenv("PROOFSTONE_ACCEPT_EULA"), ignoreCase = true)) {
            missing.add("Minecraft EULA not accepted: set PROOFSTONE_ACCEPT_EULA=true (read https://aka.ms/MinecraftEULA)")
        }
        val dockerUp = try {
            val process = ProcessBuilder("docker", "info")
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()
            process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0
        } catch (e: IOException) {
            false
        }
        if (!dockerUp) {
            missing.add("Docker is not available")
        }
        if (missing.isEmpty()) {
            true
        } else if (System.getenv("CI") == "true") {
            throw GradleException("smoke cannot run in CI, and a skip must not count as green: " + missing.joinToString("; "))
        } else {
            logger.warn("smoke SKIPPED (not a pass): " + missing.joinToString("; "))
            false
        }
    }
}
