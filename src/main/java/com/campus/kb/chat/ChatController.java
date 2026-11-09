package com.campus.kb.chat;

import com.campus.kb.auth.UserPrincipal;
import com.campus.kb.chat.ChatDtos.ChatRequest;
import com.campus.kb.chat.ChatDtos.ChatResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/ask")
    public ChatResponse ask(@AuthenticationPrincipal UserPrincipal principal,
                            @Valid @RequestBody ChatRequest request) {
        return chatService.ask(principal, request);
    }
}