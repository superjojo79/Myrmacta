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

### Scalabilité SSE en déploiement multi-instances

**Problème :** la map `emitters` de l'exemple ci-dessus est locale à la JVM. En déploiement mono-instance elle fonctionne telle quelle, mais le modèle de déploiement cible (voir README — Kubernetes, plusieurs pods) casse cette hypothèse : un utilisateur connecté en SSE au pod A ne recevra jamais un événement déclenché par un traitement (ServiceTask Flowable, webhook) exécuté sur le pod B, puisque l'`SseEmitter` de cet utilisateur n'existe que dans la mémoire du pod A.

**Décision retenue :** découpler l'émission de l'événement (n'importe quel pod) de sa diffusion aux `SseEmitter` locaux, via un relais pub/sub partagé entre instances.

```
ServiceTask / Webhook (pod B)
    │
    ▼
SendNotificationUseCase.send(event)          ← peut s'exécuter sur n'importe quel pod
    │
    ▼
BrowserPushPort.push(userId, event)
    │
    ▼
PostgreSQL NOTIFY canal "sse_notifications"  ← relais inter-pods (voir alternative Redis ci-dessous)
    │
    ├──▶ pod A : LISTEN → emitters locaux (utilisateur connecté ici) → push SSE
    ├──▶ pod B : LISTEN → aucun emitter local pour cet utilisateur → ignoré
    └──▶ pod C : LISTEN → idem
```

| Option | Mécanisme | Avantage | Coût |
|---|---|---|---|
| **PostgreSQL `LISTEN`/`NOTIFY`** (retenu par défaut) | Chaque pod maintient une connexion `LISTEN` sur un canal par tenant ou global | Aucune infrastructure supplémentaire — PostgreSQL est déjà une dépendance dure du projet | Débit et taille de payload limités (recommandé : ne transporter qu'un identifiant, puis re-fetch côté pod si besoin) ; une connexion JDBC dédiée et longue par pod |
| Redis Pub/Sub | `publish`/`subscribe` sur un canal par utilisateur ou tenant | Débit plus élevé, latence plus faible, découplage total de PostgreSQL | Ajoute une dépendance d'infrastructure non prévue dans la stack actuelle |
| Sticky sessions (affinité de pod) | Le load balancer route toujours le même utilisateur vers le même pod | Pas de relais applicatif à écrire | Ne résout pas le cas où l'émetteur de l'événement (autre requête HTTP, job async) tombe sur un pod différent de celui où l'utilisateur est connecté ; fragilise le rolling-update Kubernetes |

L'implémentation `SseNotificationAdapter` (voir [01-architecture-hexagonale.md](01-architecture-hexagonale.md)) reste responsable des deux rôles : publier sur le canal partagé, et retransmettre aux `SseEmitter` locaux sur réception. Le contrat du port `BrowserPushPort` ne change pas — seul l'adaptateur évolue, conformément au principe hexagonal.

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
