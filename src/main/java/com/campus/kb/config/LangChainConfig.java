package com.campus.kb.config;

import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.chroma.ChromaApiVersion;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LangChainConfig {

    @Bean
    public ChatModel chatModel(AppProperties properties) {
        AppProperties.Chat chat = properties.getAi().getChat();
        String apiKey = chat.getApiKey() == null || chat.getApiKey().isBlank()
                ? "not-configured" : chat.getApiKey();
        return OpenAiChatModel.builder()
                .baseUrl(chat.getBaseUrl())
                .apiKey(apiKey)
                .modelName(chat.getModel())
                .temperature(chat.getTemperature())
                .maxTokens(chat.getMaxTokens())
                .timeout(Duration.ofSeconds(chat.getTimeoutSeconds()))
                .maxRetries(1)
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel(AppProperties properties) {
        AppProperties.Embedding embedding = properties.getAi().getEmbedding();
        AppProperties.Chat chat = properties.getAi().getChat();
        String apiKey = embedding.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = chat.getApiKey();
        }
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = "not-configured";
        }
        var builder = OpenAiEmbeddingModel.builder()
                .baseUrl(embedding.getBaseUrl())
                .apiKey(apiKey)
                .modelName(embedding.getModel())
                .timeout(Duration.ofSeconds(embedding.getTimeoutSeconds()))
                .maxRetries(1)
                .maxSegmentsPerBatch(Math.max(1, embedding.getBatchSize()))
                .logRequests(false)
                .logResponses(false);
        if (embedding.isSendDimensions() && embedding.getDimensions() > 0) {
            builder.dimensions(embedding.getDimensions());
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.rag.store", havingValue = "memory")
    public EmbeddingStore<TextSegment> inMemoryEmbeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }

    @Bean
    @ConditionalOnProperty(name = "app.rag.store", havingValue = "chroma", matchIfMissing = true)
    public EmbeddingStore<TextSegment> chromaEmbeddingStore(AppProperties properties) {
        return ChromaEmbeddingStore.builder()
                .apiVersion(ChromaApiVersion.V1)
                .baseUrl(properties.getAi().getChroma().getBaseUrl())
                .collectionName(properties.getRag().getCollection())
                .timeout(Duration.ofSeconds(properties.getAi().getChroma().getTimeoutSeconds()))
                .build();
    }

    @Bean
    public DocumentParser documentParser() {
        return new dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser();
    }

    @Bean
    public DocumentSplitter documentSplitter(AppProperties properties) {
        return DocumentSplitters.recursive(
                properties.getRag().getChunkSize(),
                properties.getRag().getChunkOverlap());
    }
}