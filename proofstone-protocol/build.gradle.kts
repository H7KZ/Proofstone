plugins {
    id("proofstone.java-host")
}

dependencies {
    // Test-only. The protocol types have no runtime dependencies (AGENTS.md §3).
    testImplementation(platform(libs.jackson.bom))
    testImplementation(libs.jackson.databind)
    testImplementation(libs.json.schema.validator)
}

tasks.test {
    // The tests read the schemas from disk, not the classpath, so Gradle must see them as inputs.
    inputs.dir("schemas")
}
