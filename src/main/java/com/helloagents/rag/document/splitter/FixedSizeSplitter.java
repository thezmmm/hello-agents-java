package com.helloagents.rag.document.splitter;

import com.helloagents.rag.document.TextSplitter;

import java.util.ArrayList;
import java.util.List;

/** 按固定字符数分块，支持重叠窗口 */
public class FixedSizeSplitter implements TextSplitter {

    private final int chunkSize;
    private final int overlap;

    public FixedSizeSplitter() {
        this(512, 64);
    }

    public FixedSizeSplitter(int chunkSize, int overlap) {
        if (chunkSize <= 0) throw new IllegalArgumentException("chunkSize must be positive");
        if (overlap < 0 || overlap >= chunkSize) throw new IllegalArgumentException("overlap must be in [0, chunkSize)");
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    @Override
    public List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) return chunks;

        int start = 0;
        int step = chunkSize - overlap;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            if (end == text.length()) break;
            start += step;
        }
        return chunks;
    }
}