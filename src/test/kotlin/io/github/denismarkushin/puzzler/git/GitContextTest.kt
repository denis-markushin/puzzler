package io.github.denismarkushin.puzzler.git

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.matches
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

private class FakeGit(
    private val answers: Map<String, String?>,
) : GitCommand {
    override fun run(vararg args: String): String? = answers[args.joinToString(" ")]
}

class GitContextTest {
    @Test
    fun `flag beats every other source`() {
        val context = GitContext.detect(
            branchFlag = "release",
            shaFlag = null,
            env = mapOf("GITHUB_REF_NAME" to "main"),
            git = FakeGit(mapOf("rev-parse --abbrev-ref HEAD" to "feature")),
        )
        assertThat(context.branch, "explicit branch flag was overridden").isEqualTo("release")
    }

    @Test
    fun `github environment beats gitlab environment`() {
        val context = GitContext.detect(
            branchFlag = null,
            shaFlag = null,
            env = mapOf("GITHUB_REF_NAME" to "main", "CI_COMMIT_REF_NAME" to "feature"),
            git = FakeGit(emptyMap()),
        )
        assertThat(context.branch, "GitLab variable won over the GitHub one").isEqualTo("main")
    }

    @Test
    fun `git answers when environment is silent`() {
        val context = GitContext.detect(
            branchFlag = null,
            shaFlag = null,
            env = emptyMap(),
            git = FakeGit(mapOf("rev-parse --abbrev-ref HEAD" to "develop")),
        )
        assertThat(context.branch, "git was not consulted as the last source").isEqualTo("develop")
    }

    @Test
    fun `branch stays unknown when nothing answers`() {
        val context = GitContext.detect(
            branchFlag = null,
            shaFlag = null,
            env = emptyMap(),
            git = FakeGit(emptyMap()),
        )
        assertThat(context.branch, "a branch was invented out of nothing").isNull()
    }

    @Test
    fun `sha comes from gitlab variable`() {
        val context = GitContext.detect(
            branchFlag = null,
            shaFlag = null,
            env = mapOf("CI_COMMIT_SHA" to "abc123"),
            git = FakeGit(emptyMap()),
        )
        assertThat(context.sha, "GitLab commit variable was ignored").isEqualTo("abc123")
    }

    @Test
    @Timeout(30)
    fun `git failure degrades to no answer`(@TempDir root: Path) {
        val missing = root.resolve("no-such-directory")
        val answer = ProcessGitCommand(missing).run("rev-parse", "HEAD")
        assertThat(answer, "an unstartable git process threw instead of degrading to null").isNull()
    }

    @Test
    @Timeout(30)
    fun `process command returns real git output`() {
        val sha = ProcessGitCommand(Path.of("").toAbsolutePath()).run("rev-parse", "HEAD")
        assertThat(sha?.matches(Regex("[0-9a-f]{40}")) == true, "git output never made it back through the temp file").isEqualTo(true)
    }
}
