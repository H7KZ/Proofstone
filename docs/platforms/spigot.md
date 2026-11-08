# Spigot

Status: **experimental** (rows 1.8.8, 1.21.11 and 26.3 in `versions.yml`). See [platforms.md](../platforms.md) and the
conformance table below.

## Host and builder
- **Not downloaded.** Spigot is built from source by SpigotMC BuildTools for each row's `--rev` (the Minecraft version,
  for example `1.21.11`). The image is `images/spigot-buildtools`: one per Java tag, `FROM` the pinned eclipse-temurin JDK
  (`platforms.spigot.builder.jdk`), with git and BuildTools (`buildtools.build` and `buildtools.sha256`, checked at image
  build). The image is built locally and never pushed, so no Spigot or Mojang jar is redistributed.
- **The server runs as `TYPE=CUSTOM`** on the itzg image (`platforms.spigot.host.digests`, one per Java tag, ADR-0013).
  `SpigotServerJar` copies the built jar in as `/data/spigot-<mc>.jar` and sets `CUSTOM_SERVER` to it. itzg never builds.
- **Cache.** `SpigotServerJar` keeps jars in `build/proofstone/spigot-cache/<sha256>.jar` (override with
  `-Dproofstone.spigotCache`). The cache file is named by the row's pin (`JarDigest`, see Gotchas) and is used only when its digest equals the pin.
  A cache miss runs BuildTools and digests the result: a mismatch fails the cell, deletes the `.jar.part` and caches nothing.
- **Java per row.** BuildTools needs a `javac`, and the itzg images have none (JRE only). The row's Java tag picks the JDK:
  1.8.8 on `java8`, 1.21.11 on `java21`, 26.3 on `java25`. The server runs on the JDK it was built with.
- Run it: `PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :conformance:run -Dproofstone.cells=spigot@<mc> --no-parallel --max-workers=1`.

## Agent notes
- The agent names the platform `spigot` from `org.spigotmc.SpigotConfig` (`PlatformDetector`). The Paper and Purpur
  classes are absent on Spigot, so the detector does not mistake it for a Paper fork.
- No Spigot-specific API. The agent runs on the Bukkit API like Paper.

## Capabilities
`BUKKIT_EVENTS`, `CONSOLE`. Not `NMS` or `DATAPACKS` (not verified on Spigot). `FOLIA_REGIONS` is not declared, so C-F01
and C-F02 skip on spigot with `missing capability FOLIA_REGIONS on cell spigot@<v>`.

## Gotchas
- **BuildTools output is not byte-identical between runs, and the cause is one line.** Builds of 1.8.8 hashed
  differently every time (9 fresh builds, 9 SHA-256s). Unzipping two builds showed 11961 identical entries and one
  difference: `META-INF/maven/org.spigotmc/spigot/pom.properties`, whose second line is Maven's build time
  (`#Sun Nov 08 17:38:31 UTC 2026`). Entry times and order were equal. So the pin is a content digest (`JarDigest`): SHA-256
  over every entry name and its content, with that date comment dropped. It is as strict as a jar hash for anything that
  runs (a changed class, resource or entry changes it) and does not break on the build time. The two builds above, and the
  earlier `c7552585...` jar, share one digest. 1.21.11 and 26.3 use the same rule, so every row is strict, none is exempt.
  Decision: ADR-0005 (pin everything) is kept, not relaxed; only what is hashed changed. _1.8.8 · T-M5-05_
- **C06 on 1.21.11 flake measured.** `-Dproofstone.repeat=5 --tests '*C06AnvilCombineTest*'`: 5 of 5 PASSED, no FLAKY
  verdict. With the full row run (PASSED) that is 6 of 6 passes. The author's single failure is not reproduced; the rate
  stays unknown below 1 in 6. _1.21.11 · T-M5-05 verify_
- **BuildTools takes minutes.** 1.8.8 took 3m31s on java8 (Docker Desktop, Windows). The 1.21.11 and 26.3 builds were not
  timed. A cell run that has to build adds that to the boot time.
- **The harness client logs `moved too quickly!` (WARN) on Spigot too.** The reset teleport races the client's first move
  packets, as on Paper, Folia and Purpur. Seen on spigot@26.3 in `:conformance:smoke` (C02). The allowlist covers the
  harness's own clients only (`^T[0-9a-f]{8} moved too quickly! `). An attempt to drop that entry failed the smoke run, so
  it stays. _26.3 · T-M5-05_
- **Every test class that asks for the cell calls the cache.** A cache miss in a multi-class run builds the jar again for
  each class (the first run here re-built 1.8.8 per class and was stopped). Build once, then run. _T-M5-05_
- **`Platform` is `SPIGOT` in the API, and the platform name is `spigot` everywhere** (agent, pins, ErrorLogGuard,
  CapabilityModel, `Platform.SPIGOT`). Keep the names in step with the agent's detector. _T-M5-05_
- **`level-type` and the Java tag follow the Paper rules.** `PaperHost.levelType` and `java-rules` apply to Spigot
  unchanged, because the host is the same itzg image. _1.8.8, 1.21.11, 26.3 · T-M5-05_

## Conformance (T-M5-05)
Per-row runs, one `dockerlock` invocation each. `skipped` and `failures` read from the JUnit XML under
`conformance/build/test-results/run/`:

| Row | Mode | C01 | C02 | C03 | C04 | C05 | C06 | C07 | C08 | C-F01 / C-F02 |
|---|---|---|---|---|---|---|---|---|---|---|
| spigot 1.8.8 (java8) | bridged | pass | pass | pass | pass | pass | pass | skip (no grindstone before 1.14) | pass | skip (capability) |
| spigot 1.21.11 (java21) | bridged | pass | pass | pass | pass | pass | **see below** | pass | pass | skip (capability) |
| spigot 26.3 (java25) | native | pass | pass | pass | pass | pass | pass | pass | pass | skip (capability) |

- **1.21.11 C06 is intermittent.** The first run failed with `DISAGREE: present in the inventory ... expected present after
  10119 ms`: the server saw `diamond_sword` with sharpness 5, and the client inventory read empty. A rerun of C06 alone
  passed. Recorded as one fail and one pass, not as a clean pass. The cause is not known; the client's inventory after the
  combine is the suspect, as on Paper (`docs/platforms/paper.md`, Gotchas on the stale client inventory while a window is
  open).
- The skips are reasoned (capability and version), not hidden.
- Evidence per row: 1.8.8 ran twice with the cache cleared between (both pass the pin, C01 to C08 pass, C07 skip);
  1.21.11 and 26.3 ran once each after the digest fix. `:conformance:smoke` (8 tests, 0 failed) passed with spigot@26.3
  among the platforms it covers. 1.21.11 C06 is the only red result recorded (one failure, then a pass; verifier repeat
  5 of 5).

## Open limits
- Spigot versions other than the three pinned rows are not pinned and not run.
- 1.21.11 C06 is not explained. The row stays `experimental`, so the failure is not hidden by a `required` claim.
- Each row has one counted full run. Promotion to `required` is not done: ADR-0015 needs a maintainer decision (C06 red
  once on 1.21.11).
- Build times for 1.21.11 and 26.3 were not measured.
- No `SIMULATED_BEDROCK`, `NMS` or `DATAPACKS` run on Spigot.
