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
