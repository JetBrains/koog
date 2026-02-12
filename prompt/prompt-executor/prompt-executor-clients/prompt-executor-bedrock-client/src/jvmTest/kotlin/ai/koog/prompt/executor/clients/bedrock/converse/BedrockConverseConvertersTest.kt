package ai.koog.prompt.executor.clients.bedrock.converse

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.bedrock.BedrockModels
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import aws.sdk.kotlin.services.bedrockruntime.model.DocumentSource
import aws.sdk.kotlin.services.bedrockruntime.model.ImageSource
import aws.sdk.kotlin.services.bedrockruntime.model.ToolResultContentBlock
import aws.sdk.kotlin.services.bedrockruntime.model.VideoSource
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class BedrockConverseConvertersTest {
    @Test
    fun testCreateConverseRequestConvertsToolResultAttachments() {
        val prompt = createPromptWithToolResult(
            listOf(
                ContentPart.File(
                    content = AttachmentContent.PlainText("tool file"),
                    format = "txt",
                    mimeType = "text/plain",
                    fileName = "report.txt"
                ),
                ContentPart.Image(
                    content = AttachmentContent.URL("s3://bucket/image.png"),
                    format = "png",
                    fileName = "image.png"
                ),
                ContentPart.Video(
                    content = AttachmentContent.Binary.Bytes(byteArrayOf(1, 2, 3)),
                    format = "mp4",
                    fileName = "video.mp4"
                ),
                ContentPart.Text("ok")
            )
        )

        val request = BedrockConverseConverters.createConverseRequest(
            prompt = prompt,
            model = BedrockModels.AnthropicClaude3Sonnet,
            tools = emptyList()
        )

        val toolResultMessage = requireNotNull(request.messages).single()
        val toolResult = requireNotNull(toolResultMessage.content).single().asToolResult()
        val content = requireNotNull(toolResult.content)

        val document = assertIs<ToolResultContentBlock.Document>(content[0]).value
        assertEquals("report", document.name)
        val documentBytes = assertIs<DocumentSource.Bytes>(document.source).value
        assertContentEquals("tool file".encodeToByteArray(), documentBytes)

        val image = assertIs<ToolResultContentBlock.Image>(content[1]).value
        val imageS3 = assertIs<ImageSource.S3Location>(image.source).value
        assertEquals("s3://bucket/image.png", imageS3.uri)

        val video = assertIs<ToolResultContentBlock.Video>(content[2]).value
        val videoBytes = assertIs<VideoSource.Bytes>(video.source).value
        assertContentEquals(byteArrayOf(1, 2, 3), videoBytes)

        assertEquals("ok", assertIs<ToolResultContentBlock.Text>(content[3]).value)
    }

    @Test
    fun testCreateConverseRequestRejectsAudioInToolResult() {
        val prompt = createPromptWithToolResult(
            listOf(
                ContentPart.Audio(
                    content = AttachmentContent.Binary.Bytes(byteArrayOf(1, 2, 3)),
                    format = "mp3"
                )
            )
        )

        assertFailsWith<IllegalArgumentException> {
            BedrockConverseConverters.createConverseRequest(
                prompt = prompt,
                model = BedrockModels.AnthropicClaude3Sonnet,
                tools = emptyList()
            )
        }
    }

    private fun createPromptWithToolResult(parts: List<ContentPart>): Prompt =
        Prompt.build("test-tool-result-prompt") {
            message(
                Message.Tool.Result(
                    id = "tool-call-id",
                    tool = "test-tool",
                    parts = parts,
                    metaInfo = RequestMetaInfo.Empty
                )
            )
        }
}
