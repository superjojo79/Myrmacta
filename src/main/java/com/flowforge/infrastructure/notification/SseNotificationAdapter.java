package com.flowforge.infrastructure.notification;

import com.flowforge.domain.notification.port.out.BrowserPushPort;
import com.flowforge.infrastructure.config.NotificationProperties;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class SseNotificationAdapter implements BrowserPushPort {

    private final ConcurrentHashMap<String, Set<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public SseNotificationAdapter(NotificationProperties properties, ObjectMapper objectMapper) {
        Objects.requireNonNull(properties, "properties");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public void register(String userId, SseEmitter emitter) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(emitter, "emitter");
        emittersByUser.computeIfAbsent(userId, ignored -> new CopyOnWriteArraySet<>()).add(emitter);
        emitter.onCompletion(() -> unregister(userId, emitter));
        emitter.onTimeout(() -> unregister(userId, emitter));
        emitter.onError(error -> unregister(userId, emitter));
    }

    void unregister(String userId, SseEmitter emitter) {
        Set<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByUser.remove(userId, emitters);
        }
    }

    @Override
    public boolean push(String userId, Map<String, Object> event) {
        Set<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            return false;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(event == null ? Map.of() : event);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize SSE notification payload", ex);
        }

        boolean anySucceeded = false;
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(json, MediaType.APPLICATION_JSON));
                anySucceeded = true;
            } catch (IOException | IllegalStateException ex) {
                unregister(userId, emitter);
            }
        }
        return anySucceeded;
    }

    @Scheduled(fixedDelayString = "${app.notification.sse.heartbeat-interval:15s}")
    void sendHeartbeats() {
        for (Map.Entry<String, Set<SseEmitter>> entry : emittersByUser.entrySet()) {
            String userId = entry.getKey();
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException | IllegalStateException ex) {
                    unregister(userId, emitter);
                }
            }
        }
    }
}
