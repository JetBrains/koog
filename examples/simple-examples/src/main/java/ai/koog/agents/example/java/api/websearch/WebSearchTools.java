package ai.koog.agents.example.java.api.websearch;

import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.reflect.ToolSet;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class WebSearchTools implements ToolSet {

    private static final String BRIGHT_DATA_URL = "https://api.brightdata.com/request";

    private final String brightDataKey;
    private final HttpClient httpClient;

    public WebSearchTools(String brightDataKey) {
        this.brightDataKey = brightDataKey;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Tool
    @LLMDescription("Search for a query on Google. Returns search results with titles, links, and descriptions.")
    public String search(
            @LLMDescription("The query to search for")
            String query
    ) throws IOException, InterruptedException {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String searchUrl = "https://www.google.com/search?brd_json=1&q=" + encodedQuery;

        String requestBody = "{\"zone\":\"serp_api1\",\"url\":\"" + escapeJson(searchUrl) + "\",\"format\":\"raw\"}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BRIGHT_DATA_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + brightDataKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    @Tool
    @LLMDescription("Scrape a web page and return its content in markdown format.")
    public String scrape(
            @LLMDescription("The URL of the web page to scrape")
            String url
    ) throws IOException, InterruptedException {
        String requestBody = "{\"zone\":\"web_unlocker1\",\"url\":\"" + escapeJson(url) + "\",\"format\":\"json\",\"data_format\":\"markdown\"}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BRIGHT_DATA_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + brightDataKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}