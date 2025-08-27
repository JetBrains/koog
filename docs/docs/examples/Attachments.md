# Attachments

[:material-github: Open on GitHub](
https://github.com/JetBrains/koog/blob/develop/examples/notebooks/Attachments.ipynb
){ .md-button .md-button--primary }
[:material-download: Download .ipynb](
https://raw.githubusercontent.com/JetBrains/koog/develop/examples/notebooks/Attachments.ipynb
){ .md-button }

## Setting Up the Environment

Before diving into the code, we make sure our Kotlin Notebook is ready.
Here we load the latest descriptors and enable the **Koog** library,
which provides a clean API for working with AI model providers.



```kotlin
// Loads the latest descriptors and activates Koog integration for Kotlin Notebook.
// This makes Koog DSL types and executors available in further cells.
%useLatestDescriptors
%use koog
```

## Configuring API Keys

We read the API key from an environment variable. This keeps secrets out of the notebook file and lets you
switch providers. You can set `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, or `GEMINI_API_KEY`.


```kotlin
val apiKey = System.getenv("OPENAI_API_KEY") // or ANTHROPIC_API_KEY, or GEMINI_API_KEY
```

## Creating a Simple OpenAI Executor

The executor encapsulates authentication, base URLs, and correct defaults. Here we use a simple OpenAI executor,
but you can swap it for Anthropic or Gemini without changing the rest of the code.


```kotlin
// --- Provider selection ---
// For OpenAI-compatible models. Alternatives include:
//   val executor = simpleAnthropicExecutor(System.getenv("ANTHROPIC_API_KEY"))
//   val executor = simpleGeminiExecutor(System.getenv("GEMINI_API_KEY"))
// All executors expose the same high‑level API.
val executor = simpleOpenAIExecutor(apiKey)
```

Koog’s prompt DSL lets you add **structured Markdown** and **attachments**.
In this cell we build a prompt that asks the model to generate a short, blog‑style "content card" and
we attach two images from the local `images/` directory.


```kotlin
import ai.koog.prompt.markdown.markdown
import kotlinx.io.files.Path

val prompt = prompt("images-prompt") {
    system("You are professional assistant that can write cool and funny descriptions for Instagram posts.")

    user {
        markdown {
            +"I want to create a new post on Instagram."
            br()
            +"Can you write something creative under my instagram post with the following photos?"
            br()
            h2("Requirements")
            bulleted {
                item("It must be very funny and creative")
                item("It must increase my chance of becoming an ultra-famous blogger!!!!")
                item("It not contain explicit content, harassment or bullying")
                item("It must be a short catching phrase")
                item("You must include relevant hashtags that would increase the visibility of my post")
            }
        }

        attachments {
            image(Path("images/kodee-loving.png"))
            image(Path("images/kodee-electrified.png"))
        }
    }
}
```

## Execute and Inspect the Response

We run the prompt against `gpt-4.1`, collect the first message, and print its content.
If you want streaming, swap to a streaming API in Koog; for tool use, pass your tool list instead of `emptyList()`.

> Troubleshooting:
> * **401/403** — check your API key/environment variable.
> * **File not found** — verify the `images/` paths.
> * **Rate limits** — add minimal retry/backoff around the call if needed.


```kotlin
import kotlinx.coroutines.runBlocking

runBlocking {
    val response = executor.execute(prompt = prompt, model = OpenAIModels.Chat.GPT4_1, tools = emptyList()).first()
    println(response.content)
}
```

    Caption:
    Running on cuteness and extra giggle power! Warning: Side effects may include heart-thief vibes and spontaneous dance parties. 💜🤖💃
    
    Hashtags:  
    #ViralVibes #UltraFamousBlogger #CutieAlert #QuirkyContent #InstaFun #SpreadTheLove #DancingIntoFame #RobotLife #InstaFamous #FeedGoals



```kotlin
runBlocking {
    val response = executor.executeStreaming(prompt = prompt, model = OpenAIModels.Chat.GPT4_1)
    response.collect { print(it) }
}
```

    Caption:  
    Running on good vibes & wi-fi only! 🤖💜 Drop a like if you feel the circuit-joy! #BlogBotInTheWild #HeartDeliveryService #DancingWithWiFi #UltraFamousBlogger #MoreFunThanYourAICat #ViralVibes #InstaFun #BeepBoopFamous
