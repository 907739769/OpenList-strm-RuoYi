package com.ruoyi.openliststrm.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.openliststrm.rename.model.MediaInfo;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple OpenAI Chat client used to extract metadata when film/tv title cannot be reliably determined by heuristics/TMDb.
 * The prompt requests a JSON-only response to simplify parsing. Network errors are swallowed and method returns false.
 */
@Slf4j
public class OpenAIClient {
    private static final String DEFAULT_MODEL = "gpt-4o-mini";
    private static final String ENDPOINT = "https://api.chatanywhere.tech/v1/chat/completions";

    private final OkHttpClient http = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final String model;

    public OpenAIClient(String apiKey) {
        this(apiKey, DEFAULT_MODEL);
    }

    public OpenAIClient(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model == null ? DEFAULT_MODEL : model;
    }

    /**
     * Try to enrich MediaInfo using OpenAI. Only fills fields that are currently null or empty.
     * Returns true if any field was updated.
     */
    public boolean enrich(MediaInfo info, String filename) {
        if (apiKey == null || apiKey.isEmpty() || info == null) return false;
        try {
            String prompt = buildPrompt(info, filename);
            RequestBody body = RequestBody.create(mapper.writeValueAsBytes(buildRequestPayload(prompt)), MediaType.parse("application/json; charset=utf-8"));
            Request req = new Request.Builder()
                    .url(ENDPOINT)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .post(body)
                    .build();

            try (Response resp = http.newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) return false;
                String content = mapper.readTree(resp.body().string())
                        .path("choices").get(0).path("message").path("content").asText(null);
                if (content == null) return false;

                // Expect a JSON-only reply
                JsonNode root = mapper.readTree(content.trim());
                log.debug("OpenAI response JSON: {}", root.toString());
                boolean updated = false;
                updated |= setIfMissing(info::getOriginalTitle, info::setOriginalTitle, root, "originalTitle");
                updated |= setIfMissing(info::getTitle, info::setTitle, root, "title");
                updated |= setIfMissing(info::getYear, info::setYear, root, "year");
                updated |= setIfMissing(info::getSeason, info::setSeason, root, "season");
                updated |= setIfMissing(info::getEpisode, info::setEpisode, root, "episode");

                // Attach whole AI response for audit
                Map<String, Object> ai = new HashMap<>();
                ai.put("raw", content);
                info.getExtra().put("ai", ai);

                return updated;
            }
        } catch (IOException e) {
            // network/parsing error -> don't fail parsing flow
            log.error("openai调用失败", e);
            return false;
        }
    }

    private boolean setIfMissing(java.util.function.Supplier<String> getter,
                                 java.util.function.Consumer<String> setter,
                                 JsonNode root, String key) {
        if (getter.get() != null && !getter.get().trim().isEmpty()) return false;
        if (!root.has(key) || root.get(key).isNull()) return false;
        String v = root.get(key).asText(null);
        if (v == null || v.trim().isEmpty()) return false;
        setter.accept(v.trim());
        return true;
    }

    private Map<String, Object> buildRequestPayload(String prompt) {
        Map<String, Object> m = new HashMap<>();
        m.put("model", model);
        m.put("messages", new Object[]{
                new HashMap<String, Object>() {{
                    put("role", "user");
                    put("content", prompt);
                }}
        });
        m.put("temperature", 0.0);
        m.put("max_tokens", 300);
        return m;
    }

    private String buildPrompt(MediaInfo info, String filename) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a metadata extraction assistant.\n");
        sb.append("Given the filename and any partially extracted fields, return a JSON object only (no explanation) with the following keys: originalTitle, title, year, season, episode. Use null for unknown fields. Titles should be concise. Season and episode should be numeric strings (e.g. \"01\", \"10\"). Year should be a 4-digit string.\n\n");
        sb.append("Respond with a strict JSON object only. Example: {\"originalTitle\": \"One Piece\", \"title\": \"One Piece\", \"year\": \"2023\", \"season\": \"01\", \"episode\": \"01\"}\n\n");
        sb.append("Filename: \"").append(filename).append("\"\n");
        sb.append("Current extracted fields: \n");
        sb.append("originalTitle: ").append(info.getOriginalTitle()).append("\n");
        sb.append("title: ").append(info.getTitle()).append("\n");
        sb.append("year: ").append(info.getYear()).append("\n");
        sb.append("season: ").append(info.getSeason()).append("\n");
        sb.append("episode: ").append(info.getEpisode()).append("\n");
        sb.append("If a field is unknown, put null. Do not include any other keys.\n");
        return sb.toString();
    }
}

