# Contributing

Proofstone is planned to be maintained mostly by AI coding agents, with humans steering.

- **Everyone, human or agent, follows [AGENTS.md](AGENTS.md).**
- **Work comes from task cards.** See [docs/plan/](docs/plan/) and [docs/ai/workflow.md](docs/ai/workflow.md).
- **Decisions live in [ADRs](docs/adr/).** Challenge one by opening an issue; don't change it inside a PR.
- **Adding a Minecraft version** is a data change. See [docs/versioning.md](docs/versioning.md).
- **Adding a platform** means writing a platform pack. See [docs/architecture.md §2.3](docs/architecture.md)
  and [docs/platforms.md](docs/platforms.md).
- **Licence:** Apache-2.0 ([LICENSE](LICENSE), [NOTICE](NOTICE), [ADR-0011](docs/adr/0011-licensing.md)).

## Licence and sign-off (DCO)

Your contribution is licensed under Apache-2.0, the same as the project (Apache-2.0, section 5; ADR-0011).

Every commit needs a `Signed-off-by:` line. It certifies the [Developer Certificate of Origin 1.1](https://developercertificate.org/):
you wrote the change, or otherwise have the right to submit it under the project's licence, and you accept that it is
public under that licence. Add it with `git commit -s`, which writes `Signed-off-by: Your Name <you@example.com>` from your
git identity. There is no bot and no extra tool to install. A `Co-Authored-By:` trailer does not replace the sign-off.
