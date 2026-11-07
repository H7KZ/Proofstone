// Example of a Proofstone user project (T-M1-10). Its tests boot a real server, so they need Docker and are not
// part of `check`.
plugins {
    id("proofstone.java-host")
}

dependencies {
    testImplementation(project(":proofstone-api"))
    // Registers the JUnit extension through its service file.
    testRuntimeOnly(project(":proofstone-core"))
    // The mcpl client driver, found through ServiceLoader. It needs Java 21+, hence the toolchain below.
    testRuntimeOnly(project(":drivers:mcpl"))
    // Runs the nested error-log scenario and inspects its failure (ErrorLogFailsTheTestE2E).
    testImplementation(libs.junit.platform.launcher)
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

val agentJar = rootProject.layout.projectDirectory.file("platforms/bukkit/agent/build/libs/agent-all.jar")

// The fixture plugin (platforms/bukkit/fixture) is the plugin under test for the lifecycle examples. It is built,
// never committed, and copied under this project because plugin globs are relative to the project directory.
val fixturePlugins = layout.buildDirectory.dir("fixture-plugins")
val copyFixturePlugin = tasks.register<Copy>("copyFixturePlugin") {
    dependsOn(":platforms:bukkit:fixture:jar")
    from(rootProject.layout.projectDirectory.file("platforms/bukkit/fixture/build/libs/proofstone-fixture.jar"))
    into(fixturePlugins)
}

tasks.test {
    dependsOn(copyFixturePlugin)
    // The nested scenarios fail on purpose. Only ErrorLogFailsTheTestE2E runs them, through the JUnit launcher.
    exclude("**/nested/**")
    // The agent jar is built, never committed (AGENTS.md §3).
    dependsOn(":platforms:bukkit:agent:shadowJar")
    systemProperty("proofstone.repoRoot", rootProject.projectDir.absolutePath)
    systemProperty("proofstone.agentJar", agentJar.asFile.absolutePath)
    // The run-time cell selector and scope reach the test JVM from the Gradle command line (test-api spec §1).
    // proofstone.forceFail makes ForcedFailureE2E fail on purpose, to prove a failure leaves a repro bundle (T-M4-02).
    for (name in listOf("proofstone.cells", "proofstone.scope", "proofstone.forceFail")) {
        providers.systemProperty(name).orNull?.let { systemProperty(name, it) }
    }
    // The result depends on Docker, so always run when asked. A skipped test shows its reason, never a pass.
    outputs.upToDateWhen { false }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
