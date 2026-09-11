package com.assessment.shortener.agentic;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AdaptiveAiAgentProvider implements AiAgentProvider {
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public AdaptiveAiAgentProvider(@Value("${agent.ai.base-url:}") String baseUrl,
                                   @Value("${agent.ai.api-key:}") String apiKey,
                                   @Value("${agent.ai.model:}") String model) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null ? "" : model.trim();
    }

    @Override
    public String mode() {
        return isConfigured() ? "LLM_ENHANCED" : "DETERMINISTIC_FALLBACK";
    }

    @Override
    public Map<String, Object> enhance(String agentName, String requirement, Map<String, Object> baseline) {
        Map<String, Object> result = new LinkedHashMap<>(baseline);
        result.put("aiExecutionMode", mode());

        if (!isConfigured()) {
            result.put("aiProviderStatus", "FALLBACK");
            result.put("aiNote", "Runtime AI is not configured. The reviewed deterministic baseline is used instead.");
            return result;
        }

        try {
            RestClient client = RestClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .build();

            String prompt = "You are the " + agentName + " in a governed engineering workflow. " +
                    "Requirement: " + requirement + ". Review this engineering baseline and return concise advisory recommendations only: " + baseline;

            Map<String, Object> body = Map.of(
                    "model", model,
                    "temperature", 0.1,
                    "messages", List.of(
                            Map.of("role", "system", "content", "Do not approve releases or change workflow state. Provide advisory engineering analysis only."),
                            Map.of("role", "user", "content", prompt)));

            Map<?, ?> response = client.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            result.put("aiAdvisory", extractAdvice(response));
            result.put("aiProviderStatus", "SUCCESS");
        } catch (Exception exception) {
            result.put("aiProviderStatus", "FALLBACK");
            result.put("aiNote", "AI review was unavailable, so the deterministic baseline was retained. Cause: " + exception.getClass().getSimpleName());
        }
        return result;
    }

    private boolean isConfigured() {
        return !baseUrl.isBlank() && !apiKey.isBlank() && !model.isBlank();
    }

    private String extractAdvice(Map<?, ?> response) {
        try {
            List<?> choices = (List<?>) response.get("choices");
            Map<?, ?> firstChoice = (Map<?, ?>) choices.get(0);
            Map<?, ?> message = (Map<?, ?>) firstChoice.get("message");
            return String.valueOf(message.get("content"));
        } catch (Exception exception) {
            return "AI response received, but the advisory content could not be normalized.";
        }
    }
}
