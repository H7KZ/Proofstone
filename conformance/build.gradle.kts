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
// The proxy agents of the topology conformance (T-M7-11), found by path as proofstone-core's topologyTest does.
val velocityAgentJar = rootProject.layout.projectDirectory.file("platforms/velocity/agent/build/libs/agent-all.jar")
val bungeeAgentJar = rootProject.layout.projectDirectory.file("platforms/bungee/agent/build/libs/agent-all.jar")
// The mod agents of the loader conformance (T-M8-08). Built only when -Dproofstone.cells names that loader: they need a Java 25
// toolchain and a build-time download of the game jar, which a Paper or topology run must not pay for or depend on.
val fabricAgentJar = rootProject.layout.projectDirectory.file("platforms/fabric/agent/build/libs/agent-all.jar")
val neoforgeAgentJar = rootProject.layout.projectDirectory.file("platforms/neoforge/agent/build/libs/agent-all.jar")
fun geyserSelected(): Boolean =
    providers.systemProperty("proofstone.cells").orNull?.split(",")?.any { it.trim().startsWith("geyser@") } == true
fun loaderSelected(loader: String): Boolean =
    providers.systemProperty("proofstone.cells").orNull?.split(",")?.any { it.trim().startsWith("$loader@") } == true

// The Bedrock-prefix example plugin (T-M5-06) is the plugin under test for C-B01 only. It is built, never committed, and
// copied under this project because plugin globs are relative to the project directory (examples/bedrock-prefix).
val bedrockPrefixPlugins = layout.buildDirectory.dir("bedrock-prefix-plugins")
val copyBedrockPrefixPlugin = tasks.register<Copy>("copyBedrockPrefixPlugin") {
    dependsOn(":examples:bedrock-prefix:jar")
    from(rootProject.layout.projectDirectory.file("examples/bedrock-prefix/build/libs/proofstone-bedrock-prefix.jar"))
    into(bedrockPrefixPlugins)
}

// The fixture plugin (T-M7-12): the topology conformance's P04 has it send GetServer on the BungeeCord channel. Built, never
// committed, and copied under this project because plugin globs are relative to the project directory.
val fixturePlugins = layout.buildDirectory.dir("fixture-plugins")
val copyFixturePlugin = tasks.register<Copy>("copyFixturePlugin") {
    dependsOn(":platforms:bukkit:fixture:jar")
    from(rootProject.layout.projectDirectory.file("platforms/bukkit/fixture/build/libs/proofstone-fixture.jar"))
    into(fixturePlugins)
}

// The resource pack of C-R01 and C-R02 (T-M8-10), zipped from its source, never committed as a binary. Reproducible, so its
// sha1 is the same on every build.
val zipResourcePack = tasks.register<Zip>("zipResourcePack") {
    from(layout.projectDirectory.dir("src/test/resourcepacks/proofstone-e2e"))
    archiveFileName.set("proofstone-e2e.zip")
    destinationDirectory.set(layout.buildDirectory.dir("resourcepacks"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

/** The setup shared by both scenario tasks: one real Paper cell, the agent jar, and a guard against silent skips. */
fun Test.realServerScenarios(taskDescription: String) {
    group = "verification"
    description = taskDescription
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    // The agent jar is built, never committed (AGENTS.md §3).
    dependsOn(":platforms:bukkit:agent:shadowJar", ":platforms:velocity:agent:shadowJar",
        ":platforms:bungee:agent:shadowJar", ":drivers:bedrock:shadowJar", "testClasses", copyBedrockPrefixPlugin, copyFixturePlugin, zipResourcePack)
    systemProperty("proofstone.repoRoot", rootProject.projectDir.absolutePath)
    systemProperty("proofstone.agentJar", agentJar.asFile.absolutePath)
    systemProperty("proofstone.velocity.agentJar", velocityAgentJar.asFile.absolutePath)
    systemProperty("proofstone.bungee.agentJar", bungeeAgentJar.asFile.absolutePath)
    // The Geyser cell's Bedrock driver host jar and its pinned-jar download cache (T-M9-08, ADR-0018 sections 2 and 8). Read only
    // when a Geyser cell is selected.
    systemProperty("proofstone.bedrockHostJar",
        rootProject.layout.projectDirectory.file("drivers/bedrock/build/libs/bedrock-host-all.jar").asFile.absolutePath)
    systemProperty("proofstone.geyserDownloads",
        rootProject.layout.projectDirectory.dir("build/geyser-downloads").asFile.absolutePath)
    systemProperty("proofstone.fabric.agentJar", fabricAgentJar.asFile.absolutePath)
    systemProperty("proofstone.neoforge.agentJar", neoforgeAgentJar.asFile.absolutePath)
    // The loaders' pinned downloads (launcher, installer, Fabric API), reused only when the sha256 matches (ADR-0017 section 3).
    systemProperty("proofstone.loaderCache", layout.buildDirectory.dir("loader-cache").get().asFile.absolutePath)
    if (loaderSelected("fabric")) {
        dependsOn(":platforms:fabric:agent:shadowJar")
    }
    if (loaderSelected("neoforge")) {
        dependsOn(":platforms:neoforge:agent:shadowJar")
    }
    // The run-time cell selector, scope, driver and flake-detector repeat count reach the test JVM from the Gradle
    // command line (test-api spec §1, self-tests section 4).
    for (name in listOf("proofstone.cells", "proofstone.scope", "proofstone.driver", "proofstone.repeat", "proofstone.geyser.transport")) {
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
        "Needs Docker and the EULA. With a topology cell in -Dproofstone.cells (for example velocity@4.3.1+paper@26.3) " +
        "it runs the topology conformance (T00, P01 to P05) of that cell instead.")
    // A topology cell is named with a plus sign (written as char code 43: lintVersions reads a literal one in a build file as a
    // dynamic version). The single-server classes plan nothing for it (zero cells is a loud error),
    // and the topology class plans nothing for a plain selector, so each kind of run selects its own classes.
    val topologySelected = providers.systemProperty("proofstone.cells").orNull?.contains(43.toChar()) == true
    // A loader cell (fabric@26.3, neoforge@26.3) runs C01 to C08 only: the other classes declare no loader @Matrix, so a
    // loader selection plans no cell for them and they would fail by name (T-M8-08).
    val loaderRun = loaderSelected("fabric") || loaderSelected("neoforge")
    // A Geyser cell (geyser@2.12.1+paper@26.3) runs the Geyser conformance (the cell check, C-G01 to C-G05) only. Its name has a
    // plus sign too, so it is told apart first (T-M9-08).
    val geyserRun = geyserSelected()
    filter {
        if (geyserRun) {
            includeTestsMatching("*ConformanceGeyserTest")
        } else if (topologySelected) {
            includeTestsMatching("*ConformanceTopologyTest")
        } else if (loaderRun) {
            includeTestsMatching("*ConformanceSmokeTest")
            includeTestsMatching("*ConformanceLoaderWindowTest")
            for (scenario in listOf("C03GiveRoundTripTest", "C04ChestMoveTest", "C05AnvilRenameCostTest",
                "C06AnvilCombineTest", "C07GrindstoneTest", "C08ShiftClickCraftTest", "CD01DataPackFunctionTest",
                "CD02DataPackRecipeTest")) {
                includeTestsMatching("*$scenario")
            }
        } else {
            excludeTestsMatching("*ConformanceTopologyTest")
            // Loader rows only: a Paper selection plans no cell for it (T-M8-08).
            excludeTestsMatching("*ConformanceLoaderWindowTest")
            excludeTestsMatching("*ConformanceGeyserTest")
        }
    }
}
