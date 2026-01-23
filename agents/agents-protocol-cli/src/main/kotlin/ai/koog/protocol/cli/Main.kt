package ai.koog.protocol.cli

import kotlin.system.exitProcess
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) = runBlocking {
    try {
        FlowCli().run(args)
    } catch (e: ExitException) {
        exitProcess(e.code)
    }
}
