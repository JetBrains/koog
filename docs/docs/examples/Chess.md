# Building an AI Chess Player with Koog Framework

[:material-github: Open on GitHub](
https://github.com/JetBrains/koog/blob/develop/examples/notebooks/Chess.ipynb
){ .md-button .md-button--primary }
[:material-download: Download .ipynb](
https://raw.githubusercontent.com/JetBrains/koog/develop/examples/notebooks/Chess.ipynb
){ .md-button }

This tutorial demonstrates how to build an intelligent chess-playing agent using the Koog framework. We'll explore key concepts including tool integration, agent strategies, memory optimization, and interactive AI decision-making.

## What You'll Learn

- How to model domain-specific data structures for complex games
- Creating custom tools that agents can use to interact with the environment
- Implementing efficient agent strategies with memory management
- Building interactive AI systems with choice selection capabilities
- Optimizing agent performance for turn-based games

## Setup

First, let's import the Koog framework and set up our development environment:


```kotlin
%useLatestDescriptors
%use koog
```

## Modeling the Chess Domain

Creating a robust domain model is essential for any game AI. In chess, we need to represent players, pieces, and their relationships. Let's start by defining our core data structures:

### Core Enums and Types


```kotlin
enum class Player {
    White, Black, None;

    fun opponent(): Player = when (this) {
        White -> Black
        Black -> White
        None -> throw IllegalArgumentException("No opponent for None player")
    }
}

enum class PieceType(val id: Char) {
    King('K'), Queen('Q'), Rook('R'),
    Bishop('B'), Knight('N'), Pawn('P'), None('*');

    companion object {
        fun fromId(id: String): PieceType {
            require(id.length == 1) { "Invalid piece id: $id" }

            return entries.first { it.id == id.single() }
        }
    }
}

enum class Side {
    King, Queen
}
```

The `Player` enum represents the two sides in chess, with an `opponent()` method for easy switching between players. The `PieceType` enum maps each chess piece to its standard notation character, enabling easy parsing of chess moves.

The `Side` enum helps distinguish between kingside and queenside castling moves.

### Piece and Position Modeling


```kotlin
data class Piece(val pieceType: PieceType, val player: Player) {
    init {
        require((pieceType == PieceType.None) == (player == Player.None)) {
            "Invalid piece: $pieceType $player"
        }
    }

    fun toChar(): Char = when (player) {
        Player.White -> pieceType.id.uppercaseChar()
        Player.Black -> pieceType.id.lowercaseChar()
        Player.None -> pieceType.id
    }

    fun isNone(): Boolean = pieceType == PieceType.None

    companion object {
        val None = Piece(PieceType.None, Player.None)
    }
}

data class Position(val row: Int, val col: Char) {
    init {
        require(row in 1..8 && col in 'a'..'h') { "Invalid position: $col$row" }
    }

    constructor(position: String) : this(
        position[1].digitToIntOrNull() ?: throw IllegalArgumentException("Incorrect position: $position"),
        position[0],
    ) {
        require(position.length == 2) { "Invalid position: $position" }
    }
}

class ChessBoard {
    private val backRow = listOf(
        PieceType.Rook, PieceType.Knight, PieceType.Bishop,
        PieceType.Queen, PieceType.King,
        PieceType.Bishop, PieceType.Knight, PieceType.Rook
    )

    private val board: List<MutableList<Piece>> = listOf(
        backRow.map { Piece(it, Player.Black) }.toMutableList(),
        List(8) { Piece(PieceType.Pawn, Player.Black) }.toMutableList(),
        List(8) { Piece.None }.toMutableList(),
        List(8) { Piece.None }.toMutableList(),
        List(8) { Piece.None }.toMutableList(),
        List(8) { Piece.None }.toMutableList(),
        List(8) { Piece(PieceType.Pawn, Player.White) }.toMutableList(),
        backRow.map { Piece(it, Player.White) }.toMutableList()
    )

    override fun toString(): String = board
        .withIndex().joinToString("\n") { (index, row) ->
            "${8 - index} ${row.map { it.toChar() }.joinToString(" ")}"
        } + "\n  a b c d e f g h"

    fun getPiece(position: Position): Piece = board[8 - position.row][position.col - 'a']
    fun setPiece(position: Position, piece: Piece) {
        board[8 - position.row][position.col - 'a'] = piece
    }
}
```

