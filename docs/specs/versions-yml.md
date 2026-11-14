# Spec: `versions.yml`

Decision: [ADR-0008](../adr/0008-versions-yml-single-source-of-truth.md), amended by
[ADR-0013](../adr/0013-host-digest-per-java-tag.md) (host digest per Java tag). A JSON Schema for validation,
`proofstone-protocol/schemas/versions.schema.json`, is created in T-M0-04.

```yaml
schema: 2                                  # file format version
java-rules:                                # first match wins
  - { range: ">=1.20.5 <26", image: java21 }
drivers:
  mcpl:           { library: "<g:a>", version: "<pinned>", protocol: "<mc version>" }
  mineflayer-ref: { library: "mineflayer", version: "<pinned>", max-native: "<mc version>", image: "<repo>", digest: "sha256:…" }  # image and digest optional, written by release-images.sh --write-digests (T-M6-02)
  viaproxy:       { image: "<repo>", digest: "sha256:…" }
platforms:
  <platform-id>:
    host:
      image: "<repo>"
      digests:                             # one digest per Java tag (ADR-0013); a key per tag the rules can assign
        java21: "sha256:…"
    rows:
      - mc: "1.21.11"                      # Minecraft version (string; 26.x style accepted)
        build: 132                         # platform build number
        sha256: "…"                        # server jar checksum; spigot: JarDigest content digest (see below)
        java: java21                       # optional override of java-rules
        protocol: 774                      # optional; filled by automation
        status: required | experimental | retired
        native-drivers: [mcpl]             # filled by automation from driver metadata
        observed: "free text"              # last notable observation
        notes: "free text"
```

Built platforms (spigot, T-M5-05): a BuildTools jar differs between builds in Maven's build-time comment in
`pom.properties`, so a spigot row's `sha256` is a content digest, SHA-256 over every jar entry name and content with that
comment line removed (`JarDigest`, [spigot.md](../platforms/spigot.md)). It is strict: any other change fails the cell.

Validation rules, enforced in `check`:
- `mc` values are unique within a platform.
- `required` rows have no `TODO` values.
- `host.digests` has a key for every `image` named in `java-rules` and every row `java` override. A `required`
  row's tag has a real digest, not `TODO`.
- `drivers.viaproxy.digest` is a real digest before any cell is bridged; core fails on `TODO` (client-driver §5).
- Every row's Java tag resolves to an image that exists (an online check, run nightly only).
- `latest` always resolves to the highest `required` row.

A cell takes the digest of its row's Java tag (`java` override, else the first matching `java-rules` entry). A tag
with no digest is an error that names the tag and the row, never a fallback to another tag.

## Schema 3: proxies (additive, ADR-0016)

Decision: [ADR-0016](../adr/0016-topology-cells.md). Schema 3 adds one optional top-level key, `proxies`, next to
`platforms`. Nothing in schema 2 changes meaning: a schema 2 file is a valid schema 3 file, the `platforms` section is
read exactly as before, and the single-server cells it plans (and their `printMatrix` lines) stay identical. The
values below are the T-M7-01 spike pins, which `versions.yml` carries since T-M7-08 as `experimental` rows.

```yaml
schema: 3
proxies:
  velocity:
    host:
      image: "itzg/mc-proxy"
      digests:                             # per Java tag, as ADR-0013
        java25: "sha256:8efdaa9d990f4c509f21b7ced003dd959060777784422fd72fdd537d613c1813"
    forwarding: modern                     # one mode per proxy platform in M7: modern | legacy
    rows:
      - version: "4.3.1"                   # the proxy's own version (string)
        build: 45                          # the build the image downloads
        sha256: "0844bcab…"                # jar checksum, checked in the container before start
        java: java25                       # REQUIRED: java-rules are keyed on Minecraft versions, not proxies
        status: experimental               # required | experimental | retired
        backends:                          # the backend rows this proxy row is paired with
          - { platform: paper, mc: "26.3", status: experimental }
        observed: "free text"
        notes: "free text"
  bungeecord:
    host: { image: "itzg/mc-proxy", digests: { java21: "sha256:4e6901b4…" } }
    forwarding: legacy
    rows:
      - { version: "2111", build: 2111, sha256: "1d9dbc31…", java: java21, status: experimental,
          backends: [ { platform: paper, mc: "26.3", status: experimental } ] }
```

- **Topology cell:** one entry of a row's `backends`, written `<proxy>@<version>+<platform>@<mc>`
  (`velocity@4.3.1+paper@26.3`). Its status is the weakest of the proxy row, the pairing and the backend row
  (`retired` < `experimental` < `required`). `pr` scope plans the `required` ones, `nightly` also the `experimental`
  ones, as for single-server rows. `latest` on either half resolves as it does for that platform; the resolved pairing
  must be listed, or the selector is an error.
- **BungeeCord** has no release version, so `version` is the Jenkins build number as a string. Jenkins publishes no
  sha256: the pin is computed from the download.

