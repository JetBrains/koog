package ai.grazie.code.agents.tools.registry

import ai.grazie.code.agents.core.tools.Tool
import ai.grazie.code.agents.core.tools.ToolResult
import ai.grazie.code.agents.core.tools.ToolStage
import ai.grazie.code.agents.tools.registry.tools.*
import ai.grazie.code.agents.tools.registry.tools.composio.ComposioTools
import ai.grazie.code.files.tool.read.ListFolderTool
import ai.grazie.code.files.tool.read.ReadFilesTool

/**
 * Serves as a centralized registry for managing tools and prompts for agents.
 * It provides structured management and retrieval
 * of tool and prompt identifiers to facilitate operations in various contexts.
 *
 *
 * How to contribute:
 * - In order to contribute to [GlobalAgentToolStages] you must visit [Code Engine GitHub](https://github.com/jetbrains/code-engine)
 * and make a pull request with your changes, then a new version of the library will be published. After that, you may use
 * it in your project.
 */
@Suppress("FunctionName")
object GlobalAgentToolStages {
    fun ErrorFixing(
        searchFile: ErrorFixingTools.SearchFileTool,
        searchObject: ErrorFixingTools.SearchObjectTool,
        edit: ErrorFixingTools.EditTool,
        runTest: ErrorFixingTools.RunTestTool,

        stageName: String = ToolStage.DEFAULT_STAGE_NAME,
        toolListName: String = ToolStage.DEFAULT_TOOL_LIST_NAME
    ): ToolStage = ToolStage(stageName, toolListName) {
        tool(searchFile)
        tool(searchObject)
        tool(edit)
        tool(runTest)
    }


    fun FleetProjectGenerator(
        createDirectory: FleetProjectGeneratorTools.CreateDirectoryTool,
        createFile: FleetProjectGeneratorTools.CreateFileTool,
        setFileText: FleetProjectGeneratorTools.SetFileTextTool,
        log: FleetProjectGeneratorTools.LogTool,

        stageName: String = ToolStage.DEFAULT_STAGE_NAME,
        toolListName: String = ToolStage.DEFAULT_TOOL_LIST_NAME
    ): ToolStage = ToolStage(stageName, toolListName) {
        tool(createDirectory)
        tool(createFile)
        tool(setFileText)
        tool(log)
    }

    fun <Path> EnvironmentSetup(
        executeCommand: TerminalTools.ExecuteCommandTool,
        listFolderTool: ListFolderTool<Path>,
        readFileTool: ReadFilesTool<Path>,

        reflect: CommunicationTools.ReflectTool,
        ask: CommunicationTools.AskTool,
        submitAllTasksAsFinished: CommunicationTools.SubmitAllTasksAsFinishedTool,

        stageName: String = ToolStage.DEFAULT_STAGE_NAME,
        toolListName: String = ToolStage.DEFAULT_TOOL_LIST_NAME
    ): ToolStage = ToolStage(stageName, toolListName) {
        tool(executeCommand)
        tool(listFolderTool)
        tool(readFileTool)
        tool(reflect)
        tool(ask)
        tool(submitAllTasksAsFinished)
    }

    fun FixRedCode(
        listFilesWithErrors: RedCodeFixingTools.ListFilesWithErrorsTool,
        findErrorsInFile: RedCodeFixingTools.FindErrorsInFileTool,
        determineModuleRoots: RedCodeFixingTools.DetermineModuleRootsTool,
        findBuildScript: RedCodeFixingTools.FindBuildScriptTool,
        readFileText: RedCodeFixingTools.ReadFileTextTool,
        editFileText: RedCodeFixingTools.EditFileTextTool,
        insertTextToFile: RedCodeFixingTools.InsertIntoFileTool,
        searchWordProject: RedCodeFixingTools.SearchWordProjectTool,

        listImportsInFileTool: RedCodeFixingTools.ImportFixingTools.ListImportsInFileTool,
        addImportsToFileTool: RedCodeFixingTools.ImportFixingTools.AddImportsToFileTool,
        removeImportFromFileTool: RedCodeFixingTools.ImportFixingTools.RemoveImportFromFileTool,

        listDependenciesInModuleTool: RedCodeFixingTools.DependencyFixingTools.ListDependenciesInModuleTool,
        addDependenciesToModuleTool: RedCodeFixingTools.DependencyFixingTools.AddDependenciesToModuleTool,
        removeDependencyFromModuleTool: RedCodeFixingTools.DependencyFixingTools.RemoveDependencyFromModuleTool
    ): List<Tool<out Tool.Args, out ToolResult>> {
        return listOf(
            listFilesWithErrors,
            findErrorsInFile,
            determineModuleRoots,
            findBuildScript,
            readFileText,
            editFileText,
            insertTextToFile,
            searchWordProject,
            listImportsInFileTool,
            addImportsToFileTool,
            removeImportFromFileTool,
            listDependenciesInModuleTool,
            addDependenciesToModuleTool,
            removeDependencyFromModuleTool
        )
    }


    object SWE {
        fun Composio(
            getMethodBody: ComposioTools.CodeAnalysisTools.GetMethodBody,
            getClassInfo: ComposioTools.CodeAnalysisTools.GetClassInfo,
            getMethodSignature: ComposioTools.CodeAnalysisTools.GetMethodSignature,

            gitRepoTree: ComposioTools.FileTools.DumpGitRepoTreeFile,
            changeWorkingDirectory: ComposioTools.FileTools.ChangeWorkingDirectory,
            createFile: ComposioTools.FileTools.CreateFile,
            editFile: ComposioTools.FileTools.EditFile,
            findFile: ComposioTools.FileTools.FindFile,
            gitPatch: ComposioTools.FileTools.GitPatch,
            listFiles: ComposioTools.FileTools.ListFiles,
            openFile: ComposioTools.FileTools.OpenFile,
            scroll: ComposioTools.FileTools.Scroll,
            searchWord: ComposioTools.FileTools.SearchWord,
            write: ComposioTools.FileTools.Write,

            stageName: String = ToolStage.DEFAULT_STAGE_NAME,
            toolListName: String = ToolStage.DEFAULT_TOOL_LIST_NAME
        ): ToolStage = ToolStage(stageName, toolListName) {
            tool(getMethodBody)
            tool(getClassInfo)
            tool(getMethodSignature)

            tool(gitRepoTree)
            tool(changeWorkingDirectory)
            tool(createFile)
            tool(editFile)
            tool(findFile)
            tool(gitPatch)
            tool(listFiles)
            tool(openFile)
            tool(scroll)
            tool(searchWord)
            tool(write)
        }
    }
}
