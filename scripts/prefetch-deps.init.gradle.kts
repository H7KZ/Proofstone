// Init script for scripts/ci-prefetch-deps.sh (T-M6-113). Adds `prefetchDependencies` to every project: it resolves, and so downloads,
// the compile and runtime classpaths that `check` will need, and compiles and runs nothing. Not applied to normal builds.
allprojects {
    tasks.register("prefetchDependencies") {
        group = "verification"
        description = "Downloads the classpaths of this project. Compiles and tests nothing."
        for (name in listOf("compileClasspath", "runtimeClasspath", "testCompileClasspath", "testRuntimeClasspath")) {
            // Resolved at execution, so the configuration cache holds the file collection and not the artefacts.
            inputs.files(provider { configurations.findByName(name)?.takeIf { it.isCanBeResolved } ?: files() })
        }
    }
}
