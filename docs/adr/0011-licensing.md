# ADR-0011: Licensing

- Status: **Accepted**.
- Licence: **Apache-2.0**
- Date: 2026-11-03 (proposed). Accepted 2026-11-09.
- Source: maintainer, "do it all" after reading the proposal below (T-M6-06).

## Decision

- **Apache-2.0** for all Proofstone code, with the `LICENSE` file at the repository root, unmodified.
- **NOTICE** file at the repository root (Proofstone copyright line and third-party pointers).
- **DCO sign-off** on contributions (`Signed-off-by:` on each commit). Summary in [CONTRIBUTING.md](../../CONTRIBUTING.md). No CLA.
- Publish metadata: the POM licence is set from this ADR by `build-logic/src/main/kotlin/proofstone.publish.gradle.kts`,
  and only while the Status line above says Accepted.
- Not changed by this decision: no binaries are redistributed, ViaProxy stays a separate container, and the Minecraft
  EULA is accepted only by explicit opt-in.

## Context
- Dependencies:
  - MCProtocolLib: MIT.
  - Testcontainers: MIT.
  - JUnit 5: EPL-2.0.
  - ViaProxy: GPL-3.0. We only run it as a separate container image, unmodified, pulled by digest.
  - Mineflayer: MIT.
- Adoption by plugin authors works best with a permissive licence.

## Proposal
- **Apache-2.0** for all Proofstone code: it is permissive and includes an explicit patent grant.
  **MIT** is an equally valid choice.
- No server, client or proxy binaries are redistributed. Everything is downloaded at runtime from the
  official source, pinned by checksum.
- The Minecraft EULA is accepted only through explicit user opt-in (`acceptEula = true` or
  `PROOFSTONE_ACCEPT_EULA=true`).

## Open
- ~~Apache-2.0 or MIT?~~ Answered 2026-11-09: Apache-2.0 (see Decision).
- ~~Is a CLA or DCO needed for community packs?~~ Answered 2026-11-09: DCO.
- Lawyer review of the points below before the first public release (still open).

## Proposal for the maintainer (T-M6-99, 2026-11-09)

Prepared for the decision. The maintainer accepted the recommendation on 2026-11-09 (see Decision). The text below
is kept as the record of the trade-off. This section is an engineering comparison, not legal advice. Have a lawyer
check any point that matters for a release.

### Facts from the repository

- Shipped code: the four published modules (`proofstone-api`, `-core`, `-protocol`, `drivers:mcpl`) and the Bukkit agent
  (`platforms/bukkit/agent`). The agent shades and relocates Jackson (`io.github.h7kz.proofstone.shaded.jackson`, Apache-2.0; renamed from `dev.proofstone.*` in T-M6-12). Shaded
  code keeps its own licence and notice duties.
- Compile-only, never shaded: Spigot API (agent, fixture) and Paper API (example plugin, from `repo.papermc.io`).
  The Paper and Spigot API licences were **not checked** in this session.
- Not redistributed: ViaProxy (GPL-3.0, separate container, pulled by digest; ADR-0004 says it is not linked), the Paper and
  Spigot server jars (downloaded at run time), the Minecraft client and server (EULA, ADR-0011 Proposal). The
  spigot-buildtools image bundles `BuildTools.jar` and is **not pushed** (docs/release.md), which avoids redistributing it.
- Test-only dependencies: JUnit 5 (EPL-2.0), Testcontainers (MIT), MCProtocolLib (MIT), Mineflayer (MIT). None is shaded
  into a published artefact (to be re-checked in T-M6-01 before the first publish).
- Trademark: the name avoids "Minecraft" (ADR-0012).
- At proposal time, `CONTRIBUTING.md` said "Licence and sign-off: pending" and no `LICENSE`, `NOTICE` or DCO text existed.
- `docs/release.md` requires the POM licence entry, and refuses `--publish` until ADR-0011 is Accepted.

### Apache-2.0 vs MIT

| Point | Apache-2.0 | MIT |
|---|---|---|
| Patent grant from contributors | Explicit (section 3), ends for a party that sues over the work | None written. Relies on the general law |
| Notice duties | Keep the `LICENSE` and any `NOTICE` text in redistributions. A `NOTICE` file is optional but is the usual place for attributions | Keep the copyright and licence notice only |
| Shaded Jackson (Apache-2.0) | Same licence family, same notice habit | Fine, but the Jackson notice still has to travel with the jar |
| Contributor terms | Section 5: contributions are under the same licence unless stated otherwise. A DCO sign-off is still advisable | No clause. A DCO sign-off is needed for the same effect |
| Compatibility with GPL-3.0 code in a combined work | Compatible with GPLv3 | Compatible with GPLv3 |
| Maven Central | Needs name, url and licence in the POM, plus sources, javadoc and signatures. Same for both | Same |
| Length and familiarity | Longer. Familiar to enterprise legal teams | Short. Familiar to most plugin authors |

Neither choice blocks the current design. ViaProxy stays a separate container either way. The decision only changes the
patent language and the notice file.

### Points to settle with either licence

1. **DCO or CLA.** ADR-0011 says DCO (lightweight sign-off, `Signed-off-by:` on each commit). A CLA would add a legal step
   for contributors and is not needed for a project this size. Record the choice in `CONTRIBUTING.md`.
2. **NOTICE file.** Under Apache-2.0, add it if third-party attributions are needed (for example the Jackson notice in the
   shaded agent jar). Check the upstream notice texts at shade time.
3. **Paper and Spigot API.** The Proofstone artefacts do not contain them. Confirm that no published jar includes classes
   from either API before the first publish, and check their licence terms with a lawyer.
4. **ViaProxy (GPL-3.0).** Pulled as a digest-pinned image, not redistributed, not linked. Keep it that way. If that ever
   changes (for example a bundled image), GPL duties apply to the bundle.
5. **Minecraft EULA.** Not a software licence. Proofstone never accepts it for the user (ADR-0011, `PROOFSTONE_ACCEPT_EULA`).

### Recommendation (not a decision)

**Apache-2.0 for all Proofstone code, plus a DCO sign-off on contributions, plus a short `NOTICE` file.**

Reasons: a test framework is adopted by companies as much as by hobby plugin authors, and an explicit patent grant helps
them. The project already ships Apache-2.0 code (Jackson, shaded), so the notice habit is in place. The cost is a longer
licence text, which is standard and does not affect users.

MIT is a fair alternative if the maintainer prefers the shortest text and accepts the implicit patent position. Either way,
the maintainer must:
- choose one, and set `decidedLicence` in `build-logic/src/main/kotlin/proofstone.publish.gradle.kts` in the same PR;
- add `LICENSE` (and `NOTICE` if chosen), and update `CONTRIBUTING.md` with the DCO text;
- change Status to Accepted and record the date.

Done on 2026-11-09 (T-M6-06): Apache-2.0 chosen, `LICENSE` and `NOTICE` added, DCO in `CONTRIBUTING.md`, Status
set to Accepted, and the POM licence set from this ADR. Publishing still needs the namespace check (ADR-0012) and the
steps in `docs/release.md`.
