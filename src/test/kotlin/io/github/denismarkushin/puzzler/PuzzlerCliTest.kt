package io.github.denismarkushin.puzzler

import assertk.assertThat
import assertk.assertions.contains
import com.github.ajalt.clikt.testing.test
import org.junit.jupiter.api.Test

class PuzzlerCliTest {
    @Test
    fun `cli reports its version`() {
        val output = PuzzlerCli().test("--version").output
        assertThat(output, "version flag printed nothing recognizable").contains("puzzler")
    }
}
