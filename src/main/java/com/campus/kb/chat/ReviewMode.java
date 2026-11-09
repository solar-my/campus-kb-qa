package com.campus.kb.chat;

import java.util.Locale;

public enum ReviewMode {
    QA("问答"),
    SUMMARY("内容总结"),
    KEYPOINTS("复习重点"),
    EXPLAIN("知识点讲解"),
    QUIZ("自测出题"),
    INTEGRATE("多资料整合");

    private final String label;

    ReviewMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static ReviewMode from(String value) {
        if (value == null || value.isBlank()) {
            return QA;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return QA;
        }
    }
}