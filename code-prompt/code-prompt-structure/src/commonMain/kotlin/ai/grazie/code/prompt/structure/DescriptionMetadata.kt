package ai.grazie.code.prompt.structure

interface DescriptionMetadata {
    val className: String
    val classDescription: String?
    val fieldDescriptions: Map<String, String>

    fun allDescriptions(): Map<String, String> = buildMap {
        putAll(fieldDescriptions)
        classDescription?.let { put(className, it) }
    }
}