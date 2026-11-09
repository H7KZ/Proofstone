import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.jvm.toolchain.JavaToolchainService

plugins {
    id("proofstone.java-host")
    id("com.gradleup.shadow")
    id("proofstone.integration-test")
}

val toolchains = extensions.getByType<JavaToolchainService>()

// Velocity 4.3.1's API is Java 25 bytecode (docs/platforms/velocity.md), so the agent compiles on a JDK 26, which reads it
// (the same reason examples/gradle-paper-plugin does), and emits Java 17 bytecode, the floor ADR-0016 section 10 sets.
// The unit tests load the API's interfaces, so they run on that JDK too.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(26))
    }
}

tasks.named<JavaCompile>("compileJava") {
    options.release.set(17)
}

dependencies {
    // The API only: Velocity provides it at runtime. Its POM names artefacts that repositories here do not all serve
    // (velocity-brigadier is SNAPSHOT-only), so the types the agent touches are listed below at the POM's versions.
    compileOnly(libs.velocity.api) { isTransitive = false }
    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.text.serializer.plain)
    compileOnly(libs.velocity.guice)
    compileOnly(libs.velocity.slf4j)
    compileOnly(libs.velocity.brigadier)

    // Envelope, auth, HTTP server and log buffer, shared with the other agents (T-M7-04). Shaded into this jar.
    implementation(project(":platforms:agent-core"))

    testImplementation(libs.velocity.api) { isTransitive = false }
    testImplementation(libs.adventure.api)
    testImplementation(libs.adventure.text.serializer.plain)
    // Proxy.newProxyInstance on the API interfaces resolves every signature, which names MiniMessage's TagResolver.
    testImplementation(libs.adventure.text.minimessage)
    testImplementation(libs.velocity.guice)
    testImplementation(libs.velocity.slf4j)
    testImplementation(libs.velocity.brigadier)
    testImplementation(platform(libs.jackson.bom))
    testImplementation(libs.jackson.databind)
    // GoldenMatcher: the same shape check the real-server replay uses.
    testImplementation(testFixtures(project(":proofstone-protocol")))

    // The integration test boots a real Velocity (ProxyIT), replays golden files (GoldenMatcher) and reads agent replies.
    // None of it is shaded into the agent.
    integrationTestImplementation(project(":proofstone-core"))
    integrationTestImplementation(testFixtures(project(":proofstone-protocol")))
    integrationTestImplementation(libs.testcontainers)
    integrationTestImplementation(platform(libs.jackson.bom))
    integrationTestImplementation(libs.jackson.databind)
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
    systemProperty("proofstone.velocity.image", "${libs.versions.velocity.host.image.get()}@${libs.versions.velocity.host.digest.get()}")
    systemProperty("proofstone.velocity.version", libs.versions.velocity.api.get())
    systemProperty("proofstone.velocity.build", libs.versions.velocity.build.get())
    systemProperty("proofstone.velocity.sha256", libs.versions.velocity.jar.sha256.get())
}

// AGENTS.md section 3: bundled libraries are relocated so they never clash with the server's own copies.
tasks.withType<ShadowJar>().configureEach {
    relocate("com.fasterxml.jackson", "io.github.h7kz.proofstone.shaded.jackson")
}

// The API jars are Java 25 and 21 bytecode, which Gradle's resolution refuses for the release 17 above. The agent only
// reads them, and runs on Velocity 4.3.1, which is Java 25, so the main and test classpaths resolve for JVM 25.
listOf("compileClasspath", "testCompileClasspath", "testRuntimeClasspath").forEach { name ->
    configurations.named(name) {
        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}
