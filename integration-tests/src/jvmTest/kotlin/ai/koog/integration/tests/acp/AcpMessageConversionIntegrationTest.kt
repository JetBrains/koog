package ai.koog.integration.tests.acp

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.features.acp.toAcpEvents
import ai.koog.agents.features.acp.toKoogMessage
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.AttachmentSource
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.utils.time.KoogClock
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.EmbeddedResourceResource
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.ToolCallStatus
import io.kotest.inspectors.shouldForAny
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test

class AcpMessageConversionIntegrationTest {
    @Test
    fun integration_testAcpContentBlocksConvertToKoogMessageParts() {
        val message = listOf(
            ContentBlock.Text("hello from acp"),
            ContentBlock.Image("aW1hZ2U=", "image/png", "sample.png"),
            ContentBlock.Resource(
                EmbeddedResourceResource.TextResourceContents(
                    text = "file payload",
                    uri = "notes.txt",
                    mimeType = "text/plain",
                )
            ),
        ).toKoogMessage(KoogClock.System)

        message.parts.shouldForAny { it is MessagePart.Text && it.text == "hello from acp" }
        message.parts.shouldForAny {
            it is MessagePart.Attachment &&
                it.source is AttachmentSource.Image &&
                (it.source as AttachmentSource.Image).fileName == "sample.png"
        }
        message.parts.shouldForAny {
            it is MessagePart.Attachment &&
                it.source is AttachmentSource.File &&
                ((it.source as AttachmentSource.File).content as AttachmentContent.PlainText).text == "file payload"
        }
    }

    @Test
    fun integration_testKoogAssistantMessageConvertsToAcpSessionUpdates() {
        val toolDescriptor = ToolDescriptor(
            name = "lookup_order",
            description = "Look up an order.",
        )
        val assistant = Message.Assistant(
            parts = listOf(
                MessagePart.Reasoning("Need to inspect the order id."),
                MessagePart.Tool.Call(
                    id = "tool-call-1",
                    tool = toolDescriptor.name,
                    args = buildJsonObject { put("orderId", "A-42") },
                ),
                MessagePart.Text("The order is ready."),
                MessagePart.Attachment(
                    AttachmentSource.Image(
                        content = AttachmentContent.Binary.Base64("aW1hZ2U="),
                        format = "png",
                        mimeType = "image/png",
                        fileName = "order.png",
                    )
                ),
            ),
            metaInfo = ResponseMetaInfo.create(KoogClock.System),
            id = "assistant-message-1",
        )

        val updates = assistant.toAcpEvents(listOf(toolDescriptor))
            .map { it.update }

        updates.shouldForAny {
            it is SessionUpdate.AgentThoughtChunk &&
                (it.content as ContentBlock.Text).text == "Need to inspect the order id."
        }
        updates.shouldForAny {
            it is SessionUpdate.ToolCall &&
                it.toolCallId.value == "tool-call-1" &&
                it.title == toolDescriptor.description &&
                it.status == ToolCallStatus.PENDING &&
                (it.rawInput as JsonObject)["orderId"]?.jsonPrimitive?.content == "A-42"
        }
        updates.shouldForAny {
            it is SessionUpdate.AgentMessageChunk &&
                (it.content as? ContentBlock.Text)?.text == "The order is ready."
        }
        updates.shouldForAny {
            it is SessionUpdate.AgentMessageChunk &&
                (it.content as? ContentBlock.Image)?.uri == "order.png"
        }

        updates.size shouldBe 4
    }
}
