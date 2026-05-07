package com.helloagents.demo.travel.model;

public record SseEvent(
        String type,
        String content,
        Object data
) {}