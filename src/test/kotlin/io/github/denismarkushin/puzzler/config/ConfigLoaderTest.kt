package io.github.denismarkushin.puzzler.config

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

private fun config(root: Path, body: String): Path {
    val file = root.resolve(".puzzler.yml")
    file.writeText(body)
    return file
}

class ConfigLoaderTest {
    @Test
    fun `loader substitutes environment variables`(@TempDir root: Path) {
        val file = config(
            root,
            """
            tracker:
              type: github
              project: denis-markushin/puzzler
              token: ${'$'}{PUZZLER_TOKEN}
            repo:
              name: puzzler
            """.trimIndent(),
        )
        val loaded = ConfigLoader.load(file, mapOf("PUZZLER_TOKEN" to "secret"))
        assertThat(loaded.tracker.token, "environment variable was not substituted").isEqualTo("secret")
    }

    @Test
    fun `loader rejects a literal token`(@TempDir root: Path) {
        val file = config(
            root,
            """
            tracker:
              type: github
              project: denis-markushin/puzzler
              token: hardcoded-secret
            repo:
              name: puzzler
            """.trimIndent(),
        )
        val failure = runCatching { ConfigLoader.load(file, emptyMap()) }.exceptionOrNull()
        assertThat(failure is ConfigError, "a literal token slipped through validation").isEqualTo(true)
    }

    @Test
    fun `loader rejects a literal token even when an unrelated field uses a placeholder`(@TempDir root: Path) {
        val file = config(
            root,
            """
            tracker:
              type: github
              project: denis-markushin/puzzler
              issueType: ${'$'}{ISSUE_TYPE}
              token: hardcoded-secret
            repo:
              name: puzzler
            """.trimIndent(),
        )
        val failure = runCatching { ConfigLoader.load(file, mapOf("ISSUE_TYPE" to "Bug")) }.exceptionOrNull()
        assertThat(failure is ConfigError, "a literal token hid behind an unrelated placeholder").isEqualTo(true)
    }

    @Test
    fun `loader rejects a missing environment variable`(@TempDir root: Path) {
        val file = config(
            root,
            """
            tracker:
              type: github
              project: denis-markushin/puzzler
              token: ${'$'}{ABSENT_TOKEN}
            repo:
              name: puzzler
            """.trimIndent(),
        )
        val failure = runCatching { ConfigLoader.load(file, emptyMap()) }.exceptionOrNull()
        assertThat(failure?.message, "an unresolved variable was reported as a parse failure")
            .isEqualTo("environment variable ABSENT_TOKEN is not set")
    }

    @Test
    fun `loader refuses jira cloud with the reason`(@TempDir root: Path) {
        val file = config(
            root,
            """
            tracker:
              type: jira-cloud
              project: PROJ
              token: ${'$'}{PUZZLER_TOKEN}
            repo:
              name: puzzler
            """.trimIndent(),
        )
        val failure = runCatching { ConfigLoader.load(file, mapOf("PUZZLER_TOKEN" to "secret")) }.exceptionOrNull()
        assertThat((failure as? ConfigError)?.message, "jira cloud was not refused with a reason naming ADF")
            .isNotNull().contains("ADF")
    }

    @Test
    fun `loader rejects an unknown tracker type`(@TempDir root: Path) {
        val file = config(
            root,
            """
            tracker:
              type: redmine
              project: denis-markushin/puzzler
              token: ${'$'}{PUZZLER_TOKEN}
            repo:
              name: puzzler
            """.trimIndent(),
        )
        val failure = runCatching { ConfigLoader.load(file, mapOf("PUZZLER_TOKEN" to "secret")) }.exceptionOrNull()
        assertThat(failure is ConfigError, "an unsupported tracker type was accepted").isEqualTo(true)
    }

    @Test
    fun `loader rejects an exec tracker without a command`(@TempDir root: Path) {
        val file = config(
            root,
            """
            tracker:
              type: exec
              token: ${'$'}{PUZZLER_TOKEN}
            repo:
              name: puzzler
            """.trimIndent(),
        )
        val failure = runCatching { ConfigLoader.load(file, mapOf("PUZZLER_TOKEN" to "secret")) }.exceptionOrNull()
        assertThat(failure is ConfigError, "an exec tracker without a command was accepted").isEqualTo(true)
    }

    @Test
    fun `loader rejects a non-exec tracker without a project`(@TempDir root: Path) {
        val file = config(
            root,
            """
            tracker:
              type: gitlab
              token: ${'$'}{PUZZLER_TOKEN}
            repo:
              name: puzzler
            """.trimIndent(),
        )
        val failure = runCatching { ConfigLoader.load(file, mapOf("PUZZLER_TOKEN" to "secret")) }.exceptionOrNull()
        assertThat(failure is ConfigError, "a non-exec tracker without a project was accepted").isEqualTo(true)
    }

    @Test
    fun `loader rejects a pattern without a subject group`(@TempDir root: Path) {
        val file = config(
            root,
            """
            tracker:
              type: github
              project: denis-markushin/puzzler
              token: ${'$'}{PUZZLER_TOKEN}
            repo:
              name: puzzler
            puzzle:
              pattern: '^TODO: (.+)${'$'}'
            """.trimIndent(),
        )
        val failure = runCatching { ConfigLoader.load(file, mapOf("PUZZLER_TOKEN" to "secret")) }.exceptionOrNull()
        assertThat(failure is ConfigError, "a pattern without the subject group was accepted").isEqualTo(true)
    }

    @Test
    fun `loader wraps an invalid regex pattern as a config error`(@TempDir root: Path) {
        val file = config(
            root,
            """
            tracker:
              type: github
              project: denis-markushin/puzzler
              token: ${'$'}{PUZZLER_TOKEN}
            repo:
              name: puzzler
            puzzle:
              pattern: '(?<subject>unclosed'
            """.trimIndent(),
        )
        val failure = runCatching { ConfigLoader.load(file, mapOf("PUZZLER_TOKEN" to "secret")) }.exceptionOrNull()
        assertThat(failure is ConfigError, "a malformed regex leaked out instead of becoming a config error").isEqualTo(true)
    }

    @Test
    fun `loader falls back to the default pattern`(@TempDir root: Path) {
        val file = config(
            root,
            """
            tracker:
              type: github
              project: denis-markushin/puzzler
              token: ${'$'}{PUZZLER_TOKEN}
            repo:
              name: puzzler
            """.trimIndent(),
        )
        val loaded = ConfigLoader.load(file, mapOf("PUZZLER_TOKEN" to "secret"))
        assertThat(loaded.puzzle.regex().find("TODO: work")?.groups?.get("subject")?.value, "default pattern was not applied")
            .isEqualTo("work")
    }

    @Test
    fun `type mapping prefers the parsed type`() {
        val puzzle = PuzzleConfig(pattern = null, typeMapping = mapOf("debt" to "Technical Debt", "TODO" to "Task"))
        assertThat(puzzle.ticketType("debt"), "type mapping ignored the parsed type").isEqualTo("Technical Debt")
    }

    @Test
    fun `type mapping passes unknown values through`() {
        val puzzle = PuzzleConfig(pattern = null, typeMapping = mapOf("debt" to "Technical Debt"))
        assertThat(puzzle.ticketType("perf"), "unmapped type was not passed through").isEqualTo("perf")
    }

    @Test
    fun `loader rejects a label carrying a space`(@TempDir root: Path) {
        val file = config(
            root,
            """
            tracker:
              type: github
              project: denis-markushin/puzzler
              token: ${'$'}{PUZZLER_TOKEN}
              labels:
                - "cache warmup"
            repo:
              name: puzzler
            """.trimIndent(),
        )
        val failure = runCatching { ConfigLoader.load(file, mapOf("PUZZLER_TOKEN" to "secret")) }.exceptionOrNull()
        assertThat(failure is ConfigError, "a label carrying a space was accepted").isEqualTo(true)
    }

