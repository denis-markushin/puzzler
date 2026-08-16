package io.github.denismarkushin.puzzler.hash

import assertk.assertThat
import assertk.assertions.hasLength
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import org.junit.jupiter.api.Test

class PuzzleHashTest {
    @Test
    fun `hash has twelve characters`() {
        val hash = PuzzleHash.of("extract cache into a separate bean", "needs TTL and metrics")
        assertThat(hash, "hash length drifted from the documented twelve").hasLength(12)
    }

    @Test
    fun `hash survives reindentation`() {
        val loose = PuzzleHash.of("   extract cache   ", "\t\tneeds    TTL")
        val tight = PuzzleHash.of("extract cache", "needs TTL")
        assertThat(loose, "reindenting a puzzle changed its identity").isEqualTo(tight)
    }

    @Test
    fun `hash survives windows line endings`() {
        val windows = PuzzleHash.of("clean up retries", "first line\r\nsecond line")
        val unix = PuzzleHash.of("clean up retries", "first line\nsecond line")
        assertThat(windows, "line ending style leaked into puzzle identity").isEqualTo(unix)
    }

    @Test
    fun `hash ignores blank lines`() {
        val padded = PuzzleHash.of("remove hack", "first\n\n\nsecond")
        val plain = PuzzleHash.of("remove hack", "first\nsecond")
        assertThat(padded, "blank lines leaked into puzzle identity").isEqualTo(plain)
    }

    @Test
    fun `hash changes when subject changes`() {
        val before = PuzzleHash.of("extract cache", "needs TTL")
        val after = PuzzleHash.of("extract cache into a bean", "needs TTL")
        assertThat(after, "edited subject produced the same identity").isNotEqualTo(before)
    }

    @Test
    fun `hash respects letter case`() {
        val lower = PuzzleHash.of("extract cache", "needs ttl")
        val upper = PuzzleHash.of("extract cache", "needs TTL")
        assertThat(upper, "letter case was silently normalized away").isNotEqualTo(lower)
    }
}
