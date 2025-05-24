package ai.koog.agents.memory.providers

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
public data class FileMetadata(
    @SerialName("content_type")
    val type: FileType,
    val hidden: Boolean,
    val content: FileContent,
) {
    public enum class FileType {
        File,
        Directory
    }

    public enum class FileContent(public val display: String) {
        Text("text"),
        Binary("binary"),
        Inapplicable("inapplicable");
    }
}