// Deprecated OAuth flows are still on the wire, so they are covered too
@file:Suppress("DEPRECATION")

package ai.koog.a2a.transport.jsonrpc.serialization

import ai.koog.a2a.model.APIKeySecurityScheme
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.AuthorizationCodeOAuthFlow
import ai.koog.a2a.model.CancelTaskRequest
import ai.koog.a2a.model.ClientCredentialsOAuthFlow
import ai.koog.a2a.model.DataPart
import ai.koog.a2a.model.DeleteTaskPushNotificationConfigRequest
import ai.koog.a2a.model.DeviceCodeOAuthFlow
import ai.koog.a2a.model.FileBytesPart
import ai.koog.a2a.model.FileUrlPart
import ai.koog.a2a.model.GetExtendedAgentCardRequest
import ai.koog.a2a.model.GetTaskPushNotificationConfigRequest
import ai.koog.a2a.model.GetTaskRequest
import ai.koog.a2a.model.HTTPAuthSecurityScheme
import ai.koog.a2a.model.ImplicitOAuthFlow
import ai.koog.a2a.model.ListTaskPushNotificationConfigsRequest
import ai.koog.a2a.model.ListTaskPushNotificationConfigsResponse
import ai.koog.a2a.model.ListTasksRequest
import ai.koog.a2a.model.ListTasksResponse
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.MutualTLSSecurityScheme
import ai.koog.a2a.model.OAuth2SecurityScheme
import ai.koog.a2a.model.OpenIdConnectSecurityScheme
import ai.koog.a2a.model.PasswordOAuthFlow
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.SubscribeToTaskRequest
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskArtifactUpdateEvent
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.serialization.EventSerializer
import ai.koog.a2a.serialization.OAuthFlowSerializer
import ai.koog.a2a.serialization.PartSerializer
import ai.koog.a2a.serialization.ResponseEventSerializer
import ai.koog.a2a.serialization.SecuritySchemeSerializer
import ai.koog.a2a.serialization.TaskEventSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Walks the serial descriptors of every model reachable from the JSON-RPC params and results, so that a new
 * free-form property not covered by [SnakeCaseKeyNormalizer] fails the build instead of having its user keys
 * rewritten.
 */
class SnakeCaseKeyNormalizerCoverageTest {
    private val roots: List<KSerializer<*>> = listOf(
        // params
        GetExtendedAgentCardRequest.serializer(),
        SendMessageRequest.serializer(),
        GetTaskRequest.serializer(),
        ListTasksRequest.serializer(),
        CancelTaskRequest.serializer(),
        SubscribeToTaskRequest.serializer(),
        TaskPushNotificationConfig.serializer(),
        GetTaskPushNotificationConfigRequest.serializer(),
        ListTaskPushNotificationConfigsRequest.serializer(),
        DeleteTaskPushNotificationConfigRequest.serializer(),
        // results
        AgentCard.serializer(),
        ResponseEventSerializer,
        EventSerializer,
        Task.serializer(),
        ListTasksResponse.serializer(),
        ListTaskPushNotificationConfigsResponse.serializer(),
    )

    /** Custom serializers whose descriptors do not expose their variants: variant key to variant serializer. */
    private val variants: Map<SerialDescriptor, Map<String, KSerializer<*>>> = mapOf(
        PartSerializer.descriptor to mapOf(
            TextPart.DISCRIMINATOR to TextPart.serializer(),
            FileBytesPart.DISCRIMINATOR to FileBytesPart.serializer(),
            FileUrlPart.DISCRIMINATOR to FileUrlPart.serializer(),
            DataPart.DISCRIMINATOR to DataPart.serializer(),
        ),
        EventSerializer.descriptor to mapOf(
            Task.KIND to Task.serializer(),
            Message.KIND to Message.serializer(),
            TaskStatusUpdateEvent.KIND to TaskStatusUpdateEvent.serializer(),
            TaskArtifactUpdateEvent.KIND to TaskArtifactUpdateEvent.serializer(),
        ),
        ResponseEventSerializer.descriptor to mapOf(
            Task.KIND to Task.serializer(),
            Message.KIND to Message.serializer(),
        ),
        TaskEventSerializer.descriptor to mapOf(
            Task.KIND to Task.serializer(),
            TaskStatusUpdateEvent.KIND to TaskStatusUpdateEvent.serializer(),
            TaskArtifactUpdateEvent.KIND to TaskArtifactUpdateEvent.serializer(),
        ),
        SecuritySchemeSerializer.descriptor to mapOf(
            APIKeySecurityScheme.KIND to APIKeySecurityScheme.serializer(),
            HTTPAuthSecurityScheme.KIND to HTTPAuthSecurityScheme.serializer(),
            OAuth2SecurityScheme.KIND to OAuth2SecurityScheme.serializer(),
            OpenIdConnectSecurityScheme.KIND to OpenIdConnectSecurityScheme.serializer(),
            MutualTLSSecurityScheme.KIND to MutualTLSSecurityScheme.serializer(),
        ),
        OAuthFlowSerializer.descriptor to mapOf(
            AuthorizationCodeOAuthFlow.KIND to AuthorizationCodeOAuthFlow.serializer(),
            ClientCredentialsOAuthFlow.KIND to ClientCredentialsOAuthFlow.serializer(),
            ImplicitOAuthFlow.KIND to ImplicitOAuthFlow.serializer(),
            PasswordOAuthFlow.KIND to PasswordOAuthFlow.serializer(),
            DeviceCodeOAuthFlow.KIND to DeviceCodeOAuthFlow.serializer(),
        ),
    )

