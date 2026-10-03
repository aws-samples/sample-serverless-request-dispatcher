#!/usr/bin/env bash
# Measures cold starts for each variant x runtime x memory cell of the benchmark
# stack, then collects the results with collect-results.sh.
#
# Run only against the benchmark stack in a non-production test account.
#
# Usage:
#   ./run-matrix.sh --stack <name> [--region <region>] [--count 100]
#                   [--variants "minimal plain sample primed snapstart"]
#                   [--runtimes "java21"] [--memory "512 1024"]
#                   [--snapstart-burst 25] [--sequential] [--out <dir>]
#
# On-demand variants: one configuration update per cold start, so each sample is
# an isolated cold start of a fresh execution environment.
# SnapStart variant: one published version per round, then --snapstart-burst
# concurrent invocations of that version. Each restore is one sample.
set -euo pipefail
source "$(dirname "$0")/lib.sh"

STACK=""
REGION="${AWS_REGION:-${AWS_DEFAULT_REGION:-}}"
COUNT=100
VARIANTS="minimal plain sample primed snapstart"
RUNTIMES="java21"
MEMORY="512 1024"
SNAPSTART_BURST=25
PARALLEL=true
OUT=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --stack) STACK="$2"; shift 2 ;;
    --region) REGION="$2"; shift 2 ;;
    --count) COUNT="$2"; shift 2 ;;
    --variants) VARIANTS="$2"; shift 2 ;;
    --runtimes) RUNTIMES="$2"; shift 2 ;;
    --memory) MEMORY="$2"; shift 2 ;;
    --snapstart-burst) SNAPSTART_BURST="$2"; shift 2 ;;
    --sequential) PARALLEL=false; shift ;;
    --out) OUT="$2"; shift 2 ;;
    -h|--help) sed -n '2,17p' "$0"; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

[[ -n "$STACK" ]] || die "--stack is required"
[[ -n "$REGION" ]] || die "--region is required (or set AWS_REGION)"
require_tools aws jq git
OUT="${OUT:-$BENCH_DIR/results/$(date -u +%Y%m%dT%H%M%SZ)}"
mkdir -p "$OUT"

# Records everything a reader needs to reproduce the run
write_metadata() {
  local sample_fn arch tracing
  sample_fn=$(stack_output SampleFunctionName)
  arch=$(awsr lambda get-function-configuration --function-name "$sample_fn" \
    --query 'Architectures[0]' --output text)
  tracing=$(awsr lambda get-function-configuration --function-name "$sample_fn" \
    --query 'TracingConfig.Mode' --output text)
  jq -n \
    --arg date "$(date -u +%Y-%m-%dT%H:%M:%SZ)" --arg region "$REGION" --arg stack "$STACK" \
    --arg commit "$(git -C "$BENCH_DIR" rev-parse HEAD 2>/dev/null || echo unknown)" \
    --arg dirty "$(git -C "$BENCH_DIR" status --porcelain -- .. | grep -c . || true)" \
    --arg arch "$arch" --arg tracing "$tracing" --arg count "$COUNT" \
    --arg variants "$VARIANTS" --arg runtimes "$RUNTIMES" --arg memory "$MEMORY" \
    --arg burst "$SNAPSTART_BURST" \
    '{date: $date, region: $region, stack: $stack, commit: $commit,
      uncommittedFiles: ($dirty | tonumber), architecture: $arch, tracing: $tracing,
      requestedColdStartsPerCell: ($count | tonumber), variants: $variants,
      runtimes: $runtimes, memoryMb: $memory, snapStartBurst: ($burst | tonumber)}' \
    > "$OUT/metadata.json"
}

# Cold starts for an on-demand variant: one config update per sample
measure_on_demand() {
  local fn="$1" memory="$2" runtime="$3" i ok=0 err=0
  for i in $(seq 1 "$COUNT"); do
    update_config "$fn" "$memory" "$runtime" "$memory-$runtime-$i-$(date +%s)"
    if [[ "$(invoke_once "$fn" '$LATEST')" == ok ]]; then ok=$((ok + 1)); else err=$((err + 1)); fi
  done
  echo "$ok $err"
}

