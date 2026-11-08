# Purpur

## Host
- Image `itzg/minecraft-server:<javaTag>` with `TYPE=PURPUR`, `VERSION=<mc>` and `PURPUR_BUILD=<pinned>`. The same image
  and per-tag digests as Paper (ADR-0013). `PaperHost.buildEnv` picks `PURPUR_BUILD`; `PAPER_BUILD` is ignored by Purpur.
- Download API: `https://api.purpurmc.org/v2/purpur` (versions), `/<mc>/latest` (build and md5),
  `/<mc>/<build>/download` (jar). The API gives an **md5 only**. The updater checks the download against it and pins the
  jar's sha256 (`VersionsUpdater`, T-M5-04).
- Rows: 1.14.1 to 26.3 exist in the API. Only 1.21.11 and 26.3 are pinned so far (`versions.yml`, status `experimental`).
- Jar name: `/data/purpur-*.jar`, which `PaperHost` finds for its sha256 check.

## Agent notes
- The agent names the platform `purpur` from `org.purpurmc.purpur.PurpurConfig` (`PlatformDetector`). No Purpur-specific
  API is used; the agent runs on the Bukkit API like Paper.
- Purpur's start script sets `FAMILY=SPIGOT` and hands off to `start-spiget`, which is an itzg detail and needs no
  action from the harness.

## Capabilities
`BUKKIT_EVENTS`, `CONSOLE`. Not `NMS` or `DATAPACKS`: they were not verified on Purpur, so Purpur declares only what
its conformance run showed. `FOLIA_REGIONS` is not declared, so C-F01 and C-F02 skip on Purpur with
`missing capability FOLIA_REGIONS on cell purpur@<v>`.

## Gotchas
- **Build variable.** `PAPER_BUILD` does nothing on Purpur. The row's build is passed as `PURPUR_BUILD`, so a cell cannot
  silently run the latest build. _1.21.11, 26.3 · T-M5-04_
- **Harness clients log `moved too quickly!` (WARN).** The reset teleport races the client's first move packets, as on
  Paper and Folia. Purpur's allowlist covers only the harness's own clients (`^T[0-9a-f]{8} moved too quickly! `). Any
  other player, or a plugin's own message, still fails the test. _26.3, in C02, C04, C07, C08 · T-M5-04_
- **Folia-only tests need Purpur in their matrix.** Without it, `purpur@<v>` plans no cell for C-F01 and C-F02 and the
  run fails with "no cell to run". Both declare Purpur and skip there through `@Requires(FOLIA_REGIONS)`. _26.3 · T-M5-04_

## Conformance (T-M5-04)
Per-row runs, one `dockerlock` invocation each, `skipped="0" failures="0" errors="0"` read from the XML:

| Row | Mode | C01 | C02 | C03 | C04 | C05 | C06 | C07 | C08 | C-F01/C-F02 |
|---|---|---|---|---|---|---|---|---|---|---|
| purpur 26.3 (build 2657) | native | pass | pass | pass | pass | pass | pass | pass | pass | skip (capability) |
| purpur 1.21.11 (build 2568) | bridged | pass | pass | pass | pass | pass | pass | pass | pass | skip (capability) |

Skips are reasoned (capability), not hidden.
