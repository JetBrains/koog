package ai.grazie.code.agents.core.grazie

import ai.grazie.utils.StringValueClassSerializer

internal object GrazieEnvironmentSerializer : StringValueClassSerializer<GrazieEnvironment>(
    "GrazieEnvironment",
    { url ->
        when (url) {
            GrazieEnvironment.Staging.url -> GrazieEnvironment.Staging
            GrazieEnvironment.Production.url -> GrazieEnvironment.Production
            else -> GrazieEnvironment.Custom(url)
        }
    },
    GrazieEnvironment::url,
)