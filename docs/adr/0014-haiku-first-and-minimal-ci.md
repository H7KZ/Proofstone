# ADR-0014: Haiku-first execution and minimal CI

- Status: Accepted (amends [ADR-0010](0010-ai-first-maintenance-model-tiering.md); not a full supersession)
- Date: 2026-11-07
- Deciders: maintainer

## Context
- Agent usage limits are tight, and Haiku 5.5 is the cheapest capable model. ADR-0010 reserved work by
  `tier` and made sonnet review mandatory for `proofstone-core`, `proofstone-protocol`, `drivers/*` and
  `proofstone-api`. Under the current limits that routing costs more than the maintainer can spend.
- GitHub Actions minutes are billed to the maintainer. The `smoke`, `matrix` and `version-watch` jobs are
  expensive, and running them on every PR is not affordable.
- Real-server evidence can be produced locally, with Docker runs serialized, and pasted into the PR.

## Decision
1. **Default model is Haiku 5.5 for all work.** Any model may take any card. The `tier:` label is a hint,
   not a restriction.
2. **`sonnet` and `opus` cards get extra care, not a different model.** The model reads the listed ADRs and
   spec sections in full, takes smaller steps, and runs the real-server test after every change.
3. **Review is a second Haiku pass** that reads the diff against the card and re-runs the real-server tests
   locally. **Sonnet review is required only when a card is labelled `needs-escalation`.**
4. **Escalation is labelled, not automatic.** A card is labelled `needs-escalation` when the same gate fails
   twice, the change leaves its declared `modules:`, a spec or ADR seems wrong, or the diff passes about 400
   lines. Escalation is the only route to a stronger model.
5. **Minimal CI.** `ci.yml` runs `check docsCheck` and `renovate-validate` on pull requests only. `smoke`,
   `matrix` and `version-watch` are `workflow_dispatch` only and are never triggered by agents. No scheduled or
   default-on workflow is added without maintainer approval. Required checks are `gates` and
   `renovate-validate`.
6. **Docker runs are serialized.** Only one Docker or Testcontainers Gradle run at a time, under a lock; Docker
   Desktop breaks otherwise. `PROOFSTONE_ACCEPT_EULA=true` is set per command in the environment and is never
   written to a file.
7. **Evidence is read from the XML.** A result counts only when every suite shows `skipped="0" failures="0"
   errors="0"`. A skip is never reported as a pass.

## Consequences
- Lower cost. Foundation cards (protocol, driver, lifecycle) are now done by the cheap model, so their risk
  rises. The mitigations are the real-server test after every change, the second-pass review with evidence,
  and labelled escalation.
- CI no longer runs the real-server smoke test on every PR. The evidence lives in the PR text, produced by the
  author on their own machine. Reviewers check it, and the maintainer can run `smoke` by hand.
- Required checks shrink to `gates` and `renovate-validate`.
- [AGENTS.md](../../AGENTS.md) and [docs/ai/model-tiering.md](../ai/model-tiering.md) are updated to match. The
  rest of ADR-0010 stands: specs, golden files, cards, gates and the escalation idea.

## Alternatives considered
- **Keep ADR-0010 as written** (sonnet on `tier: sonnet` cards, sonnet review on core paths). Rejected: the
  usage limits make that routing unaffordable.
- **Run `smoke` and `matrix` on every PR in CI.** Rejected: the Actions minutes are billed to the maintainer.
- **Stronger model for everything.** Rejected: the cost is too high for routine cards and docs.
- **Haiku only on `tier: haiku` cards** (ADR-0010). Rejected: this restriction is what forces the sonnet
  usage that the limits cannot pay for.

## Update 2026-11-08: CI removed

The maintainer removed `ci.yml` and the required status checks (`gates`, `renovate-validate`) from branch protection, to stop all
billed Actions minutes. Point 5 above no longer holds: there is no PR workflow. The local gate is `scripts/gates.sh`
(AGENTS.md section 4). `matrix`, `nightly` and `version-watch` remain manual-only.
