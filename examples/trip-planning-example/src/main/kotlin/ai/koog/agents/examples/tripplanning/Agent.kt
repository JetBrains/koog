package ai.koog.agents.examples.tripplanning

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStructured
import ai.koog.agents.core.dsl.extension.replaceHistoryWithTLDR
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTool
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.core.tools.reflect.tool
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.agents.examples.tripplanning.api.OpenMeteoClient
import ai.koog.agents.examples.tripplanning.tools.UserTools
import ai.koog.agents.examples.tripplanning.tools.WeatherTools
import ai.koog.agents.examples.tripplanning.tools.addDate
import ai.koog.agents.ext.agent.subgraphWithTask
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.xml.xml

// UNUSED!
fun createSimplePlannerAgent(
    promptExecutor: PromptExecutor,
    openMeteoClient: OpenMeteoClient,
    googleMapsMcpRegistry: ToolRegistry,
    onToolCallEvent: (String) -> Unit,
    showMessage: suspend (String) -> String,
): AIAgent<String, String> {
    val weatherTools = WeatherTools(openMeteoClient)

    val userTools = UserTools(
        showMessage,
        /** other user-facing tools if needed */
    )

    val toolRegistry = ToolRegistry {
        tool(::addDate)
        tools(weatherTools)
        tools(userTools)
    } + googleMapsMcpRegistry

    return AIAgent(
        promptExecutor = promptExecutor,
        llmModel = OpenAIModels.Chat.GPT4o,
        temperature = 0.3,
        toolRegistry = toolRegistry,
        systemPrompt = """
            You are a trip planning agent that helps the user to plan their trip.
            Use the information provided by the user to suggest the best possible trip plan.
        """.trimIndent(),
        maxIterations = 200
    ) {
        handleEvents {
            onToolCall { ctx ->
                onToolCallEvent(
                    "Tool ${ctx.tool.name}, args ${
                        ctx.toolArgs.toString().replace('\n', ' ').take(100)
                    }..."
                )
            }
        }
    }
}

fun createPlannerAgent(
    promptExecutor: PromptExecutor,
    openMeteoClient: OpenMeteoClient,
    googleMapsMcpRegistry: ToolRegistry,
    onToolCallEvent: (String) -> Unit,
    showMessage: suspend (String) -> String,
): AIAgent<UserInput, TripPlan> {
    val googleMapTools = googleMapsMcpRegistry.tools
    val weatherTools = WeatherTools(openMeteoClient)
    val userTools = UserTools(
        showMessage,
        /** other user-facing tools if needed */
    )

    val toolRegistry = ToolRegistry {
        tool(::addDate)
        tools(weatherTools)
        tools(userTools)
    } + googleMapsMcpRegistry

    val plannerStrategy = plannerStrategy(
        googleMapsTools = googleMapTools,
        addDateTool = ::addDate.asTool(),
        weatherTools = weatherTools,
        userTools = userTools,
    )

    val agentConfig = AIAgentConfig(
        prompt = prompt(
            "planner-agent-prompt",
            params = LLMParams(temperature = 0.2)
        ) {
            system(
                """
                You are a trip planning agent that helps the user to plan their trip.
                Use the information provided by the user to suggest the best possible trip plan.
                """.trimIndent()
            )
        },
        model = OpenAIModels.Chat.GPT4o,
        maxAgentIterations = 200
    )

    // Create the runner
    return AIAgent<UserInput, TripPlan>(
        promptExecutor = promptExecutor,
        strategy = plannerStrategy,
        agentConfig = agentConfig,
        toolRegistry = toolRegistry,
    ) {
        handleEvents {
            onToolCall { ctx ->
                onToolCallEvent(
                    "Tool ${ctx.tool.name}, args ${
                        ctx.toolArgs.toString().replace('\n', ' ').take(100)
                    }..."
                )
            }
        }
    }
}


