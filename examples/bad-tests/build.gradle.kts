// Deliberately bad tests (T-M4-03 and later M4 cards): each one must produce its expected verdict. Its tests boot a
// real server, so they need Docker and are not part of `check`.
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

// The java plugin makes `check` run `test`. This test needs Docker, so only archTest stays in `check`.
tasks.named("check") {
    setDependsOn(listOf("archTest"))
}

// The fixture plugin (platforms/bukkit/fixture) is the plugin under test. It is built, never committed, and copied
// under this project because plugin globs are relative to the project directory.
val fixturePlugins = layout.buildDirectory.dir("fixture-plugins")
val copyFixturePlugin = tasks.register<Copy>("copyFixturePlugin") {
    dependsOn(":platforms:bukkit:fixture:jar")
    from(rootProject.layout.projectDirectory.file("platforms/bukkit/fixture/build/libs/proofstone-fixture.jar"))
    into(fixturePlugins)
}

val agentJar = rootProject.layout.projectDirectory.file("platforms/bukkit/agent/build/libs/agent-all.jar")

tasks.test {
    dependsOn(copyFixturePlugin)
    // The agent jar is built, never committed (AGENTS.md §3).
    dependsOn(":platforms:bukkit:agent:shadowJar")
    systemProperty("proofstone.repoRoot", rootProject.projectDir.absolutePath)
    systemProperty("proofstone.agentJar", agentJar.asFile.absolutePath)
    // The run-time cell selector, scope, control-run switch and flake-detector repeat count reach the test JVM from
    // the Gradle command line (test-api spec section 1, self-tests sections 2 and 4).
    for (name in listOf("proofstone.cells", "proofstone.scope", "proofstone.controlRun", "proofstone.repeat")) {
        providers.systemProperty(name).orNull?.let { systemProperty(name, it) }
    }
    // The result depends on Docker, so always run when asked. A skipped test shows its reason, never a pass.
    outputs.upToDateWhen { false }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
