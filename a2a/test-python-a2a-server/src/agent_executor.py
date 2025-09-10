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


async def say_hello(
    event_queue: EventQueue,
    message: Message
) -> None:
    await event_queue.enqueue_event(
        new_agent_text_message(
            text="Hello World",
            context_id=message.context_id,
            task_id=message.task_id
        )
    )


async def do_task(
    event_queue: EventQueue,
    message: Message
) -> None:
    task = new_task(message)

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
    message: Message,
):
    await event_queue.enqueue_event(
        new_task(message),
    )

async def do_long_running_task(
    event_queue: EventQueue,
    message: Message
):
    task = Task(
        id=message.task_id,
        context_id=message.context_id,
        status=TaskStatus(
            state=TaskState.working,
            message=message
        )
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
        # Test scenarios to test various aspects of A2A
        if "hello world" in context.get_user_input():
            await say_hello(event_queue, context.message)

        elif "do task" in context.get_user_input():
            await do_task(event_queue, context.message)

        elif "do cancelable task" in context.get_user_input():
            await do_cancelable_task(event_queue, context.message)

        elif "do long-running task" in context.get_user_input():
            await do_long_running_task(event_queue, context.message)

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
