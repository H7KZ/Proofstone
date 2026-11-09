// Host-side modules: Java 17 toolchain, warnings are errors, JUnit 5 platform, hard-rule checks.
// Decision: ADR-0001 (Java + JUnit 5), ADR-0006 (pinned versions from the catalog).
import io.github.h7kz.proofstone.build.arch.HardRules
import org.gradle.api.GradleException
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.the
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Werror")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// Precompiled script plugins cannot use the generated `libs` accessors, so look the catalog up by name.
val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    testImplementation(platform(libs.findLibrary("junit-bom").get()))
    testImplementation(libs.findLibrary("junit-jupiter").get())
    testImplementation(libs.findLibrary("assertj-core").get())
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
}

// AGENTS.md §3: ArchUnit hard rules over this module's main and test classes. Part of `check`.
val sourceSets = the<SourceSetContainer>()
val classesDirs = sourceSets["main"].output.classesDirs + sourceSets["test"].output.classesDirs

val archTest = tasks.register("archTest") {
    group = "verification"
    description = "ArchUnit hard rules over main and test classes (AGENTS.md §3)."
    dependsOn("classes", "testClasses")
    // Copy to a local so the execution lambda does not capture the script (configuration cache).
    val dirs = classesDirs
    doLast {
        val violations = HardRules.check(dirs.files.map { it.toPath() })
        if (violations.isNotEmpty()) {
            throw GradleException("archTest failed:\n" + violations.joinToString("\n"))
        }
    }
}

tasks.named("check") {
    dependsOn(archTest)
}
