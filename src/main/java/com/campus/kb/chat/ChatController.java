package com.campus.kb.chat;

import com.campus.kb.auth.UserPrincipal;
import com.campus.kb.chat.ChatDtos.ChatRequest;
import com.campus.kb.chat.ChatDtos.ChatResponse;
import com.campus.kb.chat.ChatDtos.ConversationDto;
import com.campus.kb.chat.ChatDtos.ConversationMessageDto;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    @GetMapping("/conversations")
    public List<ConversationDto> conversations(@AuthenticationPrincipal UserPrincipal principal) {
        return chatService.listConversations(principal);
    }

    @GetMapping("/conversations/{id}/messages")
    public List<ConversationMessageDto> conversationMessages(@PathVariable Long id,
                                                             @AuthenticationPrincipal UserPrincipal principal) {
        return chatService.conversationMessages(id, principal);
    }

    @DeleteMapping("/conversations/{id}")
    public Map<String, Object> deleteConversation(@PathVariable Long id,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        chatService.deleteConversation(id, principal);
        return Map.of("success", true, "message", "会话已删除");
    }
}