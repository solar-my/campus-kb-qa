package com.campus.kb.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class ChatDtos {

    private ChatDtos() {
    }

    public record ChatRequest(
            Long userId,
            @NotBlank(message = "问题不能为空")
            @Size(max = 4000, message = "问题不能超过 4000 字")
            String question,
            Long conversationId,
            String mode,
            List<Long> fileIds) {
    }

    public record ChatResponse(
            Long conversationId,
            Long messageId,
            String answer,
            List<Citation> citations,
            String mode,
            long latencyMs) {
    }
}