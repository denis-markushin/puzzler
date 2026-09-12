package io.github.denismarkushin.puzzler.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.denismarkushin.puzzler.tracker.PuzzleLabels
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Configuration error detected before the tracker is contacted.
 */
class ConfigError(
    message: String,
) : RuntimeException(message)

/**
 * Reading and validation of .puzzler.yml.
 * Validation runs before scanning, so a broken config never turns into a broken tracker.
 */
object ConfigLoader {
    private val supported = setOf("jira", "jira-cloud", "github", "gitlab", "exec")
    private val placeholder = Regex("\\$\\{([A-Z_][A-Z0-9_]*)}")
    private val mapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    fun load(file: Path, env: Map<String, String>): PuzzlerConfig {
        val raw = runCatching { file.readText() }
            .getOrElse { throw ConfigError("cannot read configuration at $file") }
        val literal = runCatching { mapper.readValue<PuzzlerConfig>(raw) }
            .getOrElse { failure -> throw ConfigError("cannot parse configuration at $file: ${failure.message}") }
        val text = resolved(raw, env)
        val config = runCatching { mapper.readValue<PuzzlerConfig>(text) }
            .getOrElse { failure -> throw ConfigError("cannot parse configuration at $file: ${failure.message}") }
        validate(config, literal.tracker.token)
        return config
    }

    private fun resolved(raw: String, env: Map<String, String>) =
        placeholder.replace(raw) { match ->
            env[match.groupValues[1]] ?: throw ConfigError("environment variable ${match.groupValues[1]} is not set")
        }

    private fun validate(config: PuzzlerConfig, token: String?) {
        if (config.tracker.type !in supported) {
            throw ConfigError("unsupported tracker type ${config.tracker.type}, expected one of $supported")
        }
        if (config.tracker.type == "jira-cloud") {
            throw ConfigError("tracker type jira-cloud is not supported yet because Jira Cloud requires ADF descriptions, see docs/trackers.md")
        }
        if (config.tracker.type == "exec" && config.tracker.command.isNullOrBlank()) {
            throw ConfigError("tracker type exec requires a command")
        }
        if (config.tracker.type != "exec" && config.tracker.project.isNullOrBlank()) {
            throw ConfigError("tracker type ${config.tracker.type} requires a project")
        }
        if (config.tracker.type == "jira" && config.tracker.closeTransition.let { names -> names.isEmpty() || names.any { name -> name.isBlank() } }) {
            throw ConfigError("tracker type jira requires closeTransition to name at least one transition and no blank ones")
        }
        if (token != null && !placeholder.matches(token)) {
            throw ConfigError("tracker token must reference an environment variable, not a literal value")
        }
        config.tracker.labels.firstOrNull { label -> !PuzzleLabels.valid(label) }?.let { label ->
            throw ConfigError(
                "tracker label $label is not usable, a label carries no whitespace, no comma, no colon and no puzzler- prefix",
            )
        }
        val pattern = runCatching { config.puzzle.regex() }
            .getOrElse { failure -> throw ConfigError("puzzle pattern is not a valid regexp: ${failure.message}") }
        if (!pattern.pattern.contains("(?<subject>")) {
            throw ConfigError("puzzle pattern must declare a named group subject")
        }
    }
}
