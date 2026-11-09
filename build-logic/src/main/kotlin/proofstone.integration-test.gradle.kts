// The `integrationTest` convention (T-M1-07): a source set and task for tests that need Docker. It is not part
// of `check`. Modules such as proofstone-core and the agents apply it next to their base Java plugin.
// Decision: ADR-0005 (real containers, no bind mounts).
import io.github.h7kz.proofstone.build.arch.HardRules
import org.gradle.api.GradleException
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.the

plugins {
    `java-library`
}

val sourceSets = the<SourceSetContainer>()
val integrationTest = sourceSets.create("integrationTest") {
    compileClasspath += sourceSets["main"].output
    runtimeClasspath += sourceSets["main"].output
}

configurations["integrationTestImplementation"].extendsFrom(configurations["implementation"])
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations["runtimeOnly"])

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    "integrationTestImplementation"(platform(libs.findLibrary("junit-bom").get()))
    "integrationTestImplementation"(libs.findLibrary("junit-jupiter").get())
    "integrationTestImplementation"(libs.findLibrary("assertj-core").get())
    "integrationTestRuntimeOnly"(libs.findLibrary("junit-platform-launcher").get())
}

val integrationTestTask = tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "Tests that need Docker. Not part of check."
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.named("test"))
    // Always run when asked: the result depends on Docker and the network, not only on the inputs.
    outputs.upToDateWhen { false }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

// AGENTS.md §3 still applies: archTest must scan the integration classes too.
tasks.matching { it.name == "archTest" }.configureEach {
    dependsOn("integrationTestClasses")
    val dirs = integrationTest.output.classesDirs
    doLast {
        val violations = HardRules.check(dirs.files.map { it.toPath() })
        if (violations.isNotEmpty()) {
            throw GradleException("archTest failed (integrationTest classes):\n" + violations.joinToString("\n"))
        }
    }
}
