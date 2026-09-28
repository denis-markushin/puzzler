# Puzzle format

A puzzle starts at a comment line that matches the puzzle pattern (a regexp with named capture
groups); one comment block can hold several puzzles. The scanner has no notion of programming
languages — it only recognizes comment line prefixes: `//`, `#`, `--`, `*` (the continuation lines
of a `/* */` block), and `;`.

## Default pattern

```
^(?<marker>TODO|FIXME|HACK)(?:\((?<type>[\p{L}\p{N}_-]+)(?:,\s*(?<estimate>[^)]+))?\))?(?:\s*\[(?<labels>[^\]]*)\])?:\s*(?<subject>.+)$
```

Shape: `MARKER(type, estimate) [labels]: subject`, where `type`, `estimate` and `labels` are all
optional. `estimate` cannot appear without `type`; `labels` is independent of both.

Kotlin:

```kotlin
class Cache {
    // TODO(debt, 30min) [perf, security]: extract cache into a separate bean
    //   needs TTL and metrics
    val store = mutableMapOf<String, String>()
}
```

Python:

```python
def flush():
    # FIXME: retries are not idempotent
    #   dedupe by request id before retrying
    pass
```

SQL:

```sql
-- HACK: index rebuilt manually until the migration lands
--   drop this once ticket ships
CREATE INDEX idx_orders_status ON orders (status);
```

## Indentation rule for the body

The head line (the one matched by the pattern) has an indent. Every following comment line in the
*same* comment block becomes part of the body **only while its indent stays strictly greater** than
the head's indent. The first line at the same or lower indent ends the body:

```kotlin
// TODO: extract cache
//   needs TTL           <- indent 2, included in the body
// ordinary comment       <- indent 0, same as head: body stops here
//   and this is not body either  <- not a head either, ignored
```

Body: `needs TTL`.

## Several puzzles in one block

Every line that matches the pattern and is not inside a body starts a puzzle of its own, so markers
can sit back-to-back in one comment block, or below an ordinary comment, and each gets its own body:

```kotlin
// TODO(debt, 30min): drop the legacy exporter
//   still read by the nightly report      <- body of the first puzzle
// TODO(debt, 30min): rename the export flag
//   keep the old name as an alias         <- body of the second puzzle
```

A matching line indented deeper than a head is inside that head's body and stays there as text,
it does not start a puzzle. Outdent it to the head's level to make it a puzzle of its own:

```kotlin
// TODO: split the importer
//   HACK: csv branch copies the json one  <- body of the TODO, not a separate puzzle
```

Adding a puzzle below another one leaves the hash of the upper puzzle unchanged, so its ticket stays
open and is not filed again.

## Named group contract

| Group | Required | Meaning |
|---|---|---|
| `subject` | yes | Ticket title. A match without a non-blank `subject` is not a puzzle. |
| `type` | no | Feeds `puzzle.typeMapping`; becomes the ticket type. |
| `estimate` | no | Free-form text, carried into the ticket body. |
| `labels` | no | Comma-separated list, split and trimmed. Merged with `tracker.labels` and put on the ticket. Values are hash-neutral, so relabelling never files a puzzle again. |
| `assignee` | no | Carried to trackers that support assignment (GitHub). Not declared by the default pattern. |
| `marker` | no | Stand-in for `type` when `type` is absent or not declared by the pattern. Used by `typeMapping` too. |

A label that is blank, carries whitespace, a comma or a colon, or starts with the reserved `puzzler-` prefix
is skipped, with a warning naming the file and the line. The ticket is still filed, without that
label.

`ConfigLoader` rejects any pattern that does not declare `subject`. Groups the pattern does not
declare are simply not read — an omitted `estimate` group means every puzzle has `estimate = null`,
not a validation error.

## Ticket type inference

1. If the pattern declares `type` and the headline matched it, that value is used.
2. Otherwise, if the pattern declares `marker` and it matched, that value is used (the default
   pattern falls back this way: a bare `FIXME: text` gets type `FIXME`).
3. Otherwise, the puzzle has no type.

The resulting raw value is then looked up in `puzzle.typeMapping`. A hit substitutes the mapped
ticket type; a miss passes the raw value straight through as the ticket type (see
`docs/configuration.md`).

## Replacing the pattern for a different convention

To recognize `// TASK(debt): text` instead of `TODO`/`FIXME`/`HACK`:

```yaml
puzzle:
  pattern: '^TASK(?:\((?<marker>[^)]+)\))?:\s*(?<subject>.+)$'
  typeMapping:
    debt: "Technical Debt"
```

Group by group:

- `TASK` — the literal marker word, matched but not captured.
- `(?:\((?<marker>[^)]+)\))?` — an optional `(...)` suffix, captured as `marker` (not `type`,
  since this convention keeps a single word rather than `type`+`estimate`). `debt` maps to
  `Technical Debt` via `typeMapping`; any other word passes through unchanged as the ticket type.
- `:\s*(?<subject>.+)` — the mandatory subject after the colon.

No `estimate` or `assignee` group is declared here, so both are always `null` for puzzles matched
by this pattern — that is legal, `subject` is the only group `ConfigLoader` requires.

## Encoding

Source files are read as strict UTF-8. A file in any other encoding — cp1251, KOI8-R, latin-1 — is
skipped without a warning, and every puzzle in it goes with it. Convert the file, or keep it out of
the repository.

The text of a puzzle carries no such limit. `subject`, the body, `type`, `estimate` and `labels`
may all be written in any language:

```kotlin
// TODO(баг, 2 часа) [бэкенд]: почистить кэш
//   вытеснение по TTL, метрики в Prometheus
```

File names are scanned in any language too: the listing asks git for raw names rather than the octal
escapes it prints by default.

## Upgrading: the label group widens what matches

Before the `labels` group existed, nothing could sit between the marker and the colon, so a comment
shaped `// TODO [WIP]: text` did not match the default pattern and was ignored. It matches now, and
files a ticket with the label `WIP`. Before upgrading, search your repository for head lines
carrying a bracket in front of the colon and resolve each one, or pin `puzzle.pattern` to the old
expression.

## Upgrading: non-ascii types and file names

Before this release the `type` group accepted ASCII only, so `// TODO(баг): текст` matched nothing at
all and was ignored, and the file listing arrived with non-ASCII names escaped, so a file called
`Кэш.kt` was never scanned. Both are visible now, and every such puzzle already in your source
becomes a ticket on the first default-branch run after the upgrade, all at once. Run `--dry-run`
first and check the plan before you let that run touch the tracker.

## Upgrading: every head in a block is read

Before this release only the first line of a comment block could be a head. A second marker in the
same block, or a marker below an ordinary comment line — including a `*` line of a `/* */` block
below its description — was ignored. Each of those is a puzzle now, and becomes a ticket on the
first default-branch run after the upgrade. Puzzles already filed keep their hashes, so none of
their tickets is closed and filed again. Run `--dry-run` first and check the plan.
