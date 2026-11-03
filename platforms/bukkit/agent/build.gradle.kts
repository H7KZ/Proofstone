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

    testImplementation(platform(libs.jackson.bom))
    testImplementation(libs.jackson.databind)
}

// AGENTS.md §3: bundled libraries are relocated so they never clash with the server's own copies.
tasks.withType<ShadowJar>().configureEach {
    relocate("com.fasterxml.jackson", "dev.proofstone.shaded.jackson")
}
