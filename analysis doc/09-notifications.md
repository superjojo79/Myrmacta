# Notifications

> Trois canaux : email, webhook sortant, notification navigateur (SSE).  
> Dispatcher unifié appelé depuis un ServiceTask Flowable ou directement via l'API.

---

### Canaux supportés

| Canal | Mécanisme | Usage typique |
|---|---|---|
| Email | Spring Mail / JavaMailSender | Alertes, confirmations |
| Webhook sortant | RestClient + Spring Retry | Intégrations noCode (Make, n8n, Zapier), Flowable |
| Navigateur (push) | SSE — Server-Sent Events | Notifications temps réel dans la SPA |

### Dispatcher unifié

Un `ServiceTask` Flowable appelle le `NotificationDispatcher`, qui route selon le canal configuré. Plusieurs canaux peuvent être déclenchés simultanément.

```java
@Service
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final EmailNotificationService   emailService;
    private final WebhookNotificationService webhookService;
    private final SseNotificationController  sseController;

    public void dispatch(NotificationEvent event) {
        switch (event.getChannel()) {
            case EMAIL   -> emailService.send(event);
            case WEBHOOK -> webhookService.send(
                                event.getWebhookConfig(), event.getPayload());
            case BROWSER -> sseController.push(event.getUserId(), event);
        }
    }
}
```

### Webhook sortant — Spring Retry

Retry exponentiel indispensable pour absorber les indisponibilités temporaires des plateformes cibles.

```java
@Retryable(
    retryFor    = { RestClientException.class },
    maxAttempts = 3,
    backoff     = @Backoff(delay = 1000, multiplier = 2) // 1s → 2s → 4s
)
public void send(WebhookConfig config, NotificationPayload payload) { … }

@Recover
public void recover(RestClientException ex, WebhookConfig config, …) {
    // Dead-letter en base pour retraitement manuel
}
```

Chaque webhook est configurable par tenant : URL cible, secret HMAC, format du payload (JSON générique ou format spécifique à la plateforme).

### Notifications navigateur — SSE

**SSE (Server-Sent Events)** retenu plutôt que WebSocket — les notifications sont unidirectionnelles (serveur → browser), SSE est plus simple, natif HTTP/2, sans infrastructure supplémentaire.

```java
// Endpoint d'abonnement — la SPA s'y connecte au démarrage
@GetMapping(value = "/api/notifications/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter subscribe(@AuthenticationPrincipal OidcUser user) {
    SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
    emitters.put(user.getSubject(), emitter);
    emitter.onCompletion(() -> emitters.remove(user.getSubject()));
    return emitter;
}
```

```javascript
// Vue 3 — composable de notifications
const es = new EventSource('/api/notifications/stream', { withCredentials: true });
es.addEventListener('notification', e => {
    notifications.value.unshift(JSON.parse(e.data));
});
```

### Dépendances Maven supplémentaires

```xml
<!-- Email -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>

<!-- Retry (webhooks) -->
<dependency>
    <groupId>org.springframework.retry</groupId>
    <artifactId>spring-retry</artifactId>
</dependency>
```

---

---

## Ports domaine

```java
// domain/notification/port/in
public interface SendNotificationUseCase {
    void send(NotificationEvent event);
}

// domain/notification/port/out
public interface EmailPort {
    void send(NotificationEvent event);
}

public interface WebhookPort {
    void send(WebhookConfig config, NotificationPayload payload);
}

public interface BrowserPushPort {
    void push(String userId, NotificationEvent event);
}
```
