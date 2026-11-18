# Security policy

## Reporting a vulnerability

Use **GitHub private vulnerability reporting**: on the repository, open **Security → Report a vulnerability**
(<https://github.com/H7KZ/Proofstone/security/advisories/new>). This opens a private advisory that only the maintainers
can see. Do **not** open a public issue, discussion or PR for a vulnerability.

Please include: the affected module and version, how to reproduce it, and the impact you see. A maintainer will acknowledge
within 7 days and will tell you the planned fix and release. Fixes for confirmed issues ship as a patch release and a
published advisory, and the reporter is credited unless they ask not to be.

## Supported versions

| Version | Supported |
|---|---|
| Latest `0.x` minor release | Security fixes |
| Older `0.x` releases | No. Upgrade to the latest |

Proofstone is `0.x` ([compatibility](docs/compatibility.md)). Once `1.0.0` is released, the latest two minor lines are supported.

## Scope

Proofstone is a test harness. It starts Docker containers, downloads server jars at runtime and opens local network ports.
Reports that are in scope:

- the harness running or leaking something it should not (secrets in logs or bundles, path traversal in file copy, unsafe
  deserialisation of an agent message, an agent that interferes with the server it observes);
- the release path: signing, the published artefacts and images, a workflow that exposes a secret;
- a published dependency with a known exploitable vulnerability that Proofstone makes reachable.

Out of scope: vulnerabilities in Minecraft, Paper, Fabric or other third-party servers themselves (report those upstream), and
the offline test authentication a Bedrock *offline-stub* cell uses on purpose ([Bedrock guide](docs/user/bedrock.md)).

## Hardening the harness gives you

Images and server builds are pinned by digest or checksum (`versions.yml`). Test inputs are copied into containers, never
bind-mounted. Secrets are never written to files by the harness, and `PROOFSTONE_ACCEPT_EULA` is read from the environment
only. Release secrets live in a protected GitHub environment ([releasing](docs/release.md)).
