import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.security.MessageDigest
import javax.inject.Inject
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.jvm.toolchain.JavaToolchainService

plugins {
    id("proofstone.java-host")
    id("com.gradleup.shadow")
    id("proofstone.integration-test")
}

val toolchains = extensions.getByType<JavaToolchainService>()

// ADR-0017 section 5: Fabric API 0.166.2+26.3 and the 26.3 game are class file 69, so the agent compiles on a Java 25
// toolchain with --release 25. Gradle itself stays on JDK 21.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.named<JavaCompile>("compileJava") {
    options.release.set(25)
}

// Plain javac against the pinned jars, the fallback ADR-0017 section 5 allows (the catalog says why Loom is not used).
// 26.x ships unobfuscated, so no mappings. The vanilla server jar is Mojang's bundler: the game jar and its libraries
// sit inside it, as Fabric API's modules sit inside its jar. Both are unpacked once into build/game.
val vanillaServer: Configuration by configurations.creating { isTransitive = false }
val fabricApi: Configuration by configurations.creating { isTransitive = false }

dependencies {
    vanillaServer(libs.minecraft.server)
    fabricApi(libs.fabric.api)
}

interface ArchiveAccess {
    @get:Inject
    val archives: ArchiveOperations
}

val unpackGame = tasks.register<Sync>("unpackGame") {
    description = "Unpacks the pinned vanilla server jar and Fabric API into the agent's compile classpath."
    // Locals only: the configuration cache cannot keep references to the script or to a Configuration.
    val archives = objects.newInstance<ArchiveAccess>().archives
    val server = vanillaServer.incoming.files
    val api = fabricApi.incoming.files
    val pins = mapOf(
        "server" to libs.versions.minecraft.server.sha256.get(),
        "fabric-api" to libs.versions.fabric.api.sha256.get(),
    )
    from(server.elements.map { files -> files.map { archives.zipTree(it.asFile) } }) {
        include("META-INF/versions/**/*.jar", "META-INF/libraries/**/*.jar")
    }
    from(api.elements.map { files -> files.map { archives.zipTree(it.asFile) } }) { include("META-INF/jars/*.jar") }
    into(layout.buildDirectory.dir("game"))
    doFirst {
        // A content mismatch is a loud failure naming the file, never a silent new input (AGENTS.md section 3).
        listOf(server.singleFile to pins.getValue("server"), api.singleFile to pins.getValue("fabric-api"))
            .forEach { (file, expected) ->
                val actual = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                    .joinToString("") { "%02x".format(it) }
                if (actual != expected) {
                    throw GradleException("${file.name}: sha256 $actual, pinned $expected (gradle/libs.versions.toml)")
                }
            }
    }
}

val gameJars = files(layout.buildDirectory.dir("game").map { dir -> fileTree(dir) { include("**/*.jar") } })
    .builtBy(unpackGame)

dependencies {
    compileOnly(gameJars)
    compileOnly(libs.fabric.loader) { isTransitive = false }

    // Envelope, auth, HTTP server and log buffer, shared with the other agents (T-M7-04). Shaded into this jar.
    implementation(project(":platforms:agent-core"))

    // The unit tests run the agent's Fabric API code (events, phases) on the real classes, against a mock server view.
    testImplementation(gameJars)
    testImplementation(libs.fabric.loader) { isTransitive = false }
    testImplementation(platform(libs.jackson.bom))
    testImplementation(libs.jackson.databind)
    // GoldenMatcher: the same shape check the real-server replay uses.
    testImplementation(testFixtures(project(":proofstone-protocol")))

    // The integration test boots a real Fabric server, joins with the mcpl driver and replays golden files. None of it
    // is shaded into the agent.
    integrationTestImplementation(project(":proofstone-core"))
    integrationTestImplementation(project(":drivers:mcpl"))
    integrationTestImplementation(testFixtures(project(":proofstone-protocol")))
    integrationTestImplementation(libs.testcontainers)
    integrationTestImplementation(platform(libs.jackson.bom))
    integrationTestImplementation(libs.jackson.databind)
}

tasks.named<Test>("test") {
    // The game jar on the test classpath carries Minecraft's log4j2.xml, which writes logs/latest.log in the working
    // directory: keep it under build/.
    val work = layout.buildDirectory.dir("test-work")
    workingDir(work)
    doFirst { work.get().asFile.mkdirs() }
    systemProperty("proofstone.golden", rootProject.file("proofstone-protocol/golden").absolutePath)
    systemProperty("proofstone.repoRoot", rootProject.projectDir.absolutePath)
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
    // The Fabric pins are planned from versions.yml schema 4 by the test itself (ADR-0017 section 3).
}

// AGENTS.md section 3: bundled libraries are relocated so they never clash with the server's own copies.
tasks.withType<ShadowJar>().configureEach {
    relocate("com.fasterxml.jackson", "io.github.h7kz.proofstone.shaded.jackson")
}
