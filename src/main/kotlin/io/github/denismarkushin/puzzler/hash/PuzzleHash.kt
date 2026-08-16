package io.github.denismarkushin.puzzler.hash

import java.security.MessageDigest

/**
 * Identity of a puzzle.
 * The hash is computed from the text alone, so moving the file or shifting its lines never produces a new puzzle.
 */
object PuzzleHash {
    private val whitespace = Regex("\\s+")

    fun of(subject: String, description: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(normalized("$subject\n$description").toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
            .take(12)

    private fun normalized(text: String): String =
        text.replace("\r\n", "\n")
            .replace('\r', '\n')
            .lineSequence()
            .map { line -> line.trim().replace(whitespace, " ") }
            .filter { line -> line.isNotEmpty() }
            .joinToString("\n")
}
