rootProject.name = "koog"

pluginManagement {
    includeBuild("convention-plugin-ai")
    repositories {
        google()
        gradlePluginPortal()
        maven(url = "https://packages.jetbrains.team/maven/p/jcs/maven")
    }
}

include(":agents:agents-tools")
include(":agents:agents-utils")
include(":prompt:prompt-executor:prompt-executor-clients")
include(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-litertlm-client")
include(":prompt:prompt-executor:prompt-executor-model")
include(":prompt:prompt-llm")
include(":prompt:prompt-markdown")
include(":prompt:prompt-model")
include(":prompt:prompt-xml")
include(":rag:rag-base")
include(":utils")
