package io.github.denismarkushin.puzzler.config

import io.github.denismarkushin.puzzler.parse.PuzzleParser

/**
 * Настройки трекера.
 * Поля url и project трактуются каждой реализацией по-своему; command используется только типом exec.
 */
data class TrackerConfig(
    val type: String,
    val url: String? = null,
    val project: String? = null,
    val issueType: String = "Task",
    val closeTransition: String = "Done",
    val token: String? = null,
    val command: String? = null,
)

/**
 * Привязка пазлов к репозиторию.
 * Имя попадает в метку и служит выборкой, шаблон ссылки — справкой в теле тикета.
 */
data class RepoConfig(
    val name: String,
    val permalink: String? = null,
)

/**
 * Границы обхода рабочего дерева.
 */
data class ScanConfig(
    val exclude: List<String> = emptyList(),
)

/**
 * Формат пазла и перевод его типа в тип тикета.
 */
data class PuzzleConfig(
    val pattern: String? = null,
    val typeMapping: Map<String, String> = emptyMap(),
) {
    fun regex(): Regex = pattern?.let { value -> Regex(value) } ?: PuzzleParser.DEFAULT_PATTERN

    fun ticketType(raw: String?): String? = raw?.let { value -> typeMapping[value] ?: value }
}

/**
 * Полная конфигурация прогона.
 */
data class PuzzlerConfig(
    val tracker: TrackerConfig,
    val repo: RepoConfig,
    val scan: ScanConfig = ScanConfig(),
    val puzzle: PuzzleConfig = PuzzleConfig(),
)
