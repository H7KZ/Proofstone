// Deliberately bad tests (T-M4-03 and later M4 cards): each one must produce its expected verdict. Its tests boot a
// real server, so they need Docker and are not part of `check`. `metaTest` runs each bad test with its switch and
// asserts the verdict (T-M4-07).
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

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
            "proofstone.injectTopologyFault",
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
    }
}

/**
 * One bad test on its own: the class pattern, the verdict its failure must start with, and the switches that make it
 * produce that verdict. Each run is a separate Test task, so the switches never leak into another run.
 */
/**
 * [pin] is the one cell the test's @Matrix names, or null for the rolling row (any paper cell). The pinned tests are the ones
 * whose verdict depends on their cell: the differential driver (26.1.2 only), the window fault and the leak test.
 */
data class BadRun(
    val task: String, val include: String, val verdict: String, val switches: Map<String, String>, val pin: String? = null,
    /** Text the failure must contain (the fault's own reason, its container and hop), so no other red satisfies the run. */
    val mustContain: List<String> = emptyList(),
    /** Text the failure must not contain, for example the join timeout that an injected fault must not be mistaken for. */
    val mustNotContain: List<String> = emptyList(),
    /** A topology run needs the proxy agent jars in the test JVM (T-M7-13). */
    val topology: Boolean = false,
    /** A loader run needs this loader's agent jar in the test JVM and its crashing example mod under build/fixture-mods (T-M8-13). */
    val loader: String? = null,
    /** The failure is the test body's own assertion (a plain FAIL), not a message the harness prefixes with the verdict. */
    val plainFailure: Boolean = false,
    /** The verdict the run's proofstone-report.json must hold for the test, so the report agrees with the XML (T-M8-11). */
    val reportVerdict: String? = null,
)

val badRuns = listOf(
    BadRun("metaVacuous", "**/VacuousE2E*", "VACUOUS", mapOf("proofstone.controlRun" to "true")),
    BadRun("metaFlaky", "**/FlakyE2E*", "FLAKY", mapOf("proofstone.repeat" to "5")),
    BadRun(
        "metaDisagree", "**/differential/DriverFaultDisagreeE2E*", "DISAGREE",
        mapOf("proofstone.differential" to "true", "proofstone.injectDriverFault" to "true"),
        pin = "paper@26.1.2",
    ),
    BadRun("metaInvalid", "**/invalid/InvalidConformanceE2E*", "INVALID", mapOf("proofstone.injectConformanceFault" to "true")),
    BadRun(
        "metaInvalidWindow", "**/invalid/window/**", "INVALID",
        mapOf("proofstone.injectConformanceFault" to "window"),
        pin = "paper@26.1.2",
    ),
    BadRun("metaLeak", "**/leak/**", "LEAK", emptyMap(), pin = "paper@26.1.2"),
    BadRun("metaExpect", "**/expect/**", "FAIL", emptyMap()),
    BadRun("metaRequires", "**/requires/**", "SKIPPED", emptyMap()),
)

