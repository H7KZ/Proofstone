plugins {
    id("proofstone.java-host")
    id("proofstone.publish")
    `java-test-fixtures`
}

description = "Proofstone wire protocol: the JSON schemas and types shared by the host and the in-server agent."

dependencies {
    // Test-only. The protocol types have no runtime dependencies (AGENTS.md §3).
    testImplementation(platform(libs.jackson.bom))
    testImplementation(libs.jackson.databind)
    testImplementation(libs.json.schema.validator)
    testFixturesImplementation(platform(libs.jackson.bom))
    testFixturesImplementation(libs.jackson.databind)
}

tasks.test {
    // The tests read the schemas from disk, not the classpath, so Gradle must see them as inputs.
    inputs.dir("schemas")
    inputs.dir("golden")
}
