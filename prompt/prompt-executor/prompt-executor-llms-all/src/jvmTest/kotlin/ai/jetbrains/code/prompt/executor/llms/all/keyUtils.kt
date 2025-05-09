package ai.jetbrains.code.prompt.executor.llms.all

fun readTestAnthropicKeyFromEnv(): String {
    return System.getenv("ANTHROPIC_API_TEST_KEY") ?: error("ERROR: environment variable ANTHROPIC_API_TEST_KEY not set")
}

fun readTestOpenAIKeyFromEnv(): String {
    return System.getenv("OPEN_AI_API_TEST_KEY") ?: error("ERROR: environment variable `OPEN_AI_API_TEST_KEY` not set")
}