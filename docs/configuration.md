# Configuration

`puzzler` reads `.puzzler.yml` from the repository root by default (override with `--config`).
`ConfigLoader` validates the file before any network or git call is made, so a broken config never
turns into a broken tracker call.

## Full example

```yaml
tracker:
  type: github
  project: denis-markushin/puzzler
  token: ${PUZZLER_TOKEN}
  issueType: Task
  labels:
    - tech-debt
repo:
  name: puzzler
  permalink: "https://github.com/denis-markushin/puzzler/blob/{sha}/{path}#L{line}"
scan:
  exclude:
    - "**/build/**"
    - "**/*.generated.kt"
puzzle:
  typeMapping:
    debt: "Technical Debt"
    perf: "Performance"
```

## Fields

| Field                     | Type                    | Default      | Purpose                                                                                                                                                                                                                                             |
|---------------------------|-------------------------|--------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `tracker.type`            | string                  | — (required) | One of `jira`, `github`, `gitlab`, `exec`.                                                                                                                                                                                                          |
| `tracker.url`             | string?                 | `null`       | Jira: required, the instance base URL. GitHub: defaults to `https://api.github.com`. GitLab: defaults to `https://gitlab.com`. Unused by `exec`.                                                                                                    |
| `tracker.project`         | string?                 | `null`       | Required unless `type: exec`. Jira: project key. GitHub: `owner/repo`. GitLab: numeric project id or URL-encoded `group/project` path.                                                                                                              |
| `tracker.issueType`       | string                  | `"Task"`     | Jira only: issue type used when `puzzle.typeMapping` does not resolve one for a puzzle.                                                                                                                                                             |
| `tracker.closeTransition` | string                  | `"Done"`     | Jira only: name of the workflow transition applied when a ticket closes.                                                                                                                                                                            |
| `tracker.token`           | string?                 | `null`       | Must be an `${ENV_VAR}` reference (see Secrets below). Read by `jira`, `github`, `gitlab`. Ignored by `exec` — the hook script reads its own secrets from its own environment.                                                                      |
| `tracker.command`         | string?                 | `null`       | Required when `type: exec`. Shell command invoked once per operation; the JSON request goes to its stdin.                                                                                                                                           |
| `tracker.labels`          | list of string          | `[]`         | Extra labels put on every ticket this repository files, on top of the labels a puzzle declares for itself. Rejected at load time unless each value is non-blank, free of whitespace, commas and colons, and free of the reserved `puzzler-` prefix. |
| `repo.name`               | string                  | — (required) | Identifies the repo in tickets: embedded in the `puzzler-repo-<name>` label and in the Jira JQL / GitHub / GitLab label filters used to list open tickets.                                                                                          |
| `repo.permalink`          | string?                 | `null`       | Template for the "source" line in a ticket body. Placeholders `{sha}`, `{path}`, `{line}` are substituted. Falls back to `path:line` when unset.                                                                                                    |
| `scan.exclude`            | list of string          | `[]`         | Extra glob patterns excluded from the scan, on top of whatever `.gitignore` already excludes (the file list always comes from `git ls-files`).                                                                                                      |
| `puzzle.pattern`          | string?                 | `null`       | Regexp with named groups, replaces the default puzzle pattern. See `docs/puzzle-format.md`. Must declare a `subject` group.                                                                                                                         |
| `puzzle.typeMapping`      | map of string to string | `{}`         | Maps a puzzle's raw type/marker value to a ticket type. Unmapped values pass through unchanged.                                                                                                                                                     |

The scanner also silently skips anything over 1&nbsp;MB and anything that looks binary (a null byte
in its first 8000 bytes) — these never need to be listed under `scan.exclude`.

## Secrets

`tracker.token` may only be an `${ENV_VAR}` reference, never a literal string. `ConfigLoader`
rejects a literal:

```
tracker:
  type: github
  project: denis-markushin/puzzler
  token: hardcoded-secret   # rejected: "tracker token must reference an environment variable, not a literal value"
```

The rule exists because `.puzzler.yml` lives in the repository — anyone who can read the repo can
read a literal token committed there. `${PUZZLER_TOKEN}` (or any other `${VAR}` name) is resolved
from the process environment at load time; a reference to a variable that is not set fails loudly
(`environment variable VAR is not set`) instead of silently authenticating as nobody.

## Labels

State that would otherwise need a database lives entirely in tracker labels, applied atomically in
the same request that creates the ticket:

- `puzzler-repo-<name>` — which repo (`repo.name`) a ticket belongs to. Used to scope the search
  that lists currently-open tickets.
- `puzzler-hash-<hash>` — the puzzle's identity hash. Read back on the next run to match tickets to
  puzzles still present in the code.

Labels never contain a colon — Jira rejects `:` in label values, so the format avoids it everywhere
rather than special-casing one tracker. The same reasoning covers whitespace, which Jira Server
rejects too.

Two more sources sit on top of that state. `tracker.labels` applies to every ticket the repository
files; a `[...]` group in the puzzle head line applies to one puzzle (see `docs/puzzle-format.md`).
Both are validated by the same rule: non-blank, no whitespace, no comma, no colon, and never starting with the
reserved `puzzler-` prefix. A bad value in the config fails the run before any request is sent. A bad
value in a comment is skipped with a warning naming the file and line, so one typo cannot block
reconciliation for the whole repository.

Labels are applied when a ticket is created and never afterwards. `puzzler` creates and closes
tickets; it does not update open ones, so relabelling a puzzle leaves its existing ticket alone.

## `scan.exclude` examples

```yaml
scan:
  exclude:
    - "**/build/**"        # matches build/ at any depth, and a top-level build/ too
    - "vendor/**"           # a specific top-level directory
    - "**/*.pb.go"          # generated files by extension, anywhere in the tree
    - "testdata/**"         # fixtures that happen to contain TODO-shaped text
```

Patterns are plain filesystem globs (`java.nio.file.PathMatcher`, `glob:` syntax) matched against
the path as reported by `git ls-files`. A `**/` prefix also matches at the repository root, so
`**/build/**` covers both `build/x` and `nested/build/x` without listing both.

## Minimal `exec` example

```yaml
tracker:
  type: exec
  command: ./puzzler-hook.sh
  token: ${PUZZLER_TOKEN}
repo:
  name: puzzler
```

See `docs/exec-hook.md` for the hook's request and response contract, and `examples/hook.sh` for a
working reference implementation.
