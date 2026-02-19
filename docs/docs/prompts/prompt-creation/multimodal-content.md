# Multimodal content

Multimodal content refers to content of different types, such as text, images, audio, video, and files.
Koog lets you send images, audio, video, and files to LLMs within the `user` message along with text. 
You can add them to the `user` message by using the corresponding functions:

- `image()`: Attaches images (JPG, PNG, WebP, GIF).
- `audio()`: Attaches audio files (MP3, WAV, FLAC).
- `video()`: Attaches video files (MP4, AVI, MOV).
- `file()` / `binaryFile()` / `textFile()`: Attaches documents (PDF, TXT, MD, etc.).

Each function supports two ways of configuring attachment parameters, so you can:

- Pass a URL or a file path to the function, and it automatically handles attachment parameters. For `file()`, `binaryFile()`, and `textFile()`, you must also provide the MIME type.
- Create and pass a `ContentPart` object to the function for custom control over attachment parameters.

!!! note
    Multimodal content support varies by [LLM provider](../llm-providers.md).
    Check the provider documentation for supported content types.

### Auto-configured attachments

If you pass a URL or a file path to the attachment functions, Koog automatically constructs
the corresponding attachment parameters based on the file extension.

The general format of the `user` message that includes a text message and a list of auto-configured attachments is as follows:

<!--- INCLUDE
import ai.koog.prompt.dsl.prompt
import kotlinx.io.files.Path

val prompt = prompt("image_analysis") {
-->
<!--- SUFFIX
}
-->
```kotlin
user {
    +"Describe these images:"

    image("https://example.com/test.png")
    image(Path("/path/to/image.png"))

    +"Focus on the main subjects."
}
```
<!--- KNIT example-multimodal-content-01.kt -->

The `+` operator adds text content to the user message along with the attachments.

### Custom-configured attachments

The [`ContentPart`](api:prompt-model::ai.koog.prompt.message.ContentPart) interface
lets you configure parameters for each attachment individually.

All attachments implement the `ContentPart.Attachment` interface.
You can create an instance of a specific implementation for each attachment,
configure its parameters, and pass it to the corresponding `image()`, `audio()`, `video()`, or `file()` functions.

The general format of the `user` message that includes a text message and a list of custom-configured attachments is as follows:

<!--- INCLUDE
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart

val prompt = prompt("custom_image") {
-->
<!--- SUFFIX
}
-->
```kotlin
user {
    +"Describe this image"
    image(
        ContentPart.Image(
            content = AttachmentContent.URL("https://example.com/capture.png"),
            format = "png",
            mimeType = "image/png",
            fileName = "capture.png"
        )
    )
}
```
<!--- KNIT example-multimodal-content-02.kt -->

Koog provides the following specialized classes for each media type that implement the `ContentPart.Attachment` interface:

- [`ContentPart.Image`](api:prompt-model::ai.koog.prompt.message.ContentPart.Image): image attachments, such as JPG or PNG files.
- [`ContentPart.Audio`](api:prompt-model::ai.koog.prompt.message.ContentPart.Audio): audio attachments, such as MP3 or WAV files.
- [`ContentPart.Video`](api:prompt-model::ai.koog.prompt.message.ContentPart.Video): video attachments, such as MP4 or AVI files.
- [`ContentPart.File`](api:prompt-model::ai.koog.prompt.message.ContentPart.File): file attachments, such as PDF or TXT files.

All `ContentPart.Attachment` types accept the following parameters:

| Name       | Data type                                                                                                          | Required | Description                                                                                                                                                                                                                             |
|------------|--------------------------------------------------------------------------------------------------------------------|----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `content`  | [AttachmentContent](api:prompt-model::ai.koog.prompt.message.AttachmentContent) | Yes      | The source of the provided file content.                                                                                                                                                                                                |
| `format`   | String                                                                                                             | Yes      | The format of the provided file. For example, `png`.                                                                                                                                                                                    |
| `mimeType` | String                                                                                                             | Only for `ContentPart.File`      | The MIME Type of the provided file.<br/>For `ContentPart.Image`, `ContentPart.Audio`, and `ContentPart.Video`, it defaults to `<type>/<format>` (for example, `image/png`).<br/>For `ContentPart.File`, it must be explicitly provided. |
| `fileName` | String?                                                                                                            | No       | The name of the provided file including the extension. For example, `screenshot.png`.                                                                                                                                                   |

#### Attachment content

Implementations of the AttachmentContent interface define the type and source of content that is provided as input to the LLM:

- [`AttachmentContent.URL`](api:prompt-model::ai.koog.prompt.message.AttachmentContent.URL) defines the URL of the provided content:
    ```kotlin
    AttachmentContent.URL("https://example.com/image.png")
    ```
- [`AttachmentContent.Binary.Bytes`](api:prompt-model::ai.koog.prompt.message.AttachmentContent.Binary) defines the file content as a byte array:
    ```kotlin
    AttachmentContent.Binary.Bytes(byteArrayOf(/* ... */))
    ```

- [`AttachmentContent.Binary.Base64`](api:prompt-model::ai.koog.prompt.message.AttachmentContent.Binary) defines the file content as a Base64-encoded string containing file data:
    ```kotlin
    AttachmentContent.Binary.Base64("iVBORw0KGgoAAAANS...")
    ```

- [`AttachmentContent.PlainText`](api:prompt-model::ai.koog.prompt.message.AttachmentContent.PlainText) defines the file content as plain text (for [`ContentPart.File`](api:prompt-model::ai.koog.prompt.message.ContentPart.File) only):
    ```kotlin
    AttachmentContent.PlainText("This is the file content.")
    ```

### Mixed attachments

In addition to providing different types of attachments in separate prompts or messages, you can also provide multiple and mixed types of attachments in a single `user()` message:

<!--- CLEAR -->
<!--- INCLUDE
import ai.koog.prompt.dsl.prompt
import kotlinx.io.files.Path
-->
```kotlin
val prompt = prompt("mixed_content") {
    system("You are a helpful assistant.")

    user {
        +"Compare the image with the document content."
        image(Path("/path/to/image.png"))
        binaryFile(Path("/path/to/page.pdf"), "application/pdf")
        +"Structure the result as a table"
    }
}
```
<!--- KNIT example-multimodal-content-03.kt -->

## Next steps

- Run prompts with [LLM clients](../llm-clients.md) if you work with a single LLM provider.
- Run prompts with [prompt executors](../prompt-executors.md) if you work with multiple LLM providers.
