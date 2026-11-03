// Gate tasks from AGENTS.md §4. Applied once, to the root project.
// `check` aggregates each module's `check`. Later cards add archTest (T-M0-03) and validateVersions (T-M0-04).
plugins {
    base
}

tasks.named("check") {
    dependsOn(subprojects.map { it.tasks.named("check") })
}

// Placeholder until T-M0-05 implements the real checks (links, card format, depends IDs).
tasks.register("docsCheck") {
    group = "verification"
    description = "Markdown links, card format and glossary terms. Placeholder until T-M0-05."
    doLast {
        logger.lifecycle("docsCheck: placeholder, real checks land in T-M0-05")
    }
}
