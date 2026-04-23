package com.helloagents.rag.document.splitter;

import com.helloagents.rag.document.TextSplitter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 递归分隔符分块。
 *
 * 按优先级依次尝试分隔符（段落 → 换行 → 中文句号 → 英文句点 → 空格），
 * 将文本分割为不超过 maxSize 的 chunk，相邻 chunk 之间保留 overlap 字符的重叠窗口。
 */
public class RecursiveSplitter implements TextSplitter {

    private static final String[] SEPARATORS = {"\n\n", "\n", "。", ".", " "};

    private final int maxSize;
    private final int overlap;

    public RecursiveSplitter() {
        this(512, 64);
    }

    public RecursiveSplitter(int maxSize, int overlap) {
        if (maxSize <= 0) throw new IllegalArgumentException("maxSize must be positive");
        if (overlap < 0 || overlap >= maxSize) throw new IllegalArgumentException("overlap must be in [0, maxSize)");
        this.maxSize = maxSize;
        this.overlap = overlap;
    }

    @Override
    public List<String> split(String text) {
        if (text == null || text.isBlank()) return List.of();
        return doSplit(text.strip(), 0);
    }

    private List<String> doSplit(String text, int sepIdx) {
        if (text.length() <= maxSize) {
            return text.isBlank() ? List.of() : List.of(text);
        }
        if (sepIdx >= SEPARATORS.length) {
            return hardCut(text);
        }

        String sep = SEPARATORS[sepIdx];
        String[] rawParts = text.split(Pattern.quote(sep), -1);

        if (rawParts.length == 1) {
            return doSplit(text, sepIdx + 1);
        }

        // 超长的单个 part 先递归拆分，再进入 merge
        List<String> splits = new ArrayList<>();
        for (String part : rawParts) {
            if (part.isBlank()) continue;
            if (part.length() > maxSize) {
                splits.addAll(doSplit(part, sepIdx + 1));
            } else {
                splits.add(part);
            }
        }

        return merge(splits, sep);
    }

    /**
     * 贪心合并 splits，使每个 chunk ≤ maxSize。
     * flush 后从头部裁剪，保留 ≤ overlap 字符作为下一个 chunk 的重叠前缀。
     */
    private List<String> merge(List<String> splits, String sep) {
        List<String> chunks = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int sepLen = sep.length();

        for (String s : splits) {
            int proposed = joinedLen(current, sepLen) + (current.isEmpty() ? 0 : sepLen) + s.length();

            if (!current.isEmpty() && proposed > maxSize) {
                chunks.add(String.join(sep, current));
                // 从头部删除，直到剩余长度落入 overlap 窗口内
                while (!current.isEmpty() && joinedLen(current, sepLen) > overlap) {
                    current.remove(0);
                }
            }

            current.add(s);
        }

        if (!current.isEmpty()) {
            chunks.add(String.join(sep, current));
        }

        return chunks;
    }

    /** 无可用分隔符时的兜底：强制按 maxSize 切割，步长 = maxSize - overlap */
    private List<String> hardCut(String text) {
        List<String> chunks = new ArrayList<>();
        int step = maxSize - overlap;
        for (int i = 0; i < text.length(); i += step) {
            chunks.add(text.substring(i, Math.min(i + maxSize, text.length())));
        }
        return chunks;
    }

    private static int joinedLen(List<String> parts, int sepLen) {
        if (parts.isEmpty()) return 0;
        return parts.stream().mapToInt(String::length).sum() + sepLen * (parts.size() - 1);
    }
}