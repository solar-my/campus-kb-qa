package com.campus.kb.chat;

public record Citation(
        int index,
        Long fileId,
        String filename,
        Integer chunkIndex,
        double score,
        String snippet) {
}