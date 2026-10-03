#!/usr/bin/env bash
# Runs the Artillery load test against the on-demand or SnapStart sample API and
# measures the cold starts caused by scale-out, the method used in several AWS
# Compute Blog posts. Repeat it (for example 3 runs, 5+ minutes apart) and report
# each run separately.
#
# Run only against the benchmark stack in a non-production test account.
#
# Usage: ./run-load.sh --stack <name> [--region <region>] [--target sample|snapstart] [--out <dir>]
set -euo pipefail
source "$(dirname "$0")/lib.sh"

STACK=""
REGION="${AWS_REGION:-${AWS_DEFAULT_REGION:-}}"
TARGET=sample
OUT=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --stack) STACK="$2"; shift 2 ;;
    --region) REGION="$2"; shift 2 ;;
    --target) TARGET="$2"; shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    -h|--help) sed -n '2,10p' "$0"; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done
[[ -n "$STACK" && -n "$REGION" ]] || die "--stack and --region are required"
require_tools aws jq artillery

case "$TARGET" in
  sample)    url=$(stack_output SampleApiUrl);    fn=$(stack_output SampleFunctionName) ;;
  snapstart) url=$(stack_output SnapStartApiUrl); fn=$(stack_output SnapStartFunctionName) ;;
  *) die "--target must be sample or snapstart" ;;
esac
memory=$(awsr lambda get-function-configuration --function-name "$fn" --query MemorySize --output text)
runtime=$(awsr lambda get-function-configuration --function-name "$fn" --query Runtime --output text)

OUT="${OUT:-$BENCH_DIR/results/load-$TARGET-$(date -u +%Y%m%dT%H%M%SZ)}"
mkdir -p "$OUT"

log "Load test against $url ($fn, $runtime, $memory MB)"
start=$(date +%s)
artillery run --target "$url" --output "$OUT/artillery-report.json" "$BENCH_DIR/load-test.yml"
end=$(date +%s)

printf 'variant\tfunction\truntime\tmemoryMb\tstartEpoch\tendEpoch\tinvokeOk\tinvokeError\n' > "$OUT/cells.tsv"
printf '%s\t%s\t%s\t%s\t%s\t%s\t0\t0\n' "load-$TARGET" "$fn" "$runtime" "$memory" "$start" "$end" >> "$OUT/cells.tsv"
jq -n --arg date "$(date -u +%Y-%m-%dT%H:%M:%SZ)" --arg region "$REGION" --arg stack "$STACK" \
  --arg commit "$(git -C "$BENCH_DIR" rev-parse HEAD 2>/dev/null || echo unknown)" --arg target "$TARGET" --arg url "$url" \
  '{date: $date, region: $region, stack: $stack, commit: $commit, mode: "load", target: $target, url: $url,
    loadProfile: "Artillery, 50 arrivals/s for 600 s, GET /api/products"}' > "$OUT/metadata.json"

log "Waiting 60 s for CloudWatch Logs ingestion before querying"
sleep 60
"$BENCH_DIR/collect-results.sh" --region "$REGION" --dir "$OUT"
