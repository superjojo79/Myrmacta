package com.flowforge.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.flowforge.infrastructure.config.NotificationProperties;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

class SseNotificationAdapterTest {

    private SseNotificationAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SseNotificationAdapter(
                new NotificationProperties(
                        null, null, new NotificationProperties.Sse(Duration.ofSeconds(15))),
                JsonMapper.builder().build());
    }

    @Test
    void push_connectedUser_returnsTrueAndSendsNotificationEvent() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        adapter.register("user-1", emitter);

        boolean delivered = adapter.push("user-1", Map.of("id", "n1", "payload", Map.of("a", 1)));

        assertThat(delivered).isTrue();
        ArgumentCaptor<SseEmitter.SseEventBuilder> captor =
                ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(emitter).send(captor.capture());
        assertThat(captor.getValue()).isNotNull();
    }

    @Test
    void push_twoEmittersSameUser_fanOutToBoth() throws Exception {
        SseEmitter first = mock(SseEmitter.class);
        SseEmitter second = mock(SseEmitter.class);
        adapter.register("user-1", first);
        adapter.register("user-1", second);

        assertThat(adapter.push("user-1", Map.of("x", 1))).isTrue();
        verify(first).send(any(SseEmitter.SseEventBuilder.class));
        verify(second).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void push_absentUser_returnsFalse() {
        assertThat(adapter.push("nobody", Map.of("x", 1))).isFalse();
    }

    @Test
    void push_removesDeadEmitter_andReturnsTrueIfAnySucceeded() throws Exception {
        SseEmitter dead = mock(SseEmitter.class);
        SseEmitter live = mock(SseEmitter.class);
        doThrow(new IOException("broken")).when(dead).send(any(SseEmitter.SseEventBuilder.class));
        adapter.register("user-1", dead);
        adapter.register("user-1", live);

        assertThat(adapter.push("user-1", Map.of("ok", true))).isTrue();
        verify(live).send(any(SseEmitter.SseEventBuilder.class));

        assertThat(adapter.push("user-1", Map.of("again", true))).isTrue();
        verify(live, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        verify(dead, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void heartbeat_pingsAllEmittersWithoutRemovingThem() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        adapter.register("user-1", emitter);

        adapter.sendHeartbeats();

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(adapter.push("user-1", Map.of("after", true))).isTrue();
    }

    @Test
    void unregisterCallbacks_removeEmitter() {
        SseEmitter emitter = mock(SseEmitter.class);
        ArgumentCaptor<Runnable> completion = ArgumentCaptor.forClass(Runnable.class);
        adapter.register("user-1", emitter);
        verify(emitter).onCompletion(completion.capture());

        completion.getValue().run();
        assertThat(adapter.push("user-1", Map.of("x", 1))).isFalse();
        assertThat(adapter.activeEmitterCount()).isZero();
    }

    @Test
    void activeEmitterCount_followsRegisterAndUnregister() {
        assertThat(adapter.activeEmitterCount()).isZero();
        SseEmitter first = mock(SseEmitter.class);
        SseEmitter second = mock(SseEmitter.class);
        adapter.register("user-1", first);
        adapter.register("user-2", second);
        assertThat(adapter.activeEmitterCount()).isEqualTo(2);
        adapter.unregister("user-1", first);
        assertThat(adapter.activeEmitterCount()).isEqualTo(1);
    }
}
