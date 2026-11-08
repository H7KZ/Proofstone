# bedrock-prefix

## Purpose

A test-only Bukkit plugin for the simulated Bedrock tier (T-M5-06). It detects a Bedrock player the way Geyser and
Floodgate name them: an online player whose name starts with `.`. `/bedrock-check <name>` reports the detection, so a
test can read it from its own client. Conformance scenario C-B01 uses it.

## Boundaries

- Must not ship. It is built from these sources into `build/libs/proofstone-bedrock-prefix.jar`, never committed.
- Must not claim Bedrock support. A `.` name is a Java client with a prefix, not a Bedrock client (test-api spec section 11).
- Java 8 bytecode, like the other Bukkit fixtures, so it loads on 1.8.8.

## Public surface

- The command `/bedrock-check <name>`, which replies `bedrock-check <name> online=<bool> bedrock=<bool>`.

## How to test

The conformance module copies the jar into the cell (`conformance/build.gradle.kts`, `copyBedrockPrefixPlugin`) and runs it
through `@Proofstone(plugins = "build/bedrock-prefix-plugins/*.jar")`:

```bash
PROOFSTONE_ACCEPT_EULA=true ./gradlew :conformance:run -Dproofstone.cells=paper@26.3 --tests '*BedrockPrefix*'
```

Needs Docker. Run one Docker-using Gradle command at a time (docs/platforms/paper.md).
