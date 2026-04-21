package ai.koog.agents.example;

import java.util.Optional;

public final class ApiKeyService {

    private ApiKeyService() {}

    public static String getOpenAIApiKey() {
        return getRequiredEnv("OPENAI_API_KEY");
    }

    public static String getAnthropicApiKey() {
        return getRequiredEnv("ANTHROPIC_API_KEY");
    }

    public static String getGoogleApiKey() {
        return getRequiredEnv("GOOGLE_API_KEY");
    }

    public static String getOpenRouterApiKey() {
        return getRequiredEnv("OPENROUTER_API_KEY");
    }

    public static String getAwsAccessKey() {
        return getRequiredEnv("AWS_ACCESS_KEY_ID");
    }

    public static String getAwsSecretAccessKey() {
        return getRequiredEnv("AWS_SECRET_ACCESS_KEY");
    }

    public static String getAwsBearerTokenBedrock() {
        return getRequiredEnv("AWS_BEARER_TOKEN_BEDROCK");
    }

    public static String getBrightDataKey() {
        return getRequiredEnv("BRIGHT_DATA_KEY");
    }

    public static String getMistralAIApiKey() {
        return getRequiredEnv("MISTRALAI_API_KEY");
    }

    private static String getRequiredEnv(String name) {
        return Optional.ofNullable(System.getenv(name))
                .orElseThrow(() -> new IllegalArgumentException(name + " env is not set"));
    }
}
