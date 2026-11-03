plugins {
    id("proofstone.java-host")
}

dependencies {
    // @CellTest is a JUnit @TestTemplate, so the API exposes JUnit's annotations (ADR-0001).
    api(platform(libs.junit.bom))
    api(libs.junit.jupiter.api)
}
