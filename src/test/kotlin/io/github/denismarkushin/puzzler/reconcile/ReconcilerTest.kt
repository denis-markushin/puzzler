package io.github.denismarkushin.puzzler.reconcile

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import io.github.denismarkushin.puzzler.parse.Puzzle
import io.github.denismarkushin.puzzler.tracker.Ticket
import io.github.denismarkushin.puzzler.tracker.TrackerPort
import org.junit.jupiter.api.Test

private class FakeTracker(
    private val existing: List<Ticket>,
) : TrackerPort {
    override fun tickets(repo: String) = existing

    override fun create(puzzle: Puzzle) = "NEW-1"

    override fun close(id: String, reason: String) = Unit
}

private fun puzzle(hash: String) = Puzzle(hash, "работа $hash", "", null, null, null, "Cache.kt", 1)

class ReconcilerTest {
    @Test
    fun `plan creates puzzles missing from the tracker`() {
        val plan = Reconciler(FakeTracker(listOf(Ticket("OLD-1", "aaa")))).plan("puzzler", listOf(puzzle("aaa"), puzzle("bbb")), false)
        assertThat(plan.create.map { item -> item.hash }, "a new puzzle was not scheduled for creation").containsExactly("bbb")
    }

    @Test
    fun `plan closes tickets missing from the code`() {
        val plan = Reconciler(FakeTracker(listOf(Ticket("OLD-1", "aaa"), Ticket("OLD-2", "bbb"))))
            .plan("puzzler", listOf(puzzle("aaa"), puzzle("ccc"), puzzle("ddd")), false)
        assertThat(plan.close.map { item -> item.id }, "an orphaned ticket was not scheduled for closing").containsExactly("OLD-2")
    }

    @Test
    fun `plan is empty when code and tracker agree`() {
        val plan = Reconciler(FakeTracker(listOf(Ticket("OLD-1", "aaa")))).plan("puzzler", listOf(puzzle("aaa")), false)
        assertThat(plan.create + plan.close, "a settled state still produced work").isEmpty()
    }

    @Test
    fun `plan refuses an empty scan even when forced`() {
        val failure = runCatching {
            Reconciler(FakeTracker(listOf(Ticket("OLD-1", "aaa")))).plan("puzzler", emptyList(), true)
        }.exceptionOrNull()
        assertThat(failure is GuardViolation, "force let an empty scan close live tickets").isEqualTo(true)
    }

    @Test
    fun `plan refuses to close more than half of the tickets`() {
        val existing = listOf(Ticket("OLD-1", "aaa"), Ticket("OLD-2", "bbb"), Ticket("OLD-3", "ccc"))
        val failure = runCatching {
            Reconciler(FakeTracker(existing)).plan("puzzler", listOf(puzzle("aaa")), false)
        }.exceptionOrNull()
        assertThat(failure is GuardViolation, "a mass closure passed without force").isEqualTo(true)
    }

    @Test
    fun `plan refuses mass closure of healthy tickets despite duplicate noise inflating the ticket count`() {
        val existing = listOf(
            Ticket("D-1", "dup"),
            Ticket("D-2", "dup"),
            Ticket("D-3", "dup"),
            Ticket("D-4", "dup"),
            Ticket("H-1", "aaa"),
            Ticket("H-2", "bbb"),
        )
        val failure = runCatching {
            Reconciler(FakeTracker(existing)).plan("puzzler", listOf(puzzle("zzz")), false)
        }.exceptionOrNull()
        assertThat(failure is GuardViolation, "duplicate tickets padded the denominator and let a mass closure through").isEqualTo(true)
    }

    @Test
    fun `force overrides the mass closure guard`() {
        val existing = listOf(Ticket("OLD-1", "aaa"), Ticket("OLD-2", "bbb"), Ticket("OLD-3", "ccc"))
        val plan = Reconciler(FakeTracker(existing)).plan("puzzler", listOf(puzzle("aaa")), true)
        assertThat(plan.close.size, "force did not override the mass closure guard").isEqualTo(2)
    }

    @Test
    fun `duplicate hashes are left untouched`() {
        val existing = listOf(Ticket("OLD-1", "aaa"), Ticket("OLD-2", "aaa"), Ticket("OLD-3", "bbb"))
        val plan = Reconciler(FakeTracker(existing)).plan("puzzler", listOf(puzzle("bbb")), true)
        assertThat(plan.close, "duplicated tickets were closed instead of being reported").isEmpty()
    }
}
