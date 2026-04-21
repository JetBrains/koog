package ai.koog.prompt.executor.clients.openai.models

import ai.koog.prompt.executor.clients.openai.base.models.OpenAIChoiceLogProbs
import ai.koog.test.utils.runWithBothJsonConfigurations
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.test.Test

class OpenAIContentTest {

    @Test
    fun `test InputContent_Text serialization`() =
        runWithBothJsonConfigurations("InputContent.Text serialization") { json ->
            json.encodeToString(InputContent.Text("Hello World")) shouldEqualJson """
            {
                "text": "Hello World"
            }
            """.trimIndent()
        }

    @Test
    fun `test InputContent_Text deserialization`() =
        runWithBothJsonConfigurations("InputContent.Text deserialization") { json ->
            val jsonInput = buildJsonObject {
                put("type", JsonPrimitive("input_text"))
                put("text", JsonPrimitive("Hello World"))
            }

            (json.decodeFromJsonElement<InputContent>(jsonInput) as InputContent.Text).text shouldBe "Hello World"
        }

    @Test
    fun `test InputContent_Image serialization with fileId`() =
        runWithBothJsonConfigurations("InputContent.Image with fileId") { json ->
            json.encodeToString(
                InputContent.Image(
                    detail = "high",
                    fileId = "file_123",
                    imageUrl = null
                )
            ) shouldEqualJson """
            {
                "detail": "high",
                "fileId": "file_123"
            }
            """.trimIndent()
        }

    @Test
    fun `test InputContent_Image serialization with imageUrl`() =
        runWithBothJsonConfigurations("InputContent.Image with imageUrl") { json ->
            json.encodeToString(
                InputContent.Image(
                    detail = "low",
                    fileId = null,
                    imageUrl = "https://example.com/image.png"
                )
            ) shouldEqualJson """
            {
                "detail": "low",
                "imageUrl": "https://example.com/image.png"
            }
            """.trimIndent()
        }

    @Test
    fun `test InputContent_Image deserialization`() =
        runWithBothJsonConfigurations("InputContent.Image deserialization") { json ->
            val jsonInput = buildJsonObject {
                put("type", JsonPrimitive("input_image"))
                put("detail", JsonPrimitive("auto"))
                put("fileId", JsonPrimitive("file_456"))
                put("imageUrl", JsonPrimitive("https://example.com/image.jpg"))
            }

            (json.decodeFromJsonElement<InputContent>(jsonInput) as InputContent.Image).shouldNotBeNull {
                detail shouldBe "auto"
                fileId shouldBe "file_456"
                imageUrl shouldBe "https://example.com/image.jpg"
            }
        }

    @Test
    fun `test InputContent_File serialization with all fields`() =
        runWithBothJsonConfigurations("InputContent.File with all fields") { json ->
            json.encodeToString(
                InputContent.File(
                    fileData = "base64data==",
                    fileId = "file_789",
                    fileUrl = "https://example.com/file.pdf",
                    filename = "document.pdf"
                )
            ) shouldEqualJson """
            {
                "fileData": "base64data==",
                "fileId": "file_789",
                "fileUrl": "https://example.com/file.pdf",
                "filename": "document.pdf"
            }
            """.trimIndent()
        }

    @Test
    fun `test InputContent_File serialization with minimal fields`() =
        runWithBothJsonConfigurations("InputContent.File with minimal fields") { json ->
            json.encodeToString(
                InputContent.File(
                    fileId = "file_minimal"
                )
            ) shouldEqualJson """
            {
                "fileId": "file_minimal"
            }
            """.trimIndent()
        }

    @Test
    fun `test InputContent_File deserialization`() =
        runWithBothJsonConfigurations("InputContent.File deserialization") { json ->
            val jsonInput = buildJsonObject {
                put("type", JsonPrimitive("input_file"))
                put("filename", JsonPrimitive("test.txt"))
                put("fileUrl", JsonPrimitive("https://example.com/test.txt"))
            }

            (json.decodeFromJsonElement<InputContent>(jsonInput) as InputContent.File).shouldNotBeNull {
                filename shouldBe "test.txt"
                fileUrl shouldBe "https://example.com/test.txt"
                fileId shouldBe null
                fileData shouldBe null
            }
        }

