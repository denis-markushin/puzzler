# Built-in trackers

Three tracker types ship as HTTP adapters: `jira`, `github`, `gitlab`. All three retry `429` and
`5xx` responses with backoff and fail fast on other 4xx responses (see `HttpCaller`). All three
authenticate with `tracker.token`, which must be `${ENV_VAR}` — see `docs/configuration.md#secrets`.

## Jira

The API version `puzzler` speaks is fixed by the tracker type, not by a setting — there is no
`apiVersion` field to get right or wrong:

| Product                                   | Tracker type | What goes in `token`    |
|-------------------------------------------|--------------|-------------------------|
| Jira Server, Jira Data Center before 8.14 | `jira`       | `username:password`     |
| Jira Data Center 8.14 and later           | `jira`       | a personal access token |
| Jira Cloud                                | `jira-cloud` | not supported yet       |

`jira` targets `<url>/rest/api/2` — what Server and Data Center actually expose. `jira-cloud` is a
recognised type, not merely an unknown string, but `ConfigLoader` refuses it during configuration
validation, before scanning and before any request reaches Jira: Cloud's REST API v3 requires
descriptions in Atlassian Document Format, which `puzzler` does not produce yet. Rejecting the type
up front — rather than letting the first request fail — is deliberate: a run that failed partway
through would leave some tickets created and the rest not, and the next run would then have to
reconcile against a half-populated tracker. Refusing before any request makes that outcome
impossible. See the `TODO(feature)` left in `JiraTracker` for the ADF work that would lift this.

Minimal config:

```yaml
tracker:
  type: jira
  url: https://your-domain.example.com
  project: PROJ
  token: ${PUZZLER_TOKEN}
repo:
  name: puzzler
```

- `url` — the Jira base URL, no trailing slash. Required in practice: the adapter builds requests
  as `<url>/rest/api/2/...` and an unset `url` produces a broken URL rather than a clear error.
- `project` — the Jira project **key** (e.g. `PROJ`), not its numeric id or display name.
- `token` is the entire credential — there is no separate `username` setting. Its shape decides the
  authentication scheme: a value containing a colon is treated as `username:password` and sent as
  `Authorization: Basic <base64 of the whole value>`; a value with no colon is treated as a personal
  access token and sent as `Authorization: Bearer <value>`. Splitting on the first colon is what
  Basic authentication does anyway, so a password that itself contains a colon still works
  unchanged.
- Token scope: enough to browse the project, create issues, add comments, and read/apply
  transitions.
- `issueType` (default `Task`) is the fallback used only when a puzzle captured **no** type and
  **no** marker at all (the raw value is `null`). It is not a fallback for unmapped values: a type
  or marker that `puzzle.typeMapping` does not recognize is sent to Jira unchanged, as the literal
  issue type — see `docs/puzzle-format.md#ticket-type-inference`. A `typeMapping` that doesn't cover
  every marker your pattern can produce will therefore try to create issues of a type Jira doesn't
  have, and creation will fail.
- `closeTransition` (default `Done`) is the exact **name** of a workflow transition, or a list of
  names tried in order — not a status name and not a transition id. `puzzler` applies the first
  listed transition the workflow offers from the ticket's current status, so
  `[To merged, Cancelled]` closes a worked-on ticket through `To merged` and one still in its
  initial status through `Cancelled`.

**Finding the transition name:** open an issue in the relevant project's workflow and read the
button label of the transition that closes it (Jira shows transition names on the workflow
buttons and on the workflow diagram's arrows), or call
`GET /rest/api/2/issue/{key}/transitions` for an issue in that project — the response lists every
transition currently available from its status, by name.

**"none of [...] is available"**: the tracker reports this verbatim, listing the configured names
and every transition the workflow actually offers from the ticket's current status. Either a name
in `closeTransition` is wrong, or the ticket is in a status from which none of the listed
transitions is reachable (workflows differ by status; a name that works from "In Progress" may not
exist from "Backlog") — add the transition that status does offer to the list.

## GitHub

Minimal config:

```yaml
tracker:
  type: github
  project: denis-markushin/puzzler
  token: ${PUZZLER_TOKEN}
repo:
  name: puzzler
```

- `url` — defaults to `https://api.github.com`; set it for GitHub Enterprise Server
  (`https://your-host/api/v3`).
- `project` — `owner/repo`, exactly as it appears in the repository URL.
- Token needs the `repo` scope (classic PAT) or Issues read/write (fine-grained PAT) on that
  repository. A token without issue-write access fails with `403`.
- Puzzle type becomes an issue **label** (not a GitHub "type" field — GitHub Issues has no native
  ticket-type concept). `assignee`, when the puzzle pattern captures one, is passed through as a
  GitHub username; an unknown username makes issue creation fail with `422`.

**Typical errors**: `404` on every call almost always means `project` isn't `owner/repo` (a bare
repo name, or a URL, will not resolve). `401`/`403` means the token is missing, expired, or lacks
scope.

## GitLab

Minimal config:

```yaml
tracker:
  type: gitlab
  project: "12345678"
  token: ${PUZZLER_TOKEN}
repo:
  name: puzzler
```

- `url` — defaults to `https://gitlab.com`; set it for self-managed GitLab.
- `project` — either the project's numeric id (simplest, shown on the project's overview page) or
  its URL-encoded `namespace%2Fproject` path. An un-encoded path containing `/` will not resolve.
- Authentication uses the `PRIVATE-TOKEN` header, not `Authorization`. A personal, project, or
  group access token with the `api` scope (or the narrower `write_repository` alone is not
  sufficient — issues need `api`) and Reporter role or above on the project.
- Puzzle type becomes an issue label, same as GitHub.

**Typical errors**: `404` on a numeric `project` usually means the token can't see that project id
(wrong instance, or no access); on a path `project` it usually means the slashes weren't
URL-encoded.
