import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.jvm.toolchain.JavaToolchainService

plugins {
    id("proofstone.java-agent-legacy")
    id("proofstone.integration-test")
}

val toolchains = extensions.getByType<JavaToolchainService>()

dependencies {
    // The API only: BungeeCord provides it at runtime. Its POM names Central artefacts only, so it resolves with its
    // transitive set (chat, config, event, protocol, netty types) and the agent is compiled against the real types.
    compileOnly(libs.bungee.api)

    // Envelope, auth, HTTP server and log buffer, shared with the other agents (T-M7-04). Shaded into this jar.
    implementation(project(":platforms:agent-core"))

    testImplementation(libs.bungee.api)
    // `provided` in the API POM: PluginManager needs them to construct (see libs.versions.toml).
    testImplementation(libs.bungee.maven.resolver.provider)
    testImplementation(libs.bungee.resolver.connector.basic)
    testImplementation(libs.bungee.resolver.transport.http)
    testImplementation(platform(libs.jackson.bom))
    testImplementation(libs.jackson.databind)
    // GoldenMatcher: the same shape check the real-server replay uses.
    testImplementation(testFixtures(project(":proofstone-protocol")))

    // The integration test boots a real BungeeCord, replays golden files (GoldenMatcher) and reads agent replies.
    // None of it is shaded into the agent.
    integrationTestImplementation(project(":proofstone-core"))
    integrationTestImplementation(testFixtures(project(":proofstone-protocol")))
    integrationTestImplementation(libs.testcontainers)
    integrationTestImplementation(platform(libs.jackson.bom))
    integrationTestImplementation(libs.jackson.databind)
}

// The agent itself is Java 8 bytecode (the convention plugin's --release 8, checked by verifyBytecode). The unit tests are
// host code that reads the Java 17 protocol fixtures and uses the Java 17 HTTP client, so they compile and resolve at 17.
tasks.named<JavaCompile>("compileTestJava") {
    options.release.set(17)
}

listOf("testCompileClasspath", "testRuntimeClasspath").forEach { name ->
    configurations.named(name) {
        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 17)
    }
}

// Golden wire cases (T-M7-03) are replayed by the contract test over HTTP.
tasks.named<Test>("test") {
    systemProperty("proofstone.golden", rootProject.file("proofstone-protocol/golden").absolutePath)
}

// The integrationTest source set is host code: Java 17 syntax, run on JDK 21 like the other agents' real-server tests.
tasks.named<JavaCompile>("compileIntegrationTestJava") {
    javaCompiler.set(toolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    options.release.set(17)
}

listOf("integrationTestCompileClasspath", "integrationTestRuntimeClasspath").forEach { name ->
    configurations.named(name) {
        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)
    }
}

// The real-server test boots the real agent jar, built by shadowJar and never committed (AGENTS.md section 3).
val agentJar = layout.buildDirectory.file("libs/agent-all.jar")

tasks.named<Test>("integrationTest") {
    javaLauncher.set(toolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    dependsOn(tasks.named("shadowJar"))
    systemProperty("proofstone.repoRoot", rootProject.projectDir.absolutePath)
    systemProperty("proofstone.agentJar", agentJar.get().asFile.absolutePath)
    // The proxy pins come from the version catalog (ADR-0006), until versions.yml schema 3 carries them (T-M7-08).
    systemProperty("proofstone.bungee.image", "${libs.versions.bungee.host.image.get()}@${libs.versions.bungee.host.digest.get()}")
    systemProperty("proofstone.bungee.build", libs.versions.bungee.build.get())
    systemProperty("proofstone.bungee.sha256", libs.versions.bungee.jar.sha256.get())
}

// AGENTS.md section 3: bundled libraries are relocated so they never clash with the server's own copies.
tasks.withType<ShadowJar>().configureEach {
    relocate("com.fasterxml.jackson", "io.github.h7kz.proofstone.shaded.jackson")
}
