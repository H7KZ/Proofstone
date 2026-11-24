# Changelog

All notable changes are listed here, newest first. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versioning: [Semantic Versioning](https://semver.org/spec/v2.0.0.html) with the 0.x rules in [compatibility](docs/compatibility.md).

## [Unreleased]

### Fixed
- Bukkit agent: `patchConfig` restore loads the snapshot into the plugin's existing config object instead of calling `reloadConfig()`, so a
  plugin that holds `getConfig()` since `onEnable` sees patches made after a restore (T-M12-04, C-C03).

## [0.2.0] - 2026-11-21

API additions found by the Disenchantment plugin end-to-end suite, two Java-driver fixes and spike notes. It is `0.x`, but the public API
change is **additive**: `proofstone-api/api-baseline.txt` gained 33 lines and lost none, so `0.1.0` test sources compile unchanged. The
agent protocol stays `1.3` (no schema, golden or version change); the Bukkit agent reports one new capability token, `pdc`. The new
methods on `Agent`, `Client` and `Window` are default methods that throw `UnsupportedOperationException`, so a custom implementation of
those interfaces keeps compiling.

### Added
- `Agent.logLines()` and `Agent.logLines(Pattern, Duration)`: the server log since the test started, as plain text (T-M11-01, #199).
- `Agent.bootLogLines()`: the server log lines from the agent's enable up to the test start, so a plugin's start-up line is readable
  (T-M11-03, #201). It does not hold the banner, library loading, the plugin loading list or the agent's own "Enabling" line, and it
  throws when the 5000-record buffer dropped records.
- `PlayerState.openWindow()` and `ServerWindow`: the server's view of the container a player has open (type, title, the container's own
  slots), read from the agent state that already carried it (T-M11-02, #200).
  `PlayerState` gains an `openWindow` record component: the old 7-argument constructor is kept, so code that constructs a `PlayerState`
  compiles unchanged, but a record pattern or deconstruction of `PlayerState` sees a new component.
- `Client.placeBlock(BlockPos, Face)`, `Client.breakBlock(BlockPos)`, `Agent.blockType(String, BlockPos)` and the public enum `Face`: a
  player's block place and break through the Java driver, read back from the server (T-M11-04, #202).
- `ItemView.pdc()` is filled by the Bukkit agent on servers with the PDC API: typed values for byte, short, int, long, float, double,
  string and the three arrays, a nested container as a map and an `unsupported` marker for the rest; agent token `pdc`; verified on
  1.18.2 and 26.3 (T-M11-05, #203).
  `DualAssert` now compares a nested `pdc` map or array by content (it used `Objects.equals`, which fails for arrays).
- `Window.drag(List<Integer>, DragMode)` and the public enum `DragMode`: an inventory drag across slots with the left, right or middle
  button, answered by the server, through the Java driver (T-M11-09, #208).
- `Window.awaitCursor(Predicate<ItemView>)`: waits for the client cursor to match, like `awaitSlot`. A click returns on the first window
  sync and the cursor can arrive just after it, so a one-shot `cursor()` read right after a click can race (T-M11-11, #209).
- Spike notes, no code: `docs/platforms/nethernet.md` (T-M10-19, #198: Go for NetherNet in the Bedrock driver, behind an ADR not yet
  filed) and `docs/platforms/fault-injection.md` with ADR-0020 (Proposed; T-M11-10, #207: making a server-side inventory write fail from
  the agent is a No-go under ADR-0002).

### Fixed
- Java driver (`drivers:mcpl`): a window packet that arrives after the client closed that window now goes to the client inventory, so the
  client oracle no longer misses a stack the server placed just before it read the close. This was the cause of the intermittent
  client-oracle `DISAGREE` seen on `folia@26.3` (T-M11-07, #205).
- Java driver: `ClientboundSetPlayerInventoryPacket` is applied to the client inventory (T-M11-06, #204). Harmless on its own and not the
  Folia cause, but the packet was ignored before.
- Bukkit agent: third-party event classes load through their owning plugin only, which removes the cross-plugin `WARN` Paper 1.18.2
  printed at start-up (T-M6-111, #190).
- One exception type for "this driver cannot": the `ClientSession.placeBlock`/`breakBlock`/`awaitServerSwitch`/`resourcePacks` and
  `WindowSession.drag` defaults threw `ClientDriverException` in `0.1.0` and now throw `UnsupportedOperationException` on non-supporting
  drivers, such as the Mineflayer reference driver, as the public `Client` and `Window` defaults already did. Both are `RuntimeException`s,
  and drivers carry no compatibility guarantee ([compatibility](docs/compatibility.md)). Internal driver SPI only, `api-baseline.txt`
  unchanged. `McplSession.breakBlock` documents why it sends no abort-digging after a rejected finish, and the block ack waits gained
  timeout, disconnect and lower-sequence-ack tests (T-M11-13, #211).
- A test class for which the cell selector leaves no row now fails naming each unselected row, instead of vanishing (T-M4-13, #180).

### Known limits
- `Client.breakBlock` completes a survival break only for a block with zero break time (instant break, or creative); a slower block is
  rejected by the server and stays. A survival dig is never aborted. There is no sneak input: `placeBlock` against a block with a use
  action (anvil, chest) opens it, as for a player who is not sneaking.
- `placeBlock`, `breakBlock` and `drag` are built for the Java driver (`awaitCursor` works with every driver). The Bedrock driver and the
  reference Mineflayer driver refuse these calls with `UnsupportedOperationException`.
- `ItemView.pdc`: whether `getKeys` exists on 1.14 to 1.17 is unverified (verified on 1.18.2 and 26.3). Where the PDC API is missing the
  agent reports no `pdc` token and the field stays empty.
- `bootLogLines` coverage is as listed above.
- Unchanged from `0.1.0`: the ViaProxy 1.8.8 bridged join stall (mechanism unproven), Bedrock through Geyser, Fabric and NeoForge stay
  experimental, and the GitHub Action is documented but not yet verified on a hosted runner.

### Changed (tooling and docs, not part of the artefacts)
- The Maven example declares Maven Central before the Paper repository, so Proofstone resolves from Central first (T-M6-107, #179).
- The GitHub Action README documents the action as a product and its self-check is hardened (T-M6-108, #178).
- `scripts/consumer-cleanroom.sh` reads the version under test from `gradle/libs.versions.toml`.
- Docs site on GitHub Pages and its `docs` workflow (T-M10-16), assertion lint for negative-only test classes (T-M4-14), a hosted
  `gates` dependency prefetch that retries only a failed download (T-M6-113), and spike notes for BDS, Sponge, Gate, Nukkit,
  PocketMine and Endstone, and the Fabric client (T-M10-01, -11, -12, -13, -14, -17).

## [0.1.0] - 2026-11-18

First public release. It is `0.x`: a minor release may break the public API ([compatibility](docs/compatibility.md)). Scope, evidence
and known problems: [release notes](docs/release-notes-0.1.0.md).

### Added
- The test harness: `@Proofstone`, `@Matrix`, `@CellTest`, the dual-oracle assertions, verdicts and repro bundles.
- Paper, Folia, Purpur and Spigot cells (required rows), Velocity and BungeeCord topologies (required rows).
- Experimental: Fabric and NeoForge server-only mods, data packs, resource packs, Bedrock through Geyser (offline-stub).
- Self-tests: control run, differential run, flake detector, bad-test meta suite, mutation check for releases.

### Added
- Public repository files: security policy, code of conduct, support guide, issue templates, code owners, release note categories.
- Workflows ([ADR-0019](docs/adr/0019-public-repo-workflows.md)): `gates` on pull requests, `release` on a version tag.
- Documentation: compatibility and deprecation policy, limits, FAQ, branching and release flow.

