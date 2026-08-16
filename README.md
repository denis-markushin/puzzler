# puzzler

`puzzler` scans your source comments for structured TODO "puzzles" and reconciles them against an
issue tracker: it opens a ticket for every puzzle in the code, and closes the ticket once the
puzzle is removed. Your comments stay the backlog; the tracker just mirrors them.

```
// TODO(debt, 30min): extract cache into a separate bean
//   needs TTL and metrics
```

becomes an open ticket the moment that comment lands on the default branch, and gets closed the
moment the comment is deleted — no manual bookkeeping in either direction.

## How a run works

```
Scan → Parse → Hash → Reconcile → Apply
```

1. **Scan** — `git ls-files` lists every tracked (and untracked-but-not-ignored) file; binary
   files and anything over 1&nbsp;MB are skipped, and contiguous comment lines are grouped into
   blocks.
2. **Parse** — each block's first line is matched against a regexp; a match yields a puzzle
   (subject, optional type/estimate/assignee, and a body from any more-indented following lines).
3. **Hash** — a puzzle's identity is a 12-character hash of its *normalized text only* (subject +
   body). Moving a file or shifting line numbers keeps the same ticket; editing the puzzle's text
   closes the old ticket and opens a new one — see `docs/puzzle-format.md`.
4. **Reconcile** — the puzzles found in code are diffed against the tracker's currently open
   tickets (matched by hash). Two guards refuse to apply a diff that looks like a broken
   environment rather than real work — see `docs/troubleshooting.md`.
5. **Apply** — new puzzles get created, orphaned tickets get closed. This step only runs on the
   default branch; everywhere else (feature branches, an undetermined branch, or `--dry-run`) the
   run degrades to a plan and writes nothing.

## Prerequisites

`puzzler` scans by running `git ls-files`, so it always needs the `git` binary on `PATH` and a git
working tree to run inside — neither is optional. The published Docker image already bundles
`git`, so this only matters if you build and run `installDist` yourself. Missing `git` and "not
inside a repository" are indistinguishable from `puzzler`'s point of view and produce the identical
error; see `docs/troubleshooting.md`.

## Quick start (GitHub Issues, five minutes)

1. Create `.puzzler.yml` at the repository root:

   ```yaml
   tracker:
     type: github
     project: your-org/your-repo
     token: ${PUZZLER_TOKEN}
   repo:
     name: your-repo
   ```

2. Export a GitHub token with issue write access, and dry-run against your own repo:

   ```bash
   docker run --rm -v "$PWD:/repo" -w /repo -e PUZZLER_TOKEN \
     ghcr.io/denis-markushin/puzzler:1 --dry-run
   ```

   `--dry-run` only prints what it would create/close (`created N, closed M (planned only)`) — it
   never touches the tracker.

3. Drop `--dry-run` in CI once you're happy with the plan — see `docs/ci-recipes.md` for
   ready-made GitHub Actions, GitLab CI and Jenkins snippets.

The only artifact `puzzler` ships as is the Docker image
`ghcr.io/denis-markushin/puzzler`; there is no native binary, no Maven/Gradle artifact, and no
GitHub Action wrapper. The image's entrypoint is the `puzzler` binary itself and its working
directory is `/repo`, so mount your checkout there.

## Documentation

- [`docs/puzzle-format.md`](docs/puzzle-format.md) — the default comment format, the body
  indentation rule, and how to replace the pattern for a different convention.
- [`docs/configuration.md`](docs/configuration.md) — every `.puzzler.yml` field, secrets handling,
  the label scheme, and `scan.exclude` globs.
- [`docs/trackers.md`](docs/trackers.md) — Jira, GitHub and GitLab adapters: minimal config,
  required token scopes, and typical errors.
- [`docs/exec-hook.md`](docs/exec-hook.md) — the `exec` tracker's request/response contract, for
  plugging in a tracker with no built-in adapter. Paired with a working reference script at
  [`examples/hook.sh`](examples/hook.sh).
- [`docs/ci-recipes.md`](docs/ci-recipes.md) — GitHub Actions, GitLab CI and Jenkins snippets that
  run `--dry-run` on pull/merge requests and the full cycle on the default branch.
- [`docs/troubleshooting.md`](docs/troubleshooting.md) — exit codes, what each guard protects
  against, and why tickets duplicate or fail to close.
