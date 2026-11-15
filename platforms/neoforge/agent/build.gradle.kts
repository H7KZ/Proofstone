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

// ADR-0017 section 5: NeoForge 26.3 and the 26.3 game are class file 69, so the agent compiles on a Java 25 toolchain
// with --release 25. Gradle itself stays on JDK 21.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.named<JavaCompile>("compileJava") {
    options.release.set(25)
}

// Plain javac against the pinned jars, the fallback ADR-0017 section 5 allows (the catalog says why ModDevGradle is not
// used). 26.x ships unobfuscated, so no mappings. The game classes come from the vanilla server jar (Mojang's bundler,
// the same pin the Fabric agent uses): NeoForge's patched server keeps every vanilla name the agent calls. Every jar is
// checked by sha256 before it is copied into build/game, so nothing unchecked reaches the compiler.
val vanillaServer: Configuration by configurations.creating { isTransitive = false }
val neoforgeJars: Configuration by configurations.creating { isTransitive = false }
val busRuntime: Configuration by configurations.creating { isTransitive = false }

dependencies {
    vanillaServer(libs.minecraft.server)
    neoforgeJars(variantOf(libs.neoforge) { classifier("universal") })
    neoforgeJars(libs.neoforge.fml)
    neoforgeJars(libs.neoforge.bus)
    neoforgeJars(variantOf(libs.neoforge.mergetool) { classifier("api") })
    neoforgeJars(libs.neoforge.maven.artifact)
    busRuntime(libs.neoforge.typetools)
}

interface ArchiveAccess {
    @get:Inject
    val archives: ArchiveOperations
}

val unpackGame = tasks.register<Sync>("unpackGame") {
    description = "Checks the pinned NeoForge jars and unpacks the vanilla server jar into the agent's compile classpath."
    // Locals only: the configuration cache cannot keep references to the script or to a Configuration.
    val archives = objects.newInstance<ArchiveAccess>().archives
    val server = vanillaServer.incoming.files
    val neo = neoforgeJars.incoming.files
    val bus = busRuntime.incoming.files
    // By file name prefix: each resolved jar must match exactly one pin (catalog), else the build fails naming it.
    val pins = mapOf(
        "server" to libs.versions.minecraft.server.sha256.get(),
        "neoforge-" to libs.versions.neoforge.sha256.get(),
        "loader-" to libs.versions.neoforge.fml.sha256.get(),
        "bus-" to libs.versions.neoforge.bus.sha256.get(),
        "mergetool-" to libs.versions.neoforge.mergetool.sha256.get(),
        "maven-artifact-" to libs.versions.neoforge.maven.artifact.sha256.get(),
        "typetools-" to libs.versions.neoforge.typetools.sha256.get(),
    )
    from(server.elements.map { files -> files.map { archives.zipTree(it.asFile) } }) {
        include("META-INF/versions/**/*.jar", "META-INF/libraries/**/*.jar")
    }
    from(neo) { into("neoforge") }
    from(bus) { into("test-runtime") }
    into(layout.buildDirectory.dir("game"))
    doFirst {
        // A content mismatch is a loud failure naming the file, never a silent new input (AGENTS.md section 3).
        (server.files + neo.files + bus.files).forEach { file ->
            val expected = pins.filterKeys { file.name.startsWith(it) }.values.singleOrNull()
                ?: throw GradleException("${file.name}: no sha256 pin in gradle/libs.versions.toml")
            val actual = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                .joinToString("") { "%02x".format(it) }
            if (actual != expected) {
                throw GradleException("${file.name}: sha256 $actual, pinned $expected (gradle/libs.versions.toml)")
            }
        }
    }
}

val gameDir = layout.buildDirectory.dir("game")
val gameJars = files(gameDir.map { dir -> fileTree(dir) { include("**/*.jar"); exclude("test-runtime/**") } })
    .builtBy(unpackGame)
val testRuntimeJars = files(gameDir.map { dir -> fileTree(dir) { include("test-runtime/*.jar") } }).builtBy(unpackGame)

