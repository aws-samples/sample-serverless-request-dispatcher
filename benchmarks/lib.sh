#!/usr/bin/env bash
# Shared helpers for the benchmark scripts. Compatible with bash 3.2 (macOS) and
# AWS CLI v1 or v2. Source this file; do not run it directly.

BENCH_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PAYLOAD_FILE="$BENCH_DIR/payload-get-products.json"

log() { printf '[%s] %s\n' "$(date -u +%H:%M:%S)" "$*" >&2; }
die() { log "ERROR: $*"; exit 1; }

require_tools() {
  local tool
  for tool in "$@"; do
    command -v "$tool" > /dev/null 2>&1 || die "'$tool' is required but not installed"
  done
}

# aws wrapper that always passes the selected Region
awsr() { aws --region "$REGION" "$@"; }

stack_output() {
  awsr cloudformation describe-stacks --stack-name "$STACK" \
    --query "Stacks[0].Outputs[?OutputKey=='$1'].OutputValue" --output text
}

# Variant name -> CloudFormation output key holding the function name
variant_output_key() {
  case "$1" in
    minimal)   echo MinimalFunctionName ;;
    plain)     echo PlainFunctionName ;;
    sample)    echo SampleFunctionName ;;
    primed)    echo SamplePrimedFunctionName ;;
    snapstart) echo SnapStartFunctionName ;;
    *) die "unknown variant '$1' (expected minimal, plain, sample, primed, snapstart)" ;;
  esac
}

function_log_group() {
  awsr lambda get-function-configuration --function-name "$1" \
    --query 'LoggingConfig.LogGroup' --output text
}

# Applies memory, runtime, and a changing environment marker in one update.
# Any configuration change makes Lambda start new execution environments, so the
# next invocation of $LATEST is a cold start.
update_config() {
  local fn="$1" memory="$2" runtime="$3" marker="$4" env_json
  env_json=$(awsr lambda get-function-configuration --function-name "$fn" \
      --query 'Environment.Variables' --output json \
    | jq -c --arg m "$marker" '{Variables: ((. // {}) + {BENCHMARK_RUN: $m})}')
  awsr lambda update-function-configuration --function-name "$fn" \
    --memory-size "$memory" --runtime "$runtime" --environment "$env_json" > /dev/null
  awsr lambda wait function-updated --function-name "$fn"
}

# Invokes the benchmark route once. Prints "ok" or "error".
invoke_once() {
  local fn="$1" qualifier="$2" out
  out=$(mktemp)
  if awsr lambda invoke --function-name "$fn" --qualifier "$qualifier" \
       --payload "fileb://$PAYLOAD_FILE" "$out" > "$out.meta" 2>&1 \
     && ! grep -q '"FunctionError"' "$out.meta"; then
    echo ok
  else
    echo error
  fi
  rm -f "$out" "$out.meta"
}

# Runs a Logs Insights query over [start, end] epoch seconds and prints the
# results as a JSON array of objects.
run_insights_query() {
  local log_group="$1" start="$2" end="$3" query_file="$4" query_id status
  query_id=$(awsr logs start-query --log-group-name "$log_group" \
    --start-time "$start" --end-time "$end" \
    --query-string "$(cat "$query_file")" --query queryId --output text)
  while true; do
    status=$(awsr logs get-query-results --query-id "$query_id" --query status --output text)
    case "$status" in
      Complete) break ;;
      Failed|Cancelled|Timeout) die "Logs Insights query $query_id ended with status $status" ;;
    esac
    sleep 2
  done
  awsr logs get-query-results --query-id "$query_id" --output json \
    | jq '[.results[] | map({(.field): .value}) | add | with_entries(select(.key != "@ptr"))]'
}
