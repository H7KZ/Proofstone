import java.util.zip.ZipFile
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
    // NetherNet (T-M12-09, ADR-0021): the transport and the WebRTC binding. The natives are classifier jars of the binding for the
    // two Linux glibc architectures the driver-host image runs on (ADR-0018 placement); no other platform is shipped.
    implementation(libs.cloudburst.netty.nethernet)
    implementation(libs.libdatachannel.java)
    for (arch in listOf("linux-glibc-x86_64", "linux-glibc-aarch64")) {
        runtimeOnly("dev.opencollab:libdatachannel-java:${libs.versions.libdatachannel.get()}:$arch")
    }
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
            // A classifier jar is named <name>-<version>-<classifier>.jar; it is its own pin (the natives of the NetherNet binding).
            val base = "${id.module}-${id.version}"
            val classifier = artifact.file.name.removePrefix(base).removeSuffix(".jar").removePrefix("-")
            ArtefactPins.Resolved("${id.group}:${id.module}:${id.version}" + if (classifier.isEmpty()) "" else ":$classifier",
                artifact.file.toPath())
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

// The two native libraries of the NetherNet binding, each from its own pinned jar, under the directory the host looks in (ADR-0021).
val prepareNatives = tasks.register<Sync>("prepareNatives") {
    into(layout.buildDirectory.dir("natives"))
    val runtimeJars = configurations.named("runtimeClasspath").map { it.files }
    for ((classifier, dir) in listOf("linux-glibc-x86_64" to "linux-x86_64", "linux-glibc-aarch64" to "linux-aarch64")) {
        from(runtimeJars.map { files -> files.filter { it.name.startsWith("libdatachannel-java-") && it.name.endsWith("-$classifier.jar") }.map { zipTree(it) } }) {
            include("native/libdatachannel-java.so")
            eachFile { path = "native/$dir/libdatachannel-java.so" }
            includeEmptyDirs = false
        }
    }
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
    // tel.schich.libdatachannel is NOT relocated: its `static native` methods are bound by JNI to that exact class name, so a
    // relocated copy could not load the library (ADR-0021, ShadedJarCheck.JNI_PACKAGES). Everything else is.
    for (pkg in listOf("com.fasterxml.jackson", "org.cloudburstmc", "io.netty", "io.airlift", "org.jose4j", "net.jodah",
        "com.nukkitx", "org.slf4j", "it.unimi", "org.bouncycastle", "com.google.gson", "com.google.errorprone")) {
        relocate(pkg, "$own.$pkg")
    }
    mergeServiceFiles()
    // Bouncy Castle ships signed jars; a repacked jar would fail its own signature check.
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    // Both architectures' natives have the same path inside their jars, so they cannot share a jar as they are: the dependency jars'
    // copies are dropped and each is added from prepareNatives under its own architecture directory, which
    // NetherNetTransport.nativeResource selects at run time.
    exclude("native/libdatachannel-java.so")
    from(prepareNatives)
    dependsOn(prepareNatives)
}

val checkShadedJar = tasks.register("checkShadedJar") {
    group = "verification"
    description = "Checks the shaded jar: no unrelocated library class, no snapshot entry."
    val jar = shadowJar.flatMap { it.archiveFile }
    inputs.file(jar)
    doLast {
        val violations = ShadedJarCheck.check(jar.get().asFile.toPath()).toMutableList()
        // The NetherNet natives (ADR-0021): one per architecture, at the path NetherNetTransport.nativeResource names.
        ZipFile(jar.get().asFile).use { zip ->
            for (entry in listOf("native/linux-x86_64/libdatachannel-java.so", "native/linux-aarch64/libdatachannel-java.so")) {
                if (zip.getEntry(entry) == null) violations.add("missing native library $entry")
            }
            if (zip.getEntry("native/libdatachannel-java.so") != null) violations.add("an architecture-less native library is in the jar")
            // The right library under the right name: the ELF header's machine field (62 = x86-64, 183 = AArch64).
            for ((entry, machine) in listOf("native/linux-x86_64/libdatachannel-java.so" to 62, "native/linux-aarch64/libdatachannel-java.so" to 183)) {
                val header = zip.getEntry(entry)?.let { zip.getInputStream(it).use { s -> s.readNBytes(20) } } ?: continue
                val elf = header.size == 20 && header[0] == 0x7f.toByte() && header[1] == 'E'.code.toByte()
                val found = if (elf) (header[18].toInt() and 0xff) or ((header[19].toInt() and 0xff) shl 8) else -1
                if (found != machine) violations.add("$entry is not an ELF machine $machine library (machine $found)")
            }
            // No other native code travels in the jar (a second library would be neither pinned by name nor relocated);
            // libnukkit-natives.so is the one the Cloudburst protocol jar has always carried.
            zip.entries().asSequence().map { it.name }
                .filter { it.endsWith(".so") || it.endsWith(".dll") || it.endsWith(".dylib") || it.endsWith(".jnilib") }
                .filter { it !in setOf("native/linux-x86_64/libdatachannel-java.so", "native/linux-aarch64/libdatachannel-java.so", "libnukkit-natives.so") }
                .forEach { violations.add("unexpected native library $it") }
        }
        if (violations.isNotEmpty()) {
            throw GradleException("checkShadedJar failed:\n" + violations.take(30).joinToString("\n"))
        }
    }
}

tasks.named("check") {
    dependsOn(checkShadedJar)
}
