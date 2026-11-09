package com.campus.kb;

import com.campus.kb.chat.PromptBuilder;
import com.campus.kb.chat.ReviewMode;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderTest {

    private final PromptBuilder builder = new PromptBuilder(new DefaultResourceLoader());

    @Test
    void buildsCitationAndKeepsSourceInPrompt() {
        Metadata metadata = new Metadata()
                .put("fileId", 7L)
                .put("filename", "软件工程复习.pdf")
                .put("chunkIndex", 2);
        TextSegment segment = TextSegment.from("数据流图用于描述系统的数据流动。", metadata);
        EmbeddingMatch<TextSegment> match = new EmbeddingMatch<>(
                0.91, "embed-1", Embedding.from(new float[]{0.1f, 0.2f}), segment);

        PromptBuilder.PromptResult result = builder.build(
                "解释数据流图", ReviewMode.EXPLAIN, List.of(match), List.of(), 7000);

        assertThat(result.citations()).hasSize(1);
        assertThat(result.citations().get(0).filename()).isEqualTo("软件工程复习.pdf");
        assertThat(result.citations().get(0).index()).isEqualTo(1);
        assertThat(result.messages()).isNotEmpty();
        assertThat(result.messages().get(0).toString()).contains("软件工程复习.pdf");
        assertThat(result.messages().get(result.messages().size() - 1).toString())
                .contains("知识点通俗讲解");
    }

    @Test
    void buildsHistoryMessages() {
        com.campus.kb.chat.ChatMessage history = new com.campus.kb.chat.ChatMessage();
        history.setRole(com.campus.kb.chat.MessageRole.USER);
        history.setContent("之前问过选课");

        PromptBuilder.PromptResult result = builder.build(
                "那退课呢", ReviewMode.QA, List.of(), List.of(history), 7000);

        List<ChatMessage> messages = result.messages();
        assertThat(messages).hasSize(3);
        assertThat(messages.get(1).toString()).contains("之前问过选课");
    }
}