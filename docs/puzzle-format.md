# Puzzle format

A puzzle is a comment block whose first line matches the puzzle pattern (a regexp with named
capture groups). The scanner has no notion of programming languages — it only recognizes comment
line prefixes: `//`, `#`, `--`, `*` (the continuation lines of a `/* */` block), and `;`.

## Default pattern

```
^(?<marker>TODO|FIXME|HACK)(?:\((?<type>[\w-]+)(?:,\s*(?<estimate>[^)]+))?\))?:\s*(?<subject>.+)$
```

Shape: `MARKER(type, estimate): subject`, where `type` and `estimate` are optional and only make
sense together — `estimate` cannot appear without `type`.

Kotlin:

```kotlin
class Cache {
    // TODO(debt, 30min): extract cache into a separate bean
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
the head's indent. The first line at the same or lower indent ends the body — and everything from
that line on is ignored, it does not start a new puzzle:

```kotlin
// TODO: extract cache
//   needs TTL           <- indent 2, included in the body
// ordinary comment       <- indent 0, same as head: body stops here
//   and this is not body either  <- never reached, still ignored
```

Body: `needs TTL`.

This also means two markers placed back-to-back in the same comment block (no blank line or code
line between them) do **not** produce two puzzles — the scanner treats consecutive comment lines
as one block, and the parser only reads the first line as a head. The second `TODO:` line is
silently swallowed. Separate puzzles with a blank line or a line of code:

```kotlin
// TODO: first puzzle
// TODO: second one gets lost, same block as above

// TODO: this one is fine, a blank line split the block
```

## Named group contract

| Group | Required | Meaning |
|---|---|---|
| `subject` | yes | Ticket title. A match without a non-blank `subject` is not a puzzle. |
| `type` | no | Feeds `puzzle.typeMapping`; becomes the ticket type. |
| `estimate` | no | Free-form text, carried into the ticket body. |
| `assignee` | no | Carried to trackers that support assignment (GitHub). Not declared by the default pattern. |
| `marker` | no | Stand-in for `type` when `type` is absent or not declared by the pattern. Used by `typeMapping` too. |

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
