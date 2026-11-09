# Next session prompt: M7 (proxies)

Paste everything below the line into a new session.

---

Continue the Proofstone project (repo C:\Users\honzi\Documents\GitHub\Proofstone, GitHub H7KZ/Proofstone, branch main).

STATE: M0 to M6 cards are done and merged (PRs #1 to #88). The M6 review (T-M6-99) is a **NO-GO for 0.1.0**, with the reasons
and the audit fixes recorded in `docs/plan/ROADMAP.md` ("M6 review", "M6 decisions: recommendations and state", "M6 pre-release
findings"). There is NO CI and no workflow (maintainer decision 2026-11-08). Nothing was ever published or tagged.

READ FIRST: AGENTS.md, docs/ai/workflow.md, docs/ai/model-tiering.md, docs/plan/ROADMAP.md (the three M6 sections above),
docs/plan/tasks/M6-dogfood-release.md (T-M6-09 to T-M6-11), docs/plan/tasks/M7-M10-epics.md (M7), docs/adr/README.md,
docs/platforms.md, docs/robustness.md section 5.

MY ANSWERS: [fill in; leave a line blank to accept the recommendation in ROADMAP "M6 decisions"]
- 1 Licence (Apache-2.0 or MIT): [ ]
- 2 Maven installed (yes/no; if yes, run T-M6-11): [ ]
- 7 Namespace: [rename to io.github.h7kz.proofstone before first publish | I own proofstone.dev | decide later]
  If "decide later": new M7 packages use the existing `dev.proofstone.*` and I accept a later mechanical rename.

TASK THIS SESSION:
1. `git pull --ff-only origin main` in the main checkout (a stale local main caused a bad run-through before), run
   `bash scripts/gates.sh --max-workers=1`, and read the ROADMAP sections above.
2. If my answers say so, do the small M6 follow-ups first, one card = one PR: T-M6-06 (licence, only if I chose), T-M6-11
   (Maven example, only if Maven is installed), T-M6-10 (flake diagnosis, 30 runs, XML kept aside).
3. Refine the M7 epic (Velocity, BungeeCord, topology API) into cards in `docs/plan/tasks/M7-proxies.md` in the standard card
   format (Sonnet or Opus for the refinement). **Stop for my approval before implementing any M7 card.** Include the M7
   milestone review card and an honest estimate. Account for join-timeout flakiness (T-M6-10) in every real-server acceptance.
4. After approval only: implement M7 cards with the rules below.

DO NOT START M8.

AUTHORITY: decide and record in PR descriptions, merge your own PRs once verified. Always ask me first for: adding any
workflow, the licence, namespace, downloading or installing anything (for example Maven), publishing anything public, tagging,
and any decision in ROADMAP "M6 decisions" marked Maintainer. Never publish to Maven Central, GHCR or any registry, never tag, never
set `PROOFSTONE_RELEASE_CONFIRM`, never run `release-maven.sh --publish` or `release-images.sh --push`. Dry runs only.

RULES LEARNED IN M6 (keep):
- Windows: PowerShell `bash` is WSL without JAVA_HOME. Use Git Bash (C:\Program Files\Git\bin\bash.exe) with
  JAVA_HOME=C:\Program Files\Java\jdk-21. JDKs: harness runs need 21 and 17 (host modules); `check` needs 17, 21 and 26.
- Always `--max-workers=1`; never two Gradle runs at once. Before each run, look for orphan `gradle-worker` JVMs (java.exe with
  gradle-worker in the command line, the daemon is fine) and stop them; stop yours at the end. They also lock worktrees.
- Real-server runs: `PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew <task> --no-parallel --max-workers=1`, one cell
  per invocation. Do not nest dockerlock (scripts such as `release-images.sh` take the lock themselves). Read the XML:
  skipped, failures and errors must be 0 except reasoned skips (list them).
- A red run is a finding. Copy its XML, bundle and container log aside BEFORE the next run overwrites them, name its verdict type,
  never retry-to-green, never raise a timeout to hide it. Report flakiness as a finding, never as a pass.
- Delegate each card to a background agent (model haiku, isolation worktree, short worktree path to avoid "Filename too long"),
  one card = branch `card/<ID>-<slug>` = one PR. Then a haiku verifier merges origin/main, re-runs `bash scripts/gates.sh` and the
  card's real-server tasks, checks the XML and the diff (no weakened test, no new runtime dependency, no workflow, no secrets), and
  merges with `gh pr merge --merge`. Escalate to sonnet only when the same gate fails twice. Check a verifier did not deviate from its
  instructions (one set the release confirm variable in M6; the gate refused, but check).
- Stop each finished agent with TaskStop so stale monitor notices end. Delete worktrees with `git worktree remove --force`; if
  Windows path length fails, mirror an empty directory over it with robocopy and prune.
- Write a short shared brief in the scratchpad for the agents (AGENTS.md section 3, no sleeps, fail loudly, pins, API v0 frozen,
  additive only with label api-change, EULA only in the command environment).

HARD RULES: AGENTS.md section 3. Every endpoint, driver or verdict feature needs a REAL-SERVER test. Never weaken a test. Never claim a
gate passed without running it. Docs claim only what was run.

FINISH: when the M7 plan is approved and its milestone review card is merged (or, if I have not approved the plan, when the plan PR is
open), stop and write a short summary (merged, unverified, decisions needed, next-session prompt). Clean up branches and worktrees
(git branch -D, git push origin --delete, git worktree remove --force).
