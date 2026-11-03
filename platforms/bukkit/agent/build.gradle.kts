plugins {
    id("proofstone.java-agent-legacy")
}

dependencies {
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)

    testImplementation(platform(libs.jackson.bom))
    testImplementation(libs.jackson.databind)
}
