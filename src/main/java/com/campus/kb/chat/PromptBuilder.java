package com.campus.kb.chat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PromptBuilder {

    private final String template;

    private static final Map<ReviewMode, String> MODE_INSTRUCTIONS = Map.of(
            ReviewMode.QA,
            "直接回答用户问题。先给结论，再分点说明；涉及流程时列出步骤、材料和注意事项。",
            ReviewMode.SUMMARY,
            "本次任务是【文档核心内容总结】。请先一句话概括主题，再梳理内容框架/章节脉络，"
                    + "最后列出 3-6 条核心结论。不要逐字复述原文。",
            ReviewMode.KEYPOINTS,
            "本次任务是【划重点 / 复习提纲】。请提炼必须掌握的考点、关键词、公式、数字和条件，"
                    + "按“必背要点 / 易错点 / 自测提示”分点整理，适合直接背诵。",
            ReviewMode.EXPLAIN,
            "本次任务是【知识点通俗讲解】。请先定位资料中的原文依据，再用通俗语言解释概念、"
                    + "原理或流程，可举例说明。不得改动原文中的数字、条件和规定。",
            ReviewMode.QUIZ,
            "本次任务是【基于资料生成自测题】。请生成 3-5 道单选题（每题 4 个选项）和 2-3 道简答题，"
                    + "题目必须来自资料内容；每题给出参考答案与解析/思路提示，并在解析中标注来源编号。"
                    + "不要出资料之外的内容。",
            ReviewMode.INTEGRATE,
            "本次任务是【多份资料知识点整合】。请按主题归纳多份资料的共同点、差异和关联，"
                    + "分点输出，并对每一部分标注来源编号。"
    );

    public PromptBuilder(ResourceLoader resourceLoader) {
        Resource resource = resourceLoader.getResource("classpath:prompts/system-prompt.txt");
        try (InputStream input = resource.getInputStream()) {
            this.template = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("无法加载系统提示词", e);
        }
    }

    public PromptResult build(String question, ReviewMode mode,
                              List<EmbeddingMatch<TextSegment>> matches,
                              List<com.campus.kb.chat.ChatMessage> history, int maxContextChars) {
        ContextResult contextResult = buildContext(matches, maxContextChars);
        String system = template.replace("{{RAG_CONTEXT}}", contextResult.context());
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(system));
        for (com.campus.kb.chat.ChatMessage item : history) {
            if (item.getContent() == null || item.getContent().isBlank()) {
                continue;
            }
            if (item.getRole() == com.campus.kb.chat.MessageRole.USER) {
                messages.add(UserMessage.from(item.getContent()));
            } else {
                messages.add(AiMessage.from(item.getContent()));
            }
        }
        messages.add(UserMessage.from(buildUserMessage(question, mode)));
        return new PromptResult(messages, contextResult.citations());
    }

    public ContextResult buildContext(List<EmbeddingMatch<TextSegment>> matches, int maxContextChars) {
        List<Citation> citations = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        StringBuilder context = new StringBuilder();
        int totalChars = 0;

        for (EmbeddingMatch<TextSegment> match : matches) {
            TextSegment segment = match.embedded();
            if (segment == null || segment.text() == null || segment.text().isBlank()) {
                continue;
            }
            var metadata = segment.metadata();
            Long fileId = numberToLong(metadata.toMap().get("fileId"));
            String filename = metadata.containsKey("filename")
                    ? metadata.getString("filename") : "未知资料";
            Integer chunkIndex = numberToInt(metadata.toMap().get("chunkIndex"));
            String text = segment.text().trim();
            String key = fileId + ":" + text.substring(0, Math.min(100, text.length()));
            if (!seen.add(key)) {
                continue;
            }
            if (!citations.isEmpty() && totalChars + text.length() > maxContextChars) {
                break;
            }
            int index = citations.size() + 1;
            double score = match.score() == null ? 0.0 : match.score();
            citations.add(new Citation(index, fileId, filename, chunkIndex, score,
                    truncate(text, 180)));
            context.append('[').append(index).append("] 来源：《").append(filename).append('》');
            if (chunkIndex != null) {
                context.append("（第 ").append(chunkIndex + 1).append(" 段）");
            }
            context.append("，相关度 ").append(String.format("%.2f", score)).append('\n');
            context.append("内容：\n").append(text).append("\n\n---\n\n");
            totalChars += text.length();
        }

        if (citations.isEmpty()) {
            context.append("（本次未检索到相关资料）");
        }
        return new ContextResult(context.toString(), citations);
    }

    private String buildUserMessage(String question, ReviewMode mode) {
        String instruction = MODE_INSTRUCTIONS.getOrDefault(mode, MODE_INSTRUCTIONS.get(ReviewMode.QA));
        return """
                【用户提问】
                %s

                【本次任务】
                %s""".formatted(question, instruction);
    }

    private Long numberToLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private Integer numberToInt(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    public static String truncate(String text, int limit) {
        if (text == null) {
            return "";
        }
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, Math.max(0, limit - 1)).stripTrailing() + "…";
    }

    public record ContextResult(String context, List<Citation> citations) {
    }

    public record PromptResult(List<dev.langchain4j.data.message.ChatMessage> messages, List<Citation> citations) {
    }
}