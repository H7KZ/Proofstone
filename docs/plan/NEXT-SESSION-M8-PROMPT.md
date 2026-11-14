# Next session prompt: M8 (mods and data)

Paste everything below the line into a new session.

---

Continue the Proofstone project (repo C:\Users\honzi\Documents\GitHub\Proofstone, GitHub H7KZ/Proofstone, branch main).

STATE OF MAIN: origin/main has M0 to M7 merged (PRs up to #112, plus the T-M7-99 review PR once merged). The M7 review
(`docs/plan/ROADMAP.md`, "M7 review (T-M7-99)", 2026-11-14) re-scores the M7 exit criterion from runs and gives the verdict.
Velocity and BungeeCord topology rows are `required` (promoted in PR card/M7-followups, 2026-11-14). Nothing is published or tagged. There is NO CI and no workflow
(maintainer decision 2026-11-08). The public 0.1.0 claim is still NO-GO for the reasons in the M6 review addendum.

DECIDED (do not ask again):
- Licence Apache-2.0 + DCO + NOTICE (ADR-0011). Namespace `io.github.h7kz.proofstone` (T-M6-12); Central verification open (ADR-0012).
- Maven 3.9.9 at `C:/Users/honzi/tools/apache-maven-3.9.9` (`MAVEN_HOME`), Maven runs on JDK 26 (`MVN_JAVA_HOME`), Gradle on JDK 21.
- M7: Waterfall out (ADR-0016 section 4). Proxy host `itzg/mc-proxy` by digest. Topology faults are NOT in
  `scripts/release-mutation-check.sh`; `:examples:bad-tests:metaTopologyTest` is a by-hand pre-release step.
- No Mojang dependency in runs: every backend is offline with proxy online-mode off (T-M7-10). No allowlist added to hide it.

STILL FOR THE MAINTAINER (not for agents):
- The decisions listed in ROADMAP "M7 review", "Decisions needed" (row promotion, the open findings).
- Central namespace verification, Disenchantment CI (T-M6-05), publishing, tagging, the 0.1.0 go/no-go.

READ FIRST: AGENTS.md, docs/ai/workflow.md, docs/ai/model-tiering.md, docs/plan/ROADMAP.md ("M7 findings" and "M7 review"),
docs/plan/tasks/M8-mods.md (the whole file: exit criterion, context, Flake rule), docs/adr/0016-topology-cells.md,
docs/platforms.md, docs/robustness.md section 5.

TASK THIS SESSION:
1. In the main checkout: `git pull --ff-only origin main`, then `bash scripts/gates.sh --max-workers=1`.
2. Only if the maintainer approved `docs/plan/tasks/M8-mods.md`: start with T-M8-01 (spike, opus) and T-M8-02 (copied-file
   ownership, the `spigot.yml` ERROR the M7 review found). They are independent. Then follow the card order in the file.
   If the plan is not approved, stop and ask.
3. T-M8-01 asks the maintainer before pulling any new image or downloading any jar (name, digest, size). Ask once with the full
   list, then record the answer in the PR.

AUTHORITY: decide and record in PR descriptions; a verifier merges a card PR once verified (docs/ai/workflow.md). Always ask first
for: adding any workflow, any new image or download not pinned yet, publishing, tagging, and any ROADMAP decision marked for the
maintainer. Never publish to Maven Central, GHCR or any registry, never tag, never set `PROOFSTONE_RELEASE_CONFIRM`, never run
`release-maven.sh --publish` or `release-images.sh --push`. Dry runs only. Do not start M9.

RULES LEARNED IN M6 AND M7 (keep):
- Windows: use Git Bash (C:\Program Files\Git\bin\bash.exe), not WSL bash. JDK 21 at `C:\Program Files\Java\jdk-21` for Gradle,
  JDK 26 at `C:\Program Files\Java\jdk-26` for Maven and the Velocity agent's compile. `check` needs 17, 21 and 26.
- Short worktree paths under `C:\pw\`. Always `--max-workers=1`; never two Gradle runs at once. Stop orphan `gradle-worker` JVMs
  before a run and at the end. If Docker Desktop is not running, start it and wait for `docker info` before any run.
- Docker containers named `ohlidame-*` (and other projects' containers) are not ours: never touch them. Leave none of ours running.
- Real-server runs: `PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew <task> --no-parallel --max-workers=1`, one cell
  per invocation. EULA in the command environment only. Do not nest dockerlock.
- `:conformance:smoke` with no selector runs four cells (paper, folia, purpur, spigot @26.3). Say which you ran.
- Flake rule (M7 and M8): three separate invocations per real-server acceptance, every one reported; a red is a finding, never
  replaced by a later green. Copy XML, bundle and container logs aside BEFORE the next run: the next run of the same task
  deletes the bundle directory (the M7 review lost one smoke bundle this way; its XML was kept).
- Known families, counted separately, never allowlisted without a card: `join-timeout` (single-server, M6 numbers), topology
  join timeouts (none in M7), and the log-guard WARN `PSConf moved too quickly!` on folia/purpur/spigot in a full smoke run.
- Copied files are root-owned in the container: Paper logs `Could not save spigot.yml (Permission denied)` at start on
  topology backends (T-M8-02 fixes it). Copy configs to `/config`, never into `/data/config` (velocity.md Gotchas).
- A Paper backend behind a proxy may call Mojang unless proxy online-mode is off (T-M7-10). Any new host lists what it fetches.
- Before a PR: `bash scripts/gates.sh --max-workers=1`, paste its summary. A gate that did not run is said so, never claimed.
- After any commit, the mutation PASS applies only to the commit it ran on.

HARD RULES: AGENTS.md section 3. Every endpoint, driver or verdict feature needs a REAL-SERVER test. Never weaken a test. Never claim a
gate passed without running it. Docs claim only what was run.

FINISH: when the approved cards you took are merged or in open PRs, stop and write a short summary (merged, unverified, decisions
needed, next-session prompt). Clean up branches and worktrees only for merged work.