The `Piece` data class combines a piece type with its owner, using uppercase letters for white pieces and lowercase for black pieces in the visual representation. The `Position` class encapsulates chess coordinates (e.g., "e4") with built-in validation.

## Game State Management

### ChessBoard Implementation

The `ChessBoard` class manages the 8×8 grid and piece positions. Key design decisions include:

- **Internal Representation**: Uses a list of mutable lists for efficient access and modification
- **Visual Display**: The `toString()` method provides a clear ASCII representation with rank numbers and file letters
- **Position Mapping**: Converts between chess notation (a1-h8) and internal array indices

### ChessGame Logic


```kotlin
/**
 * Simple chess game without checks for valid moves.
 * Stores a correct state of the board if the entered moves are valid
 */
class ChessGame {
    private val board: ChessBoard = ChessBoard()
    private var currentPlayer: Player = Player.White
    val moveNotation: String = """
        0-0 - short castle
        0-0-0 - long castle
        <piece>-<from>-<to> - usual move. e.g. p-e2-e4
        <piece>-<from>-<to>-<promotion> - promotion move. e.g. p-e7-e8-q.
        Piece names:
            p - pawn
            n - knight
            b - bishop
            r - rook
            q - queen
            k - king
    """.trimIndent()

    fun move(move: String) {
        when {
            move == "0-0" -> castleMove(Side.King)
            move == "0-0-0" -> castleMove(Side.Queen)
            move.split("-").size == 3 -> {
                val (_, from, to) = move.split("-")
                usualMove(Position(from), Position(to))
            }

            move.split("-").size == 4 -> {
                val (piece, from, to, promotion) = move.split("-")

                require(PieceType.fromId(piece) == PieceType.Pawn) { "Only pawn can be promoted" }

                usualMove(Position(from), Position(to))
                board.setPiece(Position(to), Piece(PieceType.fromId(promotion), currentPlayer))
            }

            else -> throw IllegalArgumentException("Invalid move: $move")
        }

        updateCurrentPlayer()
    }

    fun getBoard(): String = board.toString()
    fun currentPlayer(): String = currentPlayer.name.lowercase()

    private fun updateCurrentPlayer() {
        currentPlayer = currentPlayer.opponent()
    }

    private fun usualMove(from: Position, to: Position) {
        if (board.getPiece(from).pieceType == PieceType.Pawn && from.col != to.col && board.getPiece(to).isNone()) {
            // the move is en passant
            board.setPiece(Position(from.row, to.col), Piece.None)
        }

        movePiece(from, to)
    }

    private fun castleMove(side: Side) {
        val row = if (currentPlayer == Player.White) 1 else 8
        val kingFrom = Position(row, 'e')
        val (rookFrom, kingTo, rookTo) = if (side == Side.King) {
            Triple(Position(row, 'h'), Position(row, 'g'), Position(row, 'f'))
        } else {
            Triple(Position(row, 'a'), Position(row, 'c'), Position(row, 'd'))
        }

        movePiece(kingFrom, kingTo)
        movePiece(rookFrom, rookTo)
    }

    private fun movePiece(from: Position, to: Position) {
        board.setPiece(to, board.getPiece(from))
        board.setPiece(from, Piece.None)
    }
}
```

The `ChessGame` class orchestrates the game logic and maintains state. Notable features include:

- **Move Notation Support**: Accepts standard chess notation for regular moves, castling (0-0, 0-0-0), and pawn promotion
- **Special Move Handling**: Implements en passant capture and castling logic
- **Turn Management**: Automatically alternates between players after each move
- **Validation**: While it doesn't validate move legality (trusting the AI to make valid moves), it handles move parsing and state updates correctly

The `moveNotation` string provides clear documentation for the AI agent on acceptable move formats.

## Integrating with Koog Framework

### Creating Custom Tools


```kotlin
import kotlinx.serialization.Serializable

class Move(val game: ChessGame) : SimpleTool<Move.Args>() {
    @Serializable
    data class Args(val notation: String) : ToolArgs

    override val argsSerializer = Args.serializer()

    override val descriptor = ToolDescriptor(
        name = "move",
        description = "Moves a piece according to the notation:\n${game.moveNotation}",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "notation",
                description = "The notation of the piece to move",
                type = ToolParameterType.String,
            )
        )
    )

    override suspend fun doExecute(args: Args): String {
        game.move(args.notation)
        println(game.getBoard())
        println("-----------------")
        return "Current state of the game:\n${game.getBoard()}\n${game.currentPlayer()} to move! Make the move!"
    }
}
```

