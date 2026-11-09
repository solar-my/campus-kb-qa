package com.campus.kb.file;

import java.time.LocalDateTime;

public final class FileDtos {

    private FileDtos() {
    }

    public record FileDto(
            Long id,
            Long ownerId,
            String filename,
            String fileType,
            long size,
            String status,
            String visibility,
            int chunkCount,
            String ownerName,
            String errorMessage,
            LocalDateTime createdAt) {
        public static FileDto from(KnowledgeFile file) {
            return new FileDto(
                    file.getId(),
                    file.getOwnerId(),
                    file.getOriginalName(),
                    file.getFileType(),
                    file.getFileSize(),
                    file.getStatus().name(),
                    file.getVisibility().name(),
                    file.getChunkCount(),
                    file.getOwnerName(),
                    file.getErrorMessage(),
                    file.getCreatedAt());
        }
    }

    public record UploadResponse(boolean success, String message, FileDto file) {
    }
}