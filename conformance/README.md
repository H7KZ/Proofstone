# conformance

## Purpose

Shared vanilla scenarios and the runner that executes them against real servers (layer L4). `:conformance:smoke` is
a placeholder until T-M1-11.

## Boundaries

- Must not run Docker in M0. Real server runs start with T-M1-11.
- Must not turn a placeholder into a pass. Until then `smoke` prints and succeeds; with `CI_REQUIRE_SMOKE=true` it fails.

## Public surface

The `smoke` task only.

## How to test

`./gradlew :conformance:smoke`
