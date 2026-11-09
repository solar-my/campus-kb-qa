package com.campus.kb.file;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "knowledge_files", indexes = {
        @Index(name = "idx_files_owner", columnList = "owner_id"),
        @Index(name = "idx_files_visibility", columnList = "visibility"),
        @Index(name = "idx_files_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
public class KnowledgeFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "owner_name", nullable = false, length = 120)
    private String ownerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Visibility visibility = Visibility.PRIVATE;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "stored_name", nullable = false, length = 120)
    private String storedName;

    @Column(name = "file_type", nullable = false, length = 16)
    private String fileType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FileStatus status = FileStatus.PENDING;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "error_message", length = 800)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}