Validation rules for `proxies`, enforced by `VersionsCatalog.load` (run by every `:proofstone-core:test` against the real file, so by `check`) since T-M7-08. A `proxies` key in a `schema: 2` file is an error:
- A proxy id is not also a `platforms` id. `version` values are unique within a proxy.
- `java` is present on every row, and `host.digests` has a key for it. A `required` row has a real digest, no `TODO`.
- Every `backends` entry names an existing `platforms` row, and no pairing is listed twice.
- A `required` pairing needs a `required` proxy row and a `required` backend row.
- A pairing is native only: the backend row's protocol equals the driver's (`drivers.mcpl.protocol`). A bridged
  topology is refused (ADR-0016 section 3).
- `forwarding` is one of `modern` or `legacy`, and the proxy supports it (BungeeCord: `legacy` only).

## Schema 4: loaders (additive, ADR-0017)

Decision: [ADR-0017](../adr/0017-mod-platform-packs.md). Schema 4 adds one optional top-level key, `loaders`, next to
`platforms` and `proxies`. A schema 3 file is a valid schema 4 file, `platforms` and `proxies` are read exactly as before,
and every existing cell's `printMatrix` line stays identical. The values below are the T-M8-01 spike pins
([fabric.md](../platforms/fabric.md), [neoforge.md](../platforms/neoforge.md)); T-M8-04 added them to `versions.yml` as
`experimental` rows, with `versions.schema.json` accepting `schema: 4` and the `loaders` key.

```yaml
schema: 4
loaders:
  fabric:
    host:
      image: "itzg/minecraft-server"       # TYPE=FABRIC
      digests: { java25: "sha256:e2fabc53…" }   # per Java tag, as ADR-0013 (the digest Paper 26.x pins)
    rows:
      - mc: "26.3"
        loader: "0.20.1"                   # Fabric Loader version
        installer: "1.1.2"                 # Fabric installer version the launcher jar is built for
        sha256: "70446947…"                # the server launcher jar (meta.fabricmc.net); computed from the download
        vanilla-sha256: "TODO"             # the vanilla server jar the launcher fetches; checked in the container (T-M8-07)
        fabric-api: { version: "0.166.2+26.3", sha256: "TODO" }   # required on every Fabric row (ADR-0017 §5)
        java: java25                       # optional; java-rules apply (keyed on mc)
        protocol: 777
        status: experimental
        native-drivers: [mcpl]
        observed: "free text"
        notes: "free text"
  neoforge:
    host: { image: "itzg/minecraft-server", digests: { java25: "sha256:e2fabc53…" } }   # TYPE=NEOFORGE
    rows:
      - { mc: "26.3", loader: "26.3.0.103", sha256: "d8a92e17…", vanilla-sha256: "TODO",
          protocol: 777, status: experimental, native-drivers: [mcpl] }
```

- **Loader cell:** one row, written `<loader>@<mc>` (`fabric@26.3`). `pr` scope plans the `required` rows, `nightly` also
  the `experimental` ones, as for `platforms`. `latest` is the highest `required` row; with none, `latest` is an error
  naming the loader (name the version).
- The `TODO` values above are not yet run-backed: `vanilla-sha256` comes from T-M8-07's first install run, and the Fabric
  API sha256 from the download T-M8-07 makes (the T-M8-01 spike recorded only a truncated vanilla sha256 and no Fabric API
  hash, so T-M8-04 left both `TODO` in `versions.yml` rather than guess). An `experimental` row may carry `TODO`; the cell
  then fails loudly at that check, never skips it. The planner itself refuses only a `TODO` in `sha256` (the jar it names)
  or in the host digest of the row's Java tag, as it does for a platform row.
- **Planner and selector (T-M8-04):** `CellPlanner.planLoaders(loader, versions, scope)` takes an exact version, `latest`
  or `all`. `all` follows the platform scope rule (PR: `required`, nightly: also `experimental`); an exact version runs in
  any scope. `CellSelector` reads `fabric@26.3`, `neoforge@latest` and `fabric@all` as loader entries, never as platform
  entries, and refuses a loader id on either side of a `+` (a loader cell is single-server, ADR-0017 §2). An unknown
  version, an unknown loader id or an empty request is an error, never a skip. A bridged row cannot be planned: the
  catalog refuses it at load with an error naming ADR-0017 §1.
- **`printMatrix`:** loader cells follow the single-server and topology cells, one object each with `kind: "loader"`,
  `platform` (the loader id), `mc`, `loader` (the loader version), `java`, `status` and `slug` (`fabric-26.3`); there is no
  `build`. No loader row is `required`, so `--scope=pr` plans none and prints a skip line to stderr per loader; `nightly`
  plans every `experimental` row. A file with no `loaders` prints what it printed before schema 4.

Validation rules for `loaders` (T-M8-04, in `VersionsCatalog.load`). A `loaders` key in a `schema: 2` or `3` file is an error,
and so is a loader id other than `fabric` or `neoforge`. A `proxies` key is valid in a schema 3 or 4 file:
- A loader id is not also a `platforms` or `proxies` id. `mc` values are unique within a loader.
- `loader`, `sha256` and `vanilla-sha256` are present on every row; `installer` and `fabric-api` on every `fabric` row. A
  `required` row has no `TODO`.
- `host.digests` has a key for the row's Java tag; a `required` row's tag has a real digest.
- Native only: the row's `protocol` equals `drivers.mcpl.protocol`. A bridged loader row is refused (ADR-0017 §1).
