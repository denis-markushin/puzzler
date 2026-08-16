package io.github.denismarkushin.puzzler.parse

/**
 * A comment line with the prefix stripped.
 * The indent is measured from the start of the content and separates the puzzle headline from its body.
 */
data class CommentLine(
    val number: Int,
    val indent: Int,
    val text: String,
)

/**
 * A contiguous run of comment lines of one kind in one file.
 * The unit the parser works on.
 */
data class CommentBlock(
    val path: String,
    val lines: List<CommentLine>,
)
