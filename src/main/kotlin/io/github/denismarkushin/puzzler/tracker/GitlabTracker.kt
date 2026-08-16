package io.github.denismarkushin.puzzler.tracker

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.denismarkushin.puzzler.config.PuzzleConfig
import io.github.denismarkushin.puzzler.config.TrackerConfig
import io.github.denismarkushin.puzzler.parse.Puzzle
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers

/**
 * Трекер поверх GitLab Issues.
 * Поле project трактуется как числовой id либо как URL-encoded путь группы и проекта, метки передаются в том же запросе, что создаёт issue.
 */
class GitlabTracker(
    private val config: TrackerConfig,
    puzzle: PuzzleConfig,
    private val body: TicketBody,
    private val caller: HttpCaller,
    private val repoLabel: String,
) : TrackerPort {
    private val types = puzzle
    private val mapper = jacksonObjectMapper()
    private val base = "${config.url ?: "https://gitlab.com"}/api/v4/projects/${config.project}"

    override fun tickets(repo: String): List<Ticket> {
        val collected = mutableListOf<Ticket>()
        var page = 1
        while (true) {
            val uri = URI.create("$base/issues?state=opened&per_page=$PAGE&page=$page&labels=${PuzzleLabels.repo(repo)}")
            val issues = mapper.readTree(caller.call(request(uri).GET().build()))
            if (!issues.isArray || issues.isEmpty) {
                return collected
            }
            issues.forEach { issue -> ticket(issue)?.let(collected::add) }
            if (issues.size() < PAGE) {
                return collected
            }
            page += 1
        }
    }

    override fun create(puzzle: Puzzle): String {
        val labels = listOfNotNull(
            PuzzleLabels.repo(repoLabel),
            PuzzleLabels.hash(puzzle.hash),
            types.ticketType(puzzle.type),
        )
        val payload = mapper.createObjectNode().apply {
            put("title", puzzle.subject)
            put("description", body.of(puzzle))
            put("labels", labels.joinToString(","))
        }
        val created = mapper.readTree(
            caller.call(request(URI.create("$base/issues")).POST(BodyPublishers.ofString(payload.toString())).build()),
        )
        return created.path("iid").asText()
    }

    override fun close(id: String, reason: String) {
        val note = mapper.createObjectNode().put("body", reason)
        caller.call(request(URI.create("$base/issues/$id/notes")).POST(BodyPublishers.ofString(note.toString())).build())
        val event = mapper.createObjectNode().put("state_event", "close")
        caller.call(request(URI.create("$base/issues/$id")).PUT(BodyPublishers.ofString(event.toString())).build())
    }

    private fun ticket(issue: JsonNode): Ticket? {
        val labels = issue.path("labels").map { label -> label.asText() }
        return PuzzleLabels.hashOf(labels)?.let { hash -> Ticket(issue.path("iid").asText(), hash) }
    }

    private fun request(uri: URI): HttpRequest.Builder =
        HttpRequest.newBuilder(uri)
            .header("PRIVATE-TOKEN", config.token.orEmpty())
            .header("Content-Type", "application/json")

    private companion object {
        const val PAGE = 100
    }
}
