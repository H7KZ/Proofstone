plugins {
    id("proofstone.java-agent-legacy")
}

dependencies {
    // Bundled by every agent that depends on this module, and relocated there (AGENTS.md §3). Not exposed as `api`:
    // only the HTTP server and the envelope use it, and the agents' own code does not.
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)

    testImplementation(platform(libs.jackson.bom))
    testImplementation(libs.jackson.databind)
}

// Golden wire cases (T-M1-02) are replayed by these unit tests over HTTP.
tasks.named<Test>("test") {
    systemProperty("proofstone.golden", rootProject.file("proofstone-protocol/golden").absolutePath)
}
