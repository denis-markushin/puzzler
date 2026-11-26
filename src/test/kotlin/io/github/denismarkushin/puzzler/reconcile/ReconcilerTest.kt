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

private fun puzzle(hash: String) = Puzzle(hash, "work $hash", "", null, null, null, "Cache.kt", 1)

class ReconcilerTest {
    @Test
    fun `plan creates puzzles missing from the tracker`() {
        val plan = Reconciler(FakeTracker(listOf(Ticket("OLD-1", "aaa")))).plan("puzzler", listOf(puzzle("aaa"), puzzle("bbb")), 17, false)
        assertThat(plan.create.map { item -> item.hash }, "a new puzzle was not scheduled for creation").containsExactly("bbb")
    }

    @Test
    fun `plan closes tickets missing from the code`() {
        val plan = Reconciler(FakeTracker(listOf(Ticket("OLD-1", "aaa"), Ticket("OLD-2", "bbb"))))
            .plan("puzzler", listOf(puzzle("aaa"), puzzle("ccc"), puzzle("ddd")), 23, false)
        assertThat(plan.close.map { item -> item.id }, "an orphaned ticket was not scheduled for closing").containsExactly("OLD-2")
    }

    @Test
    fun `plan is empty when code and tracker agree`() {
        val plan = Reconciler(FakeTracker(listOf(Ticket("OLD-1", "aaa")))).plan("puzzler", listOf(puzzle("aaa")), 5, false)
        assertThat(plan.create + plan.close, "a settled state still produced work").isEmpty()
    }

    @Test
    fun `plan closes the only ticket when its puzzle left the code`() {
        val plan = Reconciler(FakeTracker(listOf(Ticket("OLD-7", "e4c"))))
            .plan("puzzler", emptyList(), 38, false)
        assertThat(plan.close.map { item -> item.id }, "the last ticket stayed open after its puzzle left the code").containsExactly("OLD-7")
    }

    @Test
    fun `plan closes the only ticket when its puzzle text changed`() {
        val plan = Reconciler(FakeTracker(listOf(Ticket("OLD-7", "e4c"))))
            .plan("puzzler", listOf(puzzle("9fd")), 11, false)
        assertThat(plan.close.map { item -> item.id }, "the ticket of a reworded puzzle stayed open").containsExactly("OLD-7")
    }

    @Test
    fun `plan creates the reworded puzzle of the only ticket`() {
        val plan = Reconciler(FakeTracker(listOf(Ticket("OLD-7", "e4c"))))
            .plan("puzzler", listOf(puzzle("9fd")), 11, false)
        assertThat(plan.create.map { item -> item.hash }, "a reworded puzzle was not scheduled for creation").containsExactly("9fd")
    }

    @Test
    fun `plan closes both tickets when both puzzles left the code`() {
        val plan = Reconciler(FakeTracker(listOf(Ticket("OLD-7", "e4c"), Ticket("OLD-12", "b01"))))
            .plan("puzzler", emptyList(), 64, false)
        assertThat(plan.close.map { item -> item.id }, "a backlog of two stayed open after both puzzles left the code")
            .containsExactly("OLD-7", "OLD-12")
    }

    @Test
    fun `plan closes both tickets when both puzzle texts changed`() {
        val plan = Reconciler(FakeTracker(listOf(Ticket("OLD-7", "e4c"), Ticket("OLD-12", "b01"))))
            .plan("puzzler", listOf(puzzle("9fd"), puzzle("a77")), 9, false)
        assertThat(plan.close.map { item -> item.id }, "the tickets of two reworded puzzles stayed open")
            .containsExactly("OLD-7", "OLD-12")
    }

    @Test
    fun `plan closes two of three tickets without force`() {
        val existing = listOf(Ticket("OLD-7", "e4c"), Ticket("OLD-12", "b01"), Ticket("OLD-31", "c5a"))
        val plan = Reconciler(FakeTracker(existing)).plan("puzzler", listOf(puzzle("b01")), 29, false)
        assertThat(plan.close.map { item -> item.id }, "two closures in a backlog of three were refused")
            .containsExactly("OLD-7", "OLD-31")
    }

    @Test
    fun `force closes a larger backlog after a scan without puzzles`() {
        val existing = listOf(Ticket("OLD-7", "e4c"), Ticket("OLD-12", "b01"), Ticket("OLD-31", "c5a"))
        val plan = Reconciler(FakeTracker(existing)).plan("puzzler", emptyList(), 52, true)
        assertThat(plan.close.size, "force did not close a backlog whose puzzles all left the code").isEqualTo(3)
    }

    @Test
    fun `plan refuses to empty a larger backlog after a scan without puzzles`() {
        val existing = listOf(Ticket("OLD-7", "e4c"), Ticket("OLD-12", "b01"), Ticket("OLD-31", "c5a"))
        val failure = runCatching {
            Reconciler(FakeTracker(existing)).plan("puzzler", emptyList(), 52, false)
        }.exceptionOrNull()
        assertThat(failure is GuardViolation, "a scan without puzzles emptied a backlog of three without force").isEqualTo(true)
    }

    @Test
    fun `plan refuses a scan of no files even when forced`() {
        val failure = runCatching {
            Reconciler(FakeTracker(listOf(Ticket("OLD-1", "aaa")))).plan("puzzler", emptyList(), 0, true)
        }.exceptionOrNull()
        assertThat(failure is GuardViolation, "force let a scan of no files close live tickets").isEqualTo(true)
    }

    @Test
    fun `plan closes exactly half of a larger backlog without force`() {
        val existing = listOf("e4c", "b01", "c5a", "7d2", "f90", "a13").mapIndexed { index, hash -> Ticket("OLD-${index + 3}", hash) }
        val plan = Reconciler(FakeTracker(existing)).plan("puzzler", listOf(puzzle("e4c"), puzzle("c5a"), puzzle("f90")), 71, false)
        assertThat(plan.close.size, "closing exactly half of the backlog was refused").isEqualTo(3)
    }

    @Test
    fun `plan refuses to close more than half of the tickets`() {
        val existing = listOf(Ticket("OLD-1", "aaa"), Ticket("OLD-2", "bbb"), Ticket("OLD-3", "ccc"), Ticket("OLD-4", "ddd"))
        val failure = runCatching {
            Reconciler(FakeTracker(existing)).plan("puzzler", listOf(puzzle("aaa")), 13, false)
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
            Ticket("H-3", "ccc"),
        )
        val failure = runCatching {
            Reconciler(FakeTracker(existing)).plan("puzzler", listOf(puzzle("zzz")), 46, false)
        }.exceptionOrNull()
        assertThat(failure is GuardViolation, "duplicate tickets padded the denominator and let a mass closure through").isEqualTo(true)
    }

    @Test
    fun `force overrides the mass closure guard`() {
        val existing = listOf(Ticket("OLD-1", "aaa"), Ticket("OLD-2", "bbb"), Ticket("OLD-3", "ccc"), Ticket("OLD-4", "ddd"))
        val plan = Reconciler(FakeTracker(existing)).plan("puzzler", listOf(puzzle("aaa")), 13, true)
        assertThat(plan.close.size, "force did not override the mass closure guard").isEqualTo(3)
    }

    @Test
    fun `duplicate hashes are left untouched`() {
        val existing = listOf(Ticket("OLD-1", "aaa"), Ticket("OLD-2", "aaa"), Ticket("OLD-3", "bbb"))
        val plan = Reconciler(FakeTracker(existing)).plan("puzzler", listOf(puzzle("bbb")), 8, true)
        assertThat(plan.close, "duplicated tickets were closed instead of being reported").isEmpty()
    }
}
