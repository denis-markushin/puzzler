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
 * Tracker on top of GitHub Issues.
 * The project field is treated as owner/repo, labels are passed in the same request that creates the issue.
 */
class GithubTracker(
    private val config: TrackerConfig,
    puzzle: PuzzleConfig,
    private val body: TicketBody,
    private val caller: HttpCaller,
    private val repoLabel: String,
) : TrackerPort {
    private val types = puzzle
    private val mapper = jacksonObjectMapper()
    private val base = "${config.url ?: "https://api.github.com"}/repos/${config.project}"

    override fun tickets(repo: String): List<Ticket> {
        val collected = mutableListOf<Ticket>()
        var page = 1
        while (true) {
            val uri = URI.create("$base/issues?state=open&per_page=$PAGE&page=$page&labels=${PuzzleLabels.repo(repo)}")
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
        ).plus(PuzzleLabels.extra(config.labels, puzzle)).distinct()
        val payload = mapper.createObjectNode().apply {
            put("title", puzzle.subject)
            put("body", body.of(puzzle))
            putArray("labels").apply { labels.forEach(::add) }
            puzzle.assignee?.let { assignee -> putArray("assignees").add(assignee) }
        }
        val created = mapper.readTree(
            caller.call(request(URI.create("$base/issues")).POST(BodyPublishers.ofString(payload.toString())).build()),
        )
        return created.path("number").asText()
    }

    override fun close(id: String, reason: String) {
        val comment = mapper.createObjectNode().put("body", reason)
        caller.call(request(URI.create("$base/issues/$id/comments")).POST(BodyPublishers.ofString(comment.toString())).build())
        val state = mapper.createObjectNode().put("state", "closed")
        caller.call(
            request(URI.create("$base/issues/$id"))
                .method("PATCH", BodyPublishers.ofString(state.toString()))
                .build(),
        )
    }

    private fun ticket(issue: JsonNode): Ticket? {
        val labels = issue.path("labels").map { label -> label.path("name").asText() }
        return PuzzleLabels.hashOf(labels)?.let { hash -> Ticket(issue.path("number").asText(), hash) }
    }

    private fun request(uri: URI): HttpRequest.Builder =
        HttpRequest.newBuilder(uri)
            .header("Authorization", "Bearer ${config.token}")
            .header("Accept", "application/vnd.github+json")
            .header("Content-Type", "application/json")

    private companion object {
        const val PAGE = 100
    }
}
