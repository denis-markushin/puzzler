package io.github.denismarkushin.puzzler.tracker

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.github.denismarkushin.puzzler.parse.Puzzle
import org.junit.jupiter.api.Test

private fun puzzle(labels: List<String>) =
    Puzzle("aaa111bbb222", "extract cache", "", null, null, null, "Cache.kt", 1, labels)

class PuzzleLabelsTest {
    @Test
    fun `labels from both sources merge without duplicates`() {
        val merged = PuzzleLabels.extra(listOf("tech-debt", "perf"), puzzle(listOf("perf", "security")))
        assertThat(merged, "configured and puzzle labels did not merge into a deduped list")
            .isEqualTo(listOf("tech-debt", "perf", "security"))
    }

    @Test
    fun `a label carrying a space is dropped`() {
        val merged = PuzzleLabels.extra(emptyList(), puzzle(listOf("cache warmup", "perf")))
        assertThat(merged, "a label carrying a space reached the tracker").isEqualTo(listOf("perf"))
    }

    @Test
    fun `a label wearing the reserved prefix is dropped`() {
        val merged = PuzzleLabels.extra(emptyList(), puzzle(listOf("puzzler-hash-deadbeef", "perf")))
        assertThat(merged, "a label wearing the reserved prefix reached the tracker").isEqualTo(listOf("perf"))
    }

    @Test
    fun `a label carrying a colon is dropped`() {
        val merged = PuzzleLabels.extra(emptyList(), puzzle(listOf("team:platform", "perf")))
        assertThat(merged, "a label carrying a colon reached the tracker").isEqualTo(listOf("perf"))
    }

    @Test
    fun `a label carrying a comma is dropped`() {
        val merged = PuzzleLabels.extra(emptyList(), puzzle(listOf("cache,warmup", "perf")))
        assertThat(merged, "a label carrying a comma reached the tracker").isEqualTo(listOf("perf"))
    }
}
