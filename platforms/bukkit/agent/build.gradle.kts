import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("proofstone.java-agent-legacy")
}

dependencies {
    // Bukkit API at the 1.8.8 floor. Servers provide it at runtime, so it is not shaded. Its POM points at
    // SNAPSHOT-only BungeeCord artefacts that the repos no longer serve; the agent never touches them.
    compileOnly(libs.spigot.api) {
        isTransitive = false
    }

    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)

    // Key lookups in ItemBuilderTest use the Bukkit static registries, which need no running server.
    testImplementation(libs.spigot.api) {
        isTransitive = false
    }
    testImplementation(libs.guava)
    testImplementation(libs.commons.lang)

    testImplementation(platform(libs.jackson.bom))
    testImplementation(libs.jackson.databind)
}

// Golden wire cases (T-M1-02) are replayed by the agent's unit tests over HTTP.
tasks.named<Test>("test") {
    systemProperty("proofstone.golden", rootProject.file("proofstone-protocol/golden").absolutePath)
}

// AGENTS.md §3: bundled libraries are relocated so they never clash with the server's own copies.
tasks.withType<ShadowJar>().configureEach {
    relocate("com.fasterxml.jackson", "dev.proofstone.shaded.jackson")
}
