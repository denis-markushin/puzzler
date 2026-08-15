package io.github.denismarkushin.puzzler.git

/**
 * Ветка и коммит текущего прогона.
 * Ветка решает, разрешён ли полный цикл; коммит подставляется в постоянные ссылки.
 */
data class GitContext(
    val branch: String?,
    val sha: String?,
) {
    companion object {
        fun detect(branchFlag: String?, shaFlag: String?, env: Map<String, String>, git: GitCommand) =
            GitContext(
                branch = branchFlag
                    ?: env["GITHUB_REF_NAME"]
                    ?: env["CI_COMMIT_REF_NAME"]
                    ?: git.run("rev-parse", "--abbrev-ref", "HEAD"),
                sha = shaFlag
                    ?: env["GITHUB_SHA"]
                    ?: env["CI_COMMIT_SHA"]
                    ?: git.run("rev-parse", "HEAD"),
            )
    }
}
