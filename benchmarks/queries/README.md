# Logs Insights queries

`collect-results.sh` runs these queries against each benchmark log group.

The queries select `REPORT` lines with `@message like /^REPORT RequestId/` and read
`Restore Duration` with a glob `parse`. They don't use `filter @type = "REPORT"`
together with `parse`, because that combination fails with
`MalformedQueryException: Ephemeral field is already defined` on current Lambda
logs. SnapStart `REPORT` lines also contain `Billed Restore Duration`, so a regex for
`Restore Duration:` matches twice.

Output columns in `cold-start-raw.query` are selected with `display`, not `fields`.
Listing a field that `parse` already created (such as `restoreDuration`) in a later
`fields` command raises the same "already defined" error.
