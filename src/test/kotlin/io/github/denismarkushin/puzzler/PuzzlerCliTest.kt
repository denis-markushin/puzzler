package io.github.denismarkushin.puzzler

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import com.github.ajalt.clikt.testing.test
import io.github.denismarkushin.puzzler.config.ConfigError
import io.github.denismarkushin.puzzler.reconcile.GuardViolation
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

class PuzzlerCliTest {
    @Test
    fun `cli reports its version`() {
        val output = PuzzlerCli().test("--version").output
        assertThat(output, "version flag printed nothing recognizable").contains("puzzler")
    }

    @Test
    @Timeout(30)
    fun `exit code for a guard violation is two`() {
        assertThat(exitCode(GuardViolation("refused")), "a guard violation did not map to exit code two").isEqualTo(2)
    }

    @Test
    @Timeout(30)
    fun `exit code for a config error is one`() {
        assertThat(exitCode(ConfigError("broken")), "a config error did not map to exit code one").isEqualTo(1)
    }
}
