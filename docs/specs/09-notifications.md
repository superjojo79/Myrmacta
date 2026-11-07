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

// domain/notification/port/out — appelés par NotificationOutboxRelay (EmailPort aussi par le connecteur smtp
// et les invitations portail / signature)
public interface EmailPort {
    default void send(String to, String subject, String body) { send(to, subject, body, null); }
    void send(String to, String subject, String body, EmailAttachmentContent attachment); // issue #183
}

public interface WebhookPort {
    void send(WebhookDeliveryCommand command);
}

public interface BrowserPushPort {
    boolean push(String userId, Map<String, Object> event);
}
```

---

## Implémentation — pièce jointe de « Notifier » (issue #183, TODO §96)

- **Paramètres** : `flowable:notificationAttachment="variable:<nom>|dossierField:<champ>"` et
  `flowable:notificationAttachmentName` (facultatif), lus **seulement** dans le BPMN déployé (D-NOTIF-ATTACH-LITERAL) ;
  forme vérifiée au déploiement (`BlockAttributeRules`) et dans la palette. Avec une pièce jointe, `notificationTo` et
  `notificationChannels` (avec `EMAIL`) doivent être écrits dans le BPMN (déploiement, palette et exécution).
- **Émission** (`NotificationDispatchTask`, transaction du process) : la source est résolue en référence de stockage,
  puis `ResolveEmailAttachmentUseCase` exige un élément `PRODUCED_DOC` **du dossier courant** portant cette référence,
  un contenu non vide, PDF (`%PDF-`), sous `app.notification.email.attachment-max-bytes` (défaut 7 Mio)
  (D-NOTIF-ATTACH-PRODUCED-DOC, D-NOTIF-ATTACH-MAX). Échec : `NotificationValidationException` (422 ou incident si
  asynchrone), message sans la valeur. Canal `EMAIL` absent : refus (D-NOTIF-ATTACH-EMAIL-ONLY).
- **Outbox** : le snapshot e-mail porte `attachment: {storageRef, fileName, contentType}`, jamais les octets
  (D-NOTIF-ATTACH-REF-IN-OUTBOX). Le relais relit le contenu dans le stockage du tenant de la ligne et revérifie le
  plafond ; document absent : échec ordinaire de la ligne (tentatives, puis `DEAD`), message sans la référence.
- **SMTP** : `SmtpEmailAdapter` construit un `multipart/mixed` (texte + PDF nommé), en-tête `Auto-Submitted` conservé.
  Le connecteur `smtp` n'envoie pas de pièce jointe (D-NOTIF-ATTACH-SMTP-CONNECTOR).
- **Tests de référence** : `ResolveEmailAttachmentUseCaseImplTest`, `NotificationDispatchAttachmentTest`,
  `NotificationOutboxRelayTest`, `SmtpEmailAdapterTest`, `NotifierAttachmentIT` (moteur réel, outbox et relais réels),
  `BlockAttributeRulesTest`, `blockDiagnostics.spec.ts`, E2E-PALETTE-10.

---

## Implémentation — gabarits de notification (issue #19, §123)

- **Comportement.** Une brique « Notifier » qui porte `flowable:notificationTemplate="<clé>"` n'a plus d'objet ni de corps
  littéraux : ils sont rendus à l'exécution depuis un gabarit (`notification_template`, changeSet 049). Un gabarit a une clé,
  une variante et une langue facultatives, un objet, un corps texte et un corps HTML facultatif, et suit le cycle de vie des
  templates de document (`DRAFT → ACTIVE → ARCHIVED`, une seule version active par combinaison, cascade de résolution à
  quatre niveaux, [spec 08](08-templates-documents.md)). L'objet et le corps texte sont rendus par Thymeleaf en mode `TEXT`
  (inline `[[${…}]]`, dont les entités HTML sont décodées), le corps HTML en mode `HTML`, avec la grammaire D-40 dans les deux
  cas. Le modèle est celui des expressions métier (`BusinessExpressionContext`) sans `connectorResult` : `dossier`
  (référence, type, statut, libellé, champs déclarés non sensibles) et `variables` (variables de process scalaires)
  (D-NOTIF-TPL-MODEL). Avec un corps HTML, `SmtpEmailAdapter` produit un `multipart/alternative` (texte et HTML), sinon le
  texte seul ; `Auto-Submitted: auto-generated` est toujours posé. Le HTML rendu part dans l'outbox (`htmlBody`, absent des
  anciennes lignes, qui restent lisibles et partent en texte seul). Sans `notificationTemplate`, la brique ne change pas.
- **Gestion.** API REST `/api/notification-templates` (`TENANT_ADMIN` seul : liste, détail, `resolve`, création, mise à jour
  d'un brouillon, activation, archivage, nouvelle version, aperçu), écran `/admin/notification-templates`, outils MCP
  `define_notification_template` (brouillon seulement, contrôle avant écriture, `dryRun`, corps jamais renvoyés),
  `list_notification_templates` et `preview_notification_template` (mode conception) : l'activation reste un geste humain.
  La version active de chaque combinaison voyage dans la promotion de configuration (`NOTIFICATION_TEMPLATE`,
  `NotificationTemplateArtifacts`) ; l'import crée un brouillon, jamais activé.
- **Garde-fous.**
  - *Politique HTML* (`NotificationHtmlPolicy`, D-NOTIF-TPL-HTML-POLICY) : balises de mise en forme seulement, ni image, ni
    `<style>`, ni script, ni formulaire ; liens `http`, `https` et `mailto` ; attribut `style` sans `url(`, `expression(`,
    `@import` ni `javascript:` ; attributs `th:` limités à `text`, `if`, `unless`, `each`, `with`, `switch`, `case`, `remove`
    et l'élément `th:block`. Le corps est **refusé** à l'activation (400, règle nommée), jamais nettoyé en silence ; le HTML
    rendu repasse par un nettoyeur jsoup de même politique (défense en profondeur).
  - *Rendu strict* (D-NOTIF-TPL-STRICT) : une variable ou une propriété absente ou nulle fait échouer le rendu, comme pour
    un document. La brique lève `NotificationValidationException` (incident si elle est asynchrone, 422 sinon) et rien
    n'est écrit dans l'outbox ; le message nomme le gabarit et l'expression, jamais une valeur. Un gabarit sans version
    active échoue de même, à l'exécution. Bornes (`app.notification.template.*`) : objet de 500 caractères au plus dans le
    gabarit et de 255 une fois rendu (sans CR/LF), corps texte de 64 Kio, corps HTML de 256 Kio, 1 Mio rendus au total,
    modèle d'aperçu de 64 Kio.
  - *Champs sensibles* : un champ `sensitive` n'est jamais exposé, `${dossier.fields.<champ>}` échoue comme une propriété
    absente. Risque résiduel assumé : une variable de process qui recopierait un champ sensible serait rendue ; il est
    documenté dans le [guide de conception](../guides/GUIDE-CONCEPTION-BPMN.md) §6c.
  - *Attributs lus dans le BPMN déployé* (D-NOTIF-TPL-LITERAL) : `notificationTemplate`, `notificationVariant` et
    `notificationLocale` ne sont jamais lus dans une variable de process du même nom. `BlockAttributeRules` refuse au
    déploiement un gabarit combiné à `notificationSubject` ou `notificationBody`, sans canal `EMAIL` ou avec un autre canal
    en plus, ou sans `notificationTo` écrit dans le BPMN, et une variante ou une langue sans gabarit. La clé du gabarit n'est
    pas vérifiée au déploiement (il peut arriver par la promotion).
- **Limites.** Les rappels et escalades d'échéance, les consignes des tâches du portail, les canaux webhook et navigateur et le
  connecteur `smtp` restent hors périmètre (D-NOTIF-TPL-SCOPE) ; pas d'éditeur WYSIWYG, pas de langue du destinataire déduite
  de son profil (la langue est un attribut ou une variable de la brique). Le contenu rendu est conservé en clair dans l'outbox
  jusqu'à l'envoi, comme l'adresse du destinataire.
- **Exemples.** [`exemples/digital-mail-room`](../../exemples/digital-mail-room/README.md) : accusé de réception de la mail
  room ([spec 49](49-digital-mail-room.md) §3) ; [`exemples/autorisation-riverain`](../../exemples/autorisation-riverain/README.md) :
  e-mail « informations complémentaires » qui cite la motivation saisie à l'instruction.
- **Tests de référence.**
  - Domaine et rendu : `NotificationTemplateUseCasesTest`, `ThymeleafNotificationTemplateRendererTest`,
    `ThymeleafNotificationTemplateCheckerTest`, `NotificationHtmlPolicyTest`, `SafeHtmlTemplateEngineFactoryTest`.
  - Brique et déploiement : `NotificationDispatchTemplateTest`, `BlockAttributeRulesTest`, `BuildingBlockCatalogTest`,
    `ParcoursCompilerTest`, `FlowableBpmnParserAdapterBlockTest`.
  - Envoi : `NotifierTemplateEmailIT` (moteur, outbox et relais réels, serveur SMTP en mémoire : objet, parties, en-tête
    anti-boucle, variable absente synchrone et asynchrone, champ sensible), `NotificationOutboxRelayTest`,
    `SmtpEmailAdapterTest`.
  - API, MCP et promotion : `NotificationTemplateApiTest`, `NotificationTemplatePersistenceIT`,
    `NotificationTemplateToolsTest`, `NotificationTemplateMcpChainTest`, `NotificationTemplateArtifactsTest`,
    `NotificationTemplatePromotionApiTest`.
  - Écran : `NotificationTemplatesAdminView.spec.ts`, `notificationTemplates.spec.ts`,
    `pilotage-gabarits-notification.spec.ts` (Playwright), `ConfigurationPromotionView.spec.ts`.
  - Exemples : `MailRoomExampleIT` (accusé de réception : e-mail attesté, courrier papier, expéditeur non attesté, réponse
    rattachée), `RiverainInformationRequestExampleIT` et `RiverainInformationRequestExampleTest`.
