package ai.koog.agents.core.feature.config

import ai.koog.agents.core.annotation.ExperimentalAgentsApi

/**
 * A utility object that provides constants representing system variables
 * related to the configuration of features in the system.
 *
 * These system variables enable external configuration of features via
 * environment variables or JVM options.
 */
@ExperimentalAgentsApi
public object FeatureSystemVariables {

    /**
     * The name of the environment variable used to configure features in the system.
     */
    public const val KOOG_FEATURES_ENV_VAR_NAME: String = "KOOG_FEATURES"

    /**
     * The name of the JVM option used to configure features in the system.
     */
    public const val KOOG_FEATURES_VM_OPTION_NAME: String = "koog.features"
}
