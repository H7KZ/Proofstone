// Gate tasks from AGENTS.md §4. Applied once, to the root project.
// `check` runs each module's `check` (which runs archTest), the lintVersions scan and the build-logic tests.
import dev.proofstone.build.lint.VersionLint
import org.gradle.api.GradleException

plugins {
    base
}

tasks.named("check") {
    dependsOn(subprojects.map { it.tasks.named("check") })
    dependsOn(gradle.includedBuild("build-logic").task(":check"))
}

tasks.register("lintVersions") {
    group = "verification"
    description = "Fails on floating versions in build files and versions.yml (AGENTS.md §3)."
    // Copy to a local so the execution lambda does not capture the script (configuration cache).
    val root = rootDir
    doLast {
        val violations = VersionLint.scan(root.toPath())
        if (violations.isNotEmpty()) {
            throw GradleException("lintVersions failed:\n" + violations.joinToString("\n"))
        }
    }
}

tasks.named("check") {
    dependsOn("lintVersions")
}

// Placeholder until T-M0-05 implements the real checks (links, card format, depends IDs).
tasks.register("docsCheck") {
    group = "verification"
    description = "Markdown links, card format and glossary terms. Placeholder until T-M0-05."
    doLast {
        logger.lifecycle("docsCheck: placeholder, real checks land in T-M0-05")
    }
}
