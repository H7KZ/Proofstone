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
    dependsOn(copyFixturePlugin)
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
        listOf("proofstone.controlRun", "proofstone.repeat", "proofstone.differential", "proofstone.injectConformanceFault"),
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
        exclude("**/invalid/**", "**/leak/**", "**/expect/**", "**/requires/**")
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

// The task's own JUnit XML directory, and the expectation, as plain values: the metaTest action must not capture the
// script's objects, because the configuration cache cannot store them.
val badRunDirs: Map<String, File> = badRuns.associate { run ->
    run.task to layout.buildDirectory.dir("test-results/${run.task}").get().asFile
}
val badRunExpectations: List<Pair<String, String>> = badRuns.map { run -> run.task to run.verdict }

badRuns.forEach { run ->
    tasks.register<Test>(run.task) {
        badTestSetup(run.pin)
        group = "verification"
        description = "Runs the bad test that must produce ${run.verdict}. Part of metaTest; not a user suite."
        include(run.include)
        run.switches.forEach { (name, value) -> systemProperty(name, value) }
        // A bad test fails on purpose: metaTest judges its failure, so Gradle does not fail the build here.
        ignoreFailures = true
        reports.junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/${run.task}"))
        // Each task is its own JVM, so each writes its own proofstone-report.json; metaTest merges them (T-M4-09).
        val reportDir = layout.buildDirectory.dir("proofstone-meta/${run.task}").get().asFile
        systemProperty("proofstone.reportDir", reportDir.absolutePath)
        doFirst { reportDir.deleteRecursively() }
    }
}

val badRunReportDirs: Map<String, File> = badRuns.associate { run ->
    run.task to layout.buildDirectory.dir("proofstone-meta/${run.task}").get().asFile
}

// Runs every bad test with its switch and asserts its verdict. It needs Docker and the EULA; run it through
// scripts/dockerlock.sh (examples/bad-tests/README.md). Per run: nothing skipped or errored, at least one test case, and
// every failure starts with the expected verdict, with at least one such failure.
tasks.register("metaTest") {
    group = "verification"
    description = "Asserts that every bad test produces its expected verdict (vacuous, flaky, disagree, invalid, leak)."
    dependsOn(badRuns.map { it.task })
    // Local copies, so the action captures only these values and not the script.
    val expectations = badRunExpectations
    val dirsByTask = badRunDirs
    val reportDirsByTask = badRunReportDirs
    val mergedDir = layout.buildDirectory.dir("proofstone").get().asFile
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
                    val named = message.startsWith("$verdict ") ||
                        failure.textContent.contains(Regex("Suppressed: [\\w.$]+: $verdict "))
                    if (named) {
                        verdictFailures++
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
        println("metaTest: merged report " + mergedFile)
        if (problems.isNotEmpty()) {
            throw GradleException("metaTest is red:\n" + problems.joinToString("\n"))
        }
        println("metaTest: every bad test produced its expected verdict (" +
            expectations.joinToString { it.second } + ")")
    }
}