dependencies {
    compileOnly(gameJars)

    // Envelope, auth, HTTP server and log buffer, shared with the other agents (T-M7-04). Shaded into this jar.
    implementation(project(":platforms:agent-core"))
    // The code that reads the vanilla server, shared with the Fabric agent (T-M8-15). Ours, so shaded here, not relocated.
    implementation(project(":platforms:vanilla-view"))

    // The unit tests run the agent's probes on NeoForge's real event bus, against a mock server view.
    testImplementation(gameJars)
    testRuntimeOnly(testRuntimeJars)
    // MockView, AgentClient and ClassScan, shared by both loaders' agent tests.
    testImplementation(testFixtures(project(":platforms:vanilla-view")))
    testImplementation(platform(libs.jackson.bom))
    testImplementation(libs.jackson.databind)
    // GoldenMatcher: the same shape check the real-server replay uses.
    testImplementation(testFixtures(project(":proofstone-protocol")))

    // The integration test boots a real NeoForge server, joins with the mcpl driver and replays golden files. None of
    // it is shaded into the agent.
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
        // The shared vanilla-view is Java 25 bytecode and the real-server test runs on 21; it never loads game code, it talks to
        // the agent over HTTP (T-M8-15). The agent's main classes are on this classpath already, as before.
        exclude(module = "vanilla-view")
        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)
    }
}

// The real-server test boots the real agent jar, built by shadowJar and never committed (AGENTS.md section 3).
val agentJar = layout.buildDirectory.file("libs/agent-all.jar")

// The example mod under test (T-M8-07): a real mod built from source under src/exampleMod by plain javac against the same
// pinned jars as the agent, never a downloaded artefact. The loader host's real-server test copies it into /mods.
val exampleMod = sourceSets.create("exampleMod")
configurations.named("exampleModCompileOnly") { extendsFrom(configurations.getByName("compileOnly")) }
tasks.named<JavaCompile>("compileExampleModJava") { options.release.set(25) }
val exampleModJar = tasks.register<Jar>("exampleModJar") {
    description = "Builds the example mod the loader host test puts in /mods."
    archiveBaseName.set("proofstone-example-mod")
    archiveVersion.set("")
    destinationDirectory.set(layout.buildDirectory.dir("example-mod"))
    from(exampleMod.output)
}

// The same mod with one marker resource that makes it throw at load: the mod under test of the crash-at-load bad test, declared
// through @Proofstone(mods) (T-M8-13). Built here, never downloaded or committed as a binary.
val exampleCrashModJar = tasks.register<Jar>("exampleCrashModJar") {
    description = "Builds the example mod that crashes at load, for the crash-at-load bad test."
    archiveBaseName.set("proofstone-crashing-mod")
    archiveVersion.set("")
    destinationDirectory.set(layout.buildDirectory.dir("example-mod"))
    from(exampleMod.output)
    from(resources.text.fromString("the example mod throws at load when this resource is in its jar").asFile()) {
        rename { "proofstone-example-fail" }
    }
}

tasks.named<Test>("integrationTest") {
    javaLauncher.set(toolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    dependsOn(tasks.named("shadowJar"), exampleModJar)
    systemProperty("proofstone.exampleMod", layout.buildDirectory.file("example-mod/proofstone-example-mod.jar").get().asFile.absolutePath)
    systemProperty("proofstone.repoRoot", rootProject.projectDir.absolutePath)
    systemProperty("proofstone.agentJar", agentJar.get().asFile.absolutePath)
    // The NeoForge pins are planned from versions.yml schema 4 by the test itself (ADR-0017 section 3).
}

// AGENTS.md section 3: bundled libraries are relocated so they never clash with the server's own copies.
tasks.withType<ShadowJar>().configureEach {
    relocate("com.fasterxml.jackson", "io.github.h7kz.proofstone.shaded.jackson")
    // FML reads a mod jar as an automatic module and refuses a META-INF/services file whose provider is not in the jar
    // (docs/platforms/neoforge.md, Gotchas): Jackson's service files must be relocated with the classes they name.
    mergeServiceFiles()
}
