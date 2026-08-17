package io.github.denismarkushin.puzzler.git

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Access to the git binary.
 * Pulled out behind an interface so tests can run without a real repository.
 */
interface GitCommand {
    fun run(vararg args: String): String?
}

/**
 * Implementation on top of a child process.
 * A non-zero exit code, a timeout, a missing binary and a process that never started are all treated as no answer, not a tool failure.
 * No process stream stays a pipe: stdout goes to a temp file, stderr is discarded by the system, stdin is closed immediately.
 * Otherwise, a filled pipe buffer or a hung git would block reads forever, making the timeout below unreachable.
 */
class ProcessGitCommand(
    private val root: Path,
) : GitCommand {
    override fun run(vararg args: String): String? {
        val sink = Files.createTempFile("puzzler-git", ".out")
        try {
            val process = try {
                ProcessBuilder(listOf("git") + args)
                    .directory(root.toFile())
                    .redirectOutput(sink.toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
            } catch (_: IOException) {
                return null
            }
            process.outputStream.close()
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }
            if (process.exitValue() != 0) {
                return null
            }
            return Files.readString(sink).trim().ifEmpty { null }
        } finally {
            Files.deleteIfExists(sink)
        }
    }
}
