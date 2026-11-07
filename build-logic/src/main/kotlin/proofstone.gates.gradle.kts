// Gate tasks from AGENTS.md §4. Applied once, to the root project.
// `check` runs each module's `check` (which runs archTest), the lintVersions scan and the build-logic tests.
import dev.proofstone.build.docs.DocsCheck
import dev.proofstone.build.lint.VersionLint
import dev.proofstone.build.versions.HttpVersionSource
import dev.proofstone.build.versions.VersionsUpdater
import dev.proofstone.build.versions.VersionsValidator
import org.gradle.api.GradleException

plugins {
    base
}

tasks.named("check") {
    // Skip grouping projects such as `:platforms` and `:platforms:bukkit`, which have no build file or check task.
    dependsOn(subprojects.filter { it.file("build.gradle.kts").exists() }.map { it.tasks.named("check") })
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

tasks.register("docsCheck") {
    group = "verification"
    description = "Markdown links, task-card format, depends IDs and glossary terms (T-M0-05)."
    val root = rootDir
    doLast {
        val report = DocsCheck.run(root.toPath())
        report.warnings().forEach { logger.warn("docsCheck warning: $it") }
        if (!report.passed()) {
            throw GradleException("docsCheck failed:\n" + report.errors().joinToString("\n"))
        }
        logger.lifecycle("docsCheck: 0 errors, ${report.warnings().size} warning(s)")
    }
}

// Schema and semantic checks for the version matrix (ADR-0008). Root paths resolve against the repo root.
tasks.register("validateVersions") {
    group = "verification"
    description = "Validates versions.yml against its JSON Schema and the semantic rules (ADR-0008)."
    val root = rootDir
    doLast {
        val errors = VersionsValidator.validate(
            root.toPath().resolve("versions.yml"),
            root.toPath().resolve("proofstone-protocol/schemas/versions.schema.json"),
        )
        if (errors.isNotEmpty()) {
            throw GradleException("validateVersions failed:\n" + errors.joinToString("\n"))
        }
    }
}

tasks.named("check") {
    dependsOn("validateVersions")
}

// version-watch (T-M3-01, docs/versioning.md §2). Fills in versions.yml from the fill API. Add
// -Pproofstone.versions.dryRun=true to print the diff without writing. Gradle's own --dry-run only lists tasks.
tasks.register("updateVersions") {
    group = "versions"
    description = "Fills in versions.yml from the Paper and Folia fill API (T-M3-01, docs/versioning.md §2)."
    val root = rootDir
    val dryRun = providers.gradleProperty("proofstone.versions.dryRun").map { it.toBoolean() }.orElse(false)
    doLast {
        val result = VersionsUpdater.run(
            root.toPath().resolve("versions.yml"),
            root.toPath().resolve("proofstone-protocol/schemas/versions.schema.json"),
            dryRun.get(),
            HttpVersionSource(),
        )
        result.log().forEach { logger.lifecycle(it) }
        when {
            dryRun.get() -> logger.lifecycle(result.diff().ifEmpty { "dry run: no changes" })
            result.text() == result.original() -> logger.lifecycle("versions.yml: no changes")
            else -> logger.lifecycle("versions.yml: updated")
        }
    }
}
