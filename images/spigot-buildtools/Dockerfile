# Spigot builder (T-M5-05, ADR-0005, ADR-0006). Runs SpigotMC BuildTools for one pinned --rev and writes the jar to
# /out. The image is built locally and never pushed: BuildTools.jar and the Mojang jars it fetches stay on the machine
# that builds them, and no Spigot jar is committed or redistributed.
#
# JDK_IMAGE is one eclipse-temurin JDK per Java tag, pinned by digest in versions.yml (platforms.spigot.builder).
# BuildTools needs a JDK (javac), which the itzg server images do not ship.
ARG JDK_IMAGE
FROM ${JDK_IMAGE}

# BuildTools.jar is pinned by its Jenkins build number and SHA-256. The build fails when the checksum differs.
ARG BUILDTOOLS_BUILD
ARG BUILDTOOLS_SHA256

RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates curl git \
 && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL -o /opt/BuildTools.jar \
      "https://hub.spigotmc.org/jenkins/job/BuildTools/${BUILDTOOLS_BUILD}/artifact/target/BuildTools.jar" \
 && echo "${BUILDTOOLS_SHA256}  /opt/BuildTools.jar" | sha256sum -c -

# BuildTools commits patches with git, so it needs an identity.
RUN git config --global user.name "proofstone" \
 && git config --global user.email "proofstone@localhost"

WORKDIR /work
# The rev is passed as the command: --rev <minecraft version>. The jar lands in /out as spigot-<rev>.jar.
ENTRYPOINT ["java", "-jar", "/opt/BuildTools.jar", "--output-dir", "/out"]
