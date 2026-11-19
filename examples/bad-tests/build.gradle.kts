// Deliberately bad tests (T-M4-03 and later M4 cards): each one must produce its expected verdict. Its tests boot a
// real server, so they need Docker and are not part of `check`. `metaTest` runs each bad test with its switch and
// asserts the verdict (T-M4-07).
import io.github.h7kz.proofstone.build.meta.MetaCheck

plugins {
    id("proofstone.java-host")
}

dependencies {
    testImplementation(project(":proofstone-api"))
    // Registers the JUnit extension through its service file.
    testRuntimeOnly(project(":proofstone-core"))
    // The mcpl client driver, found through ServiceLoader. It needs Java 21+, hence the toolchain below.
    testRuntimeOnly(project(":drivers:mcpl"))
    // The reference driver (ADR-0004), found through ServiceLoader for differential runs (T-M4-06) only.
    testRuntimeOnly(project(":drivers:mineflayer-ref"))
}

// MCProtocolLib is Java 21+ only, and the driver runs in the test JVM (ADR-0004).
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// The java plugin makes `check` run `test`. This test needs Docker, so only archTest stays in `check`.
tasks.named("check") {
    setDependsOn(listOf("archTest"))
}

// The fixture plugin (platforms/bukkit/fixture) is the plugin under test. It is built, never committed, and copied
// under this project because plugin globs are relative to the project directory.
val fixturePlugins = layout.buildDirectory.dir("fixture-plugins")
val copyFixturePlugin = tasks.register<Copy>("copyFixturePlugin") {
    dependsOn(":platforms:bukkit:fixture:jar")
    from(rootProject.layout.projectDirectory.file("platforms/bukkit/fixture/build/libs/proofstone-fixture.jar"))
    into(fixturePlugins)
}

val agentJar = rootProject.layout.projectDirectory.file("platforms/bukkit/agent/build/libs/agent-all.jar")

