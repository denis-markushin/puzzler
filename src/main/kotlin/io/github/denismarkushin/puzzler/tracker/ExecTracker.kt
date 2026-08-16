package io.github.denismarkushin.puzzler.tracker

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.denismarkushin.puzzler.config.PuzzleConfig
import io.github.denismarkushin.puzzler.parse.Puzzle
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Трекер поверх пользовательского процесса.
 * Одна операция — один запуск: запрос уходит в stdin, ответ читается из stdout.
 * Хэш пазла обязан попасть в запрос create, иначе повторный search не найдёт тикет и пазл заведётся заново.
 * Ни один поток процесса не остаётся трубой: и запрос, и ответ идут через временные файлы, stderr отбрасывает система.
 * Ответ хука обязан нести полную форму: пустой или неполный JSON — это отказ хука, а не пустой список тикетов.
 */
class ExecTracker(
    private val command: String,
    private val root: Path,
    puzzle: PuzzleConfig,
    private val body: TicketBody,
    private val repoLabel: String,
    private val timeout: Duration = Duration.ofSeconds(60),
) : TrackerPort {
    private val types = puzzle
    private val mapper = jacksonObjectMapper()

    override fun tickets(repo: String): List<Ticket> {
        val request = mapper.createObjectNode().apply {
            put("action", "search")
            put("repo", repo)
        }
        val answer = invoke(request.toString())
        val tickets = answer.path("tickets")
        if (!tickets.isArray) {
            throw TrackerError("hook $command returned no tickets array for repo $repo")
        }
        return tickets.map { ticket -> Ticket(ticket.path("id").asText(), ticket.path("hash").asText()) }
    }

    override fun create(puzzle: Puzzle): String {
        val request = mapper.createObjectNode().apply {
            put("action", "create")
            put("hash", puzzle.hash)
            put("subject", puzzle.subject)
            put("description", body.of(puzzle))
            put("type", types.ticketType(puzzle.type))
            put("estimate", puzzle.estimate)
            put("assignee", puzzle.assignee)
            put("repo", repoLabel)
            put("path", puzzle.path)
            put("line", puzzle.line)
        }
        val id = invoke(request.toString()).path("id").asText()
        if (id.isBlank()) {
            throw TrackerError("hook $command returned no id for puzzle ${puzzle.hash}")
        }
        return id
    }

    override fun close(id: String, reason: String) {
        val request = mapper.createObjectNode().apply {
            put("action", "close")
            put("id", id)
            put("reason", reason)
        }
        invoke(request.toString())
    }

    private fun invoke(payload: String): JsonNode {
        val source = Files.createTempFile("puzzler-hook", ".in")
        val sink = Files.createTempFile("puzzler-hook", ".out")
        try {
            Files.writeString(source, payload)
            val process = ProcessBuilder(command.split(" "))
                .directory(root.toFile())
                .redirectInput(source.toFile())
                .redirectOutput(sink.toFile())
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            if (!process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor()
                throw TrackerError("hook $command timed out")
            }
            if (process.exitValue() != 0) {
                throw TrackerError("hook $command exited with ${process.exitValue()}")
            }
            val output = Files.readString(sink)
            return runCatching { mapper.readTree(output) }
                .getOrElse { throw TrackerError("hook $command produced unparsable output: ${output.take(500)}") }
        } catch (error: IOException) {
            throw TrackerError("hook $command could not be run: ${error.message}")
        } finally {
            runCatching { Files.deleteIfExists(source) }
            runCatching { Files.deleteIfExists(sink) }
        }
    }
}