The `Move` tool demonstrates the Koog framework's tool integration pattern:

1. **Extends SimpleTool**: Inherits the basic tool functionality with type-safe argument handling
2. **Serializable Arguments**: Uses Kotlin serialization to define the tool's input parameters
3. **Rich Documentation**: The `ToolDescriptor` provides the LLM with detailed information about the tool's purpose and parameters
4. **Execution Logic**: The `doExecute` method handles the actual move execution and provides formatted feedback

Key design aspects:
- **Context Injection**: The tool receives the `ChessGame` instance, allowing it to modify game state
- **Feedback Loop**: Returns the current board state and prompts the next player, maintaining conversational flow
- **Error Handling**: Relies on the game class for move validation and error reporting

## Agent Strategy Design

### Memory Optimization Technique


```kotlin
import ai.koog.agents.core.environment.ReceivedToolResult

/**
 * Chess position is (almost) completely defined by the board state,
 * So we can trim the history of the LLM to only contain the system prompt and the last move.
 */
inline fun <reified T> AIAgentSubgraphBuilderBase<*, *>.nodeTrimHistory(
    name: String? = null
): AIAgentNodeDelegate<T, T> = node(name) { result ->
    llm.writeSession {
        rewritePrompt { prompt ->
            val messages = prompt.messages

            prompt.copy(messages = listOf(messages.first(), messages.last()))
        }
    }

    result
}

val strategy = strategy<String, String>("chess_strategy") {
    val nodeCallLLM by nodeLLMRequest("sendInput")
    val nodeExecuteTool by nodeExecuteTool("nodeExecuteTool")
    val nodeSendToolResult by nodeLLMSendToolResult("nodeSendToolResult")
    val nodeTrimHistory by nodeTrimHistory<ReceivedToolResult>()

    edge(nodeStart forwardTo nodeCallLLM)
    edge(nodeCallLLM forwardTo nodeExecuteTool onToolCall { true })
    edge(nodeCallLLM forwardTo nodeFinish onAssistantMessage { true })
    edge(nodeExecuteTool forwardTo nodeTrimHistory)
    edge(nodeTrimHistory forwardTo nodeSendToolResult)
    edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
    edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
}
```

The `nodeTrimHistory` function implements a crucial optimization for chess games. Since chess positions are largely determined by the current board state rather than the full move history, we can significantly reduce token usage by keeping only:

1. **System Prompt**: Contains the agent's core instructions and behavior guidelines
2. **Latest Message**: The most recent board state and game context

This approach:
- **Reduces Token Consumption**: Prevents exponential growth of conversation history
- **Maintains Context**: Preserves essential game state information
- **Improves Performance**: Faster processing with shorter prompts
- **Enables Long Games**: Allows for extended gameplay without hitting token limits

The chess strategy demonstrates Koog's graph-based agent architecture:

**Node Types:**
- `nodeCallLLM`: Processes input and generates responses/tool calls
- `nodeExecuteTool`: Executes the Move tool with the provided parameters
- `nodeTrimHistory`: Optimizes conversation memory as described above
- `nodeSendToolResult`: Sends tool execution results back to the LLM

**Control Flow:**
- **Linear Path**: Start → LLM Request → Tool Execution → History Trim → Send Result
- **Decision Points**: LLM responses can either finish the conversation or trigger another tool call
- **Memory Management**: History trimming occurs after each tool execution

This strategy ensures efficient, stateful gameplay while maintaining conversational coherence.

### Setting up the AI Agent


```kotlin
val baseExecutor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY"))
```

This section initializes our OpenAI executor. The `simpleOpenAIExecutor` creates a connection to OpenAI's API using your API key from environment variables.

**Configuration Notes:**
- Store your OpenAI API key in the `OPENAI_API_KEY` environment variable
- The executor handles authentication and API communication automatically
- Different executor types are available for various LLM providers

### Agent Assembly


```kotlin
val game = ChessGame()
val toolRegistry = ToolRegistry { tools(listOf(Move(game))) }

// Create a chat agent with a system prompt and the tool registry
val agent = AIAgent(
    executor = baseExecutor,
    strategy = strategy,
    llmModel = OpenAIModels.Chat.O3Mini,
    systemPrompt = """
            You are an agent who plays chess.
            You should always propose a move in response to the "Your move!" message.

            DO NOT HALLUCINATE!!!
            DO NOT PLAY ILLEGAL MOVES!!!
            YOU CAN SEND A MESSAGE ONLY IF IT IS A RESIGNATION OR A CHECKMATE!!!
        """.trimMargin(),
    temperature = 0.0,
    toolRegistry = toolRegistry,
    maxIterations = 200,
)
```

