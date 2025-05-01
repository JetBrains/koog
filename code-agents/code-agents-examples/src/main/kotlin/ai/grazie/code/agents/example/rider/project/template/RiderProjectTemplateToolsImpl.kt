package ai.grazie.code.agents.example.rider.project.template

import ai.grazie.code.agents.tools.registry.prompts.ProjectTemplates
import ai.grazie.code.agents.tools.registry.prompts.Template
import ai.grazie.code.agents.tools.registry.tools.RiderProjectTemplateTools
import ai.grazie.code.agents.tools.registry.tools.RiderProjectTemplateTools.CreateStructureTool.Result.ResponseType


object CreateStructureTool : RiderProjectTemplateTools.CreateStructureTool() {
    private fun ResponseType.Companion.fromValue(value: String): ResponseType? {
        return ResponseType.entries.find { it.value == value }
    }

    override suspend fun execute(args: Args): Result {
        while (true) {
            println("Select what you want to do: " + enumValues<ResponseType>().joinToString(" | ") { it.value })
            print("Your choice: ")
            val observationType: ResponseType? = ResponseType.fromValue(readln())
            when (observationType) {
                ResponseType.ANSWER -> {
                    while (true) {
                        val question = args.question
                        val options = args.options.joinToString(" | ")
                        print("Question: $question\nOptions: $options\nAnswer: ")
                        val answer = readln()
                        if (answer in args.options) {
                            return Result.Answer(
                                question = args.question,
                                options = args.options,
                                answer = answer
                            )
                        }
                        println("Answer type should be one of: $options!!! Try again...")
                    }
                }
                ResponseType.ADDITION -> {
                    print("Enter your addition: ")
                    val addition = readln()
                    return Result.Addition(content = addition)
                }
                ResponseType.ACCEPT -> {
                    val projects = args.projects
                    val templateIds = projects.map { it.split(":")[0] }
                    val projectsContent: Map<String, Template?> = templateIds.associateWith { templateId ->
                      ProjectTemplates.find { it.id == templateId }
                    }
                    return Result.Accept(content = projectsContent)
                }
                null -> {
                    println("Observation type should be one of: " +
                            enumValues<ResponseType>().joinToString(" | ") { it.value } +
                            "!!! Try again...")
                }
            }
        }
    }
}


object CreateDependenciesTool : RiderProjectTemplateTools.CreateDependenciesTool() {
    override suspend fun execute(args: Args): Result {
        println("Observing args:")
        println(args)
        return Result.Success()
    }
}