private fun plannerStrategy(
    googleMapsTools: List<Tool<*, *>>,
    addDateTool: Tool<*, *>,
    weatherTools: WeatherTools,
    userTools: UserTools
) = strategy<UserInput, TripPlan>("planner-strategy") {
    val userPlanKey = createStorageKey<TripPlan>("user_plan")
    val prevSuggestedPlanKey = createStorageKey<TripPlan>("prev_suggested_plan")

    // Nodes

    // Set additional system instructions
    val setup by node<UserInput, String> { userInput ->
        llm.writeSession {
            updatePrompt {
                system {
                    +"Today's date is ${userInput.currentDate}."
                    // +"User's timezone is ${userInput.timezone}."
                }
            }
        }

        userInput.message
    }

    val clarifyUserPlan by subgraphWithTask<String, TripPlan>(
        tools = userTools.asTools() + addDateTool
    ) { initialMessage ->
        xml {
            tag("instructions") {
                +"""
                Clarify a user plan until the locations, dates and additional information, such as user preferences, are provided.    
                """.trimIndent()
            }

            tag("initial_user_message") {
                +initialMessage
            }
        }
    }

    val suggestPlan by subgraphWithTask<SuggestPlanRequest, TripPlan>(
        tools = googleMapsTools + weatherTools.asTools()
    ) { input ->
        xml {
            tag("instructions") {
                markdown {
                    h2("Requirements")
                    bulleted {
                        item("Suggest the plan for ALL days and ALL locations in the user plan, preserving the order.")
                        item("Follow the user plan and provide a detailed step-by-step plan suggestion with multiple options for each date.")
                        item("Consider weather conditions when suggesting places for each date and time to assess how suitable the activity is for the weather.")
                        item("Check detailed information about each place, such as opening hours and reviews, before adding it to the final plan suggestion.")
                    }

                    h2("Tool usage guidelines")
                    +"""
                    ALWAYS use "maps_search_places" tool to search for places, AVOID making your own suggestions.
                    While searching for places, keep search query short and specific:
                    Example DO: "museum", "historical museum", "italian restaurant", "coffee shop", "art gallery"
                    Example DON'T: "interesting cultural sites", "local cuisine restaurants", "restaurant in the city center"
                    """.trimIndent()
                    br()

                    """
                    Use other "maps_*" tools to get more details about the place: reviews, opening hours, distances, etc.
                    """.trimIndent()
                    br()

                    """
                    Use ${
                        weatherTools.asTools().joinToString(", ") { it.name }
                    } tool for each date, requesting hourly granularity when you need to
                    make a detailed itinerary.
                    """.trimIndent()
                }
            }

            when (input) {
                is SuggestPlanRequest.InitialRequest -> {
                    tag("user_plan") {
                        +input.userPlan.toMarkdownString()
                    }
                }

                is SuggestPlanRequest.CorrectionRequest -> {
                    tag("additional_instructions") {
                        +"User asked for corrections to the previously suggested plan. Provide updated plan according to these corrections."
                    }

                    tag("user_plan") {
                        +input.userPlan.toMarkdownString()
                    }

                    tag("previously_suggested_plan") {
                        +input.prevSuggestedPlan.toMarkdownString()
                    }

                    tag("user_feedback") {
                        +input.userFeedback
                    }
                }
            }
        }
    }

    val saveUserPlan by node<TripPlan, Unit> { plan ->
        storage.set(userPlanKey, plan)

        llm.writeSession {
            replaceHistoryWithTLDR(strategy = HistoryCompressionStrategy.WholeHistory)
        }
    }

    val savePrevSuggestedPlan by node<TripPlan, TripPlan> { plan ->
        storage.set(prevSuggestedPlanKey, plan)

        llm.writeSession {
            replaceHistoryWithTLDR(strategy = HistoryCompressionStrategy.WholeHistory)
        }

        plan
    }

    val createInitialPlanRequest by node<Unit, SuggestPlanRequest> {
        SuggestPlanRequest.InitialRequest(
            userPlan = storage.getValue(userPlanKey),
        )
    }

    val createPlanCorrectionRequest by node<String, SuggestPlanRequest> { userFeedback ->
        SuggestPlanRequest.CorrectionRequest(
            userFeedback = userFeedback,
            userPlan = storage.getValue(userPlanKey),
            prevSuggestedPlan = storage.getValue(prevSuggestedPlanKey)
        )
    }

    // Show plan suggestion to the user and get a response
    val showPlanSuggestion by node<String, String> { message ->
        userTools.showMessage(message)
    }

    val processUserFeedback by nodeLLMRequestStructured<PlanSuggestionFeedback>()

    // Edges

    nodeStart then setup then clarifyUserPlan then saveUserPlan then createInitialPlanRequest then suggestPlan then savePrevSuggestedPlan

    edge(
        savePrevSuggestedPlan forwardTo showPlanSuggestion
            transformed { it.toMarkdownString() }
    )

    edge(showPlanSuggestion forwardTo processUserFeedback)

    edge(
        processUserFeedback forwardTo createPlanCorrectionRequest
            transformed { it.getOrThrow().structure }
            onCondition { !it.isAccepted }
            transformed { it.message }
    )
    edge(
        processUserFeedback forwardTo nodeFinish
            transformed { it.getOrThrow().structure }
            onCondition { it.isAccepted }
            transformed { storage.getValue(prevSuggestedPlanKey) }
    )

    edge(createPlanCorrectionRequest forwardTo suggestPlan)
}