Here we assemble all components into a functional chess-playing agent:

**Key Configuration:**

- **Model Choice**: Using `OpenAIModels.Chat.O3Mini` for high-quality chess play
- **Temperature**: Set to 0.0 for deterministic, strategic moves
- **System Prompt**: Carefully crafted instructions emphasizing legal moves and proper behavior
- **Tool Registry**: Provides the agent access to the Move tool
- **Max Iterations**: Set to 200 to allow for complete games

**System Prompt Design:**
- Emphasizes move proposal responsibility
- Prohibits hallucination and illegal moves
- Restricts messaging to only resignations or checkmate declarations
- Creates focused, game-oriented behavior

### Running the Basic Agent


```kotlin
import kotlinx.coroutines.runBlocking

println("Chess Game started!")

val initialMessage = "Starting position is ${game.getBoard()}. White to move!"

runBlocking {
    agent.run(initialMessage)
}
```

    Chess Game started!
    8 r n b q k b n r
    7 p p p p p p p p
    6 * * * * * * * *
    5 * * * * * * * *
    4 * * * * P * * *
    3 * * * * * * * *
    2 P P P P * P P P
    1 R N B Q K B N R
      a b c d e f g h
    -----------------
    8 r n b q k b n r
    7 p p p p * p p p
    6 * * * * * * * *
    5 * * * * p * * *
    4 * * * * P * * *
    3 * * * * * * * *
    2 P P P P * P P P
    1 R N B Q K B N R
      a b c d e f g h
    -----------------
    8 r n b q k b n r
    7 p p p p * p p p
    6 * * * * * * * *
    5 * * * * p * * *
    4 * * * * P * * *
    3 * * * * * N * *
    2 P P P P * P P P
    1 R N B Q K B * R
      a b c d e f g h
    -----------------
    8 r n b q k b * r
    7 p p p p * p p p
    6 * * * * * n * *
    5 * * * * p * * *
    4 * * * * P * * *
    3 * * * * * N * *
    2 P P P P * P P P
    1 R N B Q K B * R
      a b c d e f g h
    -----------------
    8 r n b q k b * r
    7 p p p p * p p p
    6 * * * * * n * *
    5 * * * * p * * *
    4 * * * * P * * *
    3 * * N * * N * *
    2 P P P P * P P P
    1 R * B Q K B * R
      a b c d e f g h
    -----------------



    The execution was interrupted


This basic agent plays autonomously, making moves automatically. The game output shows the sequence of moves and board states as the AI plays against itself.

## Advanced Feature: Interactive Choice Selection

The next sections demonstrate a more sophisticated approach where users can participate in the AI's decision-making process by choosing from multiple AI-generated moves.

### Custom Choice Selection Strategy


```kotlin
import ai.koog.agents.core.feature.choice.ChoiceSelectionStrategy

/**
 * `AskUserChoiceStrategy` allows users to interactively select a choice from a list of options
 * presented by a language model. The strategy uses customizable methods to display the prompt
 * and choices and read user input to determine the selected choice.
 *
 * @property promptShowToUser A function that formats and displays a given `Prompt` to the user.
 * @property choiceShowToUser A function that formats and represents a given `LLMChoice` to the user.
 * @property print A function responsible for displaying messages to the user, e.g., for showing prompts or feedback.
 * @property read A function to capture user input.
 */
class AskUserChoiceSelectionStrategy(
    private val promptShowToUser: (Prompt) -> String = { "Current prompt: $it" },
    private val choiceShowToUser: (LLMChoice) -> String = { "$it" },
    private val print: (String) -> Unit = ::println,
    private val read: () -> String? = ::readlnOrNull
) : ChoiceSelectionStrategy {
    override suspend fun choose(prompt: Prompt, choices: List<LLMChoice>): LLMChoice {
        print(promptShowToUser(prompt))

        print("Available LLM choices")

        choices.withIndex().forEach { (index, choice) ->
            print("Choice number ${index + 1}: ${choiceShowToUser(choice)}")
        }

        var choiceNumber = ask(choices.size)
        while (choiceNumber == null) {
            print("Invalid response.")
            choiceNumber = ask(choices.size)
        }

        return choices[choiceNumber - 1]
    }

    private fun ask(numChoices: Int): Int? {
        print("Please choose a choice. Enter a number between 1 and $numChoices: ")

        return read()?.toIntOrNull()?.takeIf { it in 1..numChoices }
    }
}
```

