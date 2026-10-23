# Notifications

> Trois canaux : email, webhook sortant, notification navigateur (SSE).  
> Envoi déclenché depuis un ServiceTask Flowable ou directement via l'API, via un
> **outbox transactionnel** — voir décision n° 33 ([registre des décisions](../decisions/033-pattern-outbox-pour-les-notifications-workfloweventpublisher.md)).

---

### Canaux supportés

| Canal | Mécanisme | Usage typique |
|---|---|---|
| Email | Spring Mail / JavaMailSender | Alertes, confirmations |
| Webhook sortant | `RestClient` + garde SSRF (`OutboundUrlGuard`) | Intégrations noCode (Make, n8n, Zapier), Flowable |
| Navigateur (push) | SSE — Server-Sent Events | Notifications temps réel dans la SPA |

### Pattern outbox transactionnel (décision n° 33)

**Écart assumé par rapport à une version antérieure de cette page**, qui décrivait un
`NotificationDispatcher` synchrone appelant directement les canaux. L'implémentation retenue est
symétrique à l'outbox du déclenchement workflow (décision n° 7) : le use case n'effectue **aucune
I/O** — il valide l'événement et écrit un enregistrement par canal dans `notification_outbox`, dans
la même transaction que l'action métier qui déclenche la notification. Un relais de polling
(`@Scheduled`) dispatche ensuite réellement, avec retry/backoff porté par la table (`attempts`,
`recordFailure`), pas par une annotation Spring.

```java
// domain/notification/usecase — DD-UC-NO-IO : pas de port de canal appelé ici
public final class SendNotificationUseCaseImpl implements SendNotificationUseCase {
    public List<UUID> send(NotificationEvent event, String tenantId) {
        // valide chaque canal, puis écrit un batch dans l'outbox — aucun appel EmailPort/WebhookPort/BrowserPushPort
        return outboxRepository.savePendingBatch(tenantId, writes, now);
    }
}

// infrastructure/notification — relais, hors transaction métier
@Component
public class NotificationOutboxRelay {
    @Scheduled(fixedDelayString = "${app.notification.outbox.poll-delay:1s}")
    void relayPendingEvents() {
        // lockNextPending → dispatch réel par canal → markProcessed / recordFailure / markDead
    }
}
```

Avantage recherché : durabilité de l'envoi (un crash entre la décision métier et l'appel réseau ne
perd jamais la notification, rejouable) sans introduire de brique d'infrastructure supplémentaire —
même raisonnement que la décision n° 7.

### Webhook sortant — retry porté par le relais outbox

Un échec (`RuntimeException`) lors de `dispatchWebhook` incrémente `attempts` via
`outboxRepository.recordFailure(...)` ; le relais retente au prochain cycle de polling jusqu'à
`app.notification.webhook.*` / `app.notification.outbox.max-attempts`, puis passe l'enregistrement
en `DEAD` pour retraitement manuel. Pas de `@Retryable` Spring Retry — la dépendance a été retirée
du `pom.xml` (jamais utilisée, cf. décision n° 33).

Chaque webhook est configurable par tenant : URL cible, secret HMAC (chiffré au repos), format du
payload JSON. Toute URL cible passe par `OutboundUrlGuard` (résolution DNS + blocage des plages
privées/loopback/link-local/métadonnées cloud) **avant** l'envoi. **D-44 :** `resolveSafe`
effectue **un** lookup et retourne un `PinnedEndpoint` (IP validée + hostname d'origine).
`WebhookRestAdapter` POST sur l'URI IP, envoie `Host` = hostname d'origine, vérifie le certificat
TLS contre ce hostname (pas l'IP), et refuse les redirects (`NEVER`). L'admin webhook appelle
seulement `assertSafe` (pas de connexion). Le skeleton `HttpRestServiceConnector` (rôle SERVICE,
non enregistré) réutilise le même pin.

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
```

Pas de dépendance retry dédiée — le retry webhook est porté par `NotificationOutboxRelay` (voir
ci-dessus), pas par Spring Retry.

---

## Ports domaine

```java
// domain/notification/port/in — écrit l'outbox, n'appelle aucun port de canal (DD-UC-NO-IO)
public interface SendNotificationUseCase {
    List<UUID> send(NotificationEvent event, String tenantId);
}

// domain/notification/port/out — appelés uniquement par NotificationOutboxRelay
public interface EmailPort {
    void send(String to, String subject, String body);
}

public interface WebhookPort {
    void send(WebhookDeliveryCommand command);
}

public interface BrowserPushPort {
    boolean push(String userId, Map<String, Object> event);
}
```
