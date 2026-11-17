#!/usr/bin/env bash
# T-M9-01 spike hook: after the Bedrock client spawned, wait (bounded, 20 s) until the Bukkit agent lists the player, then print
# what the agent and the server log say about it. Usage: agent-read.sh <container> <name-or-floodgate-name> <token>
set -u
C=$1; NAME=$2; TOKEN=$3
get() { docker exec "$C" curl -s -m 3 -H "Authorization: Bearer $TOKEN" "localhost:25580/v1/$1"; }
found=""
for _ in $(seq 1 40); do
  P=$(get players || true)
  case "$P" in *"\"$NAME\""*|*".$NAME"*) found=$P; break;; esac
  sleep 0.5
done
echo "AGENT health: $(get health | head -c 500)"
echo "AGENT players: ${P:-<none>}"
[ -n "$found" ] || { echo "AGENT: $NAME not listed within 20 s"; exit 6; }
N=$(echo "$found" | grep -o "\"[.]\?$NAME\"" | head -1 | tr -d '"')
echo "AGENT player($N): $(get players/$N | head -c 600)"
docker logs "$C" 2>&1 | grep -E "UUID of player|logged in with entity id|joined the game" | grep -F "$NAME" | tail -3
exit 0
