package ai.koog.agents.testing

import ai.koog.agents.core.tools.*
import kotlinx.serialization.Serializable

/**
 * Shared test tools for use across test suites to reduce duplication.
 * These provide common tool scenarios for testing permissions, execution, and filtering.
 */
public object TestTools {
    
    /**
     * Simple read tool for testing file access permissions
     */
    public class ReadTool : SimpleTool<ReadTool.Args>() {
        @Serializable
        public data class Args(val path: String) : ToolArgs
        
        override val argsSerializer: kotlinx.serialization.KSerializer<Args> = Args.serializer()
        override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "read",
            description = "Read a file",
            requiredParameters = listOf(
                ToolParameterDescriptor("path", "File path", ToolParameterType.String)
            )
        )
        
        override suspend fun doExecute(args: Args): String = "Contents of ${args.path}: [file contents here]"
    }
    
    /**
     * Simple write tool for testing file modification permissions
     */
    public class WriteTool : SimpleTool<WriteTool.Args>() {
        @Serializable
        public data class Args(val path: String, val content: String) : ToolArgs
        
        override val argsSerializer: kotlinx.serialization.KSerializer<Args> = Args.serializer()
        override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "write",
            description = "Write to a file", 
            requiredParameters = listOf(
                ToolParameterDescriptor("path", "File path", ToolParameterType.String),
                ToolParameterDescriptor("content", "Content to write", ToolParameterType.String)
            )
        )
        
        override suspend fun doExecute(args: Args): String = "Wrote ${args.content.length} characters to ${args.path}"
    }
    
    /**
     * Simple delete tool for testing destructive operation permissions
     */
    public class DeleteTool : SimpleTool<DeleteTool.Args>() {
        @Serializable
        public data class Args(val path: String) : ToolArgs
        
        override val argsSerializer: kotlinx.serialization.KSerializer<Args> = Args.serializer()
        override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "delete",
            description = "Delete a file",
            requiredParameters = listOf(
                ToolParameterDescriptor("path", "File path", ToolParameterType.String)
            )
        )
        
        override suspend fun doExecute(args: Args): String = "Deleted ${args.path}"
    }
    
    /**
     * Generic test tool for simple message testing
     */
    public class TestTool : SimpleTool<TestTool.Args>() {
        @Serializable
        public data class Args(val message: String) : ToolArgs
        
        override val argsSerializer: kotlinx.serialization.KSerializer<Args> = Args.serializer()
        override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "test_tool",
            description = "Generic test tool",
            requiredParameters = listOf(
                ToolParameterDescriptor("message", "Test message", ToolParameterType.String)
            )
        )
        
        override suspend fun doExecute(args: Args): String = "Executed: ${args.message}"
    }
    
    /**
     * Public tool accessible to everyone (no restrictions)
     */
    public class PublicTool : SimpleTool<PublicTool.Args>() {
        @Serializable
        public data class Args(val message: String) : ToolArgs
        
        override val argsSerializer: kotlinx.serialization.KSerializer<Args> = Args.serializer()
        override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "public_tool",
            description = "A tool accessible to everyone",
            requiredParameters = listOf(
                ToolParameterDescriptor("message", "Test message", ToolParameterType.String)
            )
        )
        
        override suspend fun doExecute(args: Args): String = "Public: ${args.message}"
    }
    
    /**
     * User tool requiring user role or higher
     */
    public class UserTool : SimpleTool<UserTool.Args>() {
        @Serializable
        public data class Args(val message: String) : ToolArgs
        
        override val argsSerializer: kotlinx.serialization.KSerializer<Args> = Args.serializer()
        override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "user_tool",
            description = "A tool requiring user role",
            requiredParameters = listOf(
                ToolParameterDescriptor("message", "Test message", ToolParameterType.String)
            )
        )
        
        override suspend fun doExecute(args: Args): String = "User: ${args.message}"
    }
    
    /**
     * Admin tool requiring admin role
     */
    public class AdminTool : SimpleTool<AdminTool.Args>() {
        @Serializable
        public data class Args(val message: String) : ToolArgs
        
        override val argsSerializer: kotlinx.serialization.KSerializer<Args> = Args.serializer()
        override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "admin_tool", 
            description = "A tool requiring admin role",
            requiredParameters = listOf(
                ToolParameterDescriptor("message", "Test message", ToolParameterType.String)
            )
        )
        
        override suspend fun doExecute(args: Args): String = "Admin: ${args.message}"
    }
    
    /**
     * Create instances of all test tools for convenience
     */
    public val allTools: List<Tool<*, *>> = listOf(
        ReadTool(),
        WriteTool(), 
        DeleteTool(),
        TestTool(),
        PublicTool(),
        UserTool(),
        AdminTool()
    )
}