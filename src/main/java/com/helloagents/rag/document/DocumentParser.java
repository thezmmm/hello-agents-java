package com.helloagents.rag.document;

import com.helloagents.rag.core.Document;

public interface DocumentParser {

    Document parse(String source, String content);

    /** 按文件扩展名判断是否支持该格式 */
    boolean supports(String source);
}