import asyncio

from a2a.server.agent_execution import AgentExecutor, RequestContext
from a2a.server.events import EventQueue
from a2a.types import (
    TaskStatusUpdateEvent,
    TaskStatus,
    TaskState,
)
from a2a.helpers import (
    new_text_message,
    new_task,
)


async def say_hello(
    event_queue: EventQueue,
    context: RequestContext,
) -> None:
    message = context.message

    await event_queue.enqueue_event(
        new_text_message(
            text="Hello World",
            context_id=message.context_id,
            task_id=message.task_id,
        )
    )


async def do_task(
    event_queue: EventQueue,
    context: RequestContext,
) -> None:
    message = context.message

    task = new_task(
        task_id=message.task_id,
        context_id=message.context_id,
        state=TaskState.TASK_STATE_SUBMITTED,
        history=[message],
    )

    events = [
        task,

        TaskStatusUpdateEvent(
            context_id=task.context_id,
            task_id=task.id,
            status=TaskStatus(
                state=TaskState.TASK_STATE_WORKING,
                message=new_text_message(
                    text="Working on task",
                    context_id=task.context_id,
                    task_id=task.id,
                ),
            ),
        ),

        TaskStatusUpdateEvent(
            context_id=task.context_id,
            task_id=task.id,
            status=TaskStatus(
                state=TaskState.TASK_STATE_COMPLETED,
                message=new_text_message(
                    text="Task completed",
                    context_id=task.context_id,
                    task_id=task.id,
                ),
            ),
        )
    ]

    for event in events:
        await event_queue.enqueue_event(event)


async def do_cancelable_task(
    event_queue: EventQueue,
    context: RequestContext,
):
    message = context.message

    task = new_task(
        task_id=message.task_id,
        context_id=message.context_id,
        state=TaskState.TASK_STATE_SUBMITTED,
        history=[message],
    )
    await event_queue.enqueue_event(task)


async def do_long_running_task(
    event_queue: EventQueue,
    context: RequestContext,
):
    message = context.message

    task = new_task(
        task_id=message.task_id,
        context_id=message.context_id,
        state=TaskState.TASK_STATE_SUBMITTED,
        history=[message],
    )

    await event_queue.enqueue_event(task)

    # Simulate long-running task
    for i in range(4):
        await asyncio.sleep(0.2)

        await event_queue.enqueue_event(
            TaskStatusUpdateEvent(
                task_id=task.id,
                context_id=task.context_id,
                status=TaskStatus(
                    state=TaskState.TASK_STATE_WORKING,
                    message=new_text_message(
                        text=f"Still working {i}",
                        context_id=task.context_id,
                        task_id=task.id,
                    ),
                ),
            )
        )


class HelloWorldAgentExecutor(AgentExecutor):
    """Test AgentProxy Implementation."""

    async def execute(
        self,
        context: RequestContext,
        event_queue: EventQueue,
    ) -> None:
        user_input = context.get_user_input()

        # Test scenarios to test various aspects of A2A
        if user_input == "hello world":
            await say_hello(event_queue, context)

        elif user_input == "do task":
            await do_task(event_queue, context)

        elif user_input == "do cancelable task":
            await do_cancelable_task(event_queue, context)

        elif user_input == "do long-running task":
            await do_long_running_task(event_queue, context)

        else:
            await event_queue.enqueue_event(
                new_text_message("Sorry, I don't understand you")
            )

    async def cancel(
        self,
        context: RequestContext,
        event_queue: EventQueue
    ) -> None:
        await event_queue.enqueue_event(
            TaskStatusUpdateEvent(
                context_id=context.context_id,
                task_id=context.task_id,
                status=TaskStatus(
                    state=TaskState.TASK_STATE_CANCELED,
                    message=new_text_message(
                        text="Task canceled",
                        context_id=context.context_id,
                        task_id=context.task_id,
                    ),
                ),
            )
        )