The `AskUserChoiceSelectionStrategy` implements Koog's `ChoiceSelectionStrategy` interface to enable human participation in AI decision-making:

**Key Features:**
- **Customizable Display**: Functions for formatting prompts and choices
- **Interactive Input**: Uses standard input/output for user interaction
- **Validation**: Ensures user input is within valid range
- **Flexible I/O**: Configurable print and read functions for different environments

**Use Cases:**
- Human-AI collaboration in gameplay
- AI decision transparency and explainability
- Training and debugging scenarios
- Educational demonstrations

### Enhanced Strategy with Choice Selection


```kotlin
inline fun <reified T> AIAgentSubgraphBuilderBase<*, *>.nodeTrimHistory(
    name: String? = null
): AIAgentNodeDelegate<T, T> = node(name) { result ->
    llm.writeSession {
        rewritePrompt { prompt ->
            val messages = prompt.messages

            prompt.copy(messages = listOf(messages.first(), messages.last()))
        }
    }

    result
}

val strategy = strategy<String, String>("chess_strategy") {
    val nodeCallLLM by nodeLLMRequest("sendInput")
    val nodeExecuteTool by nodeExecuteTool("nodeExecuteTool")
    val nodeSendToolResult by nodeLLMSendToolResult("nodeSendToolResult")
    val nodeTrimHistory by nodeTrimHistory<ReceivedToolResult>()

    edge(nodeStart forwardTo nodeCallLLM)
    edge(nodeCallLLM forwardTo nodeExecuteTool onToolCall { true })
    edge(nodeCallLLM forwardTo nodeFinish onAssistantMessage { true })
    edge(nodeExecuteTool forwardTo nodeTrimHistory)
    edge(nodeTrimHistory forwardTo nodeSendToolResult)
    edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
    edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
}

val askChoiceStrategy = AskUserChoiceSelectionStrategy(promptShowToUser = { prompt ->
    val lastMessage = prompt.messages.last()
    if (lastMessage is Message.Tool.Call) {
        lastMessage.content
    } else {
        ""
    }
})
```


```kotlin
val promptExecutor = PromptExecutorWithChoiceSelection(baseExecutor, askChoiceStrategy)
```

The first interactive approach uses `PromptExecutorWithChoiceSelection`, which wraps the base executor with choice selection capability. The custom display function extracts move information from tool calls to show users what the AI wants to do.

**Architecture Changes:**
- **Wrapped Executor**: `PromptExecutorWithChoiceSelection` adds choice functionality to any base executor
- **Context-Aware Display**: Shows the last tool call content instead of the full prompt
- **Higher Temperature**: Increased to 1.0 for more diverse move options

### Advanced Strategy: Manual Choice Selection


```kotlin
val game = ChessGame()
val toolRegistry = ToolRegistry { tools(listOf(Move(game))) }

val agent = AIAgent(
    executor = promptExecutor,
    strategy = strategy,
    llmModel = OpenAIModels.Chat.O3Mini,
    systemPrompt = """
            You are an agent who plays chess.
            You should always propose a move in response to the "Your move!" message.

            DO NOT HALLUCINATE!!!
            DO NOT PLAY ILLEGAL MOVES!!!
            YOU CAN SEND A MESSAGE ONLY IF IT IS A RESIGNATION OR A CHECKMATE!!!
        """.trimMargin(),
    temperature = 1.0,
    toolRegistry = toolRegistry,
    maxIterations = 200,
    numberOfChoices = 3,
)
```

The advanced strategy integrates choice selection directly into the agent's execution graph:

**New Nodes:**
- `nodeLLMSendResultsMultipleChoices`: Handles multiple LLM choices simultaneously
- `nodeSelectLLMChoice`: Integrates the choice selection strategy into the workflow

**Enhanced Control Flow:**
- Tool results are wrapped in lists to support multiple choices
- User selection occurs before continuing with the chosen path
- The selected choice is unwrapped and continues through the normal flow

**Benefits:**
- **Greater Control**: Fine-grained integration with agent workflow
- **Flexibility**: Can be combined with other agent features
- **Transparency**: Users see exactly what the AI is considering

### Running Interactive Agents


