package io.github.denismarkushin.puzzler

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.versionOption

/**
 * Точка входа инструмента.
 * Отвечает за разбор аргументов и коды выхода; вся работа делегируется нижележащим компонентам.
 */
class PuzzlerCli : CliktCommand(name = "puzzler") {
    init {
        versionOption(names = setOf("--version"), version = "puzzler 0.1.0", message = { it })
    }

    override fun run() = Unit
}

fun main(args: Array<String>) = PuzzlerCli().main(args)
