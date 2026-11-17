// A plugin's Bedrock-only branch under test through Geyser (T-M9-12). The test boots a real Geyser cell (Paper, Geyser, Floodgate,
// the discovery stand-in and the Bedrock driver host), so it needs Docker and is not part of `check`.
plugins {
    id("proofstone.java-host")
}

dependencies {
    testImplementation(project(":proofstone-api"))
    // Registers the JUnit extension through its service file.
    testRuntimeOnly(project(":proofstone-core"))
    // The mcpl client driver (the Java client of the cell), found through ServiceLoader. It needs Java 21+.
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
tasks.test {
    enabled = false
}

// The plugin under test is the Bedrock-prefix example (examples/bedrock-prefix), built from source and never committed. It is
// copied under this project because plugin globs are relative to the project directory.
val copyPlugin = tasks.register<Copy>("copyBedrockPrefixPlugin") {
    dependsOn(":examples:bedrock-prefix:jar")
    from(rootProject.layout.projectDirectory.file("examples/bedrock-prefix/build/libs/proofstone-bedrock-prefix.jar"))
    into(layout.buildDirectory.dir("plugin"))
}

tasks.register<Test>("geyserExample") {
    group = "verification"
    description = "Runs the Bedrock-branch example on a Geyser cell (-Dproofstone.cells=geyser@2.12.1+paper@26.3). " +
        "Needs Docker and the EULA."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    dependsOn("testClasses", copyPlugin, ":platforms:bukkit:agent:shadowJar", ":drivers:bedrock:shadowJar")
    systemProperty("proofstone.repoRoot", rootProject.projectDir.absolutePath)
    systemProperty("proofstone.agentJar",
        rootProject.layout.projectDirectory.file("platforms/bukkit/agent/build/libs/agent-all.jar").asFile.absolutePath)
    // The Geyser cell's Bedrock driver host jar and its pinned-jar download cache (ADR-0018 sections 2 and 8).
    systemProperty("proofstone.bedrockHostJar",
        rootProject.layout.projectDirectory.file("drivers/bedrock/build/libs/bedrock-host-all.jar").asFile.absolutePath)
    systemProperty("proofstone.geyserDownloads",
        rootProject.layout.projectDirectory.dir("build/geyser-downloads").asFile.absolutePath)
    providers.systemProperty("proofstone.cells").orNull?.let { systemProperty("proofstone.cells", it) }
    // The result depends on Docker and the EULA, so always run when asked. A skip is never a pass (AGENTS.md section 3).
    outputs.upToDateWhen { false }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
