package ai.grazie.code.prompt.structure

public interface DescriptionMetadata {
    public val className: String
    public val classDescription: String?
    public val fieldDescriptions: Map<String, String>

    public fun allDescriptions(): Map<String, String> = buildMap {
        putAll(fieldDescriptions)
        classDescription?.let { put(className, it) }
    }
}
