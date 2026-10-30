package ai.koog.a2a.server

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * How [A2AServer] delivers task events to the streams of a task: SubscribeToTask streams and the requester's
 * SendStreamingMessage stream. A blocking or `returnImmediately` SendMessage is always served as [Suspend] with
 * capacity 0, since its in-process collector never stalls.
 *
 * Every stream has its own queue of [capacity] events. The agent's `sendTaskEvent`/`sendMessage` persists the event
 * and returns once it is in the queue of every stream of the task. Events of a task reach every stream in the same
 * order. What happens when a queue is full is up to the variant:
 * - [Suspend] waits until the stream's reader takes an event, however long that takes;
 * - [Evict] waits at most [Evict.timeout], then ends that stream with an error.
 *
 * Push notifications are queued separately and never wait for streams.
 */
public sealed interface StreamDeliveryConfig {
    /**
     * The number of events queued per stream before the publisher waits. 0 means no queue: each event is handed over
     * directly, so the publisher waits until every reader has taken it.
     */
    public val capacity: Int

    /**
     * Never drops an event: when the queue of a stream is full, the publisher waits until its reader takes an event.
     *
     * A stalled reader that stays connected therefore pauses its whole task: the agent, every other stream of the
     * task, CancelTask, follow-up messages, new SubscribeToTask calls, and the end of the turn (also when the turn
     * fails), until the reader reads, disconnects, or the server is shut down. A graceful shutdown then waits for it
     * until its grace period ends. Other tasks aren't affected.
     *
     * With [capacity] 0, a publisher always waits for every reader.
     *
     * @property capacity The number of events queued per stream, at least 0.
     */
    public data class Suspend(override val capacity: Int = 0) : StreamDeliveryConfig {
        init {
            require(capacity >= 0) { "capacity must be non-negative, but was $capacity" }
        }
    }

    /**
     * Ends the stream of a reader that falls behind: when the queue of a stream is full, the publisher waits up to
     * [timeout] for space, then evicts that stream. The task and the other streams go on.
     *
     * The deadline is per event and shared by all streams: whatever the number of stalled streams, publishing one
     * event waits at most [timeout], and every stream whose queue is still full then is evicted.
     *
     * An evicted stream receives the events queued before, in order, and then fails with
     * [ai.koog.a2a.exceptions.A2AInternalErrorException] (`-32603`) "Stream closed because the client is not reading
     * events fast enough; resubscribe with SubscribeToTask or call GetTask", with a `google.rpc.ErrorInfo` detail
     * whose reason is `SLOW_CONSUMER` and whose metadata holds the `taskId`. The client may then call GetTask, which
     * also covers a terminal event that didn't fit into the queue, or SubscribeToTask, which starts with the current
     * task.
     *
     * Waiting before the eviction lets readers catch up with a burst, e.g. an agent that sends many events without
     * suspending.
     *
     * @property capacity The number of events queued per stream, at least 0.
     * @property timeout How long publishing an event waits for full queues before evicting them; must be positive.
     */
    public data class Evict(
        override val capacity: Int = 64,
        public val timeout: Duration = 30.seconds,
    ) : StreamDeliveryConfig {
        init {
            require(capacity >= 0) { "capacity must be non-negative, but was $capacity" }
            require(timeout.isPositive()) { "timeout must be positive, but was $timeout" }
        }
    }
}
