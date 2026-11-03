plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.shadow.gradle.plugin)
    // Hard-rule checks run inside the build, so ArchUnit is a build-logic dependency only.
    implementation(libs.archunit.junit5)

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
