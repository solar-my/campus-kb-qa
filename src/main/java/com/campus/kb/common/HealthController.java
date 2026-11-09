package com.campus.kb.common;

import com.campus.kb.config.AppProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    private final AppProperties properties;

    public HealthController(AppProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        AppProperties.Chat chat = properties.getAi().getChat();
        AppProperties.Embedding embedding = properties.getAi().getEmbedding();
        String embeddingKey = embedding.getApiKey() == null || embedding.getApiKey().isBlank()
                ? chat.getApiKey() : embedding.getApiKey();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "ok");
        payload.put("version", "0.2.0");
        payload.put("chatModel", chat.getModel());
        payload.put("embeddingModel", embedding.getModel());
        payload.put("chatConfigured", chat.getApiKey() != null && !chat.getApiKey().isBlank());
        payload.put("embeddingConfigured", embeddingKey != null && !embeddingKey.isBlank());
        payload.put("vectorStore", properties.getRag().getStore());
        return payload;
    }
}