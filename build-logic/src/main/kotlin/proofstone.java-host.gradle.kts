// Host-side modules: Java 17 toolchain, warnings are errors, JUnit 5 platform.
// Decision: ADR-0001 (Java + JUnit 5), ADR-0006 (pinned versions from the catalog).
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.the

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
