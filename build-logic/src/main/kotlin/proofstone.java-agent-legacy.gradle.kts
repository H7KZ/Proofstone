// In-server agents (Paper, Folia, Spigot, Purpur). They run on old JVMs, so the bytecode is Java 8.
// Everything the agent bundles is shaded into dev.proofstone.shaded.* (AGENTS.md §3, ADR-0007).
// Module build files add their own relocate rules.
import org.gradle.api.GradleException
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.the
import java.io.DataInputStream
import java.io.FileInputStream

plugins {
    id("proofstone.java-host")
    id("com.gradleup.shadow")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
}

// AGENTS.md §6, ADR-0002: a class above major 52 would not load on a 1.8.8 server. Checks the main
// output, so a stray newer class fails the build instead of the server.
val agentClasses = the<SourceSetContainer>()["main"].output.classesDirs
val verifyBytecode = tasks.register("verifyBytecode") {
    group = "verification"
    description = "Fails unless every agent class is Java 8 bytecode (major version 52)."
    dependsOn("classes")
    // Copy to a local so the execution lambda does not capture the script (configuration cache).
    val dirs = agentClasses
    doLast {
        val tooNew = dirs.files.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "class" }.toList()
        }.filter { file ->
            DataInputStream(FileInputStream(file)).use { input ->
                input.readInt() // magic CAFEBABE
                input.readUnsignedShort() // minor
                input.readUnsignedShort() != 52
            }
        }
        if (tooNew.isNotEmpty()) {
            throw GradleException("verifyBytecode: not Java 8 bytecode (major 52):\n" + tooNew.joinToString("\n"))
        }
    }
}

tasks.named("check") {
    dependsOn(verifyBytecode)
}
