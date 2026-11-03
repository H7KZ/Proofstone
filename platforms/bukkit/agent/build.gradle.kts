import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.jvm.toolchain.JavaToolchainService

plugins {
    id("proofstone.java-agent-legacy")
    id("proofstone.integration-test")
}

dependencies {
    // Bukkit API at the 1.8.8 floor. Servers provide it at runtime, so it is not shaded. Its POM points at
    // SNAPSHOT-only BungeeCord artefacts that the repos no longer serve; the agent never touches them.
    compileOnly(libs.spigot.api) {
        isTransitive = false
    }

    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)

    // Key lookups in ItemBuilderTest use the Bukkit static registries, which need no running server.
    testImplementation(libs.spigot.api) {
        isTransitive = false
    }
    testImplementation(libs.guava)
    testImplementation(libs.commons.lang)

    testImplementation(platform(libs.jackson.bom))
    testImplementation(libs.jackson.databind)

    // The contract test boots a real Paper server (PaperHost), replays the golden files (GoldenMatcher) and joins
    // a player with the mcpl driver. None of it is shaded into the agent.
    integrationTestImplementation(project(":proofstone-core"))
    integrationTestImplementation(testFixtures(project(":proofstone-protocol")))
    integrationTestImplementation(project(":drivers:mcpl"))
}

// Golden wire cases (T-M1-02) are replayed by the agent's unit tests over HTTP.
tasks.named<Test>("test") {
    systemProperty("proofstone.golden", rootProject.file("proofstone-protocol/golden").absolutePath)
}

// The integrationTest source set (T-M1-05) is host code, not agent code. Its classes use Java 17 syntax, and the
// mcpl driver is Java 21 bytecode, so the set compiles with a JDK 21 at --release 17 and runs on JDK 21. It must
// not inherit the agent's --release 8 below.
val toolchains = extensions.getByType<JavaToolchainService>()

tasks.named<JavaCompile>("compileIntegrationTestJava") {
    javaCompiler.set(toolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    options.release.set(17)
}

// Resolve the set's classpaths for JVM 21, or Gradle picks the project's JVM 17 and refuses the mcpl driver.
listOf("integrationTestCompileClasspath", "integrationTestRuntimeClasspath").forEach { name ->
    configurations.named(name) {
        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)
    }
}

// The contract test boots the real agent jar, built by shadowJar and never committed (AGENTS.md §3).
val agentJar = layout.buildDirectory.file("libs/agent-all.jar")

tasks.named<Test>("integrationTest") {
    javaLauncher.set(toolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    dependsOn(tasks.named("shadowJar"))
    systemProperty("proofstone.repoRoot", rootProject.projectDir.absolutePath)
    systemProperty("proofstone.agentJar", agentJar.get().asFile.absolutePath)
}

// AGENTS.md §3: bundled libraries are relocated so they never clash with the server's own copies.
tasks.withType<ShadowJar>().configureEach {
    relocate("com.fasterxml.jackson", "dev.proofstone.shaded.jackson")
}
