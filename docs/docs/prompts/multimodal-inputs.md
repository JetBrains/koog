# Multimodal inputs

In addition to text messages, Koog also lets you send images, audio, video, and files to LLMs within the `user` message.
You can add these attachments to the `user` message by using the corresponding functions:

- `image()`: Adds images (JPG, PNG, WebP, GIF).
- `audio()`: Adds audio files (MP3, WAV, FLAC).
- `video()`: Adds video files (MP4, AVI, MOV).
- `file()` / `binaryFile()` / `textFile()`: Add documents (PDF, TXT, MD, etc.).

Each function supports two ways of configuring media content parameters, so you can:

- Pass a URL or a file path to the function, and it automatically handles media content parameters.
- Create and pass a `ContentPart` object to the function for custom control over media content parameters.

### Auto-configured attachments

If you pass a URL or a file path to the `image()`, `audio()`, `video()`, or `file()` functions, Koog automatically constructs
the corresponding media content parameters based on the file extension.

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
    image(Path("/User/koog/image.png"))

    +"Focus on the main subjects."
}
```
<!--- KNIT example-multimodal-inputs-01.kt -->

The `+` operator adds text content to the user message along with the media attachments.

### Custom-configured attachments

The [`ContentPart`](https://api.koog.ai/prompt/prompt-model/ai.koog.prompt.message/-content-part/index.html) class
lets you configure media content parameters for each attachment individually.

You can create a `ContentPart` object for each attachment, configure its parameters,
and pass it to the corresponding `image()`, `audio()`, `video()`, or `file()` functions.

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
<!--- KNIT example-multimodal-inputs-02.kt -->

Koog provides specialized `ContentPart` classes for each media type:

- [`ContentPart.Image`](https://api.koog.ai/prompt/prompt-model/ai.koog.prompt.message/-content-part/-image/index.html): image attachments, such as JPG or PNG files.
- [`ContentPart.Audio`](https://api.koog.ai/prompt/prompt-model/ai.koog.prompt.message/-content-part/-audio/index.html): audio attachments, such as MP3 or WAV files.
- [`ContentPart.Video`](https://api.koog.ai/prompt/prompt-model/ai.koog.prompt.message/-content-part/-video/index.html): video attachments, such as MP4 or AVI files.
- [`ContentPart.File`](https://api.koog.ai/prompt/prompt-model/ai.koog.prompt.message/-content-part/-file/index.html): file attachments, such as PDF or TXT files.

All `ContentPart` types accept the following parameters:

| Name       | Data type                               | Required                    | Description                                                                                               |
|------------|-----------------------------------------|-----------------------------|-----------------------------------------------------------------------------------------------------------|
| `content`  | [AttachmentContent](https://api.koog.ai/prompt/prompt-model/ai.koog.prompt.message/-attachment-content/index.html) | Yes                         | The source of the provided file content. |
| `format`   | String                                  | Yes                         | The format of the provided file. For example, `png`.                                                      |
| `mimeType` | String                                  | Only for `ContentPart.File` | The MIME Type of the provided file. For example, `image/png`.                                             |
| `fileName` | String                                  | No                          | The name of the provided file including the extension. For example, `screenshot.png`.                     |

#### Attachment content

`AttachmentContent` defines the type and source of content that is provided as input to the LLM:

- URL of the provided content:
    ```kotlin
    AttachmentContent.URL("https://example.com/image.png")
    ```
  See also [API reference](https://api.koog.ai/prompt/prompt-model/ai.koog.prompt.message/-attachment-content/-u-r-l/index.html).

- File content as a byte array:
    ```kotlin
    AttachmentContent.Binary.Bytes(byteArrayOf(/* ... */))
    ```
  See also [API reference](https://api.koog.ai/prompt/prompt-model/ai.koog.prompt.message/-attachment-content/-binary/index.html).

- File content as a Base64-encoded string containing file data:
    ```kotlin
    AttachmentContent.Binary.Base64("iVBORw0KGgoAAAANS...")
    ```
  See also [API reference](https://api.koog.ai/prompt/prompt-model/ai.koog.prompt.message/-attachment-content/-binary/index.html).

- File content as plain text (for `ContentPart.File` only):

    ```kotlin
    AttachmentContent.PlainText("This is the file content.")
    ```
  See also [API reference](https://api.koog.ai/prompt/prompt-model/ai.koog.prompt.message/-attachment-content/-plain-text/index.html).

### Mixed attachments

In addition to providing different types of attachments in separate prompts or messages, you can also provide multiple and mixed types of attachments in a single `user` message:

<!--- INCLUDE
import ai.koog.prompt.dsl.prompt
import kotlinx.io.files.Path
-->
```kotlin
val prompt = prompt("mixed_content") {
    system("You are a helpful assistant.")

    user {
        +"Compare the image with the document content."
        image(Path("/User/koog/page.png"))
        binaryFile(Path("/User/koog/page.pdf"), "application/pdf")
        +"Structure the result as a table"
    }
}
```
<!--- KNIT example-multimodal-inputs-03.kt -->

## Next steps

- Run prompts with [LLM clients](llm-clients.md) if you work with a single LLM provider.
- Run prompts with [prompt executors](prompt-executors.md) if you work with multiple LLM providers.
