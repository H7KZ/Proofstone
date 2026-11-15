// A server-only mod under test on one loader row (T-M8-13). Its test boots a real Fabric or NeoForge server, so it needs Docker
// and is not part of `check`.
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

// One cell per invocation: -Dproofstone.cells=fabric@26.3 or neoforge@26.3 (README). The loader of that cell decides which
// agent and which example mod are built and copied, so the one glob in the test, build/example-mod/proofstone-example-mod.jar,
// is always the mod of the cell that runs. Built only for this task: a Java 25 toolchain and a build-time download that `check`
// must not pay for.
// Read as a plain value: the configuration cache tracks the property, and a cell that is not a loader fails the task before any
// download (a refusal, never a silent fall back to another loader).
val selected: String = System.getProperty("proofstone.cells")?.substringBefore('@') ?: "fabric"
val agentDir = rootProject.layout.projectDirectory.dir("platforms/$selected/agent")

val copyExampleMod = tasks.register<Copy>("copyExampleMod") {
    description = "Copies the example mod of the selected loader to build/example-mod, where the test's glob finds it."
    if (selected == "fabric" || selected == "neoforge") {
        dependsOn(":platforms:$selected:agent:exampleModJar")
    }
    from(agentDir.file("build/example-mod/proofstone-example-mod.jar"))
    into(layout.buildDirectory.dir("example-mod"))
}

tasks.register<Test>("loaderExample") {
    group = "verification"
    description = "Runs the server-only mod example on one loader cell (-Dproofstone.cells=fabric@26.3 or neoforge@26.3). " +
        "Needs Docker and the EULA."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    if (selected == "fabric" || selected == "neoforge") {
        dependsOn("testClasses", copyExampleMod, ":platforms:$selected:agent:shadowJar")
    }
    systemProperty("proofstone.repoRoot", rootProject.projectDir.absolutePath)
    systemProperty("proofstone.$selected.agentJar", agentDir.file("build/libs/agent-all.jar").asFile.absolutePath)
    // A local copy: a lambda that names the script's own value cannot be stored by the configuration cache.
    val chosen = selected
    doFirst {
        if (chosen != "fabric" && chosen != "neoforge") {
            throw GradleException("-Dproofstone.cells must name fabric@26.3 or neoforge@26.3 for this example, not \"$chosen\"")
        }
    }
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
