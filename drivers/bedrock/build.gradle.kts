import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.github.h7kz.proofstone.build.pins.ArtefactPins
import io.github.h7kz.proofstone.build.pins.ShadedJarCheck
import org.gradle.api.GradleException

plugins {
    id("proofstone.java-host")
    id("com.gradleup.shadow")
}

description = "Proofstone Bedrock client driver host on CloudburstMC Protocol (ADR-0018)."

dependencies {
    // The RPC contract (BedrockRpc) is the only part of core this module uses, and the shaded jar carries only that package;
    // core's Testcontainers and the rest never reach the driver host. The tests see all of core.
    compileOnly(project(":proofstone-core"))
    testImplementation(project(":proofstone-core"))
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)

    // CloudburstMC Protocol: exact timestamped snapshot builds (catalog), shaded and relocated below. The POMs name floating
    // snapshots for the netty and math modules, so the build pins those too; checkCloudburstPins names every jar by sha256.
    implementation(libs.cloudburst.bedrock.connection)
    implementation(libs.cloudburst.bedrock.codec)
    constraints {
        for (pin in listOf(libs.cloudburst.protocol.common, libs.cloudburst.netty.raknet, libs.cloudburst.netty.common,
            libs.cloudburst.math.immutable)) {
            implementation(pin) {
                version { strictly(pin.get().versionConstraint.requiredVersion) }
            }
        }
    }

    testImplementation(libs.archunit.junit5)
}

// ADR-0018 section 2: every resolved artefact of the driver host is checked by sha256 against cloudburst-artefacts.sha256, the
// resolved graph may hold no snapshot or dynamic version, and a stale pin fails. Part of `test`, so the acceptance command runs it.
val checkCloudburstPins = tasks.register("checkCloudburstPins") {
    group = "verification"
    description = "Checks every resolved runtime artefact of the driver host by sha256 and refuses floating versions."
    val artifacts = configurations.named("runtimeClasspath").flatMap { it.incoming.artifacts.resolvedArtifacts }
    val pins = layout.projectDirectory.file("cloudburst-artefacts.sha256").asFile
    inputs.file(pins)
    doLast {
        val resolved = artifacts.get().map { artifact ->
            val id = artifact.variant.owner as org.gradle.api.artifacts.component.ModuleComponentIdentifier
            ArtefactPins.Resolved("${id.group}:${id.module}:${id.version}", artifact.file.toPath())
        }
        val violations = ArtefactPins.check(resolved, pins.toPath())
        if (violations.isNotEmpty()) {
            throw GradleException("checkCloudburstPins failed:\n" + violations.joinToString("\n"))
        }
    }
}

tasks.named<Test>("test") {
    dependsOn(checkCloudburstPins)
    // BedrockPinsTest reads the catalog and versions.yml from the repository root.
    systemProperty("proofstone.repoRoot", rootProject.projectDir.absolutePath)
}

// One jar with everything relocated (AGENTS.md section 3): Cloudburst, its netty and the rest never clash with the host
// JVM's own classes, and users' graphs never see a snapshot.
val shadowJar = tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName.set("bedrock-host")
    archiveVersion.set("")
    manifest {
        attributes("Main-Class" to "io.github.h7kz.proofstone.driver.bedrock.BedrockHost")
    }
    // The RPC contract only, from core's classes: its Testcontainers and the rest stay out of the host's jar.
    from(rootProject.layout.projectDirectory.dir("proofstone-core/build/classes/java/main")) {
        include("io/github/h7kz/proofstone/core/driver/bedrock/rpc/**")
    }
    dependsOn(":proofstone-core:classes")
    val own = "io.github.h7kz.proofstone.shaded"
    for (pkg in listOf("com.fasterxml.jackson", "org.cloudburstmc", "io.netty", "io.airlift", "org.jose4j", "net.jodah",
        "com.nukkitx", "org.slf4j", "it.unimi")) {
        relocate(pkg, "$own.$pkg")
    }
    mergeServiceFiles()
}

val checkShadedJar = tasks.register("checkShadedJar") {
    group = "verification"
    description = "Checks the shaded jar: no unrelocated library class, no snapshot entry."
    val jar = shadowJar.flatMap { it.archiveFile }
    inputs.file(jar)
    doLast {
        val violations = ShadedJarCheck.check(jar.get().asFile.toPath())
        if (violations.isNotEmpty()) {
            throw GradleException("checkShadedJar failed:\n" + violations.take(30).joinToString("\n"))
        }
    }
}

tasks.named("check") {
    dependsOn(checkShadedJar)
}
