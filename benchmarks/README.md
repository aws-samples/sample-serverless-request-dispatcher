# Cold start benchmark

Scripts and a separate AWS SAM stack for measuring the cold start of the
`sample-product-service` function and comparing it with baselines built from the
same JAR. Use them to produce numbers you can reproduce and publish together with
their methodology.

> **Deploy this stack only in a non-production test account.** The scripts change
> function configuration repeatedly and publish function versions.

## What is measured

| Variant | Handler | Purpose |
|---|---|---|
| `minimal` | `benchmark.MinimalHandler` | Dispatcher + Dagger with one controller and **no AWS SDK clients**. The cost of the routing layer itself. |
| `plain` | `benchmark.PlainHandler` | The **same controller, services, and SDK clients**, wired by hand and dispatched with a `switch`. The baseline. |
| `sample` | `LambdaHandler` | The sample as shipped: dispatcher + Dagger + DynamoDB and S3 clients. |
| `snapstart` | `LambdaHandler` | The sample with Lambda SnapStart on published versions. |

- **`sample` minus `plain`:** what the dispatcher and Dagger add.
- **`sample` minus `minimal`:** how much creating the SDK clients contributes.

For each variant, the matrix runs every combination of runtime (default `java21`, the
runtime the sample targets) and memory (default `512 1024` MB). Pass
`--runtimes "java21 java25"` to also measure the Java 21 bytecode on the `java25` runtime. Architecture and X-Ray tracing are stack
parameters, so run the matrix once per stack configuration you want to report.

**Metric.** Results come from the Lambda `REPORT` log line, queried with CloudWatch
Logs Insights:

- **Init:** `Init Duration` for on-demand variants, or `Restore Duration` for SnapStart.
- **Init + first invoke:** Init plus the `Duration` of the invocation that triggered
  it. For SnapStart, this is the cold start definition in the
  [SnapStart monitoring documentation](https://docs.aws.amazon.com/lambda/latest/dg/snapstart-monitoring.html).

Every request calls `GET /api/products`, which runs a DynamoDB `Scan` on an empty
table, except in `minimal`.

## How cold starts are produced

**`run-matrix.sh` (isolated cold starts).**
- **On-demand variants:** each sample starts with a configuration update (memory,
  runtime, and a changing `BENCHMARK_RUN` environment variable), which makes Lambda
  start a new execution environment for the next invocation.
- **SnapStart:** each round publishes a new version and then sends
  `--snapstart-burst` concurrent invocations to it. Each restore is one sample.
  Snapshots from freshly published versions start with cold caches, so treat
  these numbers as a conservative case.
- **Counts:** cold starts are counted from the logs, so the reported `n` can differ
  from the requested count.

**`run-load.sh` (load-driven cold starts).** Artillery sends 50 requests per second
for 10 minutes through API Gateway. The cold starts are the ones caused by
scale-out, as in several AWS Compute Blog benchmarks. Run it at least three times,
at least 5 minutes apart, and report each run.

## Prerequisites

- **Tools:** Java 21, Maven 3.6+, and AWS SAM CLI. Also AWS CLI v1 or v2 and `jq`,
  plus [Artillery](https://www.artillery.io/) for `run-load.sh`.
- **Credentials:** for a **non-production** account, allowed to deploy the stack and
  call `lambda:UpdateFunctionConfiguration`, `lambda:PublishVersion`,
  `lambda:InvokeFunction`, and `logs:StartQuery` / `logs:GetQueryResults`.
- **Concurrency:** account concurrency of at least 30 for the default SnapStart
  burst of 25 plus three on-demand variants. Lower `--snapstart-burst` on accounts
  with a low limit.

## Run it

```bash
# 1. Build the library and the sample JAR
(cd serverless-request-dispatcher && mvn clean install)
(cd sample-product-service && mvn clean package)

# 2. Deploy the benchmark stack (from the repository root)
sam deploy --template-file benchmarks/template.yaml \
  --stack-name request-dispatcher-benchmark \
  --capabilities CAPABILITY_IAM --resolve-s3 \
  --parameter-overrides Architecture=x86_64 TracingMode=Active

# 3. Isolated cold starts: 4 variants x 2 memory sizes on java21, 100 each
benchmarks/run-matrix.sh --stack request-dispatcher-benchmark --region us-east-1

# 4. Optional: load-driven cold starts (repeat 3 times, 5+ minutes apart)
benchmarks/run-load.sh --stack request-dispatcher-benchmark --region us-east-1 --target sample

# 5. Collect results before deleting the stack (log groups are deleted with it)
sam delete --stack-name request-dispatcher-benchmark
```

**Useful options for `run-matrix.sh`:**
- `--count`: cold starts per cell.
- `--variants`, `--runtimes`, `--memory`: restrict the matrix.
- `--snapstart-burst`: concurrent invocations per SnapStart version.
- `--sequential`: run variants one at a time instead of concurrently.
- `--out`: output directory.

When it finishes, each function is set back to its original memory and runtime.

**Rough duration and cost.** With defaults, a full matrix takes roughly 30–60 minutes,
because variants run concurrently and each on-demand cold start needs a
configuration update. A full matrix plus three load runs typically costs a few US
dollars or less in Lambda, API Gateway, DynamoDB, X-Ray, and CloudWatch Logs
charges. Check current prices for your Region.

## Output

Each run writes a timestamped folder under `benchmarks/results/`:

| File | Content |
|---|---|
| `metadata.json` | Date, Region, stack, commit SHA, number of uncommitted files, architecture, tracing mode, matrix settings |
| `cells.tsv` | One row per cell: variant, function, runtime, memory, start and end time, invocation success and error counts |
| `results.md` | Table of n, min, p50, p90, p99, and max for Init and for Init + first invoke |
| `results.json` | The same statistics at full precision, plus the Lambda runtime version ARNs observed |
| `raw/<cell>.csv` | Every cold start `REPORT` record, so the percentiles can be recomputed independently |

Commit the results folder you publish, so reviewers and readers can check the numbers.

The queries are in [`queries/`](queries/). Paste them into the CloudWatch Logs
Insights console to explore a log group interactively.

## Reporting the numbers

- **Scope the numbers:** state that they are specific to this sample and
  configuration, and give the Region, date, runtime, memory, architecture, tracing
  mode, and `n`.
- **Show the spread:** report percentiles (p50, p90, p99) and the maximum, not
  a single run or a best case.
- **Compare against your own code:** compare `sample` with the `plain` and
  `minimal` baselines, not with other frameworks.
- **Avoid generalized claims** such as "up to", "Nx faster", or "X% lower".
- **Link the evidence:** link this folder and the committed results.

## Cleanup

```bash
sam delete --stack-name request-dispatcher-benchmark
```

This deletes the functions, including the versions published by
`run-matrix.sh`, along with the API, table, bucket, and benchmark log groups.
