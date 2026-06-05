package com.jetbrains.example.koog.compose.settings

import androidx.datastore.core.okio.OkioSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.BufferedSink
import okio.BufferedSource

internal object AppSettingsSerializer : OkioSerializer<AppSettingsData> {
    override val defaultValue: AppSettingsData = AppSettingsData(
        openAiToken = "",
        anthropicToken = "",
        geminiToken = "",
        ollamaUrl = "http://localhost:11434",
        selectedOption = SelectedOption.OpenAI
    )

    override suspend fun readFrom(source: BufferedSource): AppSettingsData = try {
        val data = Json.decodeFromString<SerializableSettings>(source.readUtf8())
        AppSettingsData(
            openAiToken = data.openAiToken,
            anthropicToken = data.anthropicToken,
            geminiToken = data.geminiToken,
            ollamaUrl = data.ollamaUrl,
            selectedOption = when (data.selectedProvider) {
                SelectedOption.Anthropic.title -> SelectedOption.Anthropic
                SelectedOption.Gemini.title -> SelectedOption.Gemini
                else -> SelectedOption.OpenAI
            }
        )
    } catch (_: Exception) {
        defaultValue
    }

    override suspend fun writeTo(t: AppSettingsData, sink: BufferedSink) {
        sink.writeUtf8(
            Json.encodeToString(
                SerializableSettings(
                    openAiToken = t.openAiToken,
                    anthropicToken = t.anthropicToken,
                    geminiToken = t.geminiToken,
                    ollamaUrl = t.ollamaUrl,
                    selectedProvider = t.selectedOption.title
                )
            )
        )
    }

    @Serializable
    private data class SerializableSettings(
        val openAiToken: String,
        val anthropicToken: String,
        val geminiToken: String,
        val ollamaUrl: String,
        val selectedProvider: String
    )
}