```kotlin
println("Chess Game started!")

val initialMessage = "Starting position is ${game.getBoard()}. White to move!"

runBlocking {
    agent.run(initialMessage)
}
```

    Chess Game started!
    
    Available LLM choices
    Choice number 1: [Call(id=call_K46Upz7XoBIG5RchDh7bZE8F, tool=move, content={"notation": "p-e2-e4"}, metaInfo=ResponseMetaInfo(timestamp=2025-08-18T21:17:40.368252Z, totalTokensCount=773, inputTokensCount=315, outputTokensCount=458, additionalInfo={}))]
    Choice number 2: [Call(id=call_zJ6OhoCHrVHUNnKaxZkOhwoU, tool=move, content={"notation": "p-e2-e4"}, metaInfo=ResponseMetaInfo(timestamp=2025-08-18T21:17:40.368252Z, totalTokensCount=773, inputTokensCount=315, outputTokensCount=458, additionalInfo={}))]
    Choice number 3: [Call(id=call_nwX6ZMJ3F5AxiNUypYlI4BH4, tool=move, content={"notation": "p-e2-e4"}, metaInfo=ResponseMetaInfo(timestamp=2025-08-18T21:17:40.368252Z, totalTokensCount=773, inputTokensCount=315, outputTokensCount=458, additionalInfo={}))]
    Please choose a choice. Enter a number between 1 and 3: 
    8 r n b q k b n r
    7 p p p p p p p p
    6 * * * * * * * *
    5 * * * * * * * *
    4 * * * * P * * *
    3 * * * * * * * *
    2 P P P P * P P P
    1 R N B Q K B N R
      a b c d e f g h
    -----------------
    
    Available LLM choices
    Choice number 1: [Call(id=call_2V93GXOcIe0fAjUAIFEk9h5S, tool=move, content={"notation": "p-e7-e5"}, metaInfo=ResponseMetaInfo(timestamp=2025-08-18T21:17:47.949303Z, totalTokensCount=1301, inputTokensCount=341, outputTokensCount=960, additionalInfo={}))]
    Choice number 2: [Call(id=call_INM59xRzKMFC1w8UAV74l9e1, tool=move, content={"notation": "p-e7-e5"}, metaInfo=ResponseMetaInfo(timestamp=2025-08-18T21:17:47.949303Z, totalTokensCount=1301, inputTokensCount=341, outputTokensCount=960, additionalInfo={}))]
    Choice number 3: [Call(id=call_r4QoiTwn0F3jizepHH5ia8BU, tool=move, content={"notation": "p-e7-e5"}, metaInfo=ResponseMetaInfo(timestamp=2025-08-18T21:17:47.949303Z, totalTokensCount=1301, inputTokensCount=341, outputTokensCount=960, additionalInfo={}))]
    Please choose a choice. Enter a number between 1 and 3: 
    8 r n b q k b n r
    7 p p p p * p p p
    6 * * * * * * * *
    5 * * * * p * * *
    4 * * * * P * * *
    3 * * * * * * * *
    2 P P P P * P P P
    1 R N B Q K B N R
      a b c d e f g h
    -----------------
    
    Available LLM choices
    Choice number 1: [Call(id=call_f9XTizn41svcrtvnmkCfpSUQ, tool=move, content={"notation": "n-g1-f3"}, metaInfo=ResponseMetaInfo(timestamp=2025-08-18T21:17:55.467712Z, totalTokensCount=917, inputTokensCount=341, outputTokensCount=576, additionalInfo={}))]
    Choice number 2: [Call(id=call_c0Dfce5RcSbN3cOOm5ESYriK, tool=move, content={"notation": "n-g1-f3"}, metaInfo=ResponseMetaInfo(timestamp=2025-08-18T21:17:55.467712Z, totalTokensCount=917, inputTokensCount=341, outputTokensCount=576, additionalInfo={}))]
    Choice number 3: [Call(id=call_Lr4Mdro1iolh0fDyAwZsutrW, tool=move, content={"notation": "n-g1-f3"}, metaInfo=ResponseMetaInfo(timestamp=2025-08-18T21:17:55.467712Z, totalTokensCount=917, inputTokensCount=341, outputTokensCount=576, additionalInfo={}))]
    Please choose a choice. Enter a number between 1 and 3: 
    8 r n b q k b n r
    7 p p p p * p p p
    6 * * * * * * * *
    5 * * * * p * * *
    4 * * * * P * * *
    3 * * * * * N * *
    2 P P P P * P P P
    1 R N B Q K B * R
      a b c d e f g h
    -----------------



    The execution was interrupted



