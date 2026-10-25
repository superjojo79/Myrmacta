package be.myrmacta.infrastructure.connector.builtin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * TODO §87 (D3d) — {@link ServiceBusPort} par l'API REST d'Azure Service Bus ({@code java.net.http}, aucune dépendance de plus) :
 * {@code POST …/messages/head} (verrouillage de lecture), {@code DELETE} de l'adresse de verrou (terminé), {@code PUT} (abandon).
 * Redirections jamais suivies ; corps borné ; le secret d'autorisation n'apparaît dans aucune erreur.
 */
@Component
class HttpServiceBusClient implements ServiceBusPort {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final ObjectMapper mapper;

    HttpServiceBusClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<Received> receive(Endpoint endpoint, int maxMessages, int waitSeconds, long maxMessageBytes) {
        List<Received> received = new ArrayList<>();
        try {
            readMessages(endpoint, maxMessages, waitSeconds, maxMessageBytes, received);
        } catch (RuntimeException failure) {
            // Les messages déjà verrouillés ne sont pas laissés expirer (ils compteraient une livraison pour rien) : rendus au courtier.
            for (Received message : received) {
                try {
                    abandon(endpoint, message);
                } catch (RuntimeException ignored) {
                    // Le verrou expirera tout seul.
                }
            }
            throw failure;
        }
        return received;
    }

    private void readMessages(Endpoint endpoint, int maxMessages, int waitSeconds, long maxMessageBytes, List<Received> received) {
        for (int i = 0; i < maxMessages; i++) {
            // La première lecture attend un message ; les suivantes ne bloquent pas (file vidée = 204).
            int timeout = i == 0 ? waitSeconds : 1;
            URI uri = URI.create(endpoint.baseUrl() + "/" + endpoint.entityPath() + "/messages/head?timeout=" + timeout);
            HttpResponse<InputStream> response = send(endpoint, HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(timeout + 15L))
                    .POST(HttpRequest.BodyPublishers.noBody()));
            try (InputStream in = response.body()) {
                int status = response.statusCode();
                if (status == 204) {
                    break;
                }
                if (status != 201) {
                    throw new IllegalStateException("service bus answered HTTP " + status);
                }
                String location = response.headers().firstValue("Location").orElse("");
                byte[] bytes = in.readNBytes((int) Math.min(maxMessageBytes + 1, Integer.MAX_VALUE - 8));
                JsonNode broker = brokerProperties(response);
                String messageId = broker.path("MessageId").asString("");
                int delivery = broker.path("DeliveryCount").asInt(1);
                if (location.isBlank() || !sameHost(endpoint.baseUrl(), location)) {
                    throw new IllegalStateException("service bus returned an unusable lock address");
                }
                Received message = new Received(new String(bytes, StandardCharsets.UTF_8), messageId, delivery, location);
                if (bytes.length > maxMessageBytes) {
                    abandon(endpoint, message);
                    continue; // trop gros : rendu au courtier (il le mettra en lettres mortes), jamais traité
                }
                received.add(message);
            } catch (IOException ex) {
                throw new IllegalStateException("service bus answer could not be read", ex);
            }
        }
    }

    @Override
    public void complete(Endpoint endpoint, Received message) {
        expectOk(send(endpoint, HttpRequest.newBuilder(URI.create(message.lockUrl()))
                .timeout(Duration.ofSeconds(20)).DELETE()));
    }

    @Override
    public void abandon(Endpoint endpoint, Received message) {
        expectOk(send(endpoint, HttpRequest.newBuilder(URI.create(message.lockUrl()))
                .timeout(Duration.ofSeconds(20)).PUT(HttpRequest.BodyPublishers.noBody())));
    }

    private HttpResponse<InputStream> send(Endpoint endpoint, HttpRequest.Builder builder) {
        HttpRequest request = builder.header("Authorization", endpoint.authorization().get()).build();
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException ex) {
            throw new IllegalStateException("service bus is unreachable", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("the call to service bus was interrupted", ex);
        }
    }

    private static void expectOk(HttpResponse<InputStream> response) {
        try (InputStream in = response.body()) {
            in.readNBytes(1024);
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("service bus answered HTTP " + response.statusCode());
            }
        } catch (IOException ex) {
            throw new IllegalStateException("service bus answer could not be read", ex);
        }
    }

    private JsonNode brokerProperties(HttpResponse<?> response) {
        String raw = response.headers().firstValue("BrokerProperties").orElse("{}");
        try {
            return mapper.readTree(raw);
        } catch (RuntimeException ex) {
            return mapper.createObjectNode();
        }
    }

    /** L'adresse de verrou doit rester sur l'hôte du connecteur : un courtier compromis ne nous fait pas appeler ailleurs. */
    static boolean sameHost(String baseUrl, String lockUrl) {
        try {
            URI base = URI.create(baseUrl);
            URI lock = URI.create(lockUrl);
            return base.getScheme().equalsIgnoreCase(lock.getScheme())
                    && base.getHost().equalsIgnoreCase(lock.getHost())
                    && base.getPort() == lock.getPort();
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
