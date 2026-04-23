package com.helloagents.rag.document;

import java.util.List;

public interface TextSplitter {

    List<String> split(String text);
}