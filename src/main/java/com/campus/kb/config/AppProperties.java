package com.campus.kb.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Auth auth = new Auth();
    private Storage storage = new Storage();
    private Rag rag = new Rag();
    private Ai ai = new Ai();

    @Data
    public static class Auth {
        private String jwtSecret = "";
        private long tokenExpireHours = 168;
        private boolean allowRegistration = true;
        private String adminUsername = "admin";
        private String adminPassword = "admin123456";
        private String adminDisplayName = "系统管理员";
    }

    @Data
    public static class Storage {
        private String uploadDir = "./uploads";
        private long maxFileMb = 50;
    }

    @Data
    public static class Rag {
        /** chroma 或 memory（测试 / 无向量库演示）。 */
        private String store = "chroma";
        private String collection = "campus_kb";
        private int chunkSize = 500;
        private int chunkOverlap = 80;
        private int topK = 6;
        private double minScore = 0.30;
        private int maxContextChars = 7000;
        private int historyMessages = 8;
    }

    @Data
    public static class Ai {
        private Chat chat = new Chat();
        private Embedding embedding = new Embedding();
        private Chroma chroma = new Chroma();
    }

    @Data
    public static class Chat {
        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        private String apiKey = "";
        private String model = "qwen-plus";
        private double temperature = 0.2;
        private int maxTokens = 1500;
        private int timeoutSeconds = 120;
    }

    @Data
    public static class Embedding {
        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        private String apiKey = "";
        private String model = "text-embedding-v3";
        private int dimensions = 1024;
        private boolean sendDimensions = true;
        private int batchSize = 10;
        private int timeoutSeconds = 120;
    }

    @Data
    public static class Chroma {
        private String baseUrl = "http://localhost:8000";
        private int timeoutSeconds = 30;
    }
}