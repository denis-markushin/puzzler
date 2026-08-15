package io.github.denismarkushin.puzzler.git

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Обращение к бинарнику git.
 * Вынесено за интерфейс, чтобы тесты работали без настоящего репозитория.
 */
interface GitCommand {
    fun run(vararg args: String): String?
}

/**
 * Реализация поверх дочернего процесса.
 * Ненулевой код возврата, таймаут, недоступный бинарник и незапустившийся процесс — всё это отсутствие ответа, а не сбой инструмента.
 * Ни один поток процесса не остаётся трубой: stdout уходит во временный файл, stderr отбрасывает система, stdin закрывается сразу.
 * Иначе заполненный буфер трубы или зависший git блокируют чтение навсегда, и таймаут ниже недостижим.
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
            } catch (error: IOException) {
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
