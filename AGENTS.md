# AGENTS.md: rules for AI agents (and humans) working on Proofstone

This file is the single source of instructions for coding agents. `CLAUDE.md` imports it.
Read it fully before every task. If a rule here conflicts with a task card, **this file wins**. Raise the
conflict in the PR.

Default model: **Claude Haiku 5.5** for all work (ADR-0014). Any model may take any card.

## 1. What this project is

A framework for real-server end-to-end tests of Minecraft plugins, mods and add-ons. Start with
[docs/vision.md](docs/vision.md), [docs/architecture.md](docs/architecture.md) and
[docs/glossary.md](docs/glossary.md). Use glossary terms exactly: *cell*, *oracle*, *agent*, *driver*,
*platform pack*, *conformance*, *control run*, *differential run* and *bridged*.

## 2. How to pick up work

1. Work only from a **task card** (`docs/plan/tasks/M*.md`, or a GitHub issue created from the card
   template). Never start untracked work.
2. Check the card's `tier`. It is a hint, not a model restriction. For `tier: sonnet` or `tier: opus`
   cards, work extra carefully: read the listed ADRs and spec sections in full, take smaller steps, and run
   the real-server test after every change.
3. Check `depends:`. Every dependency must be merged first.
4. One card means one branch (`card/T-M1-04-short-name`) and one PR. Keep the diff around 400 changed
   lines or less, tests excluded. If a card needs more, split it and explain why in the PR.
5. Read the ADRs listed on the card. **Do not re-decide an accepted ADR.** If you think it is wrong,
   finish the card as specified and open a separate "ADR challenge" issue.
6. Escalate only as section 5 says: a `needs-escalation` label, when the same gate fails twice.

## 3. Hard rules (gates enforce most of them)

- **No sleeps for synchronisation.** No `Thread.sleep`, `setTimeout`-style waits or fixed delays in
  harness or test code. Use `await(predicate, timeout)` helpers. Waits that measure time are allowed only
  inside the await implementation itself.
- **No automatic retries** that turn red into green. Retry exists only in the flake detector, and it
  reports flakiness as a failure.
- **Fail loudly on unknowns.** An empty response, unparsable data, an unknown field value or a missing
  capability is an error, never a pass or a silent skip. A skip always carries a reason, and a skipped
  test is reported as a skip, never as a pass.
- **No bind mounts** for test inputs. Copy files into containers.
- **Pin everything.** Image digests, Paper build numbers, library versions and Java tags all come from
  `versions.yml` or the Gradle version catalog. Never use `latest`, ranges or `+` versions in runtime
  paths.
- **Don't edit the wire protocol casually.** `proofstone-protocol` schemas change only through a card
  that says so. Bump the protocol version and update the golden files.
- **Agents (the in-server plugins and mods) observe and never interfere.** They set up before *act*,
  read after *act*, and listen at MONITOR priority without changing anything.
- **No new runtime dependencies** without an ADR or card approval. Agents shade and relocate everything
  they bundle.
- **Never weaken a test to make it pass.** If a test is wrong, fix it in its own commit with a reason.
- **Never claim a gate passed without having run it.**
- **Never commit secrets** or Mojang/Microsoft binaries. Server jars are downloaded at runtime.

## 4. Gates you must run before opening a PR

```bash
./gradlew check docsCheck       # compile, unit tests, lint, architecture rules, markdown links, glossary, card format; no Docker
./gradlew :conformance:smoke    # requires Docker: boots one cell, round trip (run locally)
# plus the card's Verify commands
```

- **Docker runs** (`:conformance:smoke`, any Testcontainers test, the card's Verify commands that need
  Docker): take a single machine-wide lock before the run and release it after. Only one Docker or
  Testcontainers Gradle run at a time; parallel runs break Docker Desktop. Use `bash scripts/dockerlock.sh ./gradlew <task> --no-parallel`
  (a mkdir lock; a lock older than 40 minutes counts as stale).
- **EULA:** set `PROOFSTONE_ACCEPT_EULA=true` per command, in the environment only (for example
  `PROOFSTONE_ACCEPT_EULA=true ./gradlew :conformance:smoke`). Never write it to any file, never commit it.
- **Evidence:** read the results in `build/test-results/**` XML. Every suite must show `skipped="0"
  failures="0" errors="0"`. A skip is not a pass.
- Paste the gate output summary into the PR. If a gate can't run (for example, no Docker), say so
  explicitly. Never claim it passed.

### CI cost

There is no CI. GitHub Actions minutes were billed to the maintainer, so on 2026-11-08 the maintainer removed `ci.yml` and the
required status checks on `main`. Local runs are the only gate.

- Run `bash scripts/gates.sh` before every PR (it is `./gradlew check docsCheck`, plus the pinned Renovate validation when
  `renovate.json` changed). Paste its summary into the PR. Never claim a gate passed without having run it.
- There are no workflows at all (`.github/workflows` was deleted 2026-11-08). Never add one, and never add `push`, `pull_request`
  or `schedule` triggers, without maintainer approval. The nightly set is `:examples:bad-tests:metaTest` run locally. New versions come
  from `./gradlew updateVersions` run locally (docs/versioning.md).
- A PR merges with no checks reported. The verifier (docs/ai/workflow.md) is the second pair of eyes: it merges `origin/main`,
  re-runs `bash scripts/gates.sh` and the card's real-server tasks under `scripts/dockerlock.sh`, reads the XML, then merges.

## 5. When you are stuck

- If the same gate fails twice after a fix attempt, stop. Write what you tried in the PR or issue and
  label it `needs-escalation` (see [docs/ai/model-tiering.md](docs/ai/model-tiering.md)). A stronger model
  picks it up from there.
- If the card is ambiguous, don't guess. Ask in the issue and propose an interpretation.
- If server or client behaviour surprises you, add a note to the matching `docs/platforms/<platform>.md`
  "Gotchas" section. That is how the project learns.

## 6. Code conventions

- Java 17 for host-side modules. Agents target the platform's minimum Java: the Bukkit agent uses
  **Java 8 bytecode**, so it runs on 1.8.8 servers.
- Gradle Kotlin DSL with a version catalog (`gradle/libs.versions.toml`). Convention plugins live in
  `build-logic/`.
- Each module has a `README.md` with these sections: **Purpose**, **Boundaries** (what it must not do),
  **Public surface**, **How to test**.
- Public API types live only in `proofstone-api`. Everything else is internal.
- Comments explain *why*, not *what*. Link the ADR when a line exists because of one.

## 7. Where things are

```
versions.yml        source of truth for the version matrix (ADR-0008)
docs/adr/           accepted decisions, indexed in docs/adr/README.md; read before changing related code
docs/ai/            model tiering, agent workflow and card format, module README template
docs/specs/         contracts: test API, agent protocol, client driver, self-tests, versions.yml
docs/plan/          roadmap (ROADMAP.md) and task cards (tasks/M*.md)
docs/platforms/     per-platform knowledge and gotchas: paper.md, folia.md, viaproxy.md
docs/versioning.md  how versions are added, pinned and chosen
docs/research/      founding research and prototype (historical, read-only)
```
