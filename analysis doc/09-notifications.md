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

### Scalabilité SSE — chemin d'évolution si un tenant est scalé sur plusieurs répliques

**Ce n'est pas un défaut de la V1.** Avec le modèle de déploiement retenu (décision n° 13,
[02-multi-tenant.md](02-multi-tenant.md)) — une instance Spring Boot dédiée par tenant — la map
`emitters` de l'exemple ci-dessus fonctionne telle quelle par défaut : un tenant donné n'a qu'une
seule instance, donc une seule JVM, donc une seule map en mémoire pour tous ses utilisateurs
connectés. Le problème ne se pose jamais **entre tenants différents**, puisqu'ils n'ont jamais de
composant partagé.

**Le sujet redevient pertinent uniquement si un tenant précis est scalé** sur plusieurs répliques de
sa propre instance (voir « Scaling horizontal d'un tenant » dans
[02-multi-tenant.md](02-multi-tenant.md)) : un utilisateur de ce tenant connecté en SSE à la réplique
A ne recevrait alors pas un événement déclenché par un traitement (ServiceTask Flowable, webhook)
exécuté sur la réplique B du **même tenant**, puisque l'`SseEmitter` de cet utilisateur n'existe que
dans la mémoire de la réplique A.

**Décision retenue pour ce cas** (à activer, pas par défaut) : découpler l'émission de l'événement
(n'importe quelle réplique du tenant) de sa diffusion aux `SseEmitter` locaux, via un relais pub/sub
partagé entre les répliques de ce tenant.

```
ServiceTask / Webhook (réplique B, tenant_a)
    │
    ▼
SendNotificationUseCase.send(event)          ← peut s'exécuter sur n'importe quelle réplique de tenant_a
    │
    ▼
BrowserPushPort.push(userId, event)
    │
    ▼
PostgreSQL NOTIFY canal propre au tenant     ← relais inter-répliques (voir alternative Redis ci-dessous)
    │
    ├──▶ réplique A : LISTEN → emitters locaux (utilisateur connecté ici) → push SSE
    ├──▶ réplique B : LISTEN → aucun emitter local pour cet utilisateur → ignoré
    └──▶ réplique C : LISTEN → idem
```

| Option | Mécanisme | Avantage | Coût |
|---|---|---|---|
| **PostgreSQL `LISTEN`/`NOTIFY`** (retenu par défaut si le besoin se présente) | Chaque réplique du tenant maintient une connexion `LISTEN` sur un canal propre à ce tenant | Aucune infrastructure supplémentaire — PostgreSQL est déjà une dépendance dure du projet, et la connexion est de toute façon déjà scopée au schéma du tenant | Débit et taille de payload limités (recommandé : ne transporter qu'un identifiant, puis re-fetch côté réplique si besoin) ; une connexion JDBC dédiée et longue par réplique |
| Redis Pub/Sub | `publish`/`subscribe` sur un canal par utilisateur, au sein du pool du tenant | Débit plus élevé, latence plus faible, découplage total de PostgreSQL | Ajoute une dépendance d'infrastructure non prévue dans la stack actuelle |
| Sticky sessions (affinité de réplique) | Le load balancer du tenant route toujours le même utilisateur vers la même réplique | Pas de relais applicatif à écrire | Ne résout pas le cas où l'émetteur de l'événement (autre requête HTTP, job async) tombe sur une réplique différente de celle où l'utilisateur est connecté ; fragilise le rolling-update |

L'implémentation `SseNotificationAdapter` (voir [01-architecture-hexagonale.md](01-architecture-hexagonale.md)) resterait responsable des deux rôles si ce chemin est activé : publier sur le canal partagé, et retransmettre aux `SseEmitter` locaux sur réception. Le contrat du port `BrowserPushPort` ne changerait pas — seul l'adaptateur évoluerait, conformément au principe hexagonal.

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
