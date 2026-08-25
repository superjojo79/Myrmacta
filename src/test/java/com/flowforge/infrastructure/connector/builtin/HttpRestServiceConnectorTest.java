package com.flowforge.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.connector.model.ConnectorDefinition;
import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.model.ServiceConnector;
import com.flowforge.infrastructure.notification.OutboundUrlGuard;
import java.io.IOException;
import java.net.InetAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HttpRestServiceConnectorTest {

    private static final ConnectorInstanceData INSTANCE = new ConnectorInstanceData(
            UUID.randomUUID(), "tenant_dev", "http-rest-service", "http-rest", true,
            Map.of("apiSecret", "should-never-be-echoed-back"));

    @Test
    void execute_unsafeUrl_neverCallsHttpClient() throws Exception {
        OutboundUrlGuard guard = new OutboundUrlGuard(false);
        HttpClient httpClient = mock(HttpClient.class);
        HttpRestServiceConnector connector = new HttpRestServiceConnector(guard, httpClient);

        assertThatThrownBy(() -> connector.execute(INSTANCE, Map.of("url", "https://127.0.0.1/internal")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(httpClient, never()).send(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_safeUrl_guardPassesAndCallAttempted() throws Exception {
        OutboundUrlGuard guard = new OutboundUrlGuard(false);
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"ok\":true}");
        when(httpClient.<String>send(any(), any())).thenReturn(response);
        HttpRestServiceConnector connector = new HttpRestServiceConnector(guard, httpClient);

        Map<String, Object> result = connector.execute(INSTANCE, Map.of("url", "https://example.com/hook"));

        verify(httpClient).send(any(), any());
        assertThat(result.get("status")).isEqualTo(200);
        assertThat(result.get("body")).isEqualTo("{\"ok\":true}");
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_neverEchoesInstanceConfigInResult() throws Exception {
        OutboundUrlGuard guard = new OutboundUrlGuard(false);
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("ok");
        when(httpClient.<String>send(any(), any())).thenReturn(response);
        HttpRestServiceConnector connector = new HttpRestServiceConnector(guard, httpClient);

        Map<String, Object> result = connector.execute(INSTANCE, Map.of("url", "https://example.com/hook"));

        assertThat(result.values()).noneMatch(value -> "should-never-be-echoed-back".equals(value));
        assertThat(result).doesNotContainKey("apiSecret");
    }

    @Test
    void connector_doesNotImplementConnectorDefinition() {
        assertThat(ConnectorDefinition.class.isAssignableFrom(HttpRestServiceConnector.class)).isFalse();
        assertThat(ServiceConnector.class.isAssignableFrom(HttpRestServiceConnector.class)).isTrue();
    }

    @Test
    void execute_missingUrl_throwsWithoutCallingHttpClient() throws Exception {
        OutboundUrlGuard guard = new OutboundUrlGuard(false);
        HttpClient httpClient = mock(HttpClient.class);
        HttpRestServiceConnector connector = new HttpRestServiceConnector(guard, httpClient);

        assertThatThrownBy(() -> connector.execute(INSTANCE, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("url");

        verify(httpClient, never()).send(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_httpClientThrowsIOException_wrapsInIllegalStateException() throws Exception {
        OutboundUrlGuard guard = new OutboundUrlGuard(false);
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.<String>send(any(), any())).thenThrow(new IOException("connection refused"));
        HttpRestServiceConnector connector = new HttpRestServiceConnector(guard, httpClient);

        assertThatThrownBy(() -> connector.execute(INSTANCE, Map.of("url", "https://example.com/hook")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("connection refused");
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_httpClientInterrupted_restoresInterruptFlagAndWraps() throws Exception {
        OutboundUrlGuard guard = new OutboundUrlGuard(false);
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.<String>send(any(), any())).thenThrow(new InterruptedException("interrupted"));
        HttpRestServiceConnector connector = new HttpRestServiceConnector(guard, httpClient);

        try {
            assertThatThrownBy(() -> connector.execute(INSTANCE, Map.of("url", "https://example.com/hook")))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted(); // clear the flag so it doesn't leak into other tests
        }
    }

    @Test
    void connector_isNotASpringBean() {
        assertThat(HttpRestServiceConnector.class.isAnnotationPresent(org.springframework.stereotype.Component.class))
                .isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_pinsResolvedIpWithoutSecondLookup() throws Exception {
        AtomicInteger lookups = new AtomicInteger();
        OutboundUrlGuard.HostResolver resolver = host -> {
            lookups.incrementAndGet();
            return new InetAddress[] {InetAddress.getByName("93.184.216.34")};
        };
        OutboundUrlGuard guard = new OutboundUrlGuard(false, resolver);
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("ok");
        when(httpClient.<String>send(any(), any())).thenReturn(response);
        HttpRestServiceConnector connector = new HttpRestServiceConnector(guard, httpClient);

        connector.execute(INSTANCE, Map.of("url", "https://hooks.example.com/hook"));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        assertThat(captor.getValue().uri().toString()).isEqualTo("https://93.184.216.34/hook");
        assertThat(lookups).hasValue(1);
    }
}