// The resource pack of the wrong-hash bad test (T-M8-11), zipped from its source, never committed as a binary.
val zipWrongHashPack = tasks.register<Zip>("zipWrongHashPack") {
    from(layout.projectDirectory.dir("src/test/resourcepacks/wrong-hash"))
    archiveFileName.set("wrong-hash.zip")
    destinationDirectory.set(layout.buildDirectory.dir("resourcepacks"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

/** Copies each named `-D` property from the Gradle command line into the test JVM, when it is set there. */
fun Test.forwardCommandLineProperties(names: List<String>) {
    for (name in names) {
        providers.systemProperty(name).orNull?.let { systemProperty(name, it) }
    }
}

/**
 * What every run of a bad test needs: the test classes, the fixture, the agent jar and the repo root. The cell selector
 * and the scope reach every run (T-M6-09), so `-Dproofstone.cells=<cell>` picks the cell for all of them.
 */
fun Test.badTestSetup(pinnedCell: String? = null) {
    // A Test task made by hand gets no test classes from the java plugin, so they are set here (as conformance does).
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    dependsOn(copyFixturePlugin, zipWrongHashPack)
    // The agent jar is built, never committed (AGENTS.md §3).
    dependsOn(":platforms:bukkit:agent:shadowJar")
    systemProperty("proofstone.repoRoot", rootProject.projectDir.absolutePath)
    systemProperty("proofstone.agentJar", agentJar.asFile.absolutePath)
    forwardCommandLineProperties(listOf("proofstone.cells", "proofstone.scope"))
    // A bad test whose @Matrix names one version is pinned to that cell (the selector replaces @Matrix, so a run on
    // any other cell would be a different test with a different verdict). With no selector it runs its pin; a selector
    // naming any other cell is refused in doFirst, before any server starts (T-M6-09).
    if (pinnedCell != null && providers.systemProperty("proofstone.cells").orNull == null) {
        systemProperty("proofstone.cells", pinnedCell)
    }
    // A differential run needs an explicit native cell: the reference driver has one row (paper@26.1.2). Refuse before
    // any Docker run starts, rather than let the run pick a cell that the reference driver cannot drive.
    doFirst {
        val task = this as Test
        val props = task.systemProperties
        val selected = props["proofstone.cells"]?.toString()
        if (pinnedCell != null && selected != null && selected.split(",").map { it.trim() }.any { it != pinnedCell }) {
            throw GradleException(
                "${task.name} cannot run on -Dproofstone.cells=$selected. Its @Matrix pins it to $pinnedCell, and no " +
                    "other cell gives its verdict. Supported cells: $pinnedCell. Run it with -Dproofstone.cells=$pinnedCell " +
                    "or without the selector (examples/bad-tests/README.md).",
            )
        }
        if (props["proofstone.differential"] == "true" && selected == null) {
            throw GradleException(
                "A differential run needs an explicit native cell. Add -Dproofstone.cells=paper@26.1.2 " +
                    "(the reference driver's row, examples/bad-tests/README.md).",
            )
        }
    }
    // The result depends on Docker, so always run when asked. A skipped test shows its reason, never a pass.
    outputs.upToDateWhen { false }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

tasks.test {
    badTestSetup()
    // The control-run, flake-detector repeat count and differential switches reach the test JVM from the Gradle command
    // line (self-tests sections 2, 3 and 4). The cell selector and scope come from badTestSetup, as for every run.
    // injectConformanceFault is forwarded too, so the rerun.txt of an INVALID bundle reproduces from the command line.
    forwardCommandLineProperties(
        listOf(
            "proofstone.controlRun", "proofstone.repeat", "proofstone.differential", "proofstone.injectConformanceFault",
            "proofstone.injectTopologyFault", "proofstone.injectGeyserFault",
        ),
    )
    // The differential bad test (T-M4-06) only means something in a differential run, where its reference driver is
    // faulted. A differential run runs only that package (the other bad tests have no cell on the native row), and
    // every other run leaves it out, so it never shows up in a normal bad-tests suite.
    val differential = providers.systemProperty("proofstone.differential").orNull == "true"
    // A rerun.txt command names its test with --tests. That explicit filter selects the bad test even when the whole task
    // excludes its package, so the printed command reproduces the verdict (T-M4-09).
    val explicitTests = gradle.startParameter.taskRequests.flatMap { it.args }.any { it == "--tests" }
    if (differential) {
        include("**/differential/**")
        systemProperty("proofstone.injectDriverFault", "true")
    } else if (!explicitTests) {
        exclude("**/differential/**")
    }
    // The invalid, leak and expect bad tests (T-M4-07, T-M4-15) fail on purpose in every run, so only metaTest runs them.
    // The requires bad test (T-M5-01) skips on purpose, so only metaTest runs it too.
    if (!explicitTests) {
        // The topology fault test (T-M7-13) is INVALID by design too, and boots three containers: metaTopology* only.
        exclude("**/invalid/**", "**/leak/**", "**/expect/**", "**/requires/**", "**/topology/**")
        // T-M8-11: the mod, data-pack and resource-pack faults are red by design too; metaModTest only.
        exclude("**/mod/**", "**/datapack/**", "**/resourcepack/**")
        // T-M9-11: the Geyser faults are INVALID by design and boot a four-container cell: metaBedrockTest only.
        exclude("**/bedrock/**")
    }
}

/**
 * One bad test's expectation (T-M4-13): checked on its own by MetaCheck, from the run's proofstone-report.json, its JUnit XML
 * and its repro bundles. [test] is the report's name without the package, `Class.method`.
 */
data class BadTest(
    val test: String,
    val verdict: String,
    /** Text the report's reason must contain (the fault's own reason). */
    val reasonHas: List<String> = emptyList(),
    /** Text the reason must not contain, for example the join timeout that an injected fault must not be mistaken for. */
    val reasonLacks: List<String> = emptyList(),
    /** Bundle directories the run must have written for this test, relative to build/proofstone/<cell>/ (BundleWriter). */
    val bundles: List<String> = emptyList(),
    /** JUnit testcases this test makes: a flake run makes one per repeat, a differential run one per driver row. */
    val testcases: Int = 1,
    /** One entry per JUnit failure of this test, all its texts in one failure, most specific first. No failure may go unclaimed. */
    val xml: List<List<String>> = emptyList(),
    /** Text that no failure of this test may hold (a different red from the one this fault must give). */
    val xmlLacks: List<String> = emptyList(),
    val skipReason: String? = null,
)

/** A red test whose failure is the harness's own message, `VERDICT Class.method on cell: reason` (VerdictGate). */
fun red(test: String, verdict: String, reasonHas: List<String> = emptyList(), bundle: Boolean = false) = BadTest(
    test, verdict, reasonHas = reasonHas, bundles = if (bundle) listOf(test) else emptyList(),
    xml = listOf(listOf("$verdict $test")),
)

/** A test that must be green: no failure, no bundle (a green single run writes none). */
fun green(test: String) = BadTest(test, "PASS")

/**
 * One bad test class on its own: the class pattern, the switches that make it produce its verdict, and what each of its tests
 * must give. Each run is a separate Test task, so the switches never leak into another run.
 * [pin] is the one cell the test's @Matrix names, or null for the rolling row (any paper cell). The pinned tests are the ones
 * whose verdict depends on their cell: the differential driver (26.1.2 only), the window fault and the leak test.
 */
data class BadRun(
    val task: String, val include: String, val verdict: String, val switches: Map<String, String>, val pin: String? = null,
    /** Every test the run's classes hold, each judged on its own (T-M4-13). */
    val tests: List<BadTest> = emptyList(),
    /** A topology run needs the proxy agent jars in the test JVM (T-M7-13). */
    val topology: Boolean = false,
    /** A loader run needs this loader's agent jar in the test JVM and its crashing example mod under build/fixture-mods (T-M8-13). */
    val loader: String? = null,
    /** The run declares the crashing example mod (T-M8-13); a loader run that needs no mod (the leak test, T-M8-18) does not build it. */
    val crashingMod: Boolean = loader != null,
    /** A Geyser run needs the Bedrock driver host jar and its pinned-jar cache in the test JVM (T-M9-11). */
    val bedrock: Boolean = false,
)

val vacuousTest = "VacuousE2E.aFreshPlayerHasNoExperienceLevel"
val flakyTest = "FlakyE2E.theOutcomeOfThisRunIsRandom"
val disagreeTest = "DriverFaultDisagreeE2E.theDriversDisagreeOnTheDamageOfAGivenSword"
val windowOne = "WindowConformanceInvalidE2E.aWindowBrokenCellGivesNoPluginVerdictForThisTest"
val windowTwo = "WindowConformanceInvalidE2E.aSecondTestOnTheSameWindowBrokenCellIsInvalidToo"
val expectWrong = "ExpectFailsE2E.theWrongExpectationFailsTheTest"
val leakTest = "LeakE2E.leavesTheRandomTickSpeedChangedBehind"

val badRuns = listOf(
    BadRun(
        "metaVacuous", "**/VacuousE2E*", "VACUOUS", mapOf("proofstone.controlRun" to "true"),
        tests = listOf(red(vacuousTest, "VACUOUS", listOf("passes without the plugin"), bundle = true)),
    ),
    // Seed 3 draws pass, pass, fail, pass, fail: repeats 1, 2 and 4 are green, repeat 3 fails on its draw and repeat 5 carries the
    // FLAKY verdict (and its own failed draw). Each failure is claimed by name, so the draw is no longer exempt (T-M4-13).
    BadRun(
        "metaFlaky", "**/FlakyE2E*", "FLAKY", mapOf("proofstone.repeat" to "5"),
        tests = listOf(
            BadTest(
                flakyTest, "FLAKY", reasonHas = listOf("verdicts differ across 5 runs: PASS, FAIL"),
                bundles = (1..5).map { "$flakyTest.r$it" }, testcases = 5,
                xml = listOf(
                    listOf("FLAKY $flakyTest on ", "over 5 runs: verdicts differ across 5 runs"),
                    listOf("this run's random draw"),
                ),
            ),
        ),
    ),
    BadRun(
        "metaDisagree", "**/differential/DriverFaultDisagreeE2E*", "DISAGREE",
        mapOf("proofstone.differential" to "true", "proofstone.injectDriverFault" to "true"),
        pin = "paper@26.1.2",
        tests = listOf(
            green("$disagreeTest [mcpl]"),
            BadTest(
                "$disagreeTest [mineflayer-ref]", "DISAGREE", reasonHas = listOf("client and reference driver observations differ"),
                bundles = listOf("$disagreeTest/mineflayer-ref"), xml = listOf(listOf("DISAGREE $disagreeTest on ")),
            ),
        ),
    ),
    BadRun(
        "metaInvalid", "**/invalid/InvalidConformanceE2E*", "INVALID", mapOf("proofstone.injectConformanceFault" to "true"),
        tests = listOf(
            red(
                "InvalidConformanceE2E.aCellWhoseConformanceFailsGivesNoPluginVerdict", "INVALID",
                listOf("cell conformance failed: `list` did not print \"PROOFSTONE-CONFORMANCE-FAULT\""), bundle = true,
            ),
        ),
    ),
    BadRun(
        "metaInvalidWindow", "**/invalid/window/**", "INVALID",
        mapOf("proofstone.injectConformanceFault" to "window"),
        pin = "paper@26.1.2",
        tests = listOf(windowOne, windowTwo).map {
            red(it, "INVALID", listOf("cell conformance failed: C04 failed: chest window type"), bundle = true)
        },
    ),
    BadRun(
        "metaLeak", "**/leak/LeakE2E*", "LEAK", emptyMap(), pin = "paper@26.1.2",
        // The leak is a failure, not a verdict (self-tests section 4b): the report holds FAIL, and the reason starts with LEAK.
        tests = listOf(
            BadTest(
                leakTest, "FAIL", reasonHas = listOf("LEAK $leakTest on ", "random_tick_speed"), bundles = listOf(leakTest),
                xml = listOf(listOf("LEAK $leakTest on ")),
            ),
        ),
    ),
    BadRun(
        "metaExpect", "**/expect/**", "FAIL", emptyMap(),
        tests = listOf(
            BadTest(
                expectWrong, "FAIL", reasonHas = listOf("FoliaRegionsOnly"), bundles = listOf(expectWrong),
                xml = listOf(listOf("FAIL expect ", "ExpectFailsE2E\$FoliaRegionsOnly")),
            ),
            green("ExpectFailsE2E.aHoldingExpectationLeavesThePass"),
        ),
    ),
    BadRun(
        "metaRequires", "**/requires/**", "SKIPPED", emptyMap(),
        tests = listOf(
            BadTest(
                "RequiresSkipsE2E.skippedWithoutFolia", "SKIPPED", reasonHas = listOf("missing capability FOLIA_REGIONS on cell"),
                skipReason = "CAPABILITY_MISSING",
            ),
            green("RequiresSkipsE2E.runsWithHeldCapabilities"),
            // T-M7-10: a topology test on a single-server cell is skipped with its reason, never run or passed.
            BadTest(
                "TopologySkipsE2E.skippedOnASingleServerCell", "SKIPPED", reasonHas = listOf("missing capability TOPOLOGY on cell"),
                skipReason = "CAPABILITY_MISSING",
            ),
        ),
    ),
)

// T-M7-13: the topology faults, each on its own cell and with the reason it must give. The pins are the topology cells of
// versions.yml; the plus sign is written as char code 43 because lintVersions reads a literal one in a build file as a
// dynamic version. Not part of metaTest (it boots a proxy and two backends per run): `metaTopologyTest` runs them.
val plus = 43.toChar()
val velocityCell = "velocity@4.3.1${plus}paper@26.3"
val bungeeCell = "bungeecord@2111${plus}paper@26.3"
fun topologyFault(task: String, cell: String, fault: String, vararg reason: String) = BadRun(
    task, "**/topology/**", "INVALID", mapOf("proofstone.injectTopologyFault" to fault), pin = cell, topology = true,
    tests = listOf(
        BadTest(
            "TopologyFaultE2E.aTestOnABrokenTopologyNeverRuns", "INVALID",
            xml = listOf(listOf("INVALID ") + reason), xmlLacks = listOf("timed out joining"),
        ),
    ),
)

val topologyRuns = listOf(
    topologyFault(
        "metaTopoVelocityWrongSecret", velocityCell, "wrong-secret",
        "P01 failed", "Unable to verify player details", "hop: proxy to backend lobby", "server-lobby log tail",
    ),
    topologyFault(
        "metaTopoVelocityForwardingOff", velocityCell, "forwarding-off",
        "P01 failed", "This server requires you to connect with Velocity", "hop: proxy to backend lobby",
    ),
    topologyFault(
        "metaTopoVelocityBackendForwardingOff", velocityCell, "backend-forwarding-off",
        "P01 failed", "Your server did not send a forwarding request to the proxy", "hop: proxy to backend lobby",
        "server-lobby log tail",
    ),
    topologyFault(
        "metaTopoVelocityUnreachable", velocityCell, "unreachable-backend",
        "T00 proxy lists every backend online failed", "backend survival is offline at the proxy",
        "container server-survival", "hop: proxy to backend survival",
    ),
    topologyFault(
        "metaTopoBungeeForwardingOff", bungeeCell, "forwarding-off",
        "P01 failed", "If you wish to use IP forwarding", "hop: proxy to backend lobby",
    ),
    topologyFault(
        "metaTopoBungeeBackendForwardingOff", bungeeCell, "backend-forwarding-off",
        "P01 failed", "Unknown data in login hostname", "hop: proxy to backend lobby", "server-lobby log tail",
    ),
    topologyFault(
        "metaTopoBungeeUnreachable", bungeeCell, "unreachable-backend",
        "T00 proxy lists every backend online failed", "backend survival is offline at the proxy",
        "container server-survival", "hop: proxy to backend survival",
    ),
)
// T-M8-11: the M8 negative controls, each on its own cell and with the reason it must give. Not part of metaTest: the mod run
// needs the Fabric agent (a Java 25 toolchain and a build-time download), which metaTest must not depend on. `metaModTest`.
val fabricCell = "fabric@26.3"
val paperCell = "paper@26.3"
val crashTest = "aModThatCrashesAtLoadNeverGivesAVerdict"
val crashNeedles = listOf("INVALID ", "proofstone_example", "proofstone-crashing-mod.jar", "example mod was asked to fail")
val modRuns = listOf(
    BadRun(
        "metaModCrash", "**/mod/BrokenFabricModE2E*", "INVALID", emptyMap(), pin = fabricCell, loader = "fabric",
        tests = listOf(
            BadTest(
                "BrokenFabricModE2E.$crashTest", "INVALID", xml = listOf(crashNeedles),
                xmlLacks = listOf("timed out", "BOOT TIMEOUT", "the body ran"),
            ),
        ),
    ),
    // T-M8-18: the leak detector on a loader cell. The rule is the Paper leak test's (robustness R6), read back through the Fabric
    // agent's console; the failure is a plain AssertionFailedError starting LEAK, never a boot or join timeout.
    BadRun(
        "metaLeakFabric", "**/leak/loader/LoaderLeakE2E*", "LEAK", emptyMap(), pin = fabricCell, loader = "fabric",
        crashingMod = false,
        tests = listOf(
            BadTest(
                "LoaderLeakE2E.leavesTheRandomTickSpeedChangedBehind", "FAIL",
                xml = listOf(
                    listOf(
                        "LEAK LoaderLeakE2E.leavesTheRandomTickSpeedChangedBehind on fabric@26.3", "random_tick_speed: ",
                        "is currently set to 7",
                    ),
                ),
                xmlLacks = listOf("timed out", "BOOT TIMEOUT", "INVALID "),
            ),
        ),
    ),
    BadRun(
        "metaModCrashNeoForge", "**/mod/BrokenNeoForgeModE2E*", "INVALID", emptyMap(), pin = "neoforge@26.3", loader = "neoforge",
        tests = listOf(
            BadTest(
                "BrokenNeoForgeModE2E.$crashTest", "INVALID", xml = listOf(crashNeedles),
                xmlLacks = listOf("timed out", "BOOT TIMEOUT", "the body ran"),
            ),
        ),
    ),
    BadRun(
        "metaModDataPack", "**/datapack/**", "INVALID", emptyMap(), pin = paperCell,
        tests = listOf(
            BadTest(
                "BrokenFunctionE2E.aDataPackWithABrokenFunctionNeverGivesAVerdict", "INVALID",
                xml = listOf(listOf("INVALID ", "file/broken-function.zip", "proofstone_broken:hello")),
                xmlLacks = listOf("timed out", "the body ran"),
            ),
        ),
    ),
    // A plain FAIL is the test body's own assertion: only an assertion counts, so a timeout or any other exception cannot satisfy
    // the run, and the message must still carry the fault's own reason.
    BadRun(
        "metaModPackHash", "**/resourcepack/**", "FAIL", emptyMap(), pin = paperCell,
        tests = listOf(
            BadTest(
                "WrongHashPackE2E.aPackThatFailsItsHashCheckIsNotLoaded", "FAIL",
                xml = listOf(
                    listOf(
                        "AssertionFailedError", "FAILED_DOWNLOAD",
                        "hash mismatch: expected 0000000000000000000000000000000000000000, got ",
                    ),
                ),
                xmlLacks = listOf("timed out", "CONFIGURATION_STALLED", "kicked"),
            ),
        ),
    ),
)
// T-M9-11: the Bedrock negative controls on the Geyser row, each with the reason it must give. Not part of metaTest: a Geyser cell is
// four containers and a pinned download. `metaBedrockTest`. The refused protocol (T-M10-05) needs the driver host's default-off
// -Dproofstone.bedrock.protocolOverride, which only that fault sets (docs/platforms/geyser.md, "Negative controls").
val geyserCell = "geyser@2.12.1${plus}paper@26.3"
fun bedrockFault(task: String, fault: String, vararg reason: String) = BadRun(
    task, "**/bedrock/**", "INVALID", mapOf("proofstone.injectGeyserFault" to fault), pin = geyserCell, bedrock = true,
    tests = listOf(
        BadTest(
            "BedrockFaultE2E.aTestOnABrokenGeyserCellNeverRuns", "INVALID", xml = listOf(listOf("INVALID ") + reason),
            xmlLacks = listOf("the body ran", "timed out joining", "BOOT TIMEOUT"),
        ),
    ),
)
val bedrockRuns = listOf(
    bedrockFault(
        "metaBedrockStandInAbsent", "stand-in-absent",
        "cell conformance failed", "the discovery stand-in answered none of the two expected names",
        "client.discovery.minecraft-services.net", "hop: server and driver host to the stand-in",
        "Unable to fetch discovery data from https://client.discovery.minecraft-services.net",
    ),
    bedrockFault(
        "metaBedrockKeyMismatch", "key-mismatch",
        "cell conformance failed", "Floodgate refused the data Geyser sent", "hop: Geyser to Floodgate on the server",
        "Please connect through the official Geyser",
    ),
    bedrockFault(
        "metaBedrockRefusedProtocol", "refused-protocol",
        "cell conformance failed", "Geyser refused the Bedrock protocol the driver sent",
        "hop: driver host to Geyser on the server (protocol number)",
        "Outdated Geyser proxy! This server supports the following Bedrock versions", "at join stage NETWORK_SETTINGS",
    ),
)
val allRuns = badRuns + topologyRuns + modRuns + bedrockRuns

// The task's own JUnit XML directory, and the expectation, as plain values: the metaTest action must not capture the
// script's objects, because the configuration cache cannot store them.
val badRunDirs: Map<String, File> = allRuns.associate { run ->
    run.task to layout.buildDirectory.dir("test-results/${run.task}").get().asFile
}

// The crashing example mod of each loader row (T-M8-13), copied under this project because mod globs are relative to the
// project directory, as plugin globs are. One folder per loader, so two runs in one build never share a path.
val copyCrashingMod: Map<String, TaskProvider<Copy>> = listOf("fabric", "neoforge").associateWith { loader ->
    tasks.register<Copy>("copyCrashingMod${loader.replaceFirstChar { it.uppercase() }}") {
        dependsOn(":platforms:$loader:agent:exampleCrashModJar")
        from(rootProject.layout.projectDirectory.file("platforms/$loader/agent/build/example-mod/proofstone-crashing-mod.jar"))
        into(layout.buildDirectory.dir("fixture-mods/$loader"))
    }
}

allRuns.forEach { run ->
    tasks.register<Test>(run.task) {
        badTestSetup(run.pin)
        group = "verification"
        description = "Runs the bad test that must produce ${run.verdict}. Part of metaTest; not a user suite."
        include(run.include)
        run.switches.forEach { (name, value) -> systemProperty(name, value) }
        if (run.loader != null) {
            // The loader agent and the crashing example mod are built, never committed (AGENTS.md section 3). Built only for this
            // run: they need a Java 25 toolchain and a build-time download that no Paper run must pay for or depend on. The mod
            // is the test's own @Proofstone(mods) declaration, copied to the path the test names (T-M8-13).
            val agent = rootProject.layout.projectDirectory.dir("platforms/${run.loader}/agent")
            dependsOn(":platforms:${run.loader}:agent:shadowJar")
            if (run.crashingMod) {
                dependsOn(copyCrashingMod.getValue(run.loader))
            }
            systemProperty("proofstone.${run.loader}.agentJar", agent.file("build/libs/agent-all.jar").asFile.absolutePath)
            systemProperty("proofstone.loaderCache", layout.buildDirectory.dir("loader-cache").get().asFile.absolutePath)
        }
        if (run.bedrock) {
            // The Bedrock driver host jar is built, never committed (AGENTS.md section 3); the pinned Geyser and Floodgate jars are
            // downloaded and checked by sha256 into the cache (ADR-0018 section 8).
            dependsOn(":drivers:bedrock:shadowJar")
            systemProperty("proofstone.bedrockHostJar",
                rootProject.layout.projectDirectory.file("drivers/bedrock/build/libs/bedrock-host-all.jar").asFile.absolutePath)
            systemProperty("proofstone.geyserDownloads",
                rootProject.layout.projectDirectory.dir("build/geyser-downloads").asFile.absolutePath)
        }
        if (run.topology) {
            // The proxy agents are built, never committed (AGENTS.md section 3); the host finds them by these properties.
            dependsOn(":platforms:velocity:agent:shadowJar", ":platforms:bungee:agent:shadowJar")
            systemProperty("proofstone.velocity.agentJar",
                rootProject.layout.projectDirectory.file("platforms/velocity/agent/build/libs/agent-all.jar").asFile.absolutePath)
            systemProperty("proofstone.bungee.agentJar",
                rootProject.layout.projectDirectory.file("platforms/bungee/agent/build/libs/agent-all.jar").asFile.absolutePath)
        }
        // A bad test fails on purpose: metaTest judges its failure, so Gradle does not fail the build here.
        ignoreFailures = true
        reports.junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/${run.task}"))
        // Each task is its own JVM, so each writes its own proofstone-report.json; metaTest merges them (T-M4-09).
        val reportDir = layout.buildDirectory.dir("proofstone-meta/${run.task}").get().asFile
        systemProperty("proofstone.reportDir", reportDir.absolutePath)
        doFirst { reportDir.deleteRecursively() }
    }
}

// T-M4-13: the run tasks never overlap. AGENTS.md section 4 allows one Docker run at a time, and one `gradlew` invocation is one
// run, so its Testcontainers boots must not run side by side either. T-M4-08's metaTest overlapped five of them (suite starts
// within 0.11 s) with gradle.properties' org.gradle.parallel=true. Each run waits for every earlier one that is in the graph,
// not only its neighbour, so the order holds when a meta task selects a subset.
allRuns.forEachIndexed { index, run ->
    tasks.named(run.task) { mustRunAfter(allRuns.take(index).map { it.task }) }
}

val badRunReportDirs: Map<String, File> = allRuns.associate { run ->
    run.task to layout.buildDirectory.dir("proofstone-meta/${run.task}").get().asFile
}

// Runs every bad test with its switch and judges each test on its own (T-M4-13, MetaCheck): its verdict in the proofstone report,
// its failure in the JUnit XML, its repro bundle, and no skip, failure or report row that no expectation names. It needs Docker
// and the EULA; run it through scripts/dockerlock.sh (examples/bad-tests/README.md).
fun registerMeta(taskName: String, taskDescription: String, runs: List<BadRun>, mergedName: String) {
tasks.register(taskName) {
    group = "verification"
    description = taskDescription
    dependsOn(runs.map { it.task })
    // Local copies, so the action captures only these values (strings and files) and not the script.
    val expectations = runs.map { run -> run.task to run.verdict }
    // The expectations travel as JSON text, which the configuration cache stores as it is; MetaCheck.parse reads them back.
    val expectedByTask = runs.associate { run ->
        run.task to groovy.json.JsonOutput.toJson(
            run.tests.map { t ->
                mapOf(
                    "test" to t.test, "verdict" to t.verdict, "reasonHas" to t.reasonHas, "reasonLacks" to t.reasonLacks,
                    "bundles" to t.bundles, "testcases" to t.testcases, "xmlFailures" to t.xml, "xmlLacks" to t.xmlLacks,
                    "skipReason" to t.skipReason,
                )
            },
        )
    }
    val dirsByTask = badRunDirs
    val reportDirsByTask = badRunReportDirs
    // BundleWriter writes build/proofstone/<cell>/<test> under the test's working directory, which is this project's.
    val bundleRoot = layout.buildDirectory.dir("proofstone").get().asFile
    val mergedDir = layout.buildDirectory.dir(mergedName).get().asFile
    doLast {
        val problems = mutableListOf<String>()
        for ((task, _) in expectations) {
            problems += MetaCheck.check(
                task, dirsByTask.getValue(task).toPath(), reportDirsByTask.getValue(task).toPath(), bundleRoot.toPath(),
                MetaCheck.parse(expectedByTask.getValue(task)),
            )
        }
        // One report for the whole meta run (T-M4-09): every task's own results, in the report's cell and test order.
        val results = mutableListOf<Map<String, Any?>>()
        for ((task, _) in expectations) {
            val file = File(reportDirsByTask.getValue(task), "proofstone-report.json")
            if (!file.isFile) {
                continue
            }
            val parsed = groovy.json.JsonSlurper().parse(file) as Map<*, *>
            @Suppress("UNCHECKED_CAST")
            results += (parsed["results"] as List<Map<String, Any?>>)
        }
        val sorted = results.sortedWith(compareBy<Map<String, Any?>>({ it["cell"] as String }, { it["test"] as String }))
        val merged = linkedMapOf<String, Any?>(
            "schema" to "proofstone-report/1",
            "green" to (sorted.isNotEmpty() && sorted.all { it["green"] == true }),
            "summary" to listOf("PASS", "FAIL", "INVALID", "VACUOUS", "FLAKY", "DISAGREE", "SKIPPED")
                .associateWith { verdict -> sorted.count { it["verdict"] == verdict } },
            "results" to sorted,
        )
        val mergedFile = File(mergedDir, "proofstone-report.json")
        mergedFile.parentFile.mkdirs()
        mergedFile.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(merged)) + "\n")
        println("$taskName: merged report " + mergedFile)
        if (problems.isNotEmpty()) {
            throw GradleException("$taskName is red:\n" + problems.joinToString("\n"))
        }
        println("$taskName: every bad test produced its expected verdict (" +
            expectations.joinToString { it.second } + ")")
    }
}
}

registerMeta(
    "metaTest", "Asserts that every bad test produces its expected verdict (vacuous, flaky, disagree, invalid, leak).",
    badRuns, "proofstone",
)
// T-M7-13: every injected topology fault must be INVALID with its own reason. A fault that passes is red here.
registerMeta(
    "metaTopologyTest",
    "Asserts that every injected topology fault (wrong secret, forwarding off, unreachable backend) is INVALID with " +
        "its container and hop in the message. Boots a proxy and two backends per run; needs Docker and the EULA.",
    topologyRuns, "proofstone-topology",
)
// T-M8-11: the broken mod, the data pack with a broken function and the resource pack with a wrong hash, each with its own reason.
registerMeta(
    "metaModTest",
    "Asserts that a mod that crashes at load, a data pack with a broken function and a resource pack with a wrong hash each " +
        "give their own verdict and reason. The mod run boots a Fabric cell; needs Docker and the EULA.",
    modRuns, "proofstone-mod",
)
// One cell per invocation (the M8 flake rule): the same checks per cell, so each counted run judges its own fault.
registerMeta("metaModFabricTest", "metaModTest for the Fabric cell only.", modRuns.filter { it.pin == fabricCell }, "proofstone-mod-fabric")
registerMeta("metaModNeoForgeTest", "metaModTest for the NeoForge cell only.", modRuns.filter { it.pin == "neoforge@26.3" }, "proofstone-mod-neoforge")
registerMeta("metaModPaperTest", "metaModTest for the Paper cell only (data pack and resource pack).", modRuns.filter { it.pin == paperCell }, "proofstone-mod-paper")
// T-M9-11: the Bedrock faults (stand-in absent, Floodgate key mismatch, refused protocol), each INVALID with its own reason and hop. A fault that passes is red here.
registerMeta(
    "metaBedrockTest",
    "Asserts that a Geyser cell with its discovery stand-in absent, one whose Floodgate key does not match, and a driver that sends a protocol Geyser refuses are each INVALID " +
        "with their own reason and hop, never a join timeout. Boots a Geyser cell per run; needs Docker and the EULA.",
    bedrockRuns, "proofstone-bedrock",
)
