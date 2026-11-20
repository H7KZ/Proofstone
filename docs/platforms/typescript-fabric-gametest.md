# Decision note: optional TypeScript front end and Fabric client gametest

Scope spike T-M10-17, 2026-11-20 ([card](../plan/tasks/M10-ecosystem.md)). **A spike records, it does not promote:** no ADR
changed, no `versions.yml` row, no protocol change, no code. The maintainer chooses; this note gives the options, a
recommendation per item and the evidence. Every claim is tagged **shown** (with the file or command, run on 2026-11-20 on the
Windows dev machine, nothing downloaded) or **guessed** (from memory or inference, not checked here).

## Summary

| Item | Recommendation | Effort if built | Trigger to reopen |
|---|---|---|---|
| TypeScript front end for the test API | **Drop from M10** (do not build) | 4 to 6 weeks for a usable front end (guessed) | A Bedrock (or other non-JVM) server a cell can host, plus a named author who writes TypeScript tests |
| Fabric client gametest (real client in the loop) | **Defer** (not drop); first card is a 2-day spike when triggered | spike 2 days; a `fabric-client` driver 2 to 4 weeks after a feasible spike (guessed) | A content-mod author who needs it, and maintainer approval for the client download and an Xvfb image |

## 1. TypeScript front end

### Who it would serve
- The epic named **Bedrock add-on authors on BDS** ([M7-M10-epics.md](../plan/tasks/M7-M10-epics.md), "An optional TypeScript test
  front end for Bedrock add-on developers"). **That audience has no cell:** the BDS spike found BDS refuses the Bedrock driver's
  unsigned login 5 of 5 and T-M10-02/03 are closed (shown: [bds.md](bds.md) "Verdict", [platforms.md](../platforms.md) BDS row).
- **Geyser cells** are tested by Java plugin authors through the Java API ([user/bedrock.md](../user/bedrock.md)); they already
  have their language (shown: the Geyser row runs from JUnit, `examples/geyser-bedrock`).
- **Third-party Bedrock servers** in the plan are PocketMine-MP (PHP), Nukkit (Java) and Endstone (C++ and Python)
  (guessed: the languages; the list is shown in [platforms.md](../platforms.md)). None of them makes TypeScript the authors'
  language. Their agents need the *wire protocol*, not a TypeScript *test API*.
- No issue, user or example asks for it (shown: before this note, `grep -rni typescript docs` outside `docs/research` found only
  the epic line, the card and [architecture.md](../architecture.md) section 2.5).

### What the wire protocol gives and what it does not
- The agent protocol is language neutral: 17 schema files, all JSON Schema draft 2020-12, parse with plain Node (shown:
  `node -e` over `proofstone-protocol/schemas/*.json` printed `17 [ 'https://json-schema.org/draft/2020-12/schema' ]`), plus 60
  golden JSON files (shown: `find proofstone-protocol -path '*golden*' -name '*.json' | wc -l`).
- **But the protocol is harness to agent only.** The harness itself (cell planning from `versions.yml`, Testcontainers hosts,
  ViaProxy, conformance, the `mcpl` and Bedrock drivers, dual-oracle assertions, verdicts) runs in-process inside the JUnit JVM
  through `ProofstoneExtension` (shown: `proofstone-core/src/main/java/.../core/junit/ProofstoneExtension.java`). `proofstone-core`,
  `proofstone-api` and `drivers/mcpl` main sources are about 19,400 lines of Java (shown: `wc -l` over their `src/main` Java files).
  A TypeScript test talks to none of that through the wire protocol.

### Options

| Option | How it talks to the JVM | Cost | Verdict |
|---|---|---|---|
| A. Re-implement the harness in TypeScript | directly to agents over the wire protocol | a second harness: cells, containers, drivers, conformance, oracles; every M1 to M9 rule twice | no: breaks "one framework" and doubles the AI-maintenance surface ([vision.md](../vision.md) principle 6) |
| B. Harness server: a JVM process that owns cells; a TypeScript client package drives it | a **new** RPC protocol (start cell, agent call, client call, verdict) next to the agent protocol | a second public API with its own semver ([ADR-0001](../adr/0001-java-junit5-public-api.md) says `proofstone-api` is the only public API), a Node toolchain in the build, an npm publishing channel (maintainer), a new protocol (AGENTS.md section 3) | the only workable shape; too much for no user |
| C. Generated TypeScript types plus a golden-file contract runner for agent authors | none (agent side only) | small (2 to 3 days, guessed) | not a test front end; useful only to a TypeScript *agent* (a BDS script pack), which has no cell |

Minimal surface for B, if it is ever built (guessed design): `cells(selector)`, `withCell(cell, fn)`, `agent.*` and `client.*`
mirroring test-api sections 3 and 4 as async calls, `DualAssert` as a server-side call so the two-oracle rule stays in Java, and
verdicts reported back as JUnit-style XML. The test runner would be `node --test` (Node 26 is on this machine, shown:
`node --version` = `v26.3.0`), so no new runner.

### Cost and risks (B)
- A second API to keep stable and documented, by the same AI-maintenance budget (guessed: roughly the size of `api-baseline.txt`,
  282 lines, shown).
- A Node toolchain in a Gradle monorepo (the repo already runs Node only in the `mineflayer-ref` sidecar image and `npx renovate`
  in `scripts/gates.sh`, shown) and a publish target that does not exist (npm; AGENTS.md: nothing publishes without the maintainer).
- The harness server is a long-lived process with its own lifecycle, timeouts and auth: a new failure mode for "no silent passes".

### Recommendation: drop from M10
Do not build. Record in [platforms.md](../platforms.md) and the ROADMAP that the TypeScript front end is dropped with the BDS
verdict. **Reopen** when both hold: a non-JVM server can be a cell (for example a BDS build that accepts an offline login, or a
NetherNet-capable offline client, per [bds.md](bds.md)), and a named author asks for TypeScript tests. The first card then is a
spike of option B's RPC over one cell, not the package.

## 2. Fabric client gametest

### What it is (shown from the pinned Fabric API and a cached Loom)
- Fabric API `0.166.2+26.3` (the `fabric@26.3` pin, sha256 `e8acacc7...02ea` matches `gradle/libs.versions.toml`, shown:
  `sha256sum`) declares `fabric-client-gametest-api-v1` `6.0.12+131f8d475d` and `fabric-gametest-api-v1` `4.0.36+3434d6d95d` as
  dependencies in its pom, but **does not nest them** in the runtime jar (shown: `unzip -l` lists 44 nested jars, none with
  "test"). They are test-only modules.
- Fabric Loom `1.17.21` (in the local Gradle cache, not used by this repo) has `GameTestSettings` with `enableClientGameTests`,
  `eula`, `username`, `clearRunDirectory`, a `clientGameTest` run in `build/run/clientGameTest` wired to `check`, the system
  property `fabric.client.gametest`, and an `XVFBExistsValueSource` that probes `xvfb-run --help` (shown: `javap` on the classes).
- Licences: Fabric API and Fabric Loader are Apache-2.0 (shown: `fabric.mod.json` and pom of Fabric API; `LICENSE_fabric-loader`
  in the loader jar). Loom is MIT (guessed). The **Minecraft client jar and assets are Mojang's**: no redistribution, the same rule
  as server jars (AGENTS.md section 3). No client jar is in the local cache; only the 26.3 server jar is (shown:
  `com.mojang.piston-data/server/33680f5f...`).
- How the client gametest connects (guessed, from memory, not checked here): tests run inside a real client JVM with a
  `ClientGameTestContext` (input, screens, waiting on ticks, screenshots); worlds are singleplayer (an integrated server) or a
  dedicated server the test framework starts in-process. Joining an *external* server from a client gametest is not known to be a
  supported path.

### Does it fit "real server, real client"?
Partly. It is a real client, but its server is in-process and has no Proofstone agent, so there is no server oracle and no
dual-oracle verdict ([ADR-0002](../adr/0002-server-truth-via-agent.md), [ADR-0003](../adr/0003-dual-oracle.md)). Rendering asserts
are a [vision](../vision.md) non-goal ("Client-side mods with rendering"). Proofstone adds nothing to a mod author's own
`runClientGameTest` unless the client joins a **Proofstone cell**.

### Where it would matter: the gap mcpl cannot close
The `mcpl` driver is kicked from a Fabric server as soon as a mod adds a synced registry entry ("This server requires Fabric
Loader and Fabric API installed on your client!", 3 of 3; shown: [fabric.md](fabric.md) "Content mods"). So every content mod
(blocks, items) is out of reach today. A real Fabric client with the mod loaded is the only route known; fabric.md already names
"a separate ADR (a Fabric client driver)".

### Options

| Option | What Proofstone does | Fits ADR-0002/0003 | Cost |
|---|---|---|---|
| D. Docs only: point mod authors at Fabric's own client gametest | nothing | n/a | hours |
| E. `fabric-client` driver: a `ClientDriver` backed by a real Fabric 26.3 client (user's mod in its `mods`) in a driver host container under Xvfb, controlled over JSON-RPC like `mineflayer-ref` and the Bedrock driver host; it joins a normal `fabric@26.3` cell | the client side of a dual-oracle test against an agent-observed dedicated server | yes | spike 2 days; driver 2 to 4 weeks (guessed) |
| F. Run the author's client gametest against a cell and read only the agent | the server oracle only | no (single oracle) | about 1 week (guessed) |

E is the only option that keeps the two oracles. Client gametest's in-client API could be the control loop inside E's client, or
a small client mod could be (decided by the spike).

### Headless feasibility (unverified)
- The client needs a window and OpenGL. In a container that means Xvfb plus software GL (Mesa llvmpipe) (guessed). Loom probing
  `xvfb-run` suggests Fabric runs these under Xvfb on Linux CI (inference from the shown class).
- No Xvfb exists on this machine (shown: `which xvfb-run Xvfb` found nothing); the pinned `itzg/minecraft-server` image is a
  server image (guessed: no X or GL libraries). So E needs **a new image**, which needs the maintainer (M10 rules).
- The client download (jar plus asset index and assets) happens at runtime from Mojang, pinned by content like the vanilla
  server jar ([ADR-0017](../adr/0017-mod-platform-packs.md) section 6 exception); size and boot time unknown (guessed: hundreds of
  MB of assets, 30 to 60 s boot under software GL).
- Offline mode: the cell is offline, so an offline username is enough (guessed: the client accepts `--username` without a
  Microsoft login for a direct join; Loom passes `--username Player0`, shown in `FabricApiTesting`).

### Risks
- Software GL boot time and flakiness against the Flake rule (three green invocations) are unknown.
- A Mojang client binary at runtime adds a second EULA surface (the client, not only the server).
- Client internals churn per Minecraft version; Fabric's client gametest API is young (`v1` at `6.0.x`) (guessed: churn rate).
- Newest-row only, like the server-side Fabric pack (shown: [fabric.md](fabric.md) "Stonecutter: out for M8").

### Recommendation: defer
Keep the [platforms.md](../platforms.md) row "Client-side mods ... later". Do not build now: no content-mod author has asked, and
the first step needs two maintainer approvals (a new image with Xvfb, a runtime Mojang client download). **First card when
triggered** (tier opus, needs maintainer decision):

> **T-Mx-01 · Spike: a real Fabric client in a driver host joins `fabric@26.3` with a content mod.** Build the Fabric 26.3 client
> with Fabric API and the existing spike content mod (`proofstone_spike:probe_item`) in a container under Xvfb; record image,
> client pin (URL, sha256, size), boot time, and whether it joins the cell 3 of 3 where `mcpl` is kicked; send one chat and read
> one inventory slot through a JSON-RPC stub. Verdict: feasible or not. No `versions.yml` row, no driver code in `drivers/`.

## Proposed ADR text (not filed; for the maintainer)

> **ADR-00xx: Optional front ends and clients after M10.** Status: Proposed.
> **Decision.** (1) No TypeScript test front end is built; `proofstone-api` stays the only public API (ADR-0001). It is
> reconsidered only when a non-JVM server can be a cell and an author asks for it; the shape then is a JVM harness server with a
> thin client, never a second harness. (2) Fabric client gametest is not integrated as a framework. A real modded client, if
> built, is a `ClientDriver` (`fabric-client`) in a driver host container that joins ordinary loader cells, so both oracles hold
> (ADR-0002, ADR-0003); rendering stays out of scope. It starts with a spike that needs maintainer approval for an Xvfb image and
> a runtime Mojang client download.
> **Consequences.** Content mods stay untestable until that spike runs. No Node toolchain or npm channel enters the build.

## Open questions for the maintainer
1. Drop or keep the TypeScript item in the M10 epic text (this note recommends drop with a trigger)?
2. Is a content-mod user expected soon enough to schedule the Fabric client spike, and would a new Xvfb image and a runtime
   Mojang client download be acceptable in principle?
3. Should the D option (a docs pointer to Fabric's own client gametest for rendering tests) go into
   [mods-and-packs](../user/mods-and-packs.md) now?
