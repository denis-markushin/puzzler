package io.github.denismarkushin.puzzler.scan

import io.github.denismarkushin.puzzler.git.GitCommand
import io.github.denismarkushin.puzzler.parse.CommentBlock
import io.github.denismarkushin.puzzler.parse.CommentLine
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.fileSize
import kotlin.io.path.isRegularFile

/**
 * Отказ работать вне git-репозитория.
 * Список файлов берётся у git, поэтому без репозитория сканировать нечего.
 */
class NotARepository(
    root: Path,
) : RuntimeException("git listing is unavailable in $root, run puzzler inside a git repository")

/**
 * Обход рабочего дерева и выделение блоков комментариев.
 * О языках программирования не знает: опирается только на префиксы строк.
 */
class SourceScanner(
    private val root: Path,
    private val exclude: List<String>,
    private val git: GitCommand,
) {
    private val matchers = exclude.flatMap { glob -> variants(glob) }.map { glob -> FileSystems.getDefault().getPathMatcher("glob:$glob") }

    fun blocks(): List<CommentBlock> {
        val listing = git.run("ls-files", "--cached", "--others", "--exclude-standard") ?: throw NotARepository(root)
        return listing.lineSequence()
            .map { entry -> entry.trim() }
            .filter { entry -> entry.isNotEmpty() }
            .filterNot { entry -> excluded(entry) }
            .mapNotNull { entry -> readable(entry) }
            .flatMap { (entry, text) -> blocks(entry, text) }
            .toList()
    }

    private fun excluded(entry: String) = matchers.any { matcher -> matcher.matches(Path.of(entry)) }

    private fun variants(glob: String) =
        if (glob.startsWith("**/")) listOf(glob, glob.removePrefix("**/")) else listOf(glob)

    private fun readable(entry: String): Pair<String, String>? {
        val file = root.resolve(entry)
        if (!file.isRegularFile() || file.fileSize() > maxSize) {
            return null
        }
        val bytes = file.toFile().readBytes()
        if (bytes.take(probeSize).any { byte -> byte == zero }) {
            return null
        }
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = runCatching { decoder.decode(ByteBuffer.wrap(bytes)).toString() }.getOrNull()
        return text?.let { content -> entry to content }
    }

    private fun blocks(entry: String, text: String): Sequence<CommentBlock> {
        val collected = mutableListOf<CommentBlock>()
        var current = mutableListOf<CommentLine>()
        text.lineSequence().forEachIndexed { index, raw ->
            val match = prefix.find(raw)
            if (match == null) {
                if (current.isNotEmpty()) {
                    collected.add(CommentBlock(entry, current.toList()))
                    current = mutableListOf()
                }
            } else {
                val content = match.groupValues[3]
                val indent = content.takeWhile { char -> char.isWhitespace() }.length
                current.add(CommentLine(index + 1, indent, content))
            }
        }
        if (current.isNotEmpty()) {
            collected.add(CommentBlock(entry, current.toList()))
        }
        return collected.asSequence()
    }

    private companion object {
        const val maxSize = 1L * 1024 * 1024
        const val probeSize = 8000
        const val zero: Byte = 0
        val prefix = Regex("^(\\s*)(//+|#+|--|\\*|;+)( ?.*)$")
    }
}
