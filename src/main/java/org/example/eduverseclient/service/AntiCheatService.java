package org.example.eduverseclient.service;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;

import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class AntiCheatService {
    private static AntiCheatService instance;

    private final HttpClient httpClient;
    private final Gson gson = new Gson();

    private static final String AI_SERVICE_URL = common.constant.AntiCheatConfig.AI_SERVICE_URL;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static final int MIN_FRAME_SIZE_BYTES = 1024;

    private AntiCheatService() {
        // ✅ ổn định cho uvicorn/FastAPI:
        // - Force HTTP/1.1
        // - Disable system proxy
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .version(HttpClient.Version.HTTP_1_1)
                .proxy(ProxySelector.of(null))
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
                if (imageBytes == null || imageBytes.length == 0) {
                    log.info("⏭️ Frame skipped: empty bytes");
                    return null;
                }

                if (imageBytes.length < MIN_FRAME_SIZE_BYTES) {
                    log.info("⏭️ Frame skipped: too small ({} < {})", imageBytes.length, MIN_FRAME_SIZE_BYTES);
                    return null;
                }

                if (userId == null || userId.trim().isEmpty()) {
                    log.warn("⏭️ Frame skipped: missing userId");
                    return null;
                }

                if (examId == null || examId.trim().isEmpty()) {
                    log.warn("⏭️ Frame skipped: missing examId");
                    return null;
                }

                String url = normalizeAnalyzeUrl(AI_SERVICE_URL);

                String base64Image = Base64.getEncoder().encodeToString(imageBytes);
                if (base64Image.isEmpty()) {
                    log.warn("⏭️ Frame skipped: base64 empty");
                    return null;
                }

                // ✅ build JSON thủ công (khỏi dính module reflection)
                JsonObject payload = new JsonObject();
                payload.addProperty("user_id", userId.trim());
                payload.addProperty("session_id", examId.trim());
                payload.addProperty("frame_base64", base64Image);

                String jsonBody = gson.toJson(payload);
                if (jsonBody == null || jsonBody.isEmpty()) {
                    log.error("❌ Failed to build json body");
                    return null;
                }

                log.info("➡️ AI request: url={}, userId={}, examId={}, frameBytes={}, jsonChars={}",
                        url, userId, examId, imageBytes.length, jsonBody.length());

                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(TIMEOUT)
                        .header("Content-Type", "application/json; charset=UTF-8")
                        .header("Accept", "application/json")
                        .header("User-Agent", "EduverseClient/1.0")
                        // ❌ KHÔNG set "Connection" (bị Java HttpClient cấm)
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = httpClient.send(
                        httpRequest,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
                );

                int statusCode = response.statusCode();
                String responseBody = response.body();

                log.info("⬅️ AI response: status={}, bodyPreview={}", statusCode, safePreview(responseBody, 300));

                if (statusCode == 422) {
                    log.error("❌ HTTP 422 - Validation Error. RequestPreview={}, ResponsePreview={}",
                            safePreview(jsonBody, 250), safePreview(responseBody, 600));
                    return null;
                }

                if (statusCode != 200) {
                    log.warn("⚠️ AI service returned status {} - URL: {}, Response: {}",
                            statusCode, url, safePreview(responseBody, 600));
                    return null;
                }

                AnalysisResult result = parseResponse(responseBody);
                if (result == null) {
                    log.warn("⚠️ Failed to parse AI response - URL: {}, Response: {}",
                            url, safePreview(responseBody, 600));
                    return null;
                }

                log.info("✅ AI Analysis - Decision: {}, Score: {}, Flags: {}",
                        result.decision,
                        String.format("%.2f", result.suspicionScore),
                        result.flags.isEmpty() ? "none" : String.join(", ", result.flags));

                return result;

            } catch (java.net.http.HttpTimeoutException e) {
                log.warn("⏱️ AI service timeout - URL: {}, Error: {}", AI_SERVICE_URL, e.getMessage());
                return null;
            } catch (java.net.ConnectException e) {
                log.warn("🔌 AI service connection failed - URL: {}, Error: {}", AI_SERVICE_URL, e.getMessage());
                return null;
            } catch (Exception e) {
                log.error("❌ Failed to analyze frame - URL: {}, Error: {}", AI_SERVICE_URL, e.getMessage(), e);
                return null;
            }
        });
    }

    private String normalizeAnalyzeUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return "http://127.0.0.1:8000/analyze/frame";
        }

        String url = rawUrl.trim();
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);

        if (!url.endsWith("/analyze/frame")) {
            url = url + "/analyze/frame";
        }
        return url;
    }

    private String safePreview(String str, int maxLength) {
        if (str == null) return "null";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength) + "... (truncated)";
    }

    private AnalysisResult parseResponse(String json) {
        try {
            if (json == null || json.trim().isEmpty()) return null;

            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();

            String decision = optString(jsonObject, "decision", "OK");
            double suspicionScore = optDouble(jsonObject, "suspicion_score", 0.0);

            List<String> flags = new ArrayList<>();
            if (jsonObject.has("flags") && jsonObject.get("flags").isJsonArray()) {
                jsonObject.getAsJsonArray("flags").forEach(element -> {
                    if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                        flags.add(element.getAsString());
                    }
                });
            }

            Map<String, Object> metrics = new HashMap<>();
            if (jsonObject.has("metrics") && jsonObject.get("metrics").isJsonObject()) {
                JsonObject metricsObj = jsonObject.getAsJsonObject("metrics");
                metricsObj.entrySet().forEach(entry -> {
                    JsonElement value = entry.getValue();
                    if (value.isJsonPrimitive()) {
                        if (value.getAsJsonPrimitive().isString()) {
                            metrics.put(entry.getKey(), value.getAsString());
                        } else if (value.getAsJsonPrimitive().isNumber()) {
                            metrics.put(entry.getKey(), value.getAsNumber().doubleValue());
                        } else if (value.getAsJsonPrimitive().isBoolean()) {
                            metrics.put(entry.getKey(), value.getAsBoolean());
                        }
                    }
                });
            }

            return new AnalysisResult(decision, suspicionScore, flags, metrics);

        } catch (Exception e) {
            log.error("❌ Failed to parse AI response JSON: {}", e.getMessage(), e);
            return null;
        }
    }

    private String optString(JsonObject obj, String key, String defaultValue) {
        if (!obj.has(key)) return defaultValue;
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) return defaultValue;
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) return element.getAsString();
        return defaultValue;
    }

    private double optDouble(JsonObject obj, String key, double defaultValue) {
        if (!obj.has(key)) return defaultValue;
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) return defaultValue;
        try {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                return element.getAsNumber().doubleValue();
            }
        } catch (Exception ignored) {}
        return defaultValue;
    }

    public static class AnalysisResult {
        public final String decision;
        public final double suspicionScore;
        public final List<String> flags;
        public final Map<String, Object> metrics;

        public AnalysisResult(String decision, double suspicionScore, List<String> flags, Map<String, Object> metrics) {
            this.decision = decision != null ? decision : "OK";
            this.suspicionScore = Double.isNaN(suspicionScore) || Double.isInfinite(suspicionScore) ? 0.0 : suspicionScore;
            this.flags = flags != null ? Collections.unmodifiableList(new ArrayList<>(flags)) : Collections.emptyList();
            this.metrics = metrics != null ? Collections.unmodifiableMap(new HashMap<>(metrics)) : Collections.emptyMap();
        }

        public boolean isViolation() {
            return "VIOLATION".equalsIgnoreCase(decision) || suspicionScore >= 0.70;
        }

        public boolean isWarning() {
            return "WARNING".equalsIgnoreCase(decision) || (suspicionScore >= 0.50 && suspicionScore < 0.70);
        }
    }
}
