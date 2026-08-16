# CI recipes

`puzzler` needs two things from CI: the branch name (to decide whether the full create/close cycle
is allowed) and, ideally, the commit sha (used in ticket bodies via `repo.permalink`). It reads
these automatically from well-known CI environment variables — `GITHUB_REF_NAME`/`GITHUB_SHA` on
GitHub Actions, `CI_COMMIT_REF_NAME`/`CI_COMMIT_SHA` on GitLab CI — falling back to `git
rev-parse` when neither is set. Anything else (Jenkins included) needs `--branch`/`--sha` passed
explicitly. See `docs/troubleshooting.md` for what happens when the branch can't be determined at
all.

Run `--dry-run` on every pull/merge request build and the full run only on the default branch. In
practice the branch guard inside `puzzler` already degrades a PR build to planning-only on its own
(a PR's ref is never the default branch), but passing `--dry-run` explicitly makes the two modes
visible in the CI config instead of implicit in `puzzler`'s branch detection.

## GitHub Actions

```yaml
name: Puzzles

on:
  push:
    branches: [ main ]
  pull_request:

jobs:
  puzzles:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: |
          docker run --rm -v "$PWD:/repo" -w /repo \
            -e PUZZLER_TOKEN -e GITHUB_REF_NAME -e GITHUB_SHA \
            ghcr.io/denis-markushin/puzzler:1 \
            ${{ github.event_name == 'pull_request' && '--dry-run' || '' }}
        env:
          PUZZLER_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

`GITHUB_REF_NAME` on a `pull_request` event is the merge ref (e.g. `123/merge`), never `main` — so
even without the explicit `--dry-run` the branch guard alone would already refuse to close or
create tickets there. The `pull_request` conditional keeps that intent explicit in the workflow.

## GitLab CI

```yaml
puzzles:
  image: ghcr.io/denis-markushin/puzzler:1
  entrypoint: [""]
  script:
    - |
      if [ "$CI_PIPELINE_SOURCE" = "merge_request_event" ]; then
        /opt/puzzler/bin/puzzler --dry-run
      else
        /opt/puzzler/bin/puzzler
      fi
  rules:
    - if: $CI_COMMIT_BRANCH == "main"
    - if: $CI_PIPELINE_SOURCE == "merge_request_event"
```

`CI_COMMIT_REF_NAME` and `CI_COMMIT_SHA` are picked up automatically; `PUZZLER_TOKEN` (or whatever
name `.puzzler.yml` references) must still be defined as a masked/protected CI/CD variable. The
image's own entrypoint is the `puzzler` binary, so it's overridden with `entrypoint: [""]` to let
`script:` decide dry-run vs. full run.

For the full worked example of this pairing — GitLab CI with a Jira Server tracker, including the
`.puzzler.yml`, credential storage, and troubleshooting — see `docs/gitlab-to-jira.md`.

## Jenkins (declarative pipeline)

```groovy
pipeline {
    agent any
    stages {
        stage('Puzzles') {
            steps {
                script {
                    def flag = env.CHANGE_ID ? '--dry-run' : ''
                    sh """
                        docker run --rm -v "\$PWD:/repo" -w /repo \
                          -e PUZZLER_TOKEN=${env.PUZZLER_TOKEN} \
                          ghcr.io/denis-markushin/puzzler:1 \
                          --branch ${env.BRANCH_NAME} --sha ${env.GIT_COMMIT} ${flag}
                    """
                }
            }
        }
    }
}
```

Jenkins has no environment variable `puzzler` recognizes automatically, so `--branch` and `--sha`
are always required — map them from whatever a multibranch pipeline exposes
(`BRANCH_NAME`, `GIT_COMMIT`). `CHANGE_ID` is set only on pull-request builds in a multibranch
pipeline, which is what selects `--dry-run` here.
