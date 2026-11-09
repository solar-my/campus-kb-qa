package com.campus.kb.chat;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class GuardService {

    public enum Intent {
        HOMEWORK,
        GREETING,
        OUT_OF_SCOPE,
        NORMAL
    }

    public static final String NO_CONTEXT_MESSAGE =
            "📖 当前知识库/该份资料中未查询到该相关内容，你可以核对资料，或者上传对应的学习资料后再提问。";
    public static final String HOMEWORK_REFUSAL =
            "📝 我可以为你讲解相关知识点、梳理解题思路，不能直接提供完整作业答案哦。";
    public static final String OUT_OF_SCOPE_ANSWER =
            "抱歉，我是校园知识库&学习复习助手，暂无法解答该类问题。";
    public static final String GREETING =
            "你好，我是校园知识库 & 学习复习助手。你可以查询校规、教务、奖助、宿舍、绩点、"
                    + "毕业流程等校园事务，也可以上传课件/复习资料后让我总结、划重点、讲解知识点或出题自测。"
                    + "请直接描述你要查询或复习的内容。";

    private static final List<Pattern> HOMEWORK = List.of(
            Pattern.compile("(帮我|替我|给我|直接).{0,10}(写|做|生成|完成|代写).{0,12}(作业|论文|报告|实验报告|试卷|题)"),
            Pattern.compile("(完整|全部|整套|标准|最终).{0,8}(答案|解答|试题|试卷)"),
            Pattern.compile("代写"),
            Pattern.compile("(论文|作业).{0,6}(全文|成品|完整版)")
    );

    private static final List<Pattern> OUT_OF_SCOPE = List.of(
            Pattern.compile("(王者荣耀|原神|英雄联盟|游戏攻略|游戏|追剧|综艺|动漫)"),
            Pattern.compile("(恋爱|分手|表白|情感|星座|算命|塔罗)"),
            Pattern.compile("(时政|国际局势|股票|基金|彩票|炒币)"),
            Pattern.compile("(写|作|编).{0,4}(诗|歌词|小说|段子)"),
            Pattern.compile("(leetcode|力扣|算法题|编程题|代码作业)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(讲个?笑话|聊天|陪你聊)")
    );

    private static final List<Pattern> GREETINGS = List.of(
            Pattern.compile("^(你好|您好|hi|hello|在吗|有人吗)[\\s!！。,.，？?]*$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(谢谢|感谢|多谢|好的|收到|ok)[\\s!！。,.，？?]*$", Pattern.CASE_INSENSITIVE)
    );

    public Intent classify(String message) {
        String text = message == null ? "" : message.trim();
        if (text.isEmpty()) {
            return Intent.NORMAL;
        }
        if (matches(HOMEWORK, text)) {
            return Intent.HOMEWORK;
        }
        if (matches(GREETINGS, text)) {
            return Intent.GREETING;
        }
        if (matches(OUT_OF_SCOPE, text)) {
            return Intent.OUT_OF_SCOPE;
        }
        return Intent.NORMAL;
    }

    private boolean matches(List<Pattern> patterns, String text) {
        return patterns.stream().anyMatch(pattern -> pattern.matcher(text).find());
    }
}