package io.github.denismarkushin.puzzler.hash

import assertk.assertThat
import assertk.assertions.hasLength
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import org.junit.jupiter.api.Test

class PuzzleHashTest {
    @Test
    fun `hash has twelve characters`() {
        val hash = PuzzleHash.of("вынести кэш в отдельный бин", "нужен TTL и метрики")
        assertThat(hash, "hash length drifted from the documented twelve").hasLength(12)
    }

    @Test
    fun `hash survives reindentation`() {
        val loose = PuzzleHash.of("   вынести кэш   ", "\t\tнужен    TTL")
        val tight = PuzzleHash.of("вынести кэш", "нужен TTL")
        assertThat(loose, "reindenting a puzzle changed its identity").isEqualTo(tight)
    }

    @Test
    fun `hash survives windows line endings`() {
        val windows = PuzzleHash.of("почистить ретраи", "первая строка\r\nвторая строка")
        val unix = PuzzleHash.of("почистить ретраи", "первая строка\nвторая строка")
        assertThat(windows, "line ending style leaked into puzzle identity").isEqualTo(unix)
    }

    @Test
    fun `hash ignores blank lines`() {
        val padded = PuzzleHash.of("убрать хак", "первая\n\n\nвторая")
        val plain = PuzzleHash.of("убрать хак", "первая\nвторая")
        assertThat(padded, "blank lines leaked into puzzle identity").isEqualTo(plain)
    }

    @Test
    fun `hash changes when subject changes`() {
        val before = PuzzleHash.of("вынести кэш", "нужен TTL")
        val after = PuzzleHash.of("вынести кэш в бин", "нужен TTL")
        assertThat(after, "edited subject produced the same identity").isNotEqualTo(before)
    }

    @Test
    fun `hash respects letter case`() {
        val lower = PuzzleHash.of("вынести кэш", "нужен ttl")
        val upper = PuzzleHash.of("вынести кэш", "нужен TTL")
        assertThat(upper, "letter case was silently normalized away").isNotEqualTo(lower)
    }
}
