# Troubleshooting

## Exit codes

| Code | Meaning | Where it comes from |
|---|---|---|
| `0` | Run completed (or planned) without error. | Normal completion, including `--dry-run` and off-default-branch planning. |
| `1` | An error stopped the run before or during reconciliation. | Unreadable/invalid config (`ConfigError`), no git repository (`NotARepository`), or a tracker/hook failure (`TrackerError`). |
| `2` | A guard refused to apply a change it judged unsafe. | `GuardViolation` — see the two guards below. |

## Guards

### The empty-scan guard

> `scan found no puzzles while <repo> has <n> open tickets, refusing to close them`

Fires when the scan finds **zero** puzzles anywhere in the working tree while the tracker still
has open tickets for the repo. A scan of nothing is far more likely to mean something is broken —
wrong working directory, an empty `git ls-files` because the process isn't actually inside the
repo, a `puzzle.pattern` typo that no longer matches anything — than that every puzzle in the
codebase was genuinely resolved in one commit. Refusing to act keeps a misconfiguration from
closing an entire backlog of real tickets in one run.

**This guard is never overridden by `--force`.** If the scan is legitimately empty (the last
puzzle really was removed), close the remaining ticket(s) by hand, or adjust `scan.exclude` /
`puzzle.pattern` if the scan is wrongly finding nothing.

### The mass-closure guard

> `closing <n> of <m> tickets in <repo> exceeds the safety limit, rerun with --force if intended`

Fires when a run would close more than half of the currently-open tickets for a repo. A large,
sudden wave of closures is the signature of a broken hash (a regex change, a normalization bug) or
a tracker query returning the wrong scope, not of a normal cleanup commit. `--force` overrides this
one when the mass closure is actually intended (e.g. after removing a large deprecated module).

## Duplicated tickets

Two situations produce more than one open ticket for the same puzzle:

- **A ticket was closed by hand in the tracker while the puzzle is still in the code.** All three
  built-in adapters only list *open* tickets (`statusCategory != Done` in Jira, `state=open` on
  GitHub, `state=opened` on GitLab). A manually-closed ticket drops out of that list, so its hash
  no longer looks "known" — the next run creates a brand new ticket for the same still-present
  puzzle. Don't close puzzler-managed tickets by hand; remove the puzzle from the code instead and
  let the next run close it.
- **The puzzle's text was edited.** The identity hash covers only the puzzle's normalized subject
  and body — deliberately not its file or line, so moving code around never touches a ticket. But
  it does mean an edited puzzle is, by design, a *new* puzzle: the next run creates a ticket for
  the new hash and closes the ticket for the old one (subject to the mass-closure guard above).
  Between those two runs (or if the guard blocks the closure) both tickets can be open at once —
  this is expected, not a bug.

If the reconciler ever finds **several open tickets sharing the same hash**, it leaves all of them
untouched and logs a warning (`hash <hash> is attached to several tickets in <repo>, leaving them
untouched`) rather than guessing which one is authoritative. Close the extras by hand.

## Tickets aren't closing

Work through these in order:

1. **The run isn't on the default branch.** The full create-and-close cycle only runs when the
   detected branch equals `--default-branch` (default `main`). Anywhere else — feature branches,
   detached HEAD, a branch that can't be determined at all — the run silently degrades to
   planning only (`created N, closed M (planned only)`) and writes nothing. Check the branch
   `puzzler` actually detected; in CI, confirm the ref-name environment variable your CI system
   sets is one `puzzler` reads (see `docs/ci-recipes.md`), or pass `--branch` explicitly.
2. **`--dry-run` is set.** Also plans only, on any branch.
3. **The mass-closure guard fired.** Check the logs for `exceeds the safety limit`; rerun with
   `--force` if the closure is intended.
4. **(Jira only) the close transition failed.** Look for `transition <name> is not available for
   <id>, workflow offers ...` — `tracker.closeTransition` doesn't match a transition name reachable
   from the ticket's current status. See `docs/trackers.md#jira` for how to find the right name.
5. **The tickets are hash-duplicated.** See above — duplicated hashes are never touched.
