# FAQ

## Why not MockBukkit or a unit test?
They cannot see NMS code, real inventories, packets or what a client receives. Proofstone runs a real server and a real protocol
client, then checks the server state and the client view ([vision](vision.md)). Use both: unit tests for logic, Proofstone for
behaviour.

## My test passes locally and fails on CI (or the other way). What do I check?
Cell and JDK first: print `PROOFSTONE CONFORMANCE <cell>` lines and compare the cell id, the Java tag and the driver. Then Docker
resources (memory, CPU). A red run is a finding; keep the bundle and do not retry it
([troubleshooting](user/troubleshooting.md)).

## All my tests were skipped. Is that a pass?
No. A skip is never a pass. The reason is printed: usually the EULA is not accepted, Docker is not reachable, or the cell was not
selected. Fix the reason.

## What does `VACUOUS` mean?
In a control run (your plugin removed) the test still passed, so it does not prove your plugin. Assert something only the plugin provides.

## What is `INVALID`? Is my plugin broken?
No. The cell's own conformance failed, so no plugin verdict exists for it. The harness or the server version is at fault. Check the
cell's row status in [platforms](platforms.md) and report it with the bundle.

## How do I test several Minecraft versions?
`@Matrix(platform = PAPER, versions = {"1.8.8", "1.21.11", "latest"})`. Versions are rows in `versions.yml`; an unknown version is an
error, never a silent skip ([versioning](versioning.md)).

## How do I test a proxy network, a Bedrock player or a mod?
[Topologies](user/topologies.md), [Bedrock through Geyser](user/bedrock.md), [mods, data packs and resource packs](user/mods-and-packs.md).
Check [limits](limits.md) first, since Bedrock and mods are `experimental`.

## Can I use it from Maven?
Yes: [Maven quickstart](user/quickstart-maven.md) (failsafe runs the `*E2E` tests).

## Do I need to install Paper, Spigot or Java versions for servers?
No. The harness downloads the pinned server jars and uses pinned images. You need Docker and the JDKs for your own build.

## Is it safe to put `PROOFSTONE_ACCEPT_EULA=true` in `gradle.properties` or CI secrets?
Do not write it to a file or commit it. Set it in the environment of the command that runs the tests, after reading the
[Minecraft EULA](https://aka.ms/MinecraftEULA). In a workflow, set it as a step-level `env` value.

## Can I add my own server platform or driver?
Yes, as a platform pack ([architecture](architecture.md), [platforms](platforms.md)) with its own conformance suite. A new platform
starts `experimental`.

## Which versions of Proofstone are supported, and how are breaks announced?
[Compatibility and deprecation policy](compatibility.md). While `0.x`, a minor may break the public API; every break is in the
[changelog](../CHANGELOG.md).

## How do I report a bug or a vulnerability?
Bugs: the [bug template](https://github.com/H7KZ/Proofstone/issues/new?template=bug.yml) with the repro bundle. Vulnerabilities:
[SECURITY.md](../SECURITY.md), never a public issue.

## Why are there sleeps nowhere, and why can't I add a retry?
A retry turns a real flaky bug into a green run. Proofstone fails loudly and gives you `await` helpers with timeouts instead
([robustness](robustness.md)).
