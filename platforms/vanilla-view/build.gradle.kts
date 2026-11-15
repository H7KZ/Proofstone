import java.security.MessageDigest
import javax.inject.Inject

plugins {
    id("proofstone.java-host")
    `java-test-fixtures`
}

description = "The code of the Fabric and NeoForge agents that reads the vanilla server, shared by both (T-M8-15)."

// ADR-0017 section 5: the 26.3 game is class file 69, so this module compiles on a Java 25 toolchain with --release 25, like the
// two agents that shade it. Gradle itself stays on JDK 21.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.named<JavaCompile>("compileJava") {
    options.release.set(25)
}

// Plain javac against the same pinned vanilla server jar the agents use, no new download: Gradle resolves the one
// artefact already in its cache for the agents. 26.x ships unobfuscated, so there are no mappings. The jar is Mojang's
// bundler: the game jar and its libraries sit inside it and are unpacked once into build/game, as the agents do.
val vanillaServer: Configuration by configurations.creating { isTransitive = false }

dependencies {
    vanillaServer(libs.minecraft.server)
}

interface ArchiveAccess {
    @get:Inject
    val archives: ArchiveOperations
}

val unpackGame = tasks.register<Sync>("unpackGame") {
    description = "Unpacks the pinned vanilla server jar into this module's compile classpath."
    // Locals only: the configuration cache cannot keep references to the script or to a Configuration.
    val archives = objects.newInstance<ArchiveAccess>().archives
    val server = vanillaServer.incoming.files
    val pin = libs.versions.minecraft.server.sha256.get()
    from(server.elements.map { files -> files.map { archives.zipTree(it.asFile) } }) {
        include("META-INF/versions/**/*.jar", "META-INF/libraries/**/*.jar")
    }
    into(layout.buildDirectory.dir("game"))
    doFirst {
        // A content mismatch is a loud failure naming the file, never a silent new input (AGENTS.md section 3).
        val file = server.singleFile
        val actual = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }
        if (actual != pin) {
            throw GradleException("${file.name}: sha256 $actual, pinned $pin (gradle/libs.versions.toml)")
        }
    }
}

val gameJars = files(layout.buildDirectory.dir("game").map { dir -> fileTree(dir) { include("**/*.jar") } })
    .builtBy(unpackGame)

dependencies {
    compileOnly(gameJars)

    // Fields, AgentException: the agents' shared envelope. Bundled by the agents that depend on this module.
    implementation(project(":platforms:agent-core"))

    // The fixtures (MockView, AgentClient, the class scan) are the agents' test helpers: never shaded into an agent.
    testFixturesCompileOnly(gameJars)
    testFixturesImplementation(project(":platforms:agent-core"))
    testFixturesApi(platform(libs.jackson.bom))
    testFixturesApi(libs.jackson.databind)

    testImplementation(gameJars)
}

tasks.named<Test>("test") {
    // The game jar on the test classpath carries Minecraft's log4j2.xml, which writes logs/latest.log in the working
    // directory: keep it under build/.
    val work = layout.buildDirectory.dir("test-work")
    workingDir(work)
    doFirst { work.get().asFile.mkdirs() }
}
