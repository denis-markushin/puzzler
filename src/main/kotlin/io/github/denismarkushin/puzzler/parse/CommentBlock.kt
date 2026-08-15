package io.github.denismarkushin.puzzler.parse

/**
 * Строка комментария без префикса.
 * Отступ считается от начала содержимого и разделяет заголовок пазла и его тело.
 */
data class CommentLine(
    val number: Int,
    val indent: Int,
    val text: String,
)

/**
 * Непрерывная последовательность строк комментария одного вида в одном файле.
 * Единица, которую разбирает парсер.
 */
data class CommentBlock(
    val path: String,
    val lines: List<CommentLine>,
)
