# ADR-0011: Licensing

- Status: **Proposed**. The maintainer must decide before the first public release (card T-M6-06).
- Date: 2026-11-03

## Context
- Dependencies:
  - MCProtocolLib: MIT.
  - Testcontainers: MIT.
  - JUnit 5: EPL-2.0.
  - ViaProxy: GPL-3.0. We only run it as a separate container image, unmodified, pulled by digest.
  - Mineflayer: MIT.
- Adoption by plugin authors works best with a permissive licence.

## Proposal
- **Apache-2.0** for all Testificate code: it is permissive and includes an explicit patent grant.
  **MIT** is an equally valid choice.
- No server, client or proxy binaries are redistributed. Everything is downloaded at runtime from the
  official source, pinned by checksum.
- The Minecraft EULA is accepted only through explicit user opt-in (`acceptEula = true` or
  `TESTIFICATE_ACCEPT_EULA=true`).

## Open
- Apache-2.0 or MIT?
- Is a CLA or DCO needed for community packs? The proposal is DCO: a lightweight sign-off.
