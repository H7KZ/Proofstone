# Model tiering: who does what

Decision: [ADR-0010](../adr/0010-ai-first-maintenance-model-tiering.md). Model IDs: `claude-haiku-5-5`,
`claude-sonnet-5-5`, `claude-opus-5-5`.

## Short answer to "can we start on Haiku?"

**Yes, as long as two conditions hold.**
1. **The design is frozen first.** That is these ADRs, specs and cards, which have now been written.
2. **Haiku takes only `tier: haiku` cards.** A few foundation cards are `tier: sonnet`, because a subtle
   mistake there compounds: the driver's click/resync logic, the Folia scheduling, the JUnit lifecycle and
   the verdict engine.

41 of the 66 cards in M0–M6 (62%) are Haiku cards. The share grows after v0.1: version rows, platform
docs, conformance scenarios and community-pack scaffolding are all Haiku work.

## Tiers

| Tier | Model | Typical cards | Why |
|---|---|---|---|
| `haiku` | Haiku 5.5 | scaffolding, Gradle conventions, schema-to-type generation, golden files, simple endpoints, conformance scenarios, docs, CI YAML, `versions.yml` rows, example projects, gotcha notes | Local, fully specified, verifiable by gates |
| `sonnet` | Sonnet 5.5 | driver login and configuration phase, server-authoritative click protocol, Folia schedulers, Testcontainers lifecycle and topology, verdict engine, differential comparator, API freeze review, milestone reviews, refining epics | Concurrency, protocol, lifecycle: subtle bugs pass casual tests |
| `opus` | Opus 5.5 | ADR changes, the Bedrock spike, cross-cutting redesign, release go/no-go | Rare. Architecture-level judgement. |

## Review policy

| PR touches | Author tier | Required reviewer |
|---|---|---|
| `docs/`, `examples/`, `versions.yml` rows, conformance scenarios | haiku | gates only; a human or haiku review is optional |
| `platforms/*` agents (non-core endpoints) | haiku | sonnet review |
| `proofstone-core`, `proofstone-protocol`, `drivers/*`, `proofstone-api` | any | **sonnet review is mandatory**; opus for API breaks |
| ADRs | — | maintainer + opus |

## Escalation (automatic)

A Haiku session must **stop and label the card `needs-escalation`** in any of these cases:
- the same gate fails twice after attempted fixes;
- the change needs files outside the card's `modules:`;
- a spec or ADR seems wrong or ambiguous;
- the diff passes about 400 lines (excluding tests and golden files).

Write a short handover in the issue: what you tried, the gate output, and your hypothesis. The next tier
picks it up from there.

## Cost discipline

- Start sessions **from the card**, not from chat history. Each card is self-contained (links plus
  acceptance criteria).
- Prefer `Read` on the specific spec section the card links. Don't re-read the whole docs tree.
- Gates are the source of truth. "It compiles" is not done. "The card's Verify commands pass" is done.

## Where Haiku is likely to struggle, and the mitigation

| Risk | Mitigation built into the plan |
|---|---|
| Plausible but wrong protocol code | Driver cards are `sonnet`. Haiku writes only the conformance scenarios that test them. |
| Weakening a test to pass | AGENTS.md hard rule, a review checklist item, and the control run catches vacuous tests. |
| Drifting from specs | Golden files and schema-generated types make drift a compile or test failure. |
| Over-large changes | Card diff cap, `modules:` scope, and the escalation rule. |
| Inventing APIs or versions | Pins only from `versions.yml` or the catalog; lint forbids floating versions. |
