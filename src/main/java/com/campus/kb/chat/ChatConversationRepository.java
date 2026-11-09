package com.campus.kb.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {

    Optional<ChatConversation> findByIdAndUserId(Long id, Long userId);

    List<ChatConversation> findByUserIdOrderByUpdatedAtDesc(Long userId);
}