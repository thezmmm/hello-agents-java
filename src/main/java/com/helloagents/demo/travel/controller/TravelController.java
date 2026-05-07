package com.helloagents.demo.travel.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helloagents.demo.travel.agent.TravelOrchestrator;
import com.helloagents.demo.travel.model.SseEvent;
import com.helloagents.demo.travel.model.TravelRequest;
import com.helloagents.llm.OpenAiClient;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/travel")
public class TravelController {

    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    @Value("${amap.api.key:}")
    private String amapKey;

    private final ExecutorService executor = new ThreadPoolExecutor(
            2, 10, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(20),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    public TravelController(OpenAiClient openAiClient, ObjectMapper objectMapper) {
        this.openAiClient = openAiClient;
        this.objectMapper = objectMapper;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    @PostMapping("/plan")
    public SseEmitter plan(@RequestBody TravelRequest request) {
        SseEmitter emitter = new SseEmitter(300_000L);
        AtomicBoolean active = new AtomicBoolean(true);

        emitter.onTimeout(() -> active.set(false));
        emitter.onError(e -> active.set(false));

        executor.submit(() -> {
            try {
                TravelOrchestrator orchestrator = new TravelOrchestrator(openAiClient, amapKey);
                orchestrator.plan(request, event -> {
                    if (!active.get()) return;
                    try {
                        emitter.send(SseEmitter.event()
                                .name("message")
                                .data(objectMapper.writeValueAsString(event)));
                    } catch (IOException e) {
                        active.set(false);
                    }
                });
                emitter.complete();
            } catch (Exception e) {
                if (active.get()) {
                    try {
                        emitter.send(SseEmitter.event().name("error")
                                .data(objectMapper.writeValueAsString(
                                        new SseEvent("error", e.getMessage(), null))));
                    } catch (IOException ignored) {}
                    emitter.completeWithError(e);
                }
            }
        });

        return emitter;
    }

    @GetMapping("/health")
    public String health() {
        return "ok";
    }
}
