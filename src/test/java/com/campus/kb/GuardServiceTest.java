package com.campus.kb;

import com.campus.kb.chat.GuardService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GuardServiceTest {

    private final GuardService guard = new GuardService();

    @Test
    void detectsHomeworkRequests() {
        assertThat(guard.classify("帮我写一份完整的课程论文"))
                .isEqualTo(GuardService.Intent.HOMEWORK);
        assertThat(guard.classify("直接给我整套试卷答案"))
                .isEqualTo(GuardService.Intent.HOMEWORK);
    }

    @Test
    void detectsGreetings() {
        assertThat(guard.classify("你好")).isEqualTo(GuardService.Intent.GREETING);
    }

    @Test
    void detectsOutOfScope() {
        assertThat(guard.classify("推荐几款好玩的游戏"))
                .isEqualTo(GuardService.Intent.OUT_OF_SCOPE);
        assertThat(guard.classify("帮我写一首诗"))
                .isEqualTo(GuardService.Intent.OUT_OF_SCOPE);
    }

    @Test
    void allowsCampusQuestions() {
        assertThat(guard.classify("国家励志奖学金需要什么条件？"))
                .isEqualTo(GuardService.Intent.NORMAL);
        assertThat(guard.classify("帮我划一下这份课件的复习重点"))
                .isEqualTo(GuardService.Intent.NORMAL);
    }
}