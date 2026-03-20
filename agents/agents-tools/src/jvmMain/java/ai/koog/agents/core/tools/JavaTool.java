package ai.koog.agents.core.tools;

import ai.koog.agents.annotations.JavaAPI;
import ai.koog.serialization.TypeToken;
import ai.koog.utils.coroutines.CoroutineUtilsKt;
import kotlin.coroutines.Continuation;
import kotlinx.schema.generator.json.JsonSchemaConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/*
  Java wrapper over regular Tool class to provide non-suspendable abstract `execute`.
*/

/**
 * Base class representing a tool in Java that can be invoked by the LLM.
 * Tools are usually used to return results, make changes to the environment, or perform other actions.
 *
 * @param <TArgs>   The type of arguments the tool accepts.
 * @param <TResult> The type of result the tool returns.
 */
@SuppressWarnings("unused")
@JavaAPI
public abstract class JavaTool<TArgs, TResult> extends Tool<TArgs, TResult> {

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
     */
    public JavaTool(
        TypeToken argsType,
        TypeToken resultType,
        ToolDescriptor descriptor
    ) {
        super(argsType, resultType, descriptor);
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

    @Override
    final public @Nullable TResult execute(
        TArgs args,
        @NotNull Continuation<? super TResult> $completion
    ) {
        // FIXME schedule on a different dispatcher to avoid blocking
        return execute(args);
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
