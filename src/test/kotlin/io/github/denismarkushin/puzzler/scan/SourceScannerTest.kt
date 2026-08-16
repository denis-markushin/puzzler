package io.github.denismarkushin.puzzler.scan

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import io.github.denismarkushin.puzzler.git.GitCommand
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

private class ListingGit(
    private val listing: String?,
) : GitCommand {
    override fun run(vararg args: String): String? = listing
}

class SourceScannerTest {
    @Test
    fun `scanner groups consecutive comment lines`(@TempDir root: Path) {
        root.resolve("Cache.kt").writeText(
            """
            class Cache {
                // TODO: extract cache
                //  needs TTL
                val store = mutableMapOf<String, String>()
            }
            """.trimIndent(),
        )
        val blocks = SourceScanner(root, emptyList(), ListingGit("Cache.kt")).blocks()
        assertThat(blocks.single().lines.size, "consecutive comment lines were not grouped").isEqualTo(2)
    }

    @Test
    fun `scanner splits blocks separated by code`(@TempDir root: Path) {
        root.resolve("Cache.kt").writeText(
            """
            # first
            code()
            # second
            """.trimIndent(),
        )
        val blocks = SourceScanner(root, emptyList(), ListingGit("Cache.kt")).blocks()
        assertThat(blocks.size, "comment blocks separated by code were merged").isEqualTo(2)
    }

    @Test
    fun `scanner measures indent after the prefix`(@TempDir root: Path) {
        root.resolve("Cache.kt").writeText("    //  indent two")
        val blocks = SourceScanner(root, emptyList(), ListingGit("Cache.kt")).blocks()
        assertThat(blocks.single().lines.single().indent, "indent was measured before the comment prefix").isEqualTo(2)
    }

    @Test
    fun `scanner reports one based line numbers`(@TempDir root: Path) {
        root.resolve("Cache.kt").writeText("code()\ncode()\n// third")
        val blocks = SourceScanner(root, emptyList(), ListingGit("Cache.kt")).blocks()
        assertThat(blocks.single().lines.single().number, "line numbers are not one based").isEqualTo(3)
    }

    @Test
    fun `scanner skips excluded paths`(@TempDir root: Path) {
        root.resolve("build").createDirectories()
        root.resolve("build/Generated.kt").writeText("// TODO: do not file")
        val blocks = SourceScanner(root, listOf("**/build/**"), ListingGit("build/Generated.kt")).blocks()
        assertThat(blocks, "excluded path was scanned anyway").isEmpty()
    }

    @Test
    fun `scanner skips binary files`(@TempDir root: Path) {
        root.resolve("logo.png").writeBytes("// TODO: do not file".toByteArray() + 0)
        val blocks = SourceScanner(root, emptyList(), ListingGit("logo.png")).blocks()
        assertThat(blocks, "a file with a zero byte was parsed as source").isEmpty()
    }

    @Test
    fun `scanner refuses to work outside a repository`(@TempDir root: Path) {
        val failure = runCatching { SourceScanner(root, emptyList(), ListingGit(null)).blocks() }.exceptionOrNull()
        assertThat(failure is NotARepository, "missing git listing did not raise NotARepository").isEqualTo(true)
    }
}