    private val freeFormNames = setOf(
        JsonObject.serializer().descriptor.serialName,
        JsonElement.serializer().descriptor.serialName,
    )

    private val coveredKeys = SnakeCaseKeyNormalizer.opaqueKeys + SnakeCaseKeyNormalizer.userKeyedMaps

    private fun SerialDescriptor.isFreeForm(): Boolean =
        kind == StructureKind.MAP || serialName.removeSuffix("?") in freeFormNames

    private class Walk {
        val visited = mutableSetOf<String>()
        val problems = mutableListOf<String>()
        val matchedKeys = mutableSetOf<String>()
    }

    private fun Walk.visit(descriptor: SerialDescriptor) {
        // Lists share one serial name whatever their element type, so only their element is tracked
        if (descriptor.kind == StructureKind.LIST) return visit(descriptor.getElementDescriptor(0))
        if (!visited.add(descriptor.serialName.removeSuffix("?"))) return

        val descriptorVariants = variants.entries.find { it.key.serialName == descriptor.serialName }?.value
        if (descriptorVariants != null) {
            descriptorVariants.forEach { (key, serializer) ->
                // Part variants are told apart by one of their own properties, which the walk checks below
                if (key in coveredKeys && descriptor != PartSerializer.descriptor) {
                    problems += "${descriptor.serialName}: variant key '$key' is a normalizer key"
                }
                visit(serializer.descriptor)
            }
            return
        }

        when (descriptor.kind) {
            StructureKind.CLASS, StructureKind.OBJECT -> Unit
            is PolymorphicKind -> {
                problems += "${descriptor.serialName}: polymorphic, list its variants in this test"
                return
            }
            else -> return
        }

        if (descriptor.kind == StructureKind.CLASS && descriptor.elementsCount == 0) {
            problems += "${descriptor.serialName}: no elements, list its variants in this test if it is custom"
        }

        for (i in 0 until descriptor.elementsCount) {
            val name = descriptor.getElementName(i)
            val element = descriptor.getElementDescriptor(i)
            val path = "${descriptor.serialName}.$name"

            when (name) {
                in SnakeCaseKeyNormalizer.opaqueKeys -> {
                    matchedKeys += name
                    if (!element.isFreeForm()) problems += "$path: structured property named like an opaque key"
                }

                in SnakeCaseKeyNormalizer.userKeyedMaps -> {
                    matchedKeys += name
                    if (element.kind != StructureKind.MAP) {
                        problems += "$path: not a map, but its keys would be kept"
                    } else {
                        visit(element.getElementDescriptor(1))
                    }
                }

                else -> if (element.isFreeForm()) {
                    problems += "$path: free-form property not covered by SnakeCaseKeyNormalizer"
                } else {
                    visit(element)
                }
            }
        }
    }

    @Test
    fun testFreeFormPropertiesAreCoveredByNormalizer() {
        val walk = Walk()
        roots.forEach { walk.visit(it.descriptor) }

        assertEquals(emptyList(), walk.problems)
        // Catches stale keys that no longer match any model property
        assertEquals(coveredKeys, walk.matchedKeys)
    }
}
