// Gradle example (T-M6-04): a minimal Paper plugin with an anvil mechanic and two injected bugs, and its real-server
// suite in the e2eTest source set. Proofstone comes from mavenLocal, never from a remote repository (README).
// The e2e task needs Docker and PROOFSTONE_ACCEPT_EULA, so it is not part of `check`.
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.the

plugins {
    id("proofstone.java-host")
}

val libs = the<VersionCatalogsExtension>().named("libs")
val proofstoneVersion = libs.findVersion("proofstone").get().requiredVersion
val proofstoneGroup = "io.github.h7kz.proofstone"
val toolchains = the<JavaToolchainService>()

// The plugin targets Paper 26.3 only, whose API is Java 25 bytecode, so it compiles on a JDK 26 (which reads it) at
// release 25. Paper 26.3 itself needs Java 25 or newer at runtime. The Java 17 default of proofstone.java-host does not apply.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(26))
    }
}

dependencies {
    // Only the API jar. Its POM points at brigadier and bungeecord-chat artefacts that no repository here serves, and
    // the server provides them at runtime anyway (the same reason the agent builds spigot-api with isTransitive = false).
    compileOnly(libs.findLibrary("paper-api").get()) {
        isTransitive = false
    }
    compileOnly(libs.findLibrary("adventure-api").get())
    compileOnly(libs.findLibrary("paper-guava").get())
}

tasks.named<JavaCompile>("compileJava") {
    options.release.set(25)
}

tasks.named<Jar>("jar") {
    // A fixed name, so the test's @Proofstone glob matches exactly one jar.
    archiveFileName.set("anvil-mechanic.jar")
}

// The e2e suite. It runs on Java 21 because the mcpl driver is Java 21 bytecode (ADR-0004).
val sourceSets = the<SourceSetContainer>()
val e2eSourceSet = sourceSets.create("e2eTest")

dependencies {
    "e2eTestImplementation"(platform(libs.findLibrary("junit-bom").get()))
    "e2eTestImplementation"(libs.findLibrary("junit-jupiter").get())
    "e2eTestImplementation"(libs.findLibrary("assertj-core").get())
    // Gradle needs the launcher on the test runtime classpath to start the JUnit platform.
    "e2eTestRuntimeOnly"(libs.findLibrary("junit-platform-launcher").get())
    // Consumed through publishToMavenLocal (the publishing tasks run before the suite, see tasks below).
    "e2eTestImplementation"("$proofstoneGroup:proofstone-api:$proofstoneVersion")
    "e2eTestRuntimeOnly"("$proofstoneGroup:proofstone-core:$proofstoneVersion")
    "e2eTestRuntimeOnly"("$proofstoneGroup:proofstone-drivers-mcpl:$proofstoneVersion")
}

tasks.named<JavaCompile>("compileE2eTestJava") {
    javaCompiler.set(toolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    options.release.set(21)
}

// The agent is a plugin jar, not a library, so it is not published to mavenLocal. It is built from the repository.
val agentJar = rootProject.layout.projectDirectory.file("platforms/bukkit/agent/build/libs/agent-all.jar")

tasks.register<Test>("e2eTest") {
    group = "verification"
    description = "Real-server e2e suite on Paper 26.3 (T-M6-04). Needs Docker and PROOFSTONE_ACCEPT_EULA; not part of check."
    testClassesDirs = e2eSourceSet.output.classesDirs
    classpath = e2eSourceSet.runtimeClasspath
    useJUnitPlatform()
    javaLauncher.set(toolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    dependsOn("jar", ":platforms:bukkit:agent:shadowJar")
    dependsOn(
        ":proofstone-api:publishToMavenLocal",
        ":proofstone-core:publishToMavenLocal",
        ":proofstone-protocol:publishToMavenLocal",
        ":drivers:mcpl:publishToMavenLocal",
    )
    systemProperty("proofstone.repoRoot", rootProject.projectDir.absolutePath)
    systemProperty("proofstone.agentJar", agentJar.asFile.absolutePath)
    // Selectors and the injected-bug switch reach the test JVM from the Gradle command line.
    for (name in listOf("proofstone.cells", "proofstone.scope", "proofstone.bug")) {
        providers.systemProperty(name).orNull?.let { systemProperty(name, it) }
    }
    // The result depends on Docker, so always run when asked. A skipped test shows its reason, never a pass.
    outputs.upToDateWhen { false }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
