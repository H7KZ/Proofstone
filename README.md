# Proofstone

**Real-server end-to-end testing for Minecraft plugins, mods and add-ons.**
Real servers, real clients, and every result checked twice.

> Status: **design phase**. This repository contains only the plan so far: decisions, specs, a roadmap and
> task cards. There is no code yet. See [docs/plan/ROADMAP.md](docs/plan/ROADMAP.md).

## Why

Unit-level tools like MockBukkit can't see NMS code, real inventories, packets or what a client actually
sees. Proofstone starts a **real server** (Paper, Folia, Spigot, Purpur, Velocity, Fabric, NeoForge and
Bedrock later) with your jar, connects a **real protocol client** as a player, and checks the outcome
through **two independent oracles**:

- the server's own state, read through an in-server agent;
- what the client actually received.

It runs across every server version you support, from one test written once:

```java
@Proofstone(plugins = "build/libs/MyPlugin-*.jar", acceptEula = true)
@Matrix(platform = PAPER, versions = {"1.8.8", "1.18.2", "1.21.11", "latest"})
class AnvilE2E {
    @CellTest
    void disenchant(Agent agent, Client player) {
        agent.player(player).reset().level(30)
             .give(Item.of("diamond_sword").enchant("sharpness", 5))
             .give(Item.of("book"));
        // ...the client opens a real anvil, clicks real slots, and both oracles are asserted
    }
}
```

## Principles

1. **Correct beats fast.** Every result is checked twice. There are no sleeps and no automatic retries,
   and a flaky test is treated as a bug.
2. **The harness proves itself first.** Self-tests, control runs and differential runs show that a green
   result means something.
3. **Everything is pinned.** Upgrades arrive only as automated PRs, gated by self-tests.
4. **Adding a version or a platform is data or a pack, not a rewrite.**
5. **AI agents can maintain it.** The repo has small modules, explicit contracts, task cards and gates that
   fail loudly.

## Name

*Proofstone* combines **proof**, meaning verified and bulletproof, with **-stone**, Minecraft's naming
pattern for special blocks: redstone, glowstone, lodestone. It also echoes the *touchstone*, the stone
once used to test whether gold was pure. The name puts robustness first, and it avoids the "Minecraft"
trademark. ([ADR-0012](docs/adr/0012-name.md))

## Where to read next

| You want | Go to |
|---|---|
| The big picture | [docs/vision.md](docs/vision.md), [docs/architecture.md](docs/architecture.md) |
| Why a decision was made | [docs/adr/](docs/adr/) |
| How risks are covered | [docs/robustness.md](docs/robustness.md) |
| What will be supported | [docs/platforms.md](docs/platforms.md) |
| How it gets built | [docs/plan/ROADMAP.md](docs/plan/ROADMAP.md), [docs/plan/tasks/](docs/plan/tasks/) |
| How AI agents work here | [AGENTS.md](AGENTS.md), [docs/ai/](docs/ai/) |
| The founding research and prototype | [docs/research/](docs/research/) |

## Licence

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE). Contributions use a DCO sign-off, see
[CONTRIBUTING.md](CONTRIBUTING.md). Decision record: [ADR-0011](docs/adr/0011-licensing.md).
