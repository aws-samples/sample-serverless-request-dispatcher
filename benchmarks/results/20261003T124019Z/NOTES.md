# Notes for run 20261003T124019Z

- **Code:** commit `d1fe0b6`, with 0 uncommitted files. us-east-1, `java21`, x86_64, X-Ray active.
- **Results:** every cell has 100 successful invocations and 0 errors, with one Lambda runtime version per cell.
- **`primed` at 1024 MB has n = 99.** One of its 100 invocations ran in an existing execution
  environment even though the configuration had just been updated: its `REPORT` line shows
  `Duration: 14.65 ms` and has no `Init Duration`. The window contains 99 `INIT_START` lines,
  which matches. The warm invocation is excluded because it isn't a cold start.
- **Checks:** p50 values recomputed from `raw/*.csv` match `results.json` (Logs Insights) within 0.3%.

## Comparison with run 20261003T094730Z (commit `47829b5`)

Between the two commits, the only changes that affect the deployed JAR are dependency
versions. Jackson 2.13.3 was replaced by 2.22.3, and commons-fileupload, commons-io and
jackson-module-afterburner were removed.

| Variant (512 MB) | Init p50, run 1 | Init p50, run 2 | Init + first invoke p50, run 1 | run 2 |
|---|---|---|---|---|
| minimal | 513 | 518 | 820 | 843 |
| plain | 1,889 | 1,908 | 4,706 | 4,754 |
| sample | 1,931 | 1,941 | 4,735 | 4,816 |
| primed | 2,599 | 2,625 | 3,235 | 3,255 |
| snapstart (restore) | 662 | 726 | 1,753 | 1,786 |

All medians are within +0.5% to +2.7% of run 1, except the SnapStart Restore Duration (+9.7%).
Restore time depends on snapshot cache warmth, and this run started right after a fresh
deployment. The dispatcher + Dagger overhead over the plain handler was 42 ms in run 1 and
33 ms in run 2. Both values are smaller than the plain handler's p50 to p90 spread (63 ms and 94 ms).
