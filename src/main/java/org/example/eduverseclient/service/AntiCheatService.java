package org.example.eduverseclient.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class AntiCheatService {
    private static AntiCheatService instance;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private static final String AI_SERVICE_URL = "http://127.0.0.1:8000/analyze/frame";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private AntiCheatService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public static synchronized AntiCheatService getInstance() {
        if (instance == null) {
            instance = new AntiCheatService();
        }
        return instance;
    }

    public CompletableFuture<AnalysisResult> analyzeFrame(byte[] imageBytes, String examId, String userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes);
                
                Map<String, String> requestBody = new HashMap<>();
                requestBody.put("user_id", userId);
                requestBody.put("session_id", examId);
                requestBody.put("frame_base64", base64Image);

                String jsonBody = objectMapper.writeValueAsString(requestBody);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(AI_SERVICE_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .timeout(TIMEOUT)
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
                    return new AnalysisResult(
                            (String) result.get("decision"),
                            ((Number) result.get("suspicion_score")).doubleValue(),
                            (List<String>) result.get("flags"),
                            (Map<String, Object>) result.get("metrics")
                    );
                } else {
                    log.warn("AI service returned status: {}", response.statusCode());
                    return null;
                }
            } catch (Exception e) {
                log.warn("Failed to analyze frame: {}", e.getMessage());
                return null; // Return null on error, don't block exam
            }
        });
    }

    public static class AnalysisResult {
        public final String decision;
        public final double suspicionScore;
        public final List<String> flags;
        public final Map<String, Object> metrics;

        public AnalysisResult(String decision, double suspicionScore, List<String> flags, Map<String, Object> metrics) {
            this.decision = decision;
            this.suspicionScore = suspicionScore;
            this.flags = flags;
            this.metrics = metrics;
        }
    }
}

