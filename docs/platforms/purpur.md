# Purpur

Status: **experimental** (rows 1.21.11 and 26.3 in `versions.yml`). C01 to C08 passed once per row (T-M5-04). See
[platforms.md](../platforms.md).

## Host
- Image `itzg/minecraft-server:<javaTag>` with `TYPE=PURPUR`, `VERSION=<mc>` and `PURPUR_BUILD=<pinned>`. The same image
  and per-tag digests as Paper (ADR-0013). `PaperHost.buildEnv` picks `PURPUR_BUILD`; `PAPER_BUILD` is ignored by Purpur.
- Java tag per row: 1.21.11 on `java21`, 26.3 on `java25` (`java-rules`).
- Download API: `https://api.purpurmc.org/v2/purpur` (versions), `/<mc>/latest` (build and md5),
  `/<mc>/<build>/download` (jar). The API gives an **md5 only**. The updater checks the download against it and pins the
  jar's sha256 (`VersionsUpdater`, T-M5-04).
- Jar name: `/data/purpur-*.jar`, which `PaperHost` finds for its sha256 check.
- Rows: 1.14.1 to 26.3 exist in the API. Only 1.21.11 (build 2568) and 26.3 (build 2657) are pinned.

## Agent notes
- The agent names the platform `purpur` from `org.purpurmc.purpur.PurpurConfig` (`PlatformDetector`). No Purpur-specific
  API is used; the agent runs on the Bukkit API like Paper.
- Purpur's start script sets `FAMILY=SPIGOT` and hands off to `start-spiget`. That is an itzg detail and needs no action
  from the harness.

## Capabilities
`BUKKIT_EVENTS`, `CONSOLE`. Not `NMS` or `DATAPACKS`: they were not verified on Purpur, so Purpur declares only what
its conformance run showed. `FOLIA_REGIONS` is not declared, so C-F01 and C-F02 skip on Purpur with
`missing capability FOLIA_REGIONS on cell purpur@<v>`. `SIMULATED_BEDROCK` is not declared either (C-B01 skips on
purpur@26.3 with its reason, verified in T-M5-06).

## Gotchas
- **Build variable.** `PAPER_BUILD` does nothing on Purpur. The row's build is passed as `PURPUR_BUILD`, so a cell cannot
  silently run the latest build. _1.21.11, 26.3 · T-M5-04_
- **Harness clients log `moved too quickly!` (WARN).** The reset teleport races the client's first move packets, as on
  Paper, Folia and Spigot. Purpur's allowlist covers only the harness's own clients (`^T[0-9a-f]{8} moved too quickly! `).
  Any other player, or a plugin's own message, still fails the test. The first 26.3 run was red on this WARN in C02, C04,
  C07 and C08; the entry was added and the row re-run green. Recorded here, not hidden. _26.3 · T-M5-04_
- **Folia-only tests need Purpur in their matrix.** Without it, `purpur@<v>` plans no cell for C-F01 and C-F02 and the
  run fails with "no cell to run". Both declare Purpur and skip there through `@Requires(FOLIA_REGIONS)`. _26.3 · T-M5-04_
- **Boot ERROR lines on Purpur were not checked.** The log guard covers only the test window, as for Paper and Folia.

## Conformance (T-M5-04, regression in T-M5-06)
Per-row runs, one `dockerlock` invocation each, `skipped` and `failures` read from the JUnit XML (`errors=0` on both):

| Row | Driver path | C01 | C02 | C03 | C04 | C05 | C06 | C07 | C08 | C-F01 / C-F02 | C-B01 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| purpur 26.3 (build 2657) | native | pass | pass | pass | pass | pass | pass | pass | pass | skip (capability) | skip (capability) |
| purpur 1.21.11 (build 2568) | bridged | pass | pass | pass | pass | pass | pass | pass | pass | skip (capability) | not run |

Skips are reasoned (capability), not hidden. Each row has one full run that counts. The 26.3 run that counts is the
second one, after the allowlist entry above.

## Open limits
- Purpur 1.14 to 1.20.x are not pinned and not run.
- Each row has one counted run. Promotion to `required` is not done: ADR-0015 needs a maintainer decision, because the
  26.3 pass came after an allowlist change that followed a red run. Both rows stay `experimental`.
- Boot-time ERROR lines on Purpur are not checked (see Gotchas).
- `NMS`, `DATAPACKS` and `SIMULATED_BEDROCK` are not verified on Purpur.