# Cold starts for SnapStart: one published version per round, burst-invoked
measure_snapstart() {
  local fn="$1" memory="$2" runtime="$3" variant_dir="$4"
  local rounds=$(( (COUNT + SNAPSTART_BURST - 1) / SNAPSTART_BURST )) r j version ok=0 err=0 tmp
  tmp=$(mktemp -d)
  for r in $(seq 1 "$rounds"); do
    update_config "$fn" "$memory" "$runtime" "$memory-$runtime-r$r-$(date +%s)"
    version=$(awsr lambda publish-version --function-name "$fn" --query Version --output text)
    echo "$version" >> "$variant_dir/published-versions.txt"
    awsr lambda wait published-version-active --function-name "$fn" --qualifier "$version"
    for j in $(seq 1 "$SNAPSTART_BURST"); do
      invoke_once "$fn" "$version" > "$tmp/$r.$j" &
    done
    wait
  done
  ok=$(cat "$tmp"/* | grep -c '^ok$' || true)
  err=$(cat "$tmp"/* | grep -c '^error$' || true)
  rm -rf "$tmp"
  echo "$ok $err"
}

# Runs every runtime x memory cell for one variant and appends to cells.tsv
run_variant() {
  local variant="$1" fn orig_memory orig_runtime runtime memory start end result
  local variant_dir="$OUT/$variant"
  mkdir -p "$variant_dir"
  fn=$(stack_output "$(variant_output_key "$variant")")
  [[ -n "$fn" && "$fn" != None ]] || die "stack $STACK has no function for variant $variant"
  orig_memory=$(awsr lambda get-function-configuration --function-name "$fn" --query MemorySize --output text)
  orig_runtime=$(awsr lambda get-function-configuration --function-name "$fn" --query Runtime --output text)

  for runtime in $RUNTIMES; do
    for memory in $MEMORY; do
      log "$variant: $runtime, $memory MB — starting $COUNT cold starts"
      start=$(date +%s)
      if [[ "$variant" == snapstart ]]; then
        result=$(measure_snapstart "$fn" "$memory" "$runtime" "$variant_dir")
      else
        result=$(measure_on_demand "$fn" "$memory" "$runtime")
      fi
      end=$(date +%s)
      printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$variant" "$fn" "$runtime" "$memory" \
        "$start" "$end" ${result} >> "$variant_dir/cells.tsv"
      log "$variant: $runtime, $memory MB — done (ok/error: $result)"
    done
  done

  log "$variant: restoring $orig_runtime, $orig_memory MB"
  update_config "$fn" "$orig_memory" "$orig_runtime" "restored-$(date +%s)"
}

log "Benchmark stack $STACK in $REGION; results in $OUT"
write_metadata

if $PARALLEL; then
  # Variants use separate functions, so they can run concurrently
  pids=""
  for variant in $VARIANTS; do
    run_variant "$variant" &
    pids="$pids $!"
  done
  failed=0
  for pid in $pids; do wait "$pid" || failed=1; done
  [[ $failed -eq 0 ]] || die "one or more variants failed; see the log above"
else
  for variant in $VARIANTS; do run_variant "$variant"; done
fi

printf 'variant\tfunction\truntime\tmemoryMb\tstartEpoch\tendEpoch\tinvokeOk\tinvokeError\n' > "$OUT/cells.tsv"
cat "$OUT"/*/cells.tsv >> "$OUT/cells.tsv"

log "Waiting 60 s for CloudWatch Logs ingestion before querying"
sleep 60
"$BENCH_DIR/collect-results.sh" --region "$REGION" --dir "$OUT"

if [[ -f "$OUT/snapstart/published-versions.txt" ]]; then
  log "SnapStart versions published by this run are listed in $OUT/snapstart/published-versions.txt"
  log "They are removed with the stack ('sam delete'), or individually with: aws lambda delete-function --function-name <fn> --qualifier <version>"
fi
