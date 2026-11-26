# Troubleshooting

## Exit codes

| Code | Meaning                                                   | Where it comes from                                                                                                                                                                                         |
|------|-----------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `0`  | Run completed (or planned) without error.                 | Normal completion, including `--dry-run` and off-default-branch planning.                                                                                                                                   |
| `1`  | An error stopped the run before or during reconciliation. | Unreadable/invalid config (`ConfigError`), git unavailable (`NotARepository` — see below), or a tracker/hook failure (`TrackerError`).                                                                      |
| `2`  | A guard refused to apply a change it judged unsafe.       | `GuardViolation` — see the two guards below. Only on a real run (default branch, no `--dry-run`); during a preview the same violation is reported as a warning, not a failure, and the run still exits `0`. |

## Guards

During `--dry-run` or an off-default-branch run, a guard tripping is reported as a `log.warn` with the
violation's own message instead of failing the run — a preview has nothing to lose by showing what
the guard found, so it completes with `created 0, closed 0 (planned only)` and exits `0`. On a real
run (default branch, no `--dry-run`) the same violation still propagates and exits `2` as described
below.

### The empty-scan guard

> `scan read no files while <repo> has <n> open tickets, refusing to close them`

Fires when the scan reads **zero files** while the tracker still has open tickets for the repo.
A file counts as read once it survives `scan.exclude`, exists as a regular file, and is neither
binary, nor over 1&nbsp;MB, nor broken UTF-8. A scan that read nothing means the environment is
broken — a `scan.exclude` glob that swallows the whole tree, or a working tree whose files were
removed before the run — and says nothing about the puzzles. Refusing to act keeps that from
closing the backlog.

`.puzzler.yml` is a file like any other: as long as it lies inside the working tree and is not
excluded, the scan reads at least one file and this guard stays silent. A checkout that holds
little besides the config is therefore the mass-closure guard's business, not this one's.

**This guard is never overridden by `--force`.** Fix the checkout or `scan.exclude` instead.

A scan that read files but found no puzzles is not this guard's business: it is an ordinary
closure of every open ticket, subject to the mass-closure guard below. So removing the last puzzle
of a small backlog closes its ticket on the next run.

### The mass-closure guard

> `closing <n> of <m> tickets in <repo> exceeds the safety limit, rerun with --force if intended`

Fires when a run would close **three or more** tickets that make up more than half of the
currently-open tickets for a repo. A large, sudden wave of closures is the signature of a broken
hash (a regex change, a normalization bug), a `puzzle.pattern` typo that no longer matches
anything, a run inside the wrong repository, or a tracker query returning the wrong scope, not of
a normal cleanup commit. `--force` overrides this one when the mass closure is actually intended
(e.g. after removing a large deprecated module, or the last puzzles of a backlog of three or
more).

Closing **one or two** tickets in a run always passes, whatever share of the backlog they are.
Without that allowance a backlog of one or two tickets could never be emptied or reworded: every
removal would be "more than half". The price is bounded: a `puzzle.pattern` typo or a run in the
wrong repository can close at most two tickets before this guard steps in. Once the cause is
fixed, the next run files those puzzles again as new tickets.

## "git listing is unavailable" / `NotARepository`

> `git listing is unavailable in <root>, run puzzler inside a git repository`

The scan is powered entirely by `git ls-files`, so `puzzler` requires the `git` binary on `PATH`
and a git working tree to run inside — both are hard requirements, not just defaults. The published
Docker image already has `git` installed; this mostly bites when running `installDist` output
directly on a host without `git`.

**This one message covers two different root causes, and `puzzler` cannot tell them apart:** a
missing/unrunnable `git` binary and a working directory that simply isn't a git repository both
make the underlying `git ls-files` call fail (return no output), and both surface as this exact
`NotARepository` error. If you're sure you're inside a repository, check whether `git --version`
even works in that environment before assuming the checkout itself is the problem.

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
  the new hash and closes the ticket for the old one. Tickets are created before any is closed,
  so a tracker error in the middle of that run leaves both open until the next run closes the old
  one — this is expected, not a bug. Editing three or more puzzles at once can trip the
  mass-closure guard above; then nothing is created or closed until the run is repeated with
  `--force`.

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
3. **A guard fired.** Check the logs for `exceeds the safety limit` (three or more closures that
   are more than half of the backlog; rerun with `--force` if the closure is intended) or for
   `scan read no files` (a broken checkout or `scan.exclude`; `--force` does not help).
4. **(Jira only) the close transition failed.** Look for `none of [<names>] is available for <id>,
   workflow offers ...` — no name in `tracker.closeTransition` is reachable from the ticket's
   current status. Add a transition that status offers to the list; see `docs/trackers.md#jira` for
   how to find the right names.
5. **The tickets are hash-duplicated.** See above — duplicated hashes are never touched.