// T-M7-13: the topology faults, each on its own cell and with the reason it must give. The pins are the topology cells of
// versions.yml; the plus sign is written as char code 43 because lintVersions reads a literal one in a build file as a
// dynamic version. Not part of metaTest (it boots a proxy and two backends per run): `metaTopologyTest` runs them.
val plus = 43.toChar()
val velocityCell = "velocity@4.3.1${plus}paper@26.3"
val bungeeCell = "bungeecord@2111${plus}paper@26.3"
fun topologyFault(task: String, cell: String, fault: String, vararg reason: String) = BadRun(
    task, "**/topology/**", "INVALID", mapOf("proofstone.injectTopologyFault" to fault), pin = cell,
    mustContain = listOf("INVALID ") + reason, mustNotContain = listOf("timed out joining"), topology = true,
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
val modRuns = listOf(
    BadRun(
        "metaModCrash", "**/mod/BrokenFabricModE2E*", "INVALID", emptyMap(), pin = fabricCell,
        mustContain = listOf("INVALID ", "proofstone_example", "proofstone-crashing-mod.jar", "example mod was asked to fail"),
        mustNotContain = listOf("timed out", "BOOT TIMEOUT", "the body ran"), loader = "fabric", reportVerdict = "INVALID",
    ),
    BadRun(
        "metaModCrashNeoForge", "**/mod/BrokenNeoForgeModE2E*", "INVALID", emptyMap(),
        pin = "neoforge@26.3",
        mustContain = listOf("INVALID ", "proofstone_example", "proofstone-crashing-mod.jar", "example mod was asked to fail"),
        mustNotContain = listOf("timed out", "BOOT TIMEOUT", "the body ran"), loader = "neoforge", reportVerdict = "INVALID",
    ),
    BadRun(
        "metaModDataPack", "**/datapack/**", "INVALID", emptyMap(), pin = paperCell,
        mustContain = listOf("INVALID ", "file/broken-function.zip", "proofstone_broken:hello"),
        mustNotContain = listOf("timed out", "the body ran"), reportVerdict = "INVALID",
    ),
    BadRun(
        "metaModPackHash", "**/resourcepack/**", "FAIL", emptyMap(), pin = paperCell,
        mustContain = listOf("FAILED_DOWNLOAD", "hash mismatch: expected 0000000000000000000000000000000000000000, got "),
        mustNotContain = listOf("timed out", "CONFIGURATION_STALLED", "kicked"), plainFailure = true, reportVerdict = "FAIL",
    ),
)
val allRuns = badRuns + topologyRuns + modRuns

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
            dependsOn(":platforms:${run.loader}:agent:shadowJar", copyCrashingMod.getValue(run.loader))
            systemProperty("proofstone.${run.loader}.agentJar", agent.file("build/libs/agent-all.jar").asFile.absolutePath)
            systemProperty("proofstone.loaderCache", layout.buildDirectory.dir("loader-cache").get().asFile.absolutePath)
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

val badRunReportDirs: Map<String, File> = allRuns.associate { run ->
    run.task to layout.buildDirectory.dir("proofstone-meta/${run.task}").get().asFile
}

// Runs every bad test with its switch and asserts its verdict. It needs Docker and the EULA; run it through
// scripts/dockerlock.sh (examples/bad-tests/README.md). Per run: nothing skipped or errored, at least one test case, and
// every failure starts with the expected verdict, with at least one such failure.
fun registerMeta(taskName: String, taskDescription: String, runs: List<BadRun>, mergedName: String) {
tasks.register(taskName) {
    group = "verification"
    description = taskDescription
    dependsOn(runs.map { it.task })
    // Local copies, so the action captures only these values and not the script.
    val expectations = runs.map { run -> run.task to run.verdict }
    val mustContainByTask = runs.associate { run -> run.task to run.mustContain }
    val mustNotContainByTask = runs.associate { run -> run.task to run.mustNotContain }
    val plainByTask = runs.associate { run -> run.task to run.plainFailure }
    val reportVerdictByTask = runs.associate { run -> run.task to run.reportVerdict }
    val dirsByTask = badRunDirs
    val reportDirsByTask = badRunReportDirs
    val mergedDir = layout.buildDirectory.dir(mergedName).get().asFile
    doLast {
        val problems = mutableListOf<String>()
        val factory = DocumentBuilderFactory.newInstance()
        for ((task, verdict) in expectations) {
            val dir = dirsByTask.getValue(task)
            val reports = dir.listFiles { file -> file.name.startsWith("TEST-") && file.name.endsWith(".xml") }
                .orEmpty()
            if (reports.isEmpty()) {
                problems += "$task: no JUnit XML in $dir, so the run did not happen"
                continue
            }
            var testCases = 0
            var verdictFailures = 0
            for (report in reports) {
                val suite = factory.newDocumentBuilder().parse(report).documentElement
                // Only the SKIPPED task expects skips; its skipped test is checked below, against the report's reason.
                if (verdict != "SKIPPED" && suite.getAttribute("skipped") != "0") {
                    problems += "$task: skipped=${suite.getAttribute("skipped")} in ${report.name} (a skip is not a pass)"
                }
                if (suite.getAttribute("errors") != "0") {
                    problems += "$task: errors=${suite.getAttribute("errors")} in ${report.name}"
                }
                val cases = suite.getElementsByTagName("testcase")
                for (index in 0 until cases.length) {
                    testCases++
                    val case = cases.item(index) as Element
                    if (verdict == "SKIPPED" && case.getElementsByTagName("skipped").item(0) != null) {
                        verdictFailures++
                        continue
                    }
                    val failure = case.getElementsByTagName("failure").item(0) as Element? ?: continue
                    // JUnit prefixes the message with the exception class; the verdict follows it.
                    val message = failure.getAttribute("message").replaceFirst(Regex("^[\\w.$]+: "), "")
                    // JUnit reports the test method's own failure first; a verdict from afterEach (the flake judgement)
                    // is attached to it as a suppressed exception, which the XML keeps in its body.
                    // A plain FAIL (T-M8-11) is the test body's own assertion; only an assertion counts, so a timeout or any
                    // other exception cannot satisfy the run, and the message must still carry the fault's own reason.
                    val plain = plainByTask.getValue(task) && failure.getAttribute("type").endsWith("AssertionFailedError")
                    val named = plain || message.startsWith("$verdict ") ||
                        failure.textContent.contains(Regex("Suppressed: [\\w.$]+: $verdict "))
                    if (named) {
                        verdictFailures++
                        // The failure must give the reason this run was built for, not any other red (T-M7-13).
                        val text = failure.getAttribute("message") + "\n" + failure.textContent
                        for (needle in mustContainByTask.getValue(task)) {
                            if (!text.contains(needle)) {
                                problems += "$task: ${case.getAttribute("name")} is $verdict but its message lacks " +
                                    "\"$needle\": " + message.take(300)
                            }
                        }
                        for (needle in mustNotContainByTask.getValue(task)) {
                            if (text.contains(needle)) {
                                problems += "$task: ${case.getAttribute("name")} is $verdict but its message holds " +
                                    "\"$needle\": a different red from the one this fault must give"
                            }
                        }
                    } else if (task == "metaFlaky" && message.contains("random draw")) {
                        // A flake's own runs fail on their draws; the FLAKY verdict is on the last run, and the draws
                        // are the evidence that the runs differ.
                    } else {
                        problems += "$task: ${case.getAttribute("name")} failed without $verdict: " +
                            message.take(300)
                    }
                }
            }
            if (testCases == 0) {
                problems += "$task: no test case ran"
            }
            if (verdict == "SKIPPED") {
                // The skip must name the missing capability in the proofstone report, not only in JUnit's own XML.
                val file = File(reportDirsByTask.getValue(task), "proofstone-report.json")
                val rows = if (file.isFile) (groovy.json.JsonSlurper().parse(file) as Map<*, *>)["results"] as List<*> else emptyList<Any>()
                val named = rows.filterIsInstance<Map<*, *>>().any { row ->
                    row["verdict"] == "SKIPPED" && row["skipReason"] == "CAPABILITY_MISSING" &&
                        (row["reason"] as String?)?.contains("missing capability FOLIA_REGIONS on cell") == true
                }
                if (!named) {
                    problems += "$task: no SKIPPED report naming the missing capability FOLIA_REGIONS"
                }
                // T-M7-10: a topology test on a single-server cell is skipped with its reason, never run or passed.
                val topology = rows.filterIsInstance<Map<*, *>>().any { row ->
                    row["verdict"] == "SKIPPED" && row["skipReason"] == "CAPABILITY_MISSING" &&
                        (row["reason"] as String?)?.contains("missing capability TOPOLOGY on cell") == true
                }
                if (!topology) {
                    problems += "$task: no SKIPPED report naming the missing capability TOPOLOGY"
                }
            }
            // The report must say what the XML says (T-M8-11): a fault that JUnit shows red must not be green in the report.
            reportVerdictByTask.getValue(task)?.let { wanted ->
                val file = File(reportDirsByTask.getValue(task), "proofstone-report.json")
                val rows = if (file.isFile) (groovy.json.JsonSlurper().parse(file) as Map<*, *>)["results"] as List<*> else emptyList<Any>()
                if (rows.isEmpty() || !rows.filterIsInstance<Map<*, *>>().all { row -> row["verdict"] == wanted }) {
                    problems += "$task: the proofstone report does not hold $wanted for every test: " +
                        rows.filterIsInstance<Map<*, *>>().joinToString { "${it["test"]}=${it["verdict"]}" }
                }
            }
            if (verdictFailures == 0) {
                problems += "$task: no test produced $verdict"
            }
        }
        // One report for the whole meta run (T-M4-09): every task's own results, in the report's cell and test order.
        val results = mutableListOf<Map<String, Any?>>()
        for ((task, _) in expectations) {
            val file = File(reportDirsByTask.getValue(task), "proofstone-report.json")
            if (!file.isFile) {
                problems += "$task: no proofstone-report.json, so its verdicts are missing from the report"
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