    @Test
    fun `test OutputContent_Text serialization without logprobs`() =
        runWithBothJsonConfigurations("OutputContent.Text without logprobs") { json ->
            json.encodeToString(
                OutputContent.Text(
                    annotations = listOf(
                        OpenAIAnnotations.FileCitation("file_123", "document.pdf", 0)
                    ),
                    text = "This is the response text",
                    logprobs = null
                )
            ) shouldEqualJson """
            {
                "annotations": [{
                    "type": "file_citation",
                    "fileId": "file_123",
                    "filename": "document.pdf",
                    "index": 0
                }],
                "text": "This is the response text"
            }
            """.trimIndent()
        }

    @Test
    fun `test OutputContent_Text serialization with logprobs`() =
        runWithBothJsonConfigurations("OutputContent.Text with logprobs") { json ->
            val jsonString = json.encodeToString(
                OutputContent.Text(
                    annotations = emptyList(),
                    text = "Hello",
                    logprobs = listOf(
                        OpenAIChoiceLogProbs.ContentLogProbs(
                            token = "Hello",
                            logprob = -0.5,
                            bytes = listOf(72, 101, 108, 108, 111),
                            topLogprobs = listOf(
                                OpenAIChoiceLogProbs.ContentTopLogProbs(
                                    token = "Hello",
                                    logprob = -0.5,
                                    bytes = listOf(72, 101, 108, 108, 111)
                                )
                            )
                        )
                    )
                )
            )

            json.decodeFromString<OutputContent.Text>(jsonString).shouldNotBeNull {
                text shouldBe "Hello"
                logprobs?.shouldHaveSize(1)
                logprobs?.first()?.token shouldBe "Hello"
                logprobs?.first()?.logprob shouldBe -0.5
            }
        }

    @Test
    fun `test OutputContent_Text deserialization`() =
        runWithBothJsonConfigurations("OutputContent.Text deserialization") { json ->
            val jsonInput = buildJsonObject {
                put("type", JsonPrimitive("output_text"))
                put("annotations", buildJsonArray { })
                put("text", JsonPrimitive("Response text"))
            }

            (json.decodeFromJsonElement<OutputContent>(jsonInput) as OutputContent.Text).shouldNotBeNull {
                text shouldBe "Response text"
                annotations shouldHaveSize 0
                logprobs shouldBe null
            }
        }

    @Test
    fun `test OutputContent_Refusal serialization`() =
        runWithBothJsonConfigurations("OutputContent.Refusal serialization") { json ->
            json.encodeToString(
                OutputContent.Refusal("I cannot help with that request")
            ) shouldEqualJson """
            {
                "refusal": "I cannot help with that request"
            }
            """.trimIndent()
        }

    @Test
    fun `test OutputContent_Refusal deserialization`() =
        runWithBothJsonConfigurations("OutputContent.Refusal deserialization") { json ->
            val text = "Sorry, I cannot assist with that"
            val jsonInput = buildJsonObject {
                put("type", JsonPrimitive("refusal"))
                put("refusal", JsonPrimitive(text))
            }

            (json.decodeFromJsonElement<OutputContent>(jsonInput) as OutputContent.Refusal).refusal shouldBe text
        }

    @Test
    fun `test OpenAIAnnotations_FileCitation serialization`() =
        runWithBothJsonConfigurations("OpenAIAnnotations.FileCitation") { json ->
            json.encodeToString(
                OpenAIAnnotations.FileCitation(
                    fileId = "file_123",
                    filename = "report.pdf",
                    index = 2
                )
            ) shouldEqualJson """
            {
                "fileId": "file_123",
                "filename": "report.pdf",
                "index": 2
            }
            """.trimIndent()
        }

