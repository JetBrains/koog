from copy import deepcopy

import uvicorn

from starlette.applications import Starlette

from a2a.server.request_handlers import DefaultRequestHandler
from a2a.server.routes import (
    create_agent_card_routes,
    create_jsonrpc_routes,
)
from a2a.server.tasks import (
    InMemoryTaskStore,
    InMemoryPushNotificationConfigStore,
)
from a2a.types import (
    AgentCapabilities,
    AgentCard,
    AgentInterface,
    AgentSkill,
)
from a2a.utils.constants import (
    DEFAULT_RPC_URL,
    TransportProtocol,
)
from agent_executor import (
    HelloWorldAgentExecutor,
)


if __name__ == '__main__':
    skill = AgentSkill(
        id='hello_world',
        name='Returns hello world',
        description='just returns hello world',
        tags=['hello world'],
        examples=['hi', 'hello world'],
    )

    extended_skill = AgentSkill(
        id='super_hello_world',
        name='Returns a SUPER Hello World',
        description='A more enthusiastic greeting, only for authenticated users.',
        tags=['hello world', 'super', 'extended'],
        examples=['super hi', 'give me a super hello'],
    )

    public_agent_card = AgentCard(
        name='Hello World Agent',
        description='Just a hello world agent',
        # In A2A v1.0 the single `url`/`preferred_transport` pair is replaced by
        # a list of supported interfaces describing each transport endpoint.
        supported_interfaces=[
            AgentInterface(
                url='http://localhost:9999/',
                protocol_binding=TransportProtocol.JSONRPC.value,
                protocol_version="1.0"
            ),
        ],
        version='1.0.0',
        default_input_modes=['text'],
        default_output_modes=['text'],
        capabilities=AgentCapabilities(
            streaming=True,
            push_notifications=True,
            # In A2A v1.0 `supports_authenticated_extended_card` moved here as
            # `extended_agent_card`.
            extended_agent_card=True,
        ),
        skills=[skill],  # Only the basic skill for the public card
    )

    # This will be the authenticated extended agent card.
    # It includes the additional 'extended_skill'. Protobuf messages don't
    # provide `model_copy`, so we deep-copy the public card and override fields.
    specific_extended_agent_card = deepcopy(public_agent_card)
    specific_extended_agent_card.name = 'Hello World Agent - Extended Edition'
    specific_extended_agent_card.description = (
        'The full-featured hello world agent for authenticated users.'
    )
    specific_extended_agent_card.version = '1.0.1'
    # Both skills for the extended card.
    del specific_extended_agent_card.skills[:]
    specific_extended_agent_card.skills.extend([skill, extended_skill])

    request_handler = DefaultRequestHandler(
        agent_executor=HelloWorldAgentExecutor(),
        task_store=InMemoryTaskStore(),
        agent_card=public_agent_card,
        push_config_store=InMemoryPushNotificationConfigStore(),
        extended_agent_card=specific_extended_agent_card,
    )

    # In A2A v1.0 `A2AStarletteApplication` is removed in favor of composable
    # route factories that are mounted onto a plain Starlette application.
    routes = create_agent_card_routes(
        agent_card=public_agent_card,
    ) + create_jsonrpc_routes(
        request_handler=request_handler,
        rpc_url=DEFAULT_RPC_URL,
    )

    server = Starlette(routes=routes)

    uvicorn.run(server, host='0.0.0.0', port=9999)
