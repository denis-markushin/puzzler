package io.github.denismarkushin.puzzler.parse

/**
 * Единица работы, объявленная в коде.
 * Хэш служит идентичностью, путь и строка — только справкой в теле тикета.
 */
data class Puzzle(
    val hash: String,
    val subject: String,
    val description: String,
    val type: String?,
    val estimate: String?,
    val assignee: String?,
    val path: String,
    val line: Int,
)
