#!/bin/bash
# Read-only watcher (T-M12-02): about every 0.3 s, for the container whose command or image matches <pattern>: every TCP socket
# (IPv4 and IPv6) with bytes waiting, the established count, and the interface byte counters of eth0.
# With a third argument "dump": when the server log's last line is "UUID of player ..." and has not changed for about 4 s (a join
# that has not reached "logged in"), it sends SIGQUIT to the java process in the container, once at 4 s and once at 12 s, so the JVM
# prints a thread dump to its own log. Nothing else is touched. Stops when "${TMPDIR:-/tmp}/viawatch.stop" exists.
# usage: viawatch.sh <outfile> <pattern> [dump]
OUT=$1
PAT=${2:-ViaProxy.jar}
DUMP=${3:-}
: > "$OUT"
rm -f "${TMPDIR:-/tmp}/viawatch.stop"
LAST=""; SAME=0; DUMPED=0
while [ ! -f "${TMPDIR:-/tmp}/viawatch.stop" ]; do
  ID=$(docker ps --no-trunc --format '{{.ID}} {{.Image}} {{.Command}}' 2>/dev/null | grep -- "$PAT" | head -1 | cut -d' ' -f1)
  TS=$(date -u +%H:%M:%S.%3N)
  if [ -n "$ID" ]; then
    docker exec "$ID" sh -c 'cat /proc/net/tcp /proc/net/tcp6' 2>/dev/null | awk -v ts="$TS" -v id="$ID" '
      $1=="sl" { next }
      { split($5,q,":"); st=$4; if (st=="01") est++; if (q[1]!="00000000" || q[2]!="00000000") print ts, substr(id,1,12), "local=" $2, "remote=" $3, "st=" st, "tx=" q[1], "rx=" q[2] }
      END { print ts, substr(id,1,12), "established=" est+0 }' >> "$OUT"
    docker exec "$ID" sh -c 'grep eth0 /proc/net/dev' 2>/dev/null | awk -v ts="$TS" '{ sub(":", " "); print ts, "eth0", "rx_bytes=" $2, "tx_bytes=" $10 }' >> "$OUT"
    if [ "$DUMP" = dump ]; then
      L=$(docker logs --tail 1 "$ID" 2>&1 | tr -d '\r')
      if [ "$L" = "$LAST" ]; then SAME=$((SAME + 1)); else SAME=0; DUMPED=0; LAST=$L; fi
      case "$L" in
        *"UUID of player"*)
          if [ $SAME -ge 12 ] && [ $DUMPED -eq 0 ]; then
            echo "$TS DUMP1 after $SAME same samples: $L" >> "$OUT"
            docker exec "$ID" sh -c 'for d in /proc/[0-9]*; do if [ "$(tr "\0" "\n" < $d/cmdline 2>/dev/null | head -1 | sed "s#.*/##")" = java ]; then kill -3 ${d#/proc/} 2>/dev/null; fi; done'
            DUMPED=1
          elif [ $SAME -ge 36 ] && [ $DUMPED -eq 1 ]; then
            echo "$TS DUMP2 after $SAME same samples: $L" >> "$OUT"
            docker exec "$ID" sh -c 'for d in /proc/[0-9]*; do if [ "$(tr "\0" "\n" < $d/cmdline 2>/dev/null | head -1 | sed "s#.*/##")" = java ]; then kill -3 ${d#/proc/} 2>/dev/null; fi; done'
            DUMPED=2
          fi;;
      esac
    fi
  else
    echo "$TS none" >> "$OUT"
  fi
  sleep 0.3
done
