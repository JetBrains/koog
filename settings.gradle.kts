rootProject.name = "koog"

pluginManagement {
    includeBuild("convention-plugin-ai")
    repositories {
        google()
        gradlePluginPortal()
        maven(url = "https://packages.jetbrains.team/maven/p/jcs/maven")
    }
}

include(":agents:agents-core")
include(":agents:agents-ext")
include(":agents:agents-planner")
include(":agents:agents-features:agents-features-acp")
include(":agents:agents-features:agents-features-event-handler")
include(":agents:agents-features:agents-features-memory")
include(":agents:agents-features:agents-features-opentelemetry")
include(":agents:agents-features:agents-features-sql")
include(":agents:agents-features:agents-features-trace")
include(":agents:agents-features:agents-features-tokenizer")
include(":agents:agents-features:agents-features-snapshot")
include(":agents:agents-mcp")
include(":agents:agents-test")
include(":agents:agents-tools")
include(":agents:agents-utils")
include(":test-utils")
include(":prompt:prompt-executor:prompt-executor-clients")
include(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-litertlm-client")
include(":prompt:prompt-executor:prompt-executor-model")
include(":prompt:prompt-llm")
include(":prompt:prompt-markdown")
include(":prompt:prompt-model")
include(":prompt:prompt-xml")
include(":rag:rag-base")
include(":utils")
