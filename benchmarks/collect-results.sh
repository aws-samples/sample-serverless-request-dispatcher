#!/usr/bin/env bash
# Queries CloudWatch Logs Insights for every cell in <dir>/cells.tsv and writes:
#   <dir>/results.json   per-cell statistics (n, min, p50, p90, p99, max)
#   <dir>/results.md     the same as a Markdown table, ready for the blog draft
#   <dir>/raw/*.csv      every cold-start REPORT line, so percentiles can be recomputed
#
# Usage: ./collect-results.sh --region <region> --dir <results-dir>
set -euo pipefail
source "$(dirname "$0")/lib.sh"

REGION="${AWS_REGION:-${AWS_DEFAULT_REGION:-}}"
DIR=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --region) REGION="$2"; shift 2 ;;
    --dir) DIR="$2"; shift 2 ;;
    *) die "unknown argument: $1" ;;
  esac
done
[[ -n "$REGION" && -n "$DIR" && -f "$DIR/cells.tsv" ]] || die "usage: $0 --region <region> --dir <results-dir with cells.tsv>"
require_tools aws jq

mkdir -p "$DIR/raw"
results="[]"

# Header row is skipped; REPORT lines are written when an invocation ends, which is
# before the recorded end time, so a 5-second margin is enough.
while IFS=$'\t' read -r variant fn runtime memory start end ok err <&3; do
  [[ "$variant" == variant ]] && continue
  log_group=$(function_log_group "$fn")
  cell="$variant-$runtime-${memory}mb"
  log "Querying $cell ($log_group)"

  stats=$(run_insights_query "$log_group" "$start" "$((end + 5))" "$BENCH_DIR/queries/cold-start-stats.query")
  # Logs Insights can lag ingestion by minutes. Every on-demand invocation in a cell is a
  # cold start, so retry until the count matches the successful invocations. SnapStart
  # bursts can legitimately reuse environments, and load cells have no expected count.
  if [[ "$variant" != snapstart && "$variant" != load-* ]]; then
    attempt=1
    while [[ "$(echo "$stats" | jq -r '.[0].n // 0')" -lt "$ok" && $attempt -le 6 ]]; do
      log "$cell: $(echo "$stats" | jq -r '.[0].n // 0') of $ok cold starts visible yet; waiting 30 s (attempt $attempt/6)"
      sleep 30
      stats=$(run_insights_query "$log_group" "$start" "$((end + 5))" "$BENCH_DIR/queries/cold-start-stats.query")
      attempt=$((attempt + 1))
    done
  fi
  runtime_arns=$(run_insights_query "$log_group" "$start" "$((end + 5))" "$BENCH_DIR/queries/runtime-version.query")
  run_insights_query "$log_group" "$start" "$((end + 5))" "$BENCH_DIR/queries/cold-start-raw.query" \
    | jq -r '(.[0] // {} | keys_unsorted) as $k | if ($k | length) == 0 then empty else ($k | @csv), (.[] | [.[$k[]]] | @csv) end' \
    > "$DIR/raw/$cell.csv"

  results=$(jq -n --argjson acc "$results" --argjson stats "$stats" --argjson arns "$runtime_arns" \
    --arg variant "$variant" --arg runtime "$runtime" --arg memory "$memory" \
    --arg ok "$ok" --arg err "$err" \
    '$acc + [{variant: $variant, runtime: $runtime, memoryMb: ($memory | tonumber),
              invokeOk: ($ok | tonumber), invokeError: ($err | tonumber),
              runtimeVersionArns: [$arns[].runtimeVersionArn],
              stats: (($stats[0] // {}) | with_entries(.value |= tonumber))}]')
done 3< "$DIR/cells.tsv"

echo "$results" | jq '.' > "$DIR/results.json"

# Values are rounded to whole milliseconds only for display; results.json and raw/ keep full precision
{
  echo "| Variant | Runtime | Memory | n | Init min | Init p50 | Init p90 | Init p99 | Init max | Init + first invoke p50 | p90 | p99 | max |"
  echo "|---|---|---|---|---|---|---|---|---|---|---|---|---|"
  echo "$results" | jq -r '.[] | . as $c | .stats as $s |
    def ms(x): if x == null then "–" else (x | floor | tostring) end;
    "| \($c.variant) | \($c.runtime) | \($c.memoryMb) MB | \($s.n // 0) | \(ms($s.initMin)) | \(ms($s.initP50)) | \(ms($s.initP90)) | \(ms($s.initP99)) | \(ms($s.initMax)) | \(ms($s.totalP50)) | \(ms($s.totalP90)) | \(ms($s.totalP99)) | \(ms($s.totalMax)) |"'
  echo
  echo "Init = Init Duration (on-demand) or Restore Duration (SnapStart), in ms. Metadata: metadata.json."
} > "$DIR/results.md"

log "Wrote $DIR/results.md, $DIR/results.json, and $DIR/raw/"
cat "$DIR/results.md"
