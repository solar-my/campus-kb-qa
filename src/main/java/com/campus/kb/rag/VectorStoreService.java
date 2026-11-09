package com.campus.kb.rag;

import com.campus.kb.common.BizException;
import com.campus.kb.config.AppProperties;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreService.class);

    public static final String META_FILE_ID = "fileId";
    public static final String META_ACCESS_OWNER = "accessOwnerId";
    public static final String META_FILENAME = "filename";
    public static final String META_VISIBILITY = "visibility";
    public static final String META_CHUNK_INDEX = "chunkIndex";

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final AppProperties properties;

    public VectorStoreService(EmbeddingStore<TextSegment> embeddingStore,
                              EmbeddingModel embeddingModel,
                              AppProperties properties) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.properties = properties;
    }

    public void addAll(List<Embedding> embeddings, List<TextSegment> segments) {
        embeddingStore.addAll(embeddings, segments);
    }

    public void deleteByFileId(Long fileId) {
        try {
            embeddingStore.removeAll(
                    MetadataFilterBuilder.metadataKey(META_FILE_ID).isEqualTo(fileId));
        } catch (Exception e) {
            String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            // 首次入库时 collection 可能还不存在，Chroma 会返回 not found；这属于正常情况。
            if (message.contains("does not exist") || message.contains("not found")
                    || message.contains("404")) {
                log.info("collection 尚不存在，跳过旧向量清理 fileId={}", fileId);
                return;
            }
            log.warn("删除文件向量失败 fileId={}: {}", fileId, e.getMessage());
            throw new BizException(HttpStatus.SERVICE_UNAVAILABLE, "向量库操作失败，请稍后重试");
        }
    }

    public List<EmbeddingMatch<TextSegment>> search(String query, Long userId,
                                                    List<Long> fileIds, int topK) {

        String apiKey = properties.getAi().getEmbedding().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = properties.getAi().getChat().getApiKey();
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new BizException(HttpStatus.SERVICE_UNAVAILABLE,
                    "尚未配置 Embedding 模型 API Key，请在 .env 中填写后重启服务");
        }
        try {
            Embedding queryEmbedding = embeddingModel.embed(query).content();
            Filter filter = MetadataFilterBuilder.metadataKey(META_ACCESS_OWNER)
                    .isIn(0L, userId);
            if (fileIds != null && !fileIds.isEmpty()) {
                filter = Filter.and(filter,
                        MetadataFilterBuilder.metadataKey(META_FILE_ID).isIn(fileIds));
            }
            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(topK)
                    .minScore(properties.getRag().getMinScore())
                    .filter(filter)
                    .build();
            return embeddingStore.search(request).matches();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("向量检索失败", e);
            throw new BizException(HttpStatus.SERVICE_UNAVAILABLE,
                    "知识库检索失败，请确认 Chroma 服务已启动");
        }
    }
}