```kotlin
import ai.koog.agents.core.feature.choice.nodeLLMSendResultsMultipleChoices
import ai.koog.agents.core.feature.choice.nodeSelectLLMChoice

inline fun <reified T> AIAgentSubgraphBuilderBase<*, *>.nodeTrimHistory(
    name: String? = null
): AIAgentNodeDelegate<T, T> = node(name) { result ->
    llm.writeSession {
        rewritePrompt { prompt ->
            val messages = prompt.messages

            prompt.copy(messages = listOf(messages.first(), messages.last()))
        }
    }

    result
}

val strategy = strategy<String, String>("chess_strategy") {
    val nodeCallLLM by nodeLLMRequest("sendInput")
    val nodeExecuteTool by nodeExecuteTool("nodeExecuteTool")
    val nodeSendToolResult by nodeLLMSendResultsMultipleChoices("nodeSendToolResult")
    val nodeSelectLLMChoice by nodeSelectLLMChoice(askChoiceStrategy, "chooseLLMChoice")
    val nodeTrimHistory by nodeTrimHistory<ReceivedToolResult>()

    edge(nodeStart forwardTo nodeCallLLM)
    edge(nodeCallLLM forwardTo nodeExecuteTool onToolCall { true })
    edge(nodeCallLLM forwardTo nodeFinish onAssistantMessage { true })
    edge(nodeExecuteTool forwardTo nodeTrimHistory)
    edge(nodeTrimHistory forwardTo nodeSendToolResult transformed { listOf(it) })
    edge(nodeSendToolResult forwardTo nodeSelectLLMChoice)
    edge(nodeSelectLLMChoice forwardTo nodeFinish transformed { it.first() } onAssistantMessage { true })
    edge(nodeSelectLLMChoice forwardTo nodeExecuteTool transformed { it.first() } onToolCall { true })
}
```


```kotlin
val game = ChessGame()
val toolRegistry = ToolRegistry { tools(listOf(Move(game))) }

val agent = AIAgent(
    executor = baseExecutor,
    strategy = strategy,
    llmModel = OpenAIModels.Chat.O3Mini,
    systemPrompt = """
            You are an agent who plays chess.
            You should always propose a move in response to the "Your move!" message.

            DO NOT HALLUCINATE!!!
            DO NOT PLAY ILLEGAL MOVES!!!
            YOU CAN SEND A MESSAGE ONLY IF IT IS A RESIGNATION OR A CHECKMATE!!!
        """.trimMargin(),
    temperature = 1.0,
    toolRegistry = toolRegistry,
    maxIterations = 200,
    numberOfChoices = 3,
)
```


