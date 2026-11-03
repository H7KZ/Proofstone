# AGENTS.md: rules for AI agents (and humans) working on Proofstone

This file is the single source of instructions for coding agents. `CLAUDE.md` imports it.
Read it fully before every task. If a rule here conflicts with a task card, **this file wins**. Raise the
conflict in the PR.

## 1. What this project is

A framework for real-server end-to-end tests of Minecraft plugins, mods and add-ons. Start with
[docs/vision.md](docs/vision.md), [docs/architecture.md](docs/architecture.md) and
[docs/glossary.md](docs/glossary.md). Use glossary terms exactly: *cell*, *oracle*, *agent*, *driver*,
*platform pack*, *conformance*, *control run*, *differential run* and *bridged*.

## 2. How to pick up work

1. Work only from a **task card** (`docs/plan/tasks/M*.md`, or a GitHub issue created from the card
   template). Never start untracked work.
2. Check the card's `tier`. Cards marked `tier: sonnet` or `tier: opus` must not be done on a smaller model.
   Stop and say so.
3. Check `depends:`. Every dependency must be merged first.
4. One card means one branch (`card/T-M1-04-short-name`) and one PR. Keep the diff around 400 changed
   lines or less, tests excluded. If a card needs more, split it and explain why in the PR.
5. Read the ADRs listed on the card. **Do not re-decide an accepted ADR.** If you think it is wrong,
   finish the card as specified and open a separate "ADR challenge" issue.

## 3. Hard rules (CI enforces most of them)

- **No sleeps for synchronisation.** No `Thread.sleep`, `setTimeout`-style waits or fixed delays in
  harness or test code. Use `await(predicate, timeout)` helpers. Waits that measure time are allowed only
  inside the await implementation itself.
- **No automatic retries** that turn red into green. Retry exists only in the flake detector, and it
  reports flakiness as a failure.
- **Fail loudly on unknowns.** An empty response, unparsable data, an unknown field value or a missing
  capability is an error, never a pass or a silent skip. A skip always carries a reason.
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
- **Never commit secrets** or Mojang/Microsoft binaries. Server jars are downloaded at runtime.

## 4. Gates you must run before opening a PR

Commands are planned. Card T-M0-02 creates them, and until it lands, run what exists.

```bash
./gradlew check                 # compile, unit tests, lint, architecture rules
./gradlew :conformance:smoke    # requires Docker: boots one cell, round trip
./gradlew docsCheck             # markdown links, glossary terms, card format
```

Paste the gate output summary into the PR. If a gate can't run (for example, no Docker), say so
explicitly. Never claim it passed.

## 5. When you are stuck

- If the same gate fails twice after a fix attempt, stop. Write what you tried in the PR or issue and
  label it `needs-escalation` (see [docs/ai/model-tiering.md](docs/ai/model-tiering.md)).
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
docs/adr/           accepted decisions; read before changing related code
docs/specs/         contracts: test API, agent protocol, driver SPI, self-tests, versions.yml
docs/plan/          roadmap and task cards
docs/platforms/     per-platform knowledge and gotchas
docs/research/      founding research and prototype (historical, read-only)
```
