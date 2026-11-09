package com.campus.kb.file;

import com.campus.kb.common.BizException;
import com.campus.kb.rag.VectorStoreService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class FileIngestService {

    private static final Logger log = LoggerFactory.getLogger(FileIngestService.class);
    static final String PARSE_FAILED_MESSAGE =
            "⚠️ 文件解析失败，可能是扫描图片版、加密文档，请尝试上传可编辑的文档。";

    private final KnowledgeFileRepository fileRepository;
    private final FileStorageService storageService;
    private final DocumentParser documentParser;
    private final DocumentSplitter documentSplitter;
    private final EmbeddingModel embeddingModel;
    private final VectorStoreService vectorStoreService;

    public FileIngestService(KnowledgeFileRepository fileRepository,
                             FileStorageService storageService,
                             DocumentParser documentParser,
                             DocumentSplitter documentSplitter,
                             EmbeddingModel embeddingModel,
                             VectorStoreService vectorStoreService) {
        this.fileRepository = fileRepository;
        this.storageService = storageService;
        this.documentParser = documentParser;
        this.documentSplitter = documentSplitter;
        this.embeddingModel = embeddingModel;
        this.vectorStoreService = vectorStoreService;
    }

    @Async("fileTaskExecutor")
    public void processAsync(Long fileId) {
        KnowledgeFile file = fileRepository.findById(fileId).orElse(null);
        if (file == null) {
            return;
        }
        file.setStatus(FileStatus.PROCESSING);
        file.setErrorMessage(null);
        fileRepository.save(file);

        try {
            // 先清空旧向量：重新入库失败时不留下过期内容。
            vectorStoreService.deleteByFileId(fileId);

            Path path = storageService.pathOf(file.getStoredName());
            String text;
            try (InputStream input = Files.newInputStream(path)) {
                Document parsed = documentParser.parse(input);
                text = parsed.text();
            }
            if (text == null || text.isBlank()) {
                throw new BizException(PARSE_FAILED_MESSAGE);
            }

            Document document = Document.from(text, buildMetadata(file));
            List<TextSegment> segments = documentSplitter.split(document);
            if (segments.isEmpty()) {
                throw new BizException(PARSE_FAILED_MESSAGE);
            }

            List<TextSegment> withIndex = new ArrayList<>(segments.size());
            for (int i = 0; i < segments.size(); i++) {
                TextSegment segment = segments.get(i);
                Metadata metadata = segment.metadata().copy().put("chunkIndex", i);
                withIndex.add(TextSegment.from(segment.text(), metadata));
            }

            List<Embedding> embeddings = embeddingModel.embedAll(withIndex).content();
            if (embeddings.size() != withIndex.size()) {
                throw new BizException("向量化结果数量异常，请稍后重试");
            }
            vectorStoreService.addAll(embeddings, withIndex);

            file.setStatus(FileStatus.READY);
            file.setChunkCount(withIndex.size());
            file.setErrorMessage(null);
            fileRepository.save(file);
            log.info("资料入库完成 fileId={} name={} chunks={}", fileId,
                    file.getOriginalName(), withIndex.size());
        } catch (Exception e) {
            log.error("资料入库失败 fileId={} name={}", fileId, file.getOriginalName(), e);
            try {
                vectorStoreService.deleteByFileId(fileId);
            } catch (Exception ignored) {
            }
            file.setStatus(FileStatus.FAILED);
            file.setChunkCount(0);
            file.setErrorMessage(messageOf(e));
            fileRepository.save(file);
        }
    }

    private Metadata buildMetadata(KnowledgeFile file) {
        Metadata metadata = new Metadata();
        metadata.put("fileId", file.getId());
        metadata.put("accessOwnerId",
                file.getVisibility() == Visibility.PUBLIC ? 0L : file.getOwnerId());
        metadata.put("filename", file.getOriginalName());
        metadata.put("visibility", file.getVisibility().name());
        metadata.put("fileType", file.getFileType());
        return metadata;
    }

    private String messageOf(Exception e) {
        if (e instanceof BizException biz && biz.getMessage() != null) {
            return biz.getMessage();
        }
        String message = e.getMessage();
        if (message != null && message.contains("Tika")) {
            return PARSE_FAILED_MESSAGE;
        }
        return "文件处理失败，请检查文件后重试（可查看后端日志）";
    }
}