    @Test
    fun `loader rejects a label wearing the reserved prefix`(@TempDir root: Path) {
        val file = config(
            root,
            """
            tracker:
              type: github
              project: denis-markushin/puzzler
              token: ${'$'}{PUZZLER_TOKEN}
              labels:
                - puzzler-repo-elsewhere
            repo:
              name: puzzler
            """.trimIndent(),
        )
        val failure = runCatching { ConfigLoader.load(file, mapOf("PUZZLER_TOKEN" to "secret")) }.exceptionOrNull()
        assertThat(failure is ConfigError, "a label wearing the reserved prefix was accepted").isEqualTo(true)
    }

    @Test
    fun `loader rejects an empty label`(@TempDir root: Path) {
        val file = config(
            root,
            """
            tracker:
              type: github
              project: denis-markushin/puzzler
              token: ${'$'}{PUZZLER_TOKEN}
              labels:
                - ""
            repo:
              name: puzzler
            """.trimIndent(),
        )
        val failure = runCatching { ConfigLoader.load(file, mapOf("PUZZLER_TOKEN" to "secret")) }.exceptionOrNull()
        assertThat(failure is ConfigError, "an empty label was accepted").isEqualTo(true)
    }

    @Test
    fun `loader reads a single close transition as a list of one`(@TempDir root: Path) {
        val file = config(
            root,
            """
            tracker:
              type: jira
              url: https://jira.example.com
              project: PROJ
              closeTransition: To merged
              token: ${'$'}{PUZZLER_TOKEN}
            repo:
              name: puzzler
            """.trimIndent(),
        )
        val loaded = ConfigLoader.load(file, mapOf("PUZZLER_TOKEN" to "secret"))
        assertThat(loaded.tracker.closeTransition, "a single close transition was not read as a list of one")
            .isEqualTo(listOf("To merged"))
    }

    @Test
    fun `loader reads close transitions in the configured order`(@TempDir root: Path) {
        val file = config(
            root,
            """
            tracker:
              type: jira
              url: https://jira.example.com
              project: PROJ
              closeTransition: [To merged, Cancelled]
              token: ${'$'}{PUZZLER_TOKEN}
            repo:
              name: puzzler
            """.trimIndent(),
        )
        val loaded = ConfigLoader.load(file, mapOf("PUZZLER_TOKEN" to "secret"))
        assertThat(loaded.tracker.closeTransition, "close transitions lost their configured order")
            .isEqualTo(listOf("To merged", "Cancelled"))
    }

    @Test
    fun `loader rejects a blank close transition for jira`(@TempDir root: Path) {
        val file = config(
            root,
            """
            tracker:
              type: jira
              url: https://jira.example.com
              project: PROJ
              closeTransition: " "
              token: ${'$'}{PUZZLER_TOKEN}
            repo:
              name: puzzler
            """.trimIndent(),
        )
        val failure = runCatching { ConfigLoader.load(file, mapOf("PUZZLER_TOKEN" to "secret")) }.exceptionOrNull()
        assertThat(failure is ConfigError, "a blank close transition was accepted for jira").isEqualTo(true)
    }

    @Test
    fun `loader rejects an empty list of close transitions for jira`(@TempDir root: Path) {
        val file = config(
            root,
            """
            tracker:
              type: jira
              url: https://jira.example.com
              project: PROJ
              closeTransition: []
              token: ${'$'}{PUZZLER_TOKEN}
            repo:
              name: puzzler
            """.trimIndent(),
        )
        val failure = runCatching { ConfigLoader.load(file, mapOf("PUZZLER_TOKEN" to "secret")) }.exceptionOrNull()
        assertThat(failure is ConfigError, "an empty list of close transitions was accepted for jira").isEqualTo(true)
    }

    @Test
    fun `loader rejects a null close transition for jira`(@TempDir root: Path) {
        val file = config(
            root,
            """
            tracker:
              type: jira
              url: https://jira.example.com
              project: PROJ
              closeTransition: [Done, ~]
              token: ${'$'}{PUZZLER_TOKEN}
            repo:
              name: puzzler
            """.trimIndent(),
        )
        val failure = runCatching { ConfigLoader.load(file, mapOf("PUZZLER_TOKEN" to "secret")) }.exceptionOrNull()
        assertThat(failure is ConfigError, "a null close transition was not reported as a ConfigError").isEqualTo(true)
    }
}
