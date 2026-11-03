plugins {
    id("proofstone.java-host")
}

dependencies {
    implementation(project(":proofstone-api"))
    implementation(project(":proofstone-protocol"))
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.json.schema.validator)
}

tasks.test {
    // The seed versions.yml and its schema sit at the repo root (ADR-0008). Tests read them from here.
    systemProperty("proofstone.repoRoot", rootProject.projectDir.absolutePath)
}