    @Test
    fun `test OpenAIAnnotations_UrlCitation serialization`() =
        runWithBothJsonConfigurations("OpenAIAnnotations.UrlCitation") { json ->
            json.encodeToString(
                OpenAIAnnotations.UrlCitation(
                    endIndex = 50,
                    startIndex = 10,
                    title = "Example Article",
                    url = "https://example.com/article"
                )
            ) shouldEqualJson """
            {
                "endIndex": 50,
                "startIndex": 10,
                "title": "Example Article",
                "url": "https://example.com/article"
            }
            """.trimIndent()
        }

    @Test
    fun `test OpenAIAnnotations_ContainerFileCitation serialization`() =
        runWithBothJsonConfigurations("OpenAIAnnotations.ContainerFileCitation") { json ->
            json.encodeToString(
                OpenAIAnnotations.ContainerFileCitation(
                    containerId = "container_123",
                    endIndex = 100,
                    fileId = "file_456",
                    filename = "data.csv",
                    startIndex = 20
                )
            ) shouldEqualJson """
            {
                "containerId": "container_123",
                "endIndex": 100,
                "fileId": "file_456",
                "filename": "data.csv",
                "startIndex": 20
            }
            """.trimIndent()
        }

    @Test
    fun `test OpenAIAnnotations_FilePath serialization`() =
        runWithBothJsonConfigurations("OpenAIAnnotations.FilePath") { json ->
            json.encodeToString(
                OpenAIAnnotations.FilePath(
                    fileId = "file_789",
                    index = 1
                )
            ) shouldEqualJson """
            {
                "fileId": "file_789",
                "index": 1
            }
            """.trimIndent()
        }

    @Test
    fun `test all OpenAIAnnotations deserialization`() =
        runWithBothJsonConfigurations("all OpenAIAnnotations deserialization") { json ->
            val fileCitationJson = buildJsonObject {
                put("type", JsonPrimitive("file_citation"))
                put("fileId", JsonPrimitive("file_123"))
                put("filename", JsonPrimitive("test.pdf"))
                put("index", JsonPrimitive(0))
            }

            val urlCitationJson = buildJsonObject {
                put("type", JsonPrimitive("url_citation"))
                put("endIndex", JsonPrimitive(50))
                put("startIndex", JsonPrimitive(10))
                put("title", JsonPrimitive("Test Article"))
                put("url", JsonPrimitive("https://test.com"))
            }

            val containerCitationJson = buildJsonObject {
                put("type", JsonPrimitive("container_file_citation"))
                put("containerId", JsonPrimitive("container_456"))
                put("endIndex", JsonPrimitive(75))
                put("fileId", JsonPrimitive("file_789"))
                put("filename", JsonPrimitive("container.tar"))
                put("startIndex", JsonPrimitive(25))
            }

            val filePathJson = buildJsonObject {
                put("type", JsonPrimitive("file_path"))
                put("fileId", JsonPrimitive("file_999"))
                put("index", JsonPrimitive(3))
            }

            (json.decodeFromJsonElement<OpenAIAnnotations>(fileCitationJson) as OpenAIAnnotations.FileCitation).shouldNotBeNull {
                fileId shouldBe "file_123"
                filename shouldBe "test.pdf"
                index shouldBe 0
            }

            (json.decodeFromJsonElement<OpenAIAnnotations>(urlCitationJson) as OpenAIAnnotations.UrlCitation).shouldNotBeNull {
                endIndex shouldBe 50
                startIndex shouldBe 10
                title shouldBe "Test Article"
                url shouldBe "https://test.com"
            }

            (json.decodeFromJsonElement<OpenAIAnnotations>(containerCitationJson) as OpenAIAnnotations.ContainerFileCitation).shouldNotBeNull {
                containerId shouldBe "container_456"
                endIndex shouldBe 75
                fileId shouldBe "file_789"
                filename shouldBe "container.tar"
                startIndex shouldBe 25
            }

            (json.decodeFromJsonElement<OpenAIAnnotations>(filePathJson) as OpenAIAnnotations.FilePath).shouldNotBeNull {
                fileId shouldBe "file_999"
                index shouldBe 3
            }
        }
}
