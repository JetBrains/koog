package ai.koog.a2a.server

import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.CommunicationEvent
import ai.koog.a2a.model.MessageSendParams
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskIdParams
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.model.TaskPushNotificationConfigParams
import ai.koog.a2a.model.TaskQueryParams
import ai.koog.a2a.model.UpdateEvent
import ai.koog.a2a.transport.Request
import ai.koog.a2a.transport.RequestHandler
import ai.koog.a2a.transport.Response
import ai.koog.a2a.transport.ServerCallContext
import kotlinx.coroutines.flow.Flow

/**
 * A2A server responsible for handling requests from A2A clients.
 */
public class A2AServer : RequestHandler {
    override suspend fun onGetAuthenticatedExtendedAgentCard(
        request: Request<Nothing?>,
        ctx: ServerCallContext
    ): Response<AgentCard> {
        TODO("Not yet implemented")
    }

    override suspend fun onSendMessage(
        request: Request<MessageSendParams>,
        ctx: ServerCallContext
    ): Response<CommunicationEvent> {
        TODO("Not yet implemented")
    }

    override fun onSendMessageStreaming(
        request: Request<MessageSendParams>,
        ctx: ServerCallContext
    ): Flow<Response<UpdateEvent>> {
        TODO("Not yet implemented")
    }

    override suspend fun onGetTask(
        request: Request<TaskQueryParams>,
        ctx: ServerCallContext
    ): Response<Task> {
        TODO("Not yet implemented")
    }

    override suspend fun onCancelTask(
        request: Request<TaskIdParams>,
        ctx: ServerCallContext
    ): Response<Task> {
        TODO("Not yet implemented")
    }

    override suspend fun onSetTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfig>,
        ctx: ServerCallContext
    ): Response<TaskPushNotificationConfig> {
        TODO("Not yet implemented")
    }

    override suspend fun onGetTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfigParams>,
        ctx: ServerCallContext
    ): Response<TaskPushNotificationConfig> {
        TODO("Not yet implemented")
    }

    override suspend fun onListTaskPushNotificationConfig(
        request: Request<TaskIdParams>,
        ctx: ServerCallContext
    ): Response<List<TaskPushNotificationConfig>> {
        TODO("Not yet implemented")
    }

    override suspend fun onDeleteTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfigParams>,
        ctx: ServerCallContext
    ): Response<Unit> {
        TODO("Not yet implemented")
    }
}
