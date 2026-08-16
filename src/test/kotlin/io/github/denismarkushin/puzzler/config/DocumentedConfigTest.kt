package io.github.denismarkushin.puzzler.config

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

private fun samples(document: Path): List<String> =
    Regex("```yaml\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
        .findAll(document.readText())
        .map { match -> match.groupValues[1] }
        .filter { sample -> sample.contains("tracker:") }
        .toList()

class DocumentedConfigTest {
    @Test
    @Timeout(30)
    fun `configuration document has parsable samples`(@TempDir root: Path) {
        val document = Path.of("docs/configuration.md")
        val environment = mapOf("PUZZLER_TOKEN" to "secret")
        val failures = samples(document).filter { sample ->
            val file = root.resolve("sample.yml")
            file.writeText(sample)
            runCatching { ConfigLoader.load(file, environment) }.isFailure
        }
        assertThat(failures, "documented configuration samples no longer parse").isEqualTo(emptyList())
    }

    @Test
    @Timeout(30)
    fun `readme has parsable samples`(@TempDir root: Path) {
        val document = Path.of("README.md")
        val environment = mapOf("PUZZLER_TOKEN" to "secret")
        val failures = samples(document).filter { sample ->
            val file = root.resolve("sample.yml")
            file.writeText(sample)
            runCatching { ConfigLoader.load(file, environment) }.isFailure
        }
        assertThat(failures, "readme configuration samples no longer parse").isEqualTo(emptyList())
    }
}
