package ai.koog.agents.core.tools;

import ai.koog.agents.annotations.JavaAPI;
import ai.koog.serialization.TypeToken;
import ai.koog.utils.coroutines.CoroutineUtilsKt;
import kotlin.coroutines.Continuation;
import kotlinx.schema.generator.json.JsonSchemaConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Base class representing a tool in Java that can be invoked by the LLM.
 * Tools are usually used to return results, make changes to the environment, or perform other actions.
 * <p>
 * To ensure non-blocking execution, tools are executed asynchronously using an {@link Executor}.
 * You can specify your own executor by using constructors accepting {@link Executor}.
 * By default, each tool instance uses its own single-threaded executor.
 *
 * @param <TArgs>   The type of arguments the tool accepts.
 * @param <TResult> The type of result the tool returns.
 */
@SuppressWarnings("unused")
@JavaAPI
public abstract class JavaTool<TArgs, TResult> extends Tool<TArgs, TResult> {
    private Executor executor = Executors.newSingleThreadExecutor();

    //region Constructors overload boilerplate

    /**
     * Constructor creating an instance of {@link JavaTool}
     *
     * @param argsType   Type token representing arguments type {@link TArgs}.
     * @param resultType Type token representing result type {@link TResult}.
     * @param descriptor A {@link ToolDescriptor} representing the tool's schema, including its name, description, and parameters.
     * @param metadata   A map of arbitrary metadata associated with the tool.
     */
    public JavaTool(
        TypeToken argsType,
        TypeToken resultType,
        ToolDescriptor descriptor,
        Map<String, String> metadata
    ) {
        super(argsType, resultType, descriptor, metadata);
    }

    /**
     * Constructor creating an instance of {@link JavaTool}
     *
     * @param argsType   Type token representing arguments type {@link TArgs}.
     * @param resultType Type token representing result type {@link TResult}.
     * @param descriptor A {@link ToolDescriptor} representing the tool's schema, including its name, description, and parameters.
     * @param metadata   A map of arbitrary metadata associated with the tool.
     * @param executor   The {@link Executor} to use for executing the tool's logic.
     */
    public JavaTool(
        TypeToken argsType,
        TypeToken resultType,
        ToolDescriptor descriptor,
        Map<String, String> metadata,
        Executor executor
    ) {
        super(argsType, resultType, descriptor, metadata);
        this.executor = executor;
    }

    /**
     * Constructor creating an instance of {@link JavaTool}
     *
     * @param argsType   Type token representing arguments type {@link TArgs}.
     * @param resultType Type token representing result type {@link TResult}.
     * @param descriptor A {@link ToolDescriptor} representing the tool's schema, including its name, description, and parameters.
     */
    public JavaTool(
        TypeToken argsType,
        TypeToken resultType,
        ToolDescriptor descriptor
    ) {
        super(argsType, resultType, descriptor);
    }

    /**
     * Constructor creating an instance of {@link JavaTool}
     *
     * @param argsType   Type token representing arguments type {@link TArgs}.
     * @param resultType Type token representing result type {@link TResult}.
     * @param descriptor A {@link ToolDescriptor} representing the tool's schema, including its name, description, and parameters.
     * @param executor   The {@link Executor} to use for executing the tool's logic.
     */
    public JavaTool(
        TypeToken argsType,
        TypeToken resultType,
        ToolDescriptor descriptor,
        Executor executor
    ) {
        super(argsType, resultType, descriptor);
        this.executor = executor;
    }

    /**
     * Convenience constructor for the base tool class that generates {@link ToolDescriptor} from the provided
     * name, description and argsType
     *
     * @param argsType         Type token representing arguments type {@link TArgs}.
     * @param resultType       Type token representing result type {@link TResult}.
     * @param name             The name of the tool.
     * @param description      Textual explanation of what the tool does and how it can be used (for the LLM).
     * @param jsonSchemaConfig Optional custom {@link JsonSchemaConfig} for the tool schema generation.
     */
    public JavaTool(
        TypeToken argsType,
        TypeToken resultType,
        String name,
        String description,
        JsonSchemaConfig jsonSchemaConfig
    ) {
        super(argsType, resultType, name, description, jsonSchemaConfig);
    }

    /**
     * Convenience constructor for the base tool class that generates {@link ToolDescriptor} from the provided
     * name, description and argsType
     *
     * @param argsType         Type token representing arguments type {@link TArgs}.
     * @param resultType       Type token representing result type {@link TResult}.
     * @param name             The name of the tool.
     * @param description      Textual explanation of what the tool does and how it can be used (for the LLM).
     * @param jsonSchemaConfig Optional custom {@link JsonSchemaConfig} for the tool schema generation.
     * @param executor         The {@link Executor} to use for executing the tool's logic.
     */
    public JavaTool(
        TypeToken argsType,
        TypeToken resultType,
        String name,
        String description,
        JsonSchemaConfig jsonSchemaConfig,
        Executor executor
    ) {
        super(argsType, resultType, name, description, jsonSchemaConfig);
        this.executor = executor;
    }

    /**
     * Convenience constructor for the base tool class that generates {@link ToolDescriptor} from the provided
     * name, description and argsType
     *
     * @param argsType    Type token representing arguments type {@link TArgs}.
     * @param resultType  Type token representing result type {@link TResult}.
     * @param name        The name of the tool.
     * @param description Textual explanation of what the tool does and how it can be used (for the LLM).
     */
    public JavaTool(
        TypeToken argsType,
        TypeToken resultType,
        String name,
        String description
    ) {
        super(argsType, resultType, name, description);
    }

    /**
     * Convenience constructor for the base tool class that generates {@link ToolDescriptor} from the provided
     * name, description and argsType
     *
     * @param argsType    Type token representing arguments type {@link TArgs}.
     * @param resultType  Type token representing result type {@link TResult}.
     * @param name        The name of the tool.
     * @param description Textual explanation of what the tool does and how it can be used (for the LLM).
     * @param executor    The {@link Executor} to use for executing the tool's logic.
     */
    public JavaTool(
        TypeToken argsType,
        TypeToken resultType,
        String name,
        String description,
        Executor executor
    ) {
        super(argsType, resultType, name, description);
        this.executor = executor;
    }

    //endregion

    /**
     * Get {@link Executor} instance used to run the tool execute method.
     */
    public Executor getExecutor() {
        return executor;
    }

    @Override
    final public @Nullable Object execute(
        TArgs args,
        @NotNull Continuation<? super TResult> $completion
    ) {
        return CoroutineUtilsKt.withBlocking(
            executor,
            () -> execute(args),
            $completion
        );
    }

    /**
     * Executes the tool's logic with the provided arguments.
     * <p>
     * In the actual agent implementation, it is not recommended to call tools directly as this might cause issues, such as:
     * - Bugs with feature pipelines
     * - Inability to test/mock
     * <p>
     * Consider using methods like `findTool(tool: Class)` or similar, to retrieve a `SafeTool`, and then call `execute`
     * on it. This ensures that the tool call is delegated properly to the underlying `environment` object.
     *
     * @param args The input arguments required to execute the tool.
     * @return The result of the tool's execution.
     */
    public abstract TResult execute(TArgs args);
}
