# Spec: `versions.yml`

Decision: [ADR-0008](../adr/0008-versions-yml-single-source-of-truth.md), amended by
[ADR-0013](../adr/0013-host-digest-per-java-tag.md) (host digest per Java tag). A JSON Schema for validation,
`proofstone-protocol/schemas/versions.schema.json`, is created in T-M0-04.

```yaml
schema: 1                                  # file format version
java-rules:                                # first match wins
  - { range: ">=1.20.5 <26", image: java21 }
drivers:
  mcpl:           { library: "<g:a>", version: "<pinned>", protocol: "<mc version>" }
  mineflayer-ref: { library: "mineflayer", version: "<pinned>", max-native: "<mc version>" }
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
        sha256: "…"                        # server jar checksum
        java: java21                       # optional override of java-rules
        protocol: 774                      # optional; filled by automation
        status: required | experimental | retired
        native-drivers: [mcpl]             # filled by automation from driver metadata
        observed: "free text"              # last notable observation
        notes: "free text"
```

Validation rules, enforced in `check`:
- `mc` values are unique within a platform.
- `required` rows have no `TODO` values.
- `host.digests` has a key for every `image` named in `java-rules` and every row `java` override. A `required`
  row's tag has a real digest, not `TODO`.
- Every row's Java tag resolves to an image that exists (an online check, run nightly only).
- `latest` always resolves to the highest `required` row.

A cell takes the digest of its row's Java tag (`java` override, else the first matching `java-rules` entry). A tag
with no digest is an error that names the tag and the row, never a fallback to another tag.
