plugins {
    id("proofstone.java-host")
}

dependencies {
    // @CellTest is a JUnit @TestTemplate, so the API exposes JUnit's annotations (ADR-0001).
    api(platform(libs.junit.bom))
    api(libs.junit.jupiter.api)
}

tasks.test {
    // The public-surface baseline (T-M2-07). Regenerate on purpose with -Dproofstone.updateApiBaseline=true.
    systemProperty("proofstone.apiBaseline", layout.projectDirectory.file("api-baseline.txt").asFile.absolutePath)
    systemProperty("proofstone.updateApiBaseline", providers.systemProperty("proofstone.updateApiBaseline")
        .orElse("false").get())
    inputs.file(layout.projectDirectory.file("api-baseline.txt"))
}
