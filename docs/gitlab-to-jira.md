# GitLab to Jira Server

The primary deployment `puzzler` was built for: code hosted in GitLab, tickets created in Jira
Server or Data Center. This is the worked example end to end — configuration, CI job, credentials,
and the errors you'll hit if any of it is wrong.

## `.puzzler.yml`

```yaml
tracker:
  type: jira
  url: https://jira.example.com
  project: PROJ
  issueType: Task
  closeTransition: Done
  token: ${PUZZLER_JIRA_TOKEN}

repo:
  name: backend
  permalink: "https://gitlab.example.com/your-org/backend/-/blob/{sha}/{path}#L{line}"

scan:
  exclude:
    - "**/build/**"

puzzle:
  typeMapping:
    TODO: Task
    FIXME: Bug
    HACK: Technical Debt
```

`PUZZLER_JIRA_TOKEN` holds `username:password` for Jira Server. That's the whole credential — there
is no separate `username` setting; the colon inside the value is what tells `puzzler` to send Basic
authentication instead of Bearer. See `docs/trackers.md#jira` for the full product-to-token table.

## `.gitlab-ci.yml`

```yaml
puzzles:
  stage: .post
  image:
    name: ghcr.io/denis-markushin/puzzler:1
    entrypoint: [""]
  variables:
    GIT_DEPTH: "0"
  script:
    - /opt/puzzler/bin/puzzler --default-branch "$CI_DEFAULT_BRANCH" $PUZZLER_ARGS
  rules:
    - if: $CI_PIPELINE_SOURCE == "merge_request_event"
      variables:
        PUZZLER_ARGS: "--dry-run"
    - if: $CI_COMMIT_REF_NAME == $CI_DEFAULT_BRANCH
```

Every non-obvious line here breaks the job if you drop it:

- **`entrypoint: [""]`** — GitLab overrides a container's own entrypoint and runs the job's
  `script:` through a shell instead. The image's `ENTRYPOINT` is `/opt/puzzler/bin/puzzler`, so
  without clearing it GitLab would try to run the shell script *as an argument to* `puzzler` rather
  than invoke `puzzler` itself. Clearing the entrypoint and invoking the binary by path in `script:`
  is what makes the job runnable at all.
- **`--default-branch "$CI_DEFAULT_BRANCH"`** — the create/close cycle only runs on the default
  branch, and `puzzler` has no way to know what that branch is called (`main`, `master`, `develop`,
  ...) without being told. `CI_DEFAULT_BRANCH` is the variable GitLab sets to the project's actual
  default branch; leaving this off means `puzzler` compares against its own default of `main`, which
  silently degrades every run to planning-only on a repo whose default branch is anything else.
- **`GIT_DEPTH: "0"`** — not required by `puzzler`: `git ls-files` lists the tracked working tree at
  the checked-out commit, which a shallow clone still checks out in full, and the branch/commit
  `puzzler` uses come from `CI_COMMIT_REF_NAME`/`CI_COMMIT_SHA` (GitLab sets both on every job), not
  from walking history with `git rev-parse`. Clone depth doesn't change what `puzzler` sees. It's in
  this example only because a full clone is GitLab's own conventional default when depth isn't a
  concern — harmless, not load-bearing. Drop it if you'd rather keep the job's git fetch fast.
- **the `rules:` block** — a dry run on merge requests (`PUZZLER_ARGS: "--dry-run"`, so nothing is
  created or closed while reviewing), and a real run on the default branch. Without a `rules:`
  entry matching a branch, the job wouldn't run there at all.

## Variables GitLab supplies for free

`puzzler` reads two GitLab CI variables automatically, with no configuration:

- `CI_COMMIT_REF_NAME` — the branch, compared against `--default-branch` to decide whether to
  create/close or only plan.
- `CI_COMMIT_SHA` — the commit used to fill `{sha}` in `repo.permalink`.

Both are set by GitLab on every job; nothing in `.gitlab-ci.yml` needs to pass them through.

## Storing the credential

Create `PUZZLER_JIRA_TOKEN` under Settings → CI/CD → Variables as **masked** and **protected**.
Protected keeps it off branches that aren't protected; masked keeps it out of job logs.

GitLab refuses to mask a value that's shorter than 8 characters or that contains characters outside
its allowed set (masking works by exact string replacement in the log, so it's picky about what it
will accept). A `username:password` pair that fails masking for either reason — a short password, or
one with a character GitLab won't mask — has no workaround on Jira Server itself; on Jira Data
Center 8.14 and later, switch to a personal access token instead, which is a single opaque string
that masks cleanly.

## Jira-side setup

- **Project key** — the prefix of the issue keys the project produces, e.g. `PROJ` in `PROJ-42`.
  It's shown on the project's summary page and in every issue key; it is not the project's numeric
  id or its display name.
- **`closeTransition`** — the exact name of a workflow transition, not a status, or a list of names
  tried in order, e.g. `[To merged, Cancelled]` for a workflow that only offers `Cancelled` before
  work starts. Open an issue in the workflow and read the button labels of the transitions that close
  it, or just get the name wrong once: `puzzler` lists every transition name the workflow actually
  offers from the ticket's current status in the error message.
- **Account permissions** — the Jira account behind the token needs, on the target project:
  Browse Projects, Create Issues, Add Comments, and Transition Issues (specifically, permission to
  execute every transition listed in `closeTransition`).

## Troubleshooting

| Symptom | Cause |
|---|---|
| `tracker type jira-cloud is not supported yet` at startup | `type: jira-cloud` in `.puzzler.yml` — refused before any request; switch to `type: jira` for Server/Data Center |
| 404 on every request | a wrong `url` (this is not what `jira-cloud` produces — that type is refused during config validation, before any request is sent) |
| 401 or 403 | the token shape does not match the instance: Server needs `username:password`, Data Center 8.14+ needs a personal access token |
| `none of [Done] is available` | no name in `closeTransition` is offered from the ticket's current status; the message lists the names that are — add one of them to the list |
| `run puzzler inside a git repository` | the job cleared the checkout, or `git` is missing from a custom image |
| Exit code 2 on the default branch | a guard refused: either the scan found nothing while tickets exist, or more than half the tickets would close |

See `docs/troubleshooting.md` for the full detail behind the last two rows.
