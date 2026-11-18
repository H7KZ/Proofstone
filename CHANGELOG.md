# Changelog

All notable changes are listed here, newest first. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versioning: [Semantic Versioning](https://semver.org/spec/v2.0.0.html) with the 0.x rules in [compatibility](docs/compatibility.md).

## [Unreleased]

## [0.1.0] - 2026-11-18

First public release. It is `0.x`: a minor release may break the public API ([compatibility](docs/compatibility.md)). Scope, evidence
and known problems: [release notes](docs/release-notes-0.1.0.md).

### Added
- The test harness: `@Proofstone`, `@Matrix`, `@CellTest`, the dual-oracle assertions, verdicts and repro bundles.
- Paper, Folia, Purpur and Spigot cells (required rows), Velocity and BungeeCord topologies (required rows).
- Experimental: Fabric and NeoForge server-only mods, data packs, resource packs, Bedrock through Geyser (offline-stub).
- Self-tests: control run, differential run, flake detector, bad-test meta suite, mutation check for releases.

### Added
- Public repository files: security policy, code of conduct, support guide, issue templates, code owners, release note categories.
- Workflows ([ADR-0019](docs/adr/0019-public-repo-workflows.md)): `gates` on pull requests, `release` on a version tag.
- Documentation: compatibility and deprecation policy, limits, FAQ, branching and release flow.

