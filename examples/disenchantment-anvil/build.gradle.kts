// Third-party plugin scenario (T-M2-10): the Disenchantment anvil flow against a real release jar. Its tests boot a
// real server, so they need Docker and are not part of `check`.
import java.net.URI
import java.security.MessageDigest

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

// The plugin under test is pinned by release and SHA-256 (AGENTS.md section 3). The jar is downloaded into build/ and
// never committed. The digest is the one GitHub shows for the release asset.
val disenchantmentVersion = "6.5.12"
val disenchantmentUrl =
    "https://github.com/H7KZ/Disenchantment/releases/download/v$disenchantmentVersion/Disenchantment-$disenchantmentVersion.jar"
val disenchantmentSha256 = "18195f6687ba7d2616bbe989ca1978ddf2538cd65b994ab2d00693f8c0920707"
val disenchantmentJar = layout.buildDirectory.file("disenchantment/Disenchantment-$disenchantmentVersion.jar")

// A typed task, so the build stays compatible with the configuration cache.
abstract class DownloadPinnedJar : DefaultTask() {
    @get:Input
    abstract val url: Property<String>

    @get:Input
    abstract val sha256: Property<String>

    @get:OutputFile
    abstract val target: RegularFileProperty

    @TaskAction
    fun download() {
        val file = target.get().asFile
        val pin = sha256.get()
        // A file already there is trusted only if it matches the pin, never because it exists.
        if (file.exists() && digest(file) == pin) {
            return
        }
        file.parentFile.mkdirs()
        val partial = File(file.parentFile, file.name + ".part")
        URI(url.get()).toURL().openStream().use { input ->
            partial.outputStream().use { output -> input.copyTo(output) }
        }
        val actual = digest(partial)
        if (actual != pin) {
            partial.delete()
            throw GradleException("${url.get()} has SHA-256 $actual, expected $pin")
        }
        partial.renameTo(file)
    }

    private fun digest(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        .joinToString("") { "%02x".format(it) }
}

val downloadDisenchantment = tasks.register<DownloadPinnedJar>("downloadDisenchantment") {
    description = "Downloads the pinned Disenchantment release and verifies its SHA-256."
    url.set(disenchantmentUrl)
    sha256.set(disenchantmentSha256)
    target.set(disenchantmentJar)
}

val agentJar = rootProject.layout.projectDirectory.file("platforms/bukkit/agent/build/libs/agent-all.jar")

tasks.test {
    dependsOn(downloadDisenchantment)
    // The agent jar is built, never committed (AGENTS.md §3).
    dependsOn(":platforms:bukkit:agent:shadowJar")
    systemProperty("proofstone.repoRoot", rootProject.projectDir.absolutePath)
    systemProperty("proofstone.agentJar", agentJar.asFile.absolutePath)
    // The run-time cell selector and scope reach the test JVM from the Gradle command line (test-api spec §1).
    for (name in listOf("proofstone.cells", "proofstone.scope")) {
        providers.systemProperty(name).orNull?.let { systemProperty(name, it) }
    }
    // The result depends on Docker, so always run when asked. A skipped test shows its reason, never a pass.
    outputs.upToDateWhen { false }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
