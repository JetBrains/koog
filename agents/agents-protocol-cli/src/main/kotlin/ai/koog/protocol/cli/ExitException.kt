package ai.koog.protocol.cli

internal class ExitException(val code: Int) : Exception("Exit with code $code")
