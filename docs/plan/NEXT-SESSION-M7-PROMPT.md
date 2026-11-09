# Next session prompt: M7 (proxies)

Paste everything below the line into a new session.

---

Continue the Proofstone project (repo C:\Users\honzi\Documents\GitHub\Proofstone, GitHub H7KZ/Proofstone, branch main).

STATE OF MAIN: origin/main has M0 to M6 merged (PRs #1 to #93, plus the T-M6-13 PR once merged). The M6 review addendum
(`docs/plan/ROADMAP.md`, "M6 review addendum", 2026-11-09) re-scores the exit criterion: licence MET, examples MET locally
(flake bounded, not explained), mutation check PASS on 4df12c8. Still **NO-GO for a public 0.1.0 claim**: Disenchantment CI
(other repo), publish (Maven Central, GHCR) and the GitHub Action are not done. Nothing is published or tagged. There is NO CI and no
workflow (maintainer decision 2026-11-08).

DECIDED (do not ask again):
- Licence: **Apache-2.0** + DCO + NOTICE (ADR-0011 Accepted, T-M6-06).
- Maven: **installed**, Apache Maven 3.9.9 at `C:/Users/honzi/tools/apache-maven-3.9.9`. Set `MAVEN_HOME` to that directory.
  Maven runs use JDK 26 (`MVN_JAVA_HOME`, or `JAVA_HOME` for the Maven command). Gradle runs use JDK 21 (`JAVA_HOME`).
  Maven example (T-M6-11) is merged; the mutation check runs both examples.
- Namespace: **`io.github.h7kz.proofstone`** (T-M6-12). `proofstone.dev` is not owned. Central verification of this namespace is still
  open (ADR-0012). New M7 packages use `io.github.h7kz.proofstone.*`.

STILL FOR THE MAINTAINER (not for agents):
- Central namespace verification (ADR-0012).
- Disenchantment CI repository and its run (T-M6-05).
- Publishing (Maven Central, GHCR). The maintainer sets `PROOFSTONE_RELEASE_CONFIRM` and the credentials, never a file.
- Tagging, and the go/no-go for a public 0.1.0 claim.

READ FIRST: AGENTS.md, docs/ai/workflow.md, docs/ai/model-tiering.md, docs/plan/ROADMAP.md (the M6 review, "M6 review addendum" and
"M6 decisions" sections), docs/plan/tasks/M6-dogfood-release.md (T-M6-13 and T-M6-14), docs/plan/tasks/M7-M10-epics.md (M7), docs/adr/README.md,
docs/platforms.md, docs/robustness.md section 5.

TASK THIS SESSION:
1. In the main checkout: `git pull --ff-only origin main`. Run `bash scripts/gates.sh --max-workers=1` and read the ROADMAP sections above.
2. T-M6-14 (log the server's handshake view at join and name the stalled stage in INVALID): one card, one PR, sonnet. Do it first,
   because every M7 real-server acceptance depends on join.
3. Refine the M7 epic (Velocity, BungeeCord, topology API) into cards in `docs/plan/tasks/M7-proxies.md` in the standard card format
   (Sonnet or Opus for the refinement). Include the M7 milestone review card and an honest estimate. Account for join-timeout
   flakiness (T-M6-10, T-M6-14) in every real-server acceptance.
4. **M7 task = refine the epic into cards and STOP for approval.** Do not implement any M7 card in this session.

DO NOT START M8.

AUTHORITY: decide and record in PR descriptions, merge your own PRs once verified. Always ask first for: adding any workflow, the
licence, the namespace, downloading or installing anything (for example Maven or a JDK), publishing anything public, tagging, and any
decision marked Maintainer in ROADMAP "M6 decisions". Never publish to Maven Central, GHCR or any registry, never tag, never set
`PROOFSTONE_RELEASE_CONFIRM`, never run `release-maven.sh --publish` or `release-images.sh --push`. Dry runs only.

RULES LEARNED IN M6 (keep):
- Windows: PowerShell `bash` is WSL without JAVA_HOME. Use Git Bash (C:\Program Files\Git\bin\bash.exe). JDKs: JDK 21 at
  `C:\Program Files\Java\jdk-21` (harness runs need 21 and 17, host modules), JDK 26 at `C:\Program Files\Java\jdk-26` (Maven
  example and `check`). `check` needs 17, 21 and 26.
- Use short worktree paths under `C:\pw\` (for example `C:\pw\m7a`). Long paths give "Filename too long".
- Always `--max-workers=1`; never two Gradle runs at once. Before each run, look for orphan `gradle-worker` JVMs (java.exe with
  gradle-worker in the command line; the daemon is fine) and stop them. Stop yours at the end (`./gradlew --stop` if no other build
  is running). They also lock worktrees.
- **Docker containers named `ohlidame-*` belong to another project. Never touch them** (no stop, no remove, no inspect-and-act).
  During a verifier run, 12 `ohlidame-e2e` containers vanished; cause unknown, probably that project's own test teardown. Count only
  the containers this project starts. Leave none of ours running at the end.
- Real-server runs: `PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew <task> --no-parallel --max-workers=1`, one cell
  per invocation. Do not nest dockerlock (scripts such as `release-images.sh` take the lock themselves). Read the XML:
  skipped, failures and errors must be 0 except reasoned skips (list them).
- Flake family, all `join-timeout` (`timed out joining ... after PT30S`): 3 of 61 plain Gradle runs on paper@26.3 (1 of 15, then
  2 of 46 in T-M6-10), 2 of 4 earlier action-path runs, one purpur@26.3 C06 red, one metaExpect INVALID. Cause unknown. Bounded,
  not explained. T-M6-14 names the stalled handshake stage. Until then, a join timeout is a finding, not a harness pass.
- A red run is a finding. Copy its XML, bundle and container log aside BEFORE the next run overwrites them, name its verdict type,
  never retry-to-green, never raise a timeout to hide it.
- Delegate each card to a background agent (haiku by default, isolation worktree, short worktree path), one card = branch
  `card/<ID>-<slug>` = one PR. Then a verifier merges origin/main, re-runs `bash scripts/gates.sh --max-workers=1` and the card's
  real-server tasks, checks the XML and the diff (no weakened test, no new runtime dependency, no workflow, no secrets), and merges with
  `gh pr merge --merge`. Escalate to sonnet only when the same gate fails twice. Check that a verifier did not deviate from its
  instructions.
- Stop each finished agent with TaskStop so stale monitor notices end. Delete worktrees with `git worktree remove --force`; if
  Windows path length fails, mirror an empty directory over it with robocopy and prune.
- Write a short shared brief in the scratchpad for the agents (AGENTS.md section 3, no sleeps, fail loudly, pins, API v0 frozen,
  additive only with label api-change, EULA only in the command environment).
- Before a PR: `bash scripts/gates.sh --max-workers=1`, paste its summary into the PR. A gate that did not run is said so, never claimed.
- After any commit, the mutation PASS applies to the commit it ran on. Re-run `bash scripts/release-mutation-check.sh` at the tag HEAD
  before any publish.

HARD RULES: AGENTS.md section 3. Every endpoint, driver or verdict feature needs a REAL-SERVER test. Never weaken a test. Never claim a
gate passed without running it. Docs claim only what was run.

FINISH: when the T-M6-14 PR is merged and the M7 plan (cards plus milestone review card) is in an open PR, stop and write a short
summary (merged, unverified, decisions needed, next-session prompt). Do not implement M7. Clean up branches and worktrees
(`git branch -D`, `git push origin --delete`, `git worktree remove --force`) only for work that is merged.
