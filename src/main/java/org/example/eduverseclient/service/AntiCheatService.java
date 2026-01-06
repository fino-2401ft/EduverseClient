package org.example.eduverseclient.service;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class AntiCheatService {
    private static AntiCheatService instance;
    private final HttpClient httpClient;
    private static final String AI_SERVICE_URL = common.constant.AntiCheatConfig.AI_SERVICE_URL;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private AntiCheatService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
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
                log.debug("🔍 Analyzing frame - User: {}, Exam: {}, Frame size: {} bytes", userId, examId, imageBytes.length);
                
                String base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes);
                log.debug("📦 Base64 encoded size: {} characters", base64Image.length());
                
                // Manual JSON construction - use StringBuilder for large base64 strings
                StringBuilder jsonBuilder = new StringBuilder();
                jsonBuilder.append("{");
                jsonBuilder.append("\"user_id\":\"").append(escapeJson(userId)).append("\",");
                jsonBuilder.append("\"session_id\":\"").append(escapeJson(examId)).append("\",");
                jsonBuilder.append("\"frame_base64\":\"").append(base64Image).append("\"");
                jsonBuilder.append("}");
                String jsonBody = jsonBuilder.toString();

                log.debug("📤 Sending request to AI service: {} (JSON body size: {} chars)", AI_SERVICE_URL, jsonBody.length());
                log.debug("📋 Request preview (first 200 chars): {}", jsonBody.length() > 200 ? jsonBody.substring(0, 200) + "..." : jsonBody);
                
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(AI_SERVICE_URL))
                        .header("Content-Type", "application/json; charset=UTF-8")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .timeout(TIMEOUT)
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                log.debug("📥 AI service response - Status: {}, Body length: {}", response.statusCode(), response.body().length());

                if (response.statusCode() == 200) {
                    AnalysisResult result = parseJsonResponse(response.body());
                    if (result != null) {
                        log.info("✅ AI Analysis - Decision: {}, Score: {:.2f}, Flags: {}", 
                                result.decision, String.format("%.2f", result.suspicionScore), result.flags);
                    } else {
                        log.warn("⚠️ Failed to parse AI response");
                    }
                    return result;
                } else {
                    log.warn("⚠️ AI service returned status: {}, Body: {}", response.statusCode(), response.body());
                    return null;
                }
            } catch (Exception e) {
                log.error("❌ Failed to analyze frame: {}", e.getMessage(), e);
                return null; // Return null on error, don't block exam
            }
        });
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    private AnalysisResult parseJsonResponse(String json) {
        try {
            String decision = extractString(json, "decision");
            double suspicionScore = extractDouble(json, "suspicion_score");
            List<String> flags = extractStringArray(json, "flags");
            Map<String, Object> metrics = extractObject(json, "metrics");
            
            return new AnalysisResult(decision, suspicionScore, flags, metrics);
        } catch (Exception e) {
            log.error("Failed to parse JSON response", e);
            return null;
        }
    }

    private String extractString(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private double extractDouble(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*([0-9]+\\.?[0-9]*)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }

    private List<String> extractStringArray(String json, String key) {
        List<String> result = new ArrayList<>();
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\[([^\\]]*)\\]");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            String arrayContent = matcher.group(1);
            Pattern stringPattern = Pattern.compile("\"([^\"]*)\"");
            Matcher stringMatcher = stringPattern.matcher(arrayContent);
            while (stringMatcher.find()) {
                result.add(stringMatcher.group(1));
            }
        }
        return result;
    }

    private Map<String, Object> extractObject(String json, String key) {
        Map<String, Object> result = new HashMap<>();
        // Simple extraction - just return empty map for now
        // Can be extended if needed
        return result;
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

