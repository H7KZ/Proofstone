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

tasks.test {
    // The agent jar is built, never committed (AGENTS.md §3).
    dependsOn(":platforms:bukkit:agent:shadowJar")
    systemProperty("proofstone.repoRoot", rootProject.projectDir.absolutePath)
    systemProperty("proofstone.agentJar", agentJar.asFile.absolutePath)
    // The result depends on Docker, so always run when asked. A skipped test shows its reason, never a pass.
    outputs.upToDateWhen { false }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
