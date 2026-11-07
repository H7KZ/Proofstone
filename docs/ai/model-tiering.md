# Model tiering: who does what

Decisions: [ADR-0010](../adr/0010-ai-first-maintenance-model-tiering.md), as amended by
[ADR-0014](../adr/0014-haiku-first-and-minimal-ci.md). Model IDs: `claude-haiku-5-5`, `claude-sonnet-5-5`,
`claude-opus-5-5`.

## Short answer to "which model does the work?"

**Haiku 5.5, for everything.** Any model may take any card (ADR-0014). The `tier:` label on a card is a hint
for extra care and for escalation, not a restriction. Usage limits and billed Actions minutes make a
cheaper default the right trade-off.

The design is frozen first: the ADRs, specs and cards are the contract that cheap models execute against.

## Tiers

| Tier | Meaning | Typical cards |
|---|---|---|
| `haiku` | Default. Standard care. | scaffolding, Gradle conventions, schema-to-type generation, golden files, simple endpoints, conformance scenarios, docs, CI YAML, `versions.yml` rows, example projects, gotcha notes |
| `sonnet` | Subtle work: extra care (see below). | driver login and configuration phase, server-authoritative click protocol, Folia schedulers, Testcontainers lifecycle and topology, verdict engine, differential comparator, API freeze review, milestone reviews, refining epics |
| `opus` | Architecture-level judgement: extra care (see below). | ADR changes, the Bedrock spike, cross-cutting redesign, release go/no-go |

## Working a `sonnet` or `opus` card

Any model may take it. Before starting, and before each change:
- read the listed ADRs and spec sections in full, not only the parts the card quotes;
- take smaller steps, one behaviour at a time;
- run the real-server test (`:conformance:smoke` or the card's Verify command) after every change, locally,
  under the Docker lock (AGENTS.md section 4);
- stop and escalate if the behaviour surprises you (section below).

## Review policy

| PR touches | Author | Required reviewer |
|---|---|---|
| any card | any model | a second Haiku pass that reads the diff against the card and **re-runs the real-server tests** locally, with the results pasted into the PR |
| a card labelled `needs-escalation` | any | sonnet review, after the stronger model fixes it |
| ADRs | maintainer | maintainer decides; opus drafts only when asked |

Sonnet review is no longer mandatory for `proofstone-core`, `proofstone-protocol`, `drivers/*` or
`proofstone-api`. Those changes get the second Haiku pass with real-server evidence. Escalate to sonnet only
on `needs-escalation`.

## Escalation (labelled, not automatic)

Escalate by labelling the card `needs-escalation` and stopping. Do this when:
- the same gate fails twice after attempted fixes;
- the change needs files outside the card's `modules:`;
- a spec or ADR seems wrong or ambiguous;
- the diff passes about 400 lines (excluding tests and golden files).

Write a short handover in the issue: what you tried, the gate output, and your hypothesis. The next model
picks it up from there.

## Cost discipline

- Start sessions **from the card**, not from chat history. Each card is self-contained (links plus
  acceptance criteria).
- Prefer `Read` on the specific spec section the card links. Don't re-read the whole docs tree.
- Gates are the source of truth. "It compiles" is not done. "The card's Verify commands pass, with XML
  showing `skipped="0" failures="0" errors="0"`" is done.
- Batch commits and push to a PR branch as few times as possible (AGENTS.md section 4, CI cost).

## Where Haiku is likely to struggle, and the mitigation

| Risk | Mitigation built into the plan |
|---|---|
| Plausible but wrong protocol or driver code | Extra care on `sonnet` cards; real-server test after every change; second-pass review with evidence. |
| Weakening a test to pass | AGENTS.md hard rule, a review checklist item, and the control run catches vacuous tests. |
| Drifting from specs | Golden files and schema-generated types make drift a compile or test failure. |
| Over-large changes | Card diff cap, `modules:` scope, and the escalation rule. |
| Inventing APIs or versions | Pins only from `versions.yml` or the catalog; lint forbids floating versions. |
| Real-server evidence skipped or faked | Results are read from build/test-results XML, and a skip is reported as a skip. |
