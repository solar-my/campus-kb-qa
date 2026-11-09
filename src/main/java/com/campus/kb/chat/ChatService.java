package com.campus.kb.chat;

import com.campus.kb.auth.UserPrincipal;
import com.campus.kb.chat.ChatDtos.ChatRequest;
import com.campus.kb.chat.ChatDtos.ChatResponse;
import com.campus.kb.common.BizException;
import com.campus.kb.config.AppProperties;
import com.campus.kb.file.FileService;
import com.campus.kb.file.FileStatus;
import com.campus.kb.file.KnowledgeFile;
import com.campus.kb.rag.VectorStoreService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final Pattern FOLLOW_UP = Pattern.compile("(它|这个|那个|上述|上面|该|这门|这份|还有|继续)");

    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final FileService fileService;
    private final VectorStoreService vectorStoreService;
    private final PromptBuilder promptBuilder;
    private final GuardService guardService;
    private final ChatModel chatModel;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public ChatService(ChatConversationRepository conversationRepository,
                       ChatMessageRepository messageRepository,
                       FileService fileService,
                       VectorStoreService vectorStoreService,
                       PromptBuilder promptBuilder,
                       GuardService guardService,
                       ChatModel chatModel,
                       AppProperties properties,
                       ObjectMapper objectMapper) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.fileService = fileService;
        this.vectorStoreService = vectorStoreService;
        this.promptBuilder = promptBuilder;
        this.guardService = guardService;
        this.chatModel = chatModel;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public ChatResponse ask(UserPrincipal principal, ChatRequest request) {
        if (request.userId() != null && !request.userId().equals(principal.id())) {
            throw new BizException(HttpStatus.FORBIDDEN, "用户身份不一致，请重新登录");
        }
        String question = request.question().trim();
        ReviewMode mode = ReviewMode.from(request.mode());
        ChatConversation conversation = resolveConversation(principal, request.conversationId(), question);
        List<ChatMessage> history = loadHistory(conversation.getId());

        ChatMessage userMessage = new ChatMessage();
        userMessage.setConversationId(conversation.getId());
        userMessage.setRole(MessageRole.USER);
        userMessage.setContent(question);
        userMessage.setCitationsJson("[]");
        messageRepository.save(userMessage);

        GuardService.Intent intent = guardService.classify(question);
        String fixedAnswer = switch (intent) {
            case HOMEWORK -> GuardService.HOMEWORK_REFUSAL;
            case GREETING -> GuardService.GREETING;
            case OUT_OF_SCOPE -> GuardService.OUT_OF_SCOPE_ANSWER;
            case NORMAL -> null;
        };
        if (fixedAnswer != null) {
            ChatMessage saved = saveAssistant(conversation, fixedAnswer, List.of());
            return new ChatResponse(conversation.getId(), saved.getId(), fixedAnswer,
                    List.of(), mode.name().toLowerCase(Locale.ROOT), 0);
        }

        ensureModelConfigured();
        List<Long> fileIds = normalizeFileIds(request.fileIds());
        validateSelectedFiles(principal, fileIds);

        String retrievalQuery = buildRetrievalQuery(question, history);
        int topK = mode == ReviewMode.QA ? properties.getRag().getTopK()
                : Math.max(properties.getRag().getTopK(), 8);
        if (!fileIds.isEmpty()) {
            topK = Math.max(topK, 8);
        }
        topK = Math.min(topK, 12);

        long started = System.currentTimeMillis();
        List<EmbeddingMatch<TextSegment>> matches = vectorStoreService.search(
                retrievalQuery, principal.id(), fileIds, topK);

        if (matches.isEmpty()) {
            ChatMessage saved = saveAssistant(conversation, GuardService.NO_CONTEXT_MESSAGE, List.of());
            long latency = System.currentTimeMillis() - started;
            return new ChatResponse(conversation.getId(), saved.getId(),
                    GuardService.NO_CONTEXT_MESSAGE, List.of(),
                    mode.name().toLowerCase(Locale.ROOT), latency);
        }

        PromptBuilder.PromptResult prompt = promptBuilder.build(
                question, mode, matches, history, properties.getRag().getMaxContextChars());

        String answer;
        try {
            var response = chatModel.chat(prompt.messages());
            answer = response.aiMessage().text();
        } catch (Exception e) {
            log.error("调用对话模型失败", e);
            throw new BizException(HttpStatus.BAD_GATEWAY,
                    "模型服务暂时不可用，请检查 API Key、模型名称与网络后重试");
        }
        if (answer == null || answer.isBlank()) {
            answer = "模型没有返回有效内容，请换一种问法后重试。";
        }

        ChatMessage saved = saveAssistant(conversation, answer, prompt.citations());
        long latency = System.currentTimeMillis() - started;
        return new ChatResponse(conversation.getId(), saved.getId(), answer,
                prompt.citations(), mode.name().toLowerCase(Locale.ROOT), latency);
    }

    private ChatConversation resolveConversation(UserPrincipal principal, Long conversationId,
                                                 String question) {
        if (conversationId != null) {
            return conversationRepository.findByIdAndUserId(conversationId, principal.id())
                    .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "会话不存在或已删除"));
        }
        ChatConversation conversation = new ChatConversation();
        conversation.setUserId(principal.id());
        conversation.setTitle(PromptBuilder.truncate(question.replace("\n", " "), 30));
        return conversationRepository.save(conversation);
    }

    private List<ChatMessage> loadHistory(Long conversationId) {
        List<ChatMessage> recent = new ArrayList<>(
                messageRepository.findTop12ByConversationIdOrderByIdDesc(conversationId));
        Collections.reverse(recent);
        int limit = Math.max(2, properties.getRag().getHistoryMessages());
        if (recent.size() > limit) {
            recent = new ArrayList<>(recent.subList(recent.size() - limit, recent.size()));
        }
        return recent;
    }

    private List<Long> normalizeFileIds(List<Long> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return raw.stream().filter(java.util.Objects::nonNull).distinct().limit(10).toList();
    }

    private void validateSelectedFiles(UserPrincipal principal, List<Long> fileIds) {
        for (Long fileId : fileIds) {
            KnowledgeFile file = fileService.getAccessible(fileId, principal);
            if (file.getStatus() != FileStatus.READY) {
                throw new BizException(HttpStatus.CONFLICT,
                        "资料《" + file.getOriginalName() + "》尚未解析完成，请稍后再试");
            }
        }
    }

    private String buildRetrievalQuery(String question, List<ChatMessage> history) {
        String previous = history.stream()
                .filter(item -> item.getRole() == MessageRole.USER)
                .reduce((first, second) -> second)
                .map(ChatMessage::getContent)
                .orElse(null);
        if (previous == null) {
            return question;
        }
        boolean followUp = question.length() < 15 || FOLLOW_UP.matcher(question).find();
        if (!followUp) {
            return question;
        }
        return PromptBuilder.truncate(previous, 200) + " " + question;
    }

    private ChatMessage saveAssistant(ChatConversation conversation, String answer,
                                      List<Citation> citations) {
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversation.getId());
        message.setRole(MessageRole.ASSISTANT);
        message.setContent(answer);
        try {
            message.setCitationsJson(objectMapper.writeValueAsString(citations));
        } catch (JsonProcessingException e) {
            message.setCitationsJson("[]");
        }
        ChatMessage saved = messageRepository.save(message);
        conversation.setUpdatedAt(java.time.LocalDateTime.now());
        conversationRepository.save(conversation);
        return saved;
    }

    private void ensureModelConfigured() {
        AppProperties.Chat chat = properties.getAi().getChat();
        if (chat.getApiKey() == null || chat.getApiKey().isBlank()) {
            throw new BizException(HttpStatus.SERVICE_UNAVAILABLE,
                    "尚未配置对话模型 API Key，请在 .env 中填写 LLM_API_KEY 后重启服务");
        }
        AppProperties.Embedding embedding = properties.getAi().getEmbedding();
        String embeddingKey = embedding.getApiKey();
        if (embeddingKey == null || embeddingKey.isBlank()) {
            embeddingKey = chat.getApiKey();
        }
        if (embeddingKey == null || embeddingKey.isBlank()) {
            throw new BizException(HttpStatus.SERVICE_UNAVAILABLE,
                    "尚未配置向量模型 API Key，请填写 EMBEDDING_API_KEY 后重启服务");
        }
    }
}