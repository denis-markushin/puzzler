package io.github.denismarkushin.puzzler.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Ошибка конфигурации, обнаруженная до обращения к трекеру.
 */
class ConfigError(
    message: String,
) : RuntimeException(message)

/**
 * Чтение и проверка .puzzler.yml.
 * Валидация выполняется до сканирования, чтобы сломанный конфиг не превращался в сломанный трекер.
 */
object ConfigLoader {
    private val supported = setOf("jira", "github", "gitlab", "exec")
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
        if (config.tracker.type == "exec" && config.tracker.command.isNullOrBlank()) {
            throw ConfigError("tracker type exec requires a command")
        }
        if (config.tracker.type != "exec" && config.tracker.project.isNullOrBlank()) {
            throw ConfigError("tracker type ${config.tracker.type} requires a project")
        }
        if (token != null && !placeholder.matches(token)) {
            throw ConfigError("tracker token must reference an environment variable, not a literal value")
        }
        val pattern = runCatching { config.puzzle.regex() }
            .getOrElse { failure -> throw ConfigError("puzzle pattern is not a valid regexp: ${failure.message}") }
        if (!pattern.pattern.contains("(?<subject>")) {
            throw ConfigError("puzzle pattern must declare a named group subject")
        }
    }
}
