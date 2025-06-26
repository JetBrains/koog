package ai.koog.prompt.executor.clients.openai.azure

import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic

/**
 * Represents the version of the Azure OpenAI Service.
 *
 * This class encapsulates the version string and provides a way to access predefined versions.
 * It also allows for easy comparison and retrieval of the latest stable and preview versions.
 *
 * @property value The version string of the Azure OpenAI Service.
 */
public class AzureOpenAIServiceVersion private constructor(
    @get:JvmName("value") public val value: String,
) {

    /**
     * Companion object to hold predefined versions and utility methods.
     */
    public companion object {

        private val values: MutableMap<String, AzureOpenAIServiceVersion> =
            mutableMapOf<String, AzureOpenAIServiceVersion>()


        /**
         * Returns the latest stable version of the Azure OpenAI Service.
         */
        @JvmStatic
        public fun latestStableVersion(): AzureOpenAIServiceVersion {
            // We can update the value every general available(GA)/stable announcement.
            return V2024_10_21
        }

        /**
         * Returns the latest preview version of the Azure OpenAI Service.
         */
        @JvmStatic
        public fun latestPreviewVersion(): AzureOpenAIServiceVersion {
            // We can update the value every preview announcement.
            return V2025_03_01_PREVIEW
        }

        /**
         * Creates an instance of [AzureOpenAIServiceVersion] from a version string.
         */
        @JvmStatic
        public fun fromString(version: String): AzureOpenAIServiceVersion =
            values.getOrPut(version) { AzureOpenAIServiceVersion(version) }

        /**
         * Version 2022-12-01 of the Azure OpenAI Service.
         */
        @JvmStatic
        public val V2022_12_01: AzureOpenAIServiceVersion = fromString("2022-12-01")

        /**
         * Version 2023-05-15 of the Azure OpenAI Service.
         */
        @JvmStatic
        public val V2023_05_15: AzureOpenAIServiceVersion = fromString("2023-05-15")

        /**
         * Version 2024-02-01 of the Azure OpenAI Service.
         */
        @JvmStatic
        public val V2024_02_01: AzureOpenAIServiceVersion = fromString("2024-02-01")

        /**
         * Version 2024-06-01 of the Azure OpenAI Service.
         */
        @JvmStatic
        public val V2024_06_01: AzureOpenAIServiceVersion = fromString("2024-06-01")

        /**
         * Version 2024-10-21 of the Azure OpenAI Service.
         */
        @JvmStatic
        public val V2024_10_21: AzureOpenAIServiceVersion = fromString("2024-10-21")

        /**
         * Version 2023-06-01-preview of the Azure OpenAI Service.
         */
        @JvmStatic
        public val V2023_06_01_PREVIEW: AzureOpenAIServiceVersion = fromString("2023-06-01-preview")

        /**
         * Version 2023-07-01-preview of the Azure OpenAI Service.
         */
        @JvmStatic
        public val V2023_07_01_PREVIEW: AzureOpenAIServiceVersion = fromString("2023-07-01-preview")

        /**
         * Version 2024-02-15-preview of the Azure OpenAI Service.
         */
        @JvmStatic
        public val V2024_02_15_PREVIEW: AzureOpenAIServiceVersion = fromString("2024-02-15-preview")

        /**
         * Version 2024-03-01-preview of the Azure OpenAI Service.
         */
        @JvmStatic
        public val V2024_03_01_PREVIEW: AzureOpenAIServiceVersion = fromString("2024-03-01-preview")

        /**
         * Version 2024-04-01-preview of the Azure OpenAI Service.
         */
        @JvmStatic
        public val V2024_04_01_PREVIEW: AzureOpenAIServiceVersion = fromString("2024-04-01-preview")

        /**
         * Version 2024-05-01-preview of the Azure OpenAI Service.
         */
        @JvmStatic
        public val V2024_05_01_PREVIEW: AzureOpenAIServiceVersion = fromString("2024-05-01-preview")

        /**
         * Version 2024-07-01-preview of the Azure OpenAI Service.
         */
        @JvmStatic
        public val V2024_07_01_PREVIEW: AzureOpenAIServiceVersion = fromString("2024-07-01-preview")

        /**
         * Version 2024-08-01-preview of the Azure OpenAI Service.
         */
        @JvmStatic
        public val V2024_08_01_PREVIEW: AzureOpenAIServiceVersion = fromString("2024-08-01-preview")

        /**
         * Version 2024-09-01-preview of the Azure OpenAI Service.
         */
        @JvmStatic
        public val V2024_09_01_PREVIEW: AzureOpenAIServiceVersion = fromString("2024-09-01-preview")

        /**
         * Version 2024-10-01-preview of the Azure OpenAI Service.
         */
        @JvmStatic
        public val V2024_10_01_PREVIEW: AzureOpenAIServiceVersion = fromString("2024-10-01-preview")

        /**
         * Version 2024-12-01-preview of the Azure OpenAI Service.
         */
        @JvmStatic
        public val V2024_12_01_PREVIEW: AzureOpenAIServiceVersion = fromString("2024-12-01-preview")

        /**
         * Version 2025-01-01-preview of the Azure OpenAI Service.
         */
        @JvmStatic
        public val V2025_01_01_PREVIEW: AzureOpenAIServiceVersion = fromString("2025-01-01-preview")

        /**
         * Version 2025-02-01-preview of the Azure OpenAI Service.
         */
        @JvmStatic
        public val V2025_02_01_PREVIEW: AzureOpenAIServiceVersion = fromString("2025-02-01-preview")

        /**
         * Version 2025-03-01-preview of the Azure OpenAI Service.
         */
        @JvmStatic
        public val V2025_03_01_PREVIEW: AzureOpenAIServiceVersion = fromString("2025-03-01-preview")

    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is AzureOpenAIServiceVersion && value == other.value)

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "AzureOpenAIServiceVersion{value=$value}"

}
