plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.shadow.gradle.plugin)
    // Hard-rule checks and versions.yml validation run inside the build, so these are build-logic dependencies only.
    implementation(libs.archunit.junit5)
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.json.schema.validator)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.testcontainers)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // Fixtures are inputs to the rule tests, not tests. Some deliberately use @RepeatedTest.
    exclude("**/fixtures/**", "**/flake/**")
}
