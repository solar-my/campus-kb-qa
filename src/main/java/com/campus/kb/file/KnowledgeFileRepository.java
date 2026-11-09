package com.campus.kb.file;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface KnowledgeFileRepository extends JpaRepository<KnowledgeFile, Long> {

    List<KnowledgeFile> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    List<KnowledgeFile> findByVisibilityOrderByCreatedAtDesc(Visibility visibility);

    List<KnowledgeFile> findByVisibilityOrOwnerIdOrderByCreatedAtDesc(
            Visibility visibility, Long ownerId);

    Optional<KnowledgeFile> findByIdAndOwnerId(Long id, Long ownerId);

    List<KnowledgeFile> findByIdIn(Collection<Long> ids);

    long countByStatus(FileStatus status);

    long countByVisibility(Visibility visibility);

    boolean existsBySha256AndOwnerIdAndVisibility(String sha256, Long ownerId, Visibility visibility);

    boolean existsBySha256AndVisibility(String sha256, Visibility visibility);
}