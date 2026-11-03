# ADR-0010: AI-first maintenance with model tiering

- Status: Accepted
- Date: 2026-11-03

## Context
- The framework is large: many platforms, versions and moving dependencies. Maintaining it by hand is
  not realistic, so AI agents will do most of the work.
- The maintainer wants to start on a cheap model (Claude Haiku 5.5) to control cost.

## Decision
- The repository is built to be maintained by AI agents:
  - `AGENTS.md`;
  - ADRs;
  - specs with golden files;
  - small modules, each with a README;
  - task cards with explicit acceptance criteria and verify commands;
  - gates that fail loudly.
- Work is tiered by risk, not by size ([ai/model-tiering.md](../ai/model-tiering.md)):
  - **Opus** writes the design: this ADR set, the specs and the roadmap, frozen up front.
  - **Haiku 5.5** is the default executor for cards marked `tier: haiku`. These are well-specified,
    local and gate-verifiable.
  - **Sonnet 5.5** handles cards marked `tier: sonnet`: concurrency, protocol, lifecycle, the SPI design
    details. It also reviews every PR that touches `core`, `protocol` or `drivers`.
  - Escalation is automatic: if a gate fails twice, or a card touches more than its declared modules, the
    card is labelled `needs-escalation`.

## Consequences
- The expensive design thinking happens once, and cheap models execute against frozen contracts.
- Gates (conformance, golden files, control runs) are what make cheap-model output trustworthy. They
  must be built **first** (M0/M1), before bulk feature work.

## Alternatives considered
- **Haiku for everything from day one:** foundation mistakes compound. Rejected. Haiku is used from day
  one, but only on `tier: haiku` cards.
- **Strongest model for everything:** too costly for routine version bumps and docs. Rejected.
