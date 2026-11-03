#!/usr/bin/env bash
# usage: run.sh <mcVersion> <javaTag> [botProtocolVersion]
# setup: npm install here; copy the built plugin jar into ./plugins/ (Docker + Node 18+ required)
# env: TYPE=FOLIA, EXTRA_ENV="-e MODRINTH_PROJECTS=viaversion,viabackwards" (bot older than server)
set -u
V=$1; J=$2; BV=${3:-$1}; D="$(cd "$(dirname "$0")" && (pwd -W 2>/dev/null || pwd))"
docker rm -f dis-e2e >/dev/null 2>&1
T0=$(date +%s)
MSYS_NO_PATHCONV=1 docker run -d --name dis-e2e -p 25565:25565 -p 25575:25575 \
 -e EULA=TRUE -e TYPE=${TYPE:-PAPER} ${EXTRA_ENV:-} -e VERSION=$V -e ONLINE_MODE=FALSE -e ENABLE_RCON=true -e RCON_PASSWORD=e2e \
 -e LEVEL_TYPE=minecraft:flat -e SPAWN_PROTECTION=0 -e DIFFICULTY=peaceful -e GENERATE_STRUCTURES=false -e MODE=survival -e MEMORY=2G -e VIEW_DISTANCE=4 \
 -v "$D/plugins:/plugins:ro" itzg/minecraft-server:$J >/dev/null
until docker logs dis-e2e 2>&1 | grep -q 'Done ('; do
  [ "$(docker inspect -f '{{.State.Status}}' dis-e2e)" != running ] && { docker logs dis-e2e | tail -30; exit 3; }
  sleep 2
done
echo "boot ${V}: $(( $(date +%s) - T0 ))s"
docker logs dis-e2e 2>&1 | grep -E 'Disenchantment\] (NMS|Enabling)|This server is running'
(cd "$D" && timeout 90 node e2e.js 127.0.0.1 "$BV"); RC=$?
docker logs dis-e2e 2>&1 | grep -iE 'exception|\[Disenchantment\].*(error|warn)' | head -5
docker logs dis-e2e > "$D/server-$V.log" 2>&1; docker rm -f dis-e2e >/dev/null
exit $RC
