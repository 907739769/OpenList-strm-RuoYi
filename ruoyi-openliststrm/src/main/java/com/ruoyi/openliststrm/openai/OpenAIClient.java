package com.ruoyi.openliststrm.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.openliststrm.rename.model.MediaInfo;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Simple OpenAI Chat client used to extract metadata when film/tv title cannot be reliably determined by heuristics/TMDb.
 * The prompt requests a JSON-only response to simplify parsing. Network errors are swallowed and method returns false.
 */
@Slf4j
public class OpenAIClient {
    private static final String DEFAULT_MODEL = "gpt-5-mini";
    // default OpenAI chat completions endpoint
    private static final String DEFAULT_ENDPOINT = "https://api.openai.com";

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(90, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .connectionPool(new ConnectionPool(5, 5, TimeUnit.SECONDS))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final String model;
    private final String endpoint;

    public OpenAIClient(String apiKey) {
        this(apiKey, null, null);
    }

    // allow passing an explicit endpoint (may be a full URL or just a host/domain)
    public OpenAIClient(String apiKey, String endpoint) {
        this(apiKey, endpoint, null);
    }

    // prefer this constructor when callers want to supply a model string explicitly
    public OpenAIClient(String apiKey, String endpoint, String model) {
        this.apiKey = apiKey;
        // determine model: prefer explicit param, then env OPENAI_MODEL, then hardcoded default
        String chosenModel = null;
        if (model != null && !model.trim().isEmpty()) {
            chosenModel = model.trim();
        } else {
            String envModel = System.getenv().getOrDefault("OPENAI_MODEL", "");
            if (envModel != null && !envModel.trim().isEmpty()) chosenModel = envModel.trim();
            else chosenModel = DEFAULT_MODEL;
        }
        this.model = chosenModel;
        this.endpoint = buildEndpoint(endpoint);
    }

    private String buildEndpoint(String cfg) {
        if (cfg == null || cfg.trim().isEmpty()) return DEFAULT_ENDPOINT;
        String t = cfg.trim();
        // if a full URL is provided, use it directly
        if (t.startsWith("http://") || t.startsWith("https://")) {
            return t;
        }
        // otherwise treat as host/domain and construct the standard path
        return "https://" + t;
    }

    /**
     * Try to enrich MediaInfo using OpenAI. Only fills fields that are currently null or empty.
     * Returns true if any field was updated.
     */
    public boolean enrich(MediaInfo info, String filename) {
        if (apiKey == null || apiKey.isEmpty()) return false;
        try {
            String prompt = buildPrompt(info, filename);
            RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), mapper.writeValueAsBytes(buildRequestPayload(prompt)));
            Request req = new Request.Builder()
                    .url(endpoint + "/v1/chat/completions")
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
        sb.append("Given the filename and any partially extracted fields, return a JSON object only (no explanation) with the following keys: originalTitle, title, year, season, episode.\n");
        sb.append("Use null for unknown fields.\n\n");

        sb.append("IMPORTANT RULES FOR TITLES / 拼音处理：\n");
        sb.append("1) Titles should be concise. If the filename or existing fields are in pinyin, convert the pinyin tokens into Chinese characters by mapping each pinyin token in-order. **Do NOT reorder tokens for grammatical fluency**; preserve the original token sequence as the likely original title. Example: 'Xi Huan Ni Wo Ye Shi' -> '喜欢你我也是' (NOT '我也喜欢你').\n");
        sb.append("2) If the pinyin forms a known/standard title, prefer the standard Chinese title (still keep original token order unless the standard title clearly differs and is more established).\n");
        sb.append("3) When ambiguous, produce the most literal Chinese candidate preserving token order; do not invent or paraphrase.\n");
        sb.append("4) Season and episode should be numeric strings (e.g. \\\"01\\\", \\\"10\\\"). Year should be a 4-digit string.\n\n");

        sb.append("Respond with a strict JSON object only. Example: {\\\"originalTitle\\\": \\\"Xi.Huan.Ni.Wo.Ye.Shi.S05E07.2024.2160p.WEB-DL.H265.EDR.AAC-HHWEB.strm\\\", \\\"title\\\": \\\"喜欢你我也是\\\", \\\"year\\\": \\\"2024\\\", \\\"season\\\": \\\"05\\\", \\\"episode\\\": \\\"07\\\"}\\n\\n");

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
