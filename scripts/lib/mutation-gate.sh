# Release gate for the mutation-check result (T-M6-07, T-M6-09). Sourced by scripts/release-maven.sh and by
# scripts/release-gate-test.sh. It reads one result file and decides; it never runs a check and never publishes.
#
# mutation_gate_check <result-file> <expected-head> <now-epoch>
#   Returns 0 only for a complete PASS of <expected-head>: status=PASS, allowed_missing empty (no example was NOT RUN,
#   so --allow-missing results cannot be published), dirty=false, head equal to <expected-head>, every key present,
#   and generated no more than 24 hours before <now-epoch>. Any other case returns 1 with the reason on stderr.
mutation_gate_check() {
  local file=$1 want_head=$2 now=$3 key
  local -A v=()
  if [[ ! -s "$file" ]]; then
    echo "mutation-gate: no result file at $file" >&2
    return 1
  fi
  for key in status head dirty cell generated_epoch killed allowed_missing problems; do
    if ! grep -q "^$key=" "$file"; then
      echo "mutation-gate: result has no '$key=' line (malformed, refused)" >&2
      return 1
    fi
    v[$key]=$(sed -n "s/^$key=//p" "$file" | head -1)
  done
  if [[ "${v[status]}" != PASS ]]; then
    echo "mutation-gate: status is '${v[status]}', not PASS" >&2
    return 1
  fi
  if [[ -n "${v[allowed_missing]}" ]]; then
    echo "mutation-gate: allowed_missing=${v[allowed_missing]}: a run with examples NOT RUN cannot be published" >&2
    return 1
  fi
  if [[ "${v[dirty]}" != false ]]; then
    echo "mutation-gate: the check ran on a dirty tree" >&2
    return 1
  fi
  if [[ "${v[head]}" != "$want_head" ]]; then
    echo "mutation-gate: the check ran on ${v[head]:0:12}, not on HEAD" >&2
    return 1
  fi
  if ! [[ "${v[generated_epoch]}" =~ ^[0-9]+$ ]] || ((now - v[generated_epoch] > 86400)); then
    echo "mutation-gate: the result is older than 24 hours or its timestamp is unreadable" >&2
    return 1
  fi
  return 0
}
