// Real-server conformance (specs/self-tests.md §1). The scenarios are JUnit tests on the newest Paper row. They boot a
// server, so they need Docker and the EULA, and `check` never runs them: `:conformance:smoke` and `:conformance:run`
// are their gates.
import java.io.IOException
import java.util.concurrent.TimeUnit

plugins {
    id("proofstone.java-host")
}

dependencies {
    testImplementation(project(":proofstone-api"))
    // The C03 to C08 scenarios live in core (T-M4-10a), so the per-cell check and these wrappers run the same code.
    testImplementation(project(":proofstone-core"))
    // Registers the JUnit extension through its service file.
    testRuntimeOnly(project(":proofstone-core"))
    // The mcpl client driver, found through ServiceLoader. It needs Java 21+, hence the toolchain below.
    testRuntimeOnly(project(":drivers:mcpl"))
    // The reference driver, selected with -Dproofstone.driver=mineflayer-ref (T-M4-05b).
    testRuntimeOnly(project(":drivers:mineflayer-ref"))
}

// MCProtocolLib is Java 21+ only, and the driver runs in the test JVM (ADR-0004).
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// Only archTest stays in `check`. The scenarios run in `smoke` and `run`, never through `test`.
tasks.named("check") {
    setDependsOn(listOf("archTest"))
}
tasks.test {
    enabled = false
}

val agentJar = rootProject.layout.projectDirectory.file("platforms/bukkit/agent/build/libs/agent-all.jar")

// The Bedrock-prefix example plugin (T-M5-06) is the plugin under test for C-B01 only. It is built, never committed, and
// copied under this project because plugin globs are relative to the project directory (examples/bedrock-prefix).
val bedrockPrefixPlugins = layout.buildDirectory.dir("bedrock-prefix-plugins")
val copyBedrockPrefixPlugin = tasks.register<Copy>("copyBedrockPrefixPlugin") {
    dependsOn(":examples:bedrock-prefix:jar")
    from(rootProject.layout.projectDirectory.file("examples/bedrock-prefix/build/libs/proofstone-bedrock-prefix.jar"))
    into(bedrockPrefixPlugins)
}

/** The setup shared by both scenario tasks: one real Paper cell, the agent jar, and a guard against silent skips. */
fun Test.realServerScenarios(taskDescription: String) {
    group = "verification"
    description = taskDescription
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    // The agent jar is built, never committed (AGENTS.md §3).
    dependsOn(":platforms:bukkit:agent:shadowJar", "testClasses", copyBedrockPrefixPlugin)
    systemProperty("proofstone.repoRoot", rootProject.projectDir.absolutePath)
    systemProperty("proofstone.agentJar", agentJar.asFile.absolutePath)
    // The run-time cell selector, scope, driver and flake-detector repeat count reach the test JVM from the Gradle
    // command line (test-api spec §1, self-tests section 4).
    for (name in listOf("proofstone.cells", "proofstone.scope", "proofstone.driver", "proofstone.repeat")) {
        providers.systemProperty(name).orNull?.let { systemProperty(name, it) }
    }
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
            throw GradleException("$name cannot run in CI, and a skip must not count as green: " + missing.joinToString("; "))
        } else {
            logger.warn("$name SKIPPED (not a pass): " + missing.joinToString("; "))
            false
        }
    }
}

// C01 and C02 only: a quick gate that one cell boots and answers.
tasks.register<Test>("smoke") {
    realServerScenarios("Boots one real Paper cell (newest row) and runs C01 and C02. Needs Docker and the EULA.")
    filter {
        includeTestsMatching("*ConformanceSmokeTest")
    }
}

// Every scenario, C01 to C06. Filter one with `--tests '*C03*'`; a bare `--tests C03` matches no class name.
tasks.register<Test>("run") {
    realServerScenarios("Boots the real Paper cell (newest row) and runs every conformance scenario, C01 to C06. " +
        "Needs Docker and the EULA.")
}
