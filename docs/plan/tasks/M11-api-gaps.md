# M11: API gaps found by plugin E2E suites

Written 2026-11-20. Cards for public API that a real plugin's end-to-end suite (the Disenchantment suite, its `docs/engineering/E2E.md`
"Proofstone gaps" list) needed and the test API lacks. Each is an `api-change` card: additions only, a reviewed `api-baseline.txt`
diff, no existing signature changed. The **Flake rule** in [M8-mods.md](M8-mods.md) applies to every real-server acceptance.

### T-M11-01 · `Agent.logLines()`: the server log as plain text
- tier: sonnet
- depends: —
- modules: proofstone-api, proofstone-core, conformance, docs/specs, docs/user
- adrs: 0002
- specs: specs/test-api.md §3, specs/agent-protocol.md §2
- test-first: yes

**Goal.** A test reads the server log lines since it started, as plain text, through the public API: to assert a per-cell
expectation that the server logged (for example a plugin's start-up line) and that no stack trace or `WARN` was logged during an
operation. Label `api-change`.

**Evidence.** Gap 1 of the Disenchantment E2E suite. The agent already captures every record (`LogBuffer`, `GET /v1/log`,
log-record.json) and the lifecycle already reads it for the per-test error check, but `Agent` exposes no way to read it.

**Do.**
- Add `Agent.logLines()` and `Agent.logLines(Pattern, Duration)` as default methods that throw `UnsupportedOperationException`
  (the style of `Client.bedrock` and `Cell.capabilities()`), so no implementer breaks.
- Implement them in core over `GET /v1/log?since=<test cursor>&minLevel=TRACE` for `HostAgent` and `ProxyAgent`, with the cursor
  the lifecycle already takes before the client joins (cell and topology invocations).
- A ring buffer that dropped records since the cursor throws; an empty window is an empty list.
- Unit tests in core and api, one real-server conformance scenario (C-L01), docs, a regenerated `api-baseline.txt`.

**Don't.**
- Change the wire protocol (no new message, no version bump: the endpoint and schema exist), the agents or add a capability.
- Add a sleep or retry; the wait goes through `Await`. Include boot lines (the window is "since the test started").

**Acceptance.**
- [ ] `api-baseline.txt` gains only the two `Agent` default methods; `ApiSurfaceTest` is green.
- [ ] Unit tests: window cursor, plain-text rendering with stack traces, dropped records throw, await match and timeout.
- [ ] C-L01 (`ConformanceLogLinesTest`) green on `paper@26.3` in three separate invocations, XML read, `skipped="0" failures="0" errors="0"`.
- [ ] `bash scripts/gates.sh --max-workers=1` green; test-api spec, writing-tests guide, self-tests table, API README and CHANGELOG updated.

**Verify.**
`bash scripts/gates.sh --max-workers=1`; `scripts/dockerlock.sh` + `PROOFSTONE_ACCEPT_EULA=true ./gradlew :conformance:run -Dproofstone.cells=paper@26.3 --tests '*ConformanceLogLinesTest' --no-parallel --max-workers=1`
