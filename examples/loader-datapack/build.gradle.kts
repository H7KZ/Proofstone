// A data pack under test on one loader row (T-M8-12). Its test boots a real Fabric server, so it needs Docker and is not part
// of `check`.
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
tasks.test {
    enabled = false
}

val fabricAgentJar = rootProject.layout.projectDirectory.file("platforms/fabric/agent/build/libs/agent-all.jar")

// One cell per invocation: -Dproofstone.cells=fabric@26.3 (README). The agent is built only for this task, because it needs a
// Java 25 toolchain and a build-time download that `check` must not pay for.
tasks.register<Test>("loaderExample") {
    group = "verification"
    description = "Runs the data-pack example on one loader cell (-Dproofstone.cells=fabric@26.3). Needs Docker and the EULA."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    dependsOn("testClasses", ":platforms:fabric:agent:shadowJar", ":platforms:bukkit:agent:shadowJar")
    systemProperty("proofstone.repoRoot", rootProject.projectDir.absolutePath)
    // CellInvocation.boot reads proofstone.agentJar (the Bukkit agent) before it branches to the loader host, so a loader run
    // must set it although a Fabric cell never loads that jar (finding, T-M8-12; the conformance task sets it the same way).
    systemProperty("proofstone.agentJar",
        rootProject.layout.projectDirectory.file("platforms/bukkit/agent/build/libs/agent-all.jar").asFile.absolutePath)
    systemProperty("proofstone.fabric.agentJar", fabricAgentJar.asFile.absolutePath)
    // The loader's pinned downloads (launcher, Fabric API), reused only when the sha256 matches (ADR-0017 section 3).
    systemProperty("proofstone.loaderCache", layout.buildDirectory.dir("loader-cache").get().asFile.absolutePath)
    providers.systemProperty("proofstone.cells").orNull?.let { systemProperty("proofstone.cells", it) }
    // The result depends on Docker and the EULA, so always run when asked. A skip is never a pass (AGENTS.md section 3).
    outputs.upToDateWhen { false }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