```kotlin
println("Chess Game started!")

val initialMessage = "Starting position is ${game.getBoard()}. White to move!"

runBlocking {
    agent.run(initialMessage)
}
```

    Chess Game started!
    8 r n b q k b n r
    7 p p p p p p p p
    6 * * * * * * * *
    5 * * * * * * * *
    4 * * * * P * * *
    3 * * * * * * * *
    2 P P P P * P P P
    1 R N B Q K B N R
      a b c d e f g h
    -----------------
    
    Available LLM choices
    Choice number 1: [Call(id=call_gqMIar0z11CyUl5nup3zbutj, tool=move, content={"notation": "p-e7-e5"}, metaInfo=ResponseMetaInfo(timestamp=2025-08-18T21:18:17.313548Z, totalTokensCount=917, inputTokensCount=341, outputTokensCount=576, additionalInfo={}))]
    Choice number 2: [Call(id=call_6niUGnZPPJILRFODIlJsCKax, tool=move, content={"notation": "p-e7-e5"}, metaInfo=ResponseMetaInfo(timestamp=2025-08-18T21:18:17.313548Z, totalTokensCount=917, inputTokensCount=341, outputTokensCount=576, additionalInfo={}))]
    Choice number 3: [Call(id=call_q1b8ZmIBph0EoVaU3Ic9A09j, tool=move, content={"notation": "p-e7-e5"}, metaInfo=ResponseMetaInfo(timestamp=2025-08-18T21:18:17.313548Z, totalTokensCount=917, inputTokensCount=341, outputTokensCount=576, additionalInfo={}))]
    Please choose a choice. Enter a number between 1 and 3: 
    8 r n b q k b n r
    7 p p p p * p p p
    6 * * * * * * * *
    5 * * * * p * * *
    4 * * * * P * * *
    3 * * * * * * * *
    2 P P P P * P P P
    1 R N B Q K B N R
      a b c d e f g h
    -----------------
    
    Available LLM choices
    Choice number 1: [Call(id=call_pdBIX7MVi82MyWwawTm1Q2ef, tool=move, content={"notation": "n-g1-f3"}, metaInfo=ResponseMetaInfo(timestamp=2025-08-18T21:18:24.505344Z, totalTokensCount=1237, inputTokensCount=341, outputTokensCount=896, additionalInfo={}))]
    Choice number 2: [Call(id=call_oygsPHaiAW5OM6pxhXhtazgp, tool=move, content={"notation": "n-g1-f3"}, metaInfo=ResponseMetaInfo(timestamp=2025-08-18T21:18:24.505344Z, totalTokensCount=1237, inputTokensCount=341, outputTokensCount=896, additionalInfo={}))]
    Choice number 3: [Call(id=call_GJTEsZ8J8cqOKZW4Tx54RqCh, tool=move, content={"notation": "n-g1-f3"}, metaInfo=ResponseMetaInfo(timestamp=2025-08-18T21:18:24.505344Z, totalTokensCount=1237, inputTokensCount=341, outputTokensCount=896, additionalInfo={}))]
    Please choose a choice. Enter a number between 1 and 3: 
    8 r n b q k b n r
    7 p p p p * p p p
    6 * * * * * * * *
    5 * * * * p * * *
    4 * * * * P * * *
    3 * * * * * N * *
    2 P P P P * P P P
    1 R N B Q K B * R
      a b c d e f g h
    -----------------
    
    Available LLM choices
    Choice number 1: [Call(id=call_5C7HdlTU4n3KdXcyNogE4rGb, tool=move, content={"notation": "n-g8-f6"}, metaInfo=ResponseMetaInfo(timestamp=2025-08-18T21:18:34.646667Z, totalTokensCount=1621, inputTokensCount=341, outputTokensCount=1280, additionalInfo={}))]
    Choice number 2: [Call(id=call_EjCcyeMLQ88wMa5yh3vmeJ2w, tool=move, content={"notation": "n-g8-f6"}, metaInfo=ResponseMetaInfo(timestamp=2025-08-18T21:18:34.646667Z, totalTokensCount=1621, inputTokensCount=341, outputTokensCount=1280, additionalInfo={}))]
    Choice number 3: [Call(id=call_NBMMSwmFIa8M6zvfbPw85NKh, tool=move, content={"notation": "n-g8-f6"}, metaInfo=ResponseMetaInfo(timestamp=2025-08-18T21:18:34.646667Z, totalTokensCount=1621, inputTokensCount=341, outputTokensCount=1280, additionalInfo={}))]
    Please choose a choice. Enter a number between 1 and 3: 
    8 r n b q k b * r
    7 p p p p * p p p
    6 * * * * * n * *
    5 * * * * p * * *
    4 * * * * P * * *
    3 * * * * * N * *
    2 P P P P * P P P
    1 R N B Q K B * R
      a b c d e f g h
    -----------------



    The execution was interrupted


The interactive examples show how users can guide the AI's decision-making process. In the output, you can see:

1. **Multiple Choices**: The AI generates 3 different move options
2. **User Selection**: Users input numbers 1-3 to choose their preferred move
3. **Game Continuation**: The selected move is executed and the game continues

## Conclusion

This tutorial demonstrates several key aspects of building intelligent agents with the Koog framework:

### Key Takeaways

1. **Domain Modeling**: Well-structured data models are crucial for complex applications
2. **Tool Integration**: Custom tools enable agents to interact with external systems effectively
3. **Memory Management**: Strategic history trimming optimizes performance for long interactions
4. **Strategy Graphs**: Koog's graph-based approach provides flexible control flow
5. **Interactive AI**: Choice selection enables human-AI collaboration and transparency

### Framework Features Explored

- ✅ Custom tool creation and integration
- ✅ Agent strategy design and graph-based control flow
- ✅ Memory optimization techniques
- ✅ Interactive choice selection
- ✅ Multiple LLM response handling
- ✅ Stateful game management

The Koog framework provides the foundation for building sophisticated AI agents that can handle complex, multi-turn interactions while maintaining efficiency and transparency.
