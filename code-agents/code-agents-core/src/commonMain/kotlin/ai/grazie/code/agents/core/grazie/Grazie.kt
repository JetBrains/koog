package ai.grazie.code.agents.core.grazie

import kotlinx.serialization.Serializable

/**
 * Represents the configuration for connecting to a Grazie platform.
 *
 * @property url Base URL of the Grazie platform environment.
 */
@Serializable(with = GrazieEnvironmentSerializer::class)
sealed class GrazieEnvironment(val url: String) {

    /**
     * Represents the staging environment configuration for connecting to the Grazie platform.
     */
    data object Staging : GrazieEnvironment("https://api.app.stgn.grazie.aws.intellij.net")

    /**
     * Represents the production environment configuration for connecting to the Grazie platform.
     */
    data object Production : GrazieEnvironment("https://api.app.prod.grazie.aws.intellij.net")

    /**
     * Represents a custom Grazie platform connection configuration.
     */
    class Custom(url: String) : GrazieEnvironment(url)
}
