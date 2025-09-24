import asyncio

from a2a.server.agent_execution import AgentExecutor, RequestContext
from a2a.server.events import EventQueue
from a2a.types import (
    Message,
    TaskStatusUpdateEvent,
    TaskStatus,
    TaskState,
    Task,
)
from a2a.utils import (
    new_agent_text_message,
    new_task
)
from datetime import datetime, timezone


def get_current_timestamp():
    """Get current timestamp in ISO 8601 format (UTC)"""
    return datetime.now(timezone.utc).isoformat()


async def say_hello(
    event_queue: EventQueue,
    context: RequestContext,
) -> None:
    message = context.message

    await event_queue.enqueue_event(
        new_agent_text_message(
            text="Hello World",
            context_id=message.context_id,
            task_id=message.task_id
        )
    )


async def do_task(
    event_queue: EventQueue,
    context: RequestContext,
) -> None:
    message = context.message

    # noinspection PyTypeChecker
    task = Task(
        id=message.task_id,
        context_id=message.context_id,
        status=TaskStatus(
            state=TaskState.submitted,
            timestamp=get_current_timestamp()
        ),
        history=[message]
    )

    # noinspection PyTypeChecker
    events = [
        task,

        TaskStatusUpdateEvent(
            context_id=task.context_id,
            task_id=task.id,
            status=TaskStatus(
                state=TaskState.working,
                message=new_agent_text_message(
                    text="Working on task",
                    context_id=task.context_id,
                    task_id=task.id
                )
            ),
            final=False
        ),

        TaskStatusUpdateEvent(
            context_id=task.context_id,
            task_id=task.id,
            status=TaskStatus(
                state=TaskState.completed,
                message=new_agent_text_message(
                    text="Task completed",
                    context_id=task.context_id,
                    task_id=task.id
                )
            ),
            final=True
        )
    ]

    for event in events:
        await event_queue.enqueue_event(event)


async def do_cancelable_task(
    event_queue: EventQueue,
    context: RequestContext,
):
    message = context.message

    # noinspection PyTypeChecker
    task = Task(
        id=message.task_id,
        context_id=message.context_id,
        status=TaskStatus(
            state=TaskState.submitted,
            timestamp=get_current_timestamp()
        ),
        history=[message]
    )
    await event_queue.enqueue_event(task)


async def do_long_running_task(
    event_queue: EventQueue,
    context: RequestContext,
):
    message = context.message

    # noinspection PyTypeChecker
    task = Task(
        id=message.task_id,
        context_id=message.context_id,
        status=TaskStatus(
            state=TaskState.submitted,
            timestamp=get_current_timestamp()
        ),
        history=[message]
    )

    await event_queue.enqueue_event(task)

    # Simulate long-running task
    for i in range(4):
        await asyncio.sleep(0.2)

        # noinspection PyTypeChecker
        await event_queue.enqueue_event(
            TaskStatusUpdateEvent(
                task_id=task.id,
                context_id=task.context_id,
                status=TaskStatus(
                    state=TaskState.working,
                    message=new_agent_text_message(
                        text=f"Still working {i}",
                        context_id=task.context_id,
                        task_id=task.id
                    )
                ),
                final=False
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
                new_agent_text_message("Sorry, I don't understand you")
            )

    async def cancel(
        self,
        context: RequestContext,
        event_queue: EventQueue
    ) -> None:
        # noinspection PyTypeChecker
        await event_queue.enqueue_event(
            TaskStatusUpdateEvent(
                context_id=context.context_id,
                task_id=context.task_id,
                status=TaskStatus(
                    state=TaskState.canceled,
                    message=new_agent_text_message(
                        text="Task canceled",
                        context_id=context.context_id,
                        task_id=context.task_id
                    )
                ),
                final=True,
            )
        )
