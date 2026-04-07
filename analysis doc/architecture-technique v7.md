# Architecture technique — Moteur de flux métier & génération de documents

> Projet : activité indépendante complémentaire  
> Stack de base : Java 21 · Spring Boot 3.x · PostgreSQL

---

## 1. Moteur de workflow — Flowable

**Choix retenu : Flowable (Community Edition)**

| Critère | Décision |
|---|---|
| Standard | BPMN 2.0 + DMN |
| Licence | Apache 2.0 |
| Intégration Spring Boot | `flowable-spring-boot-starter` |
| Designer visuel | Flowable UI (web, embarqué) |
| Monitoring instances | Flowable UI Cockpit |

### Dépendance Maven

```xml
<dependency>
    <groupId>org.flowable</groupId>
    <artifactId>flowable-spring-boot-starter</artifactId>
    <version>7.x.x</version>
</dependency>
```

### Alternatives écartées

- **Camunda 7** — en maintenance, migration Camunda 8 complexe (Zeebe/Kafka)
- **jBPM / Kogito** — orienté Quarkus, intégration Spring secondaire
- **Spring State Machine** — pas de designer visuel, adapté aux flux linéaires simples uniquement

---

## 2. Déclenchement des workflows — Event Bus

**Choix retenu : Spring `ApplicationEventPublisher`**

Les workflows sont déclenchés par des événements externes matérialisés en `ApplicationEvent` Spring, puis transmis au moteur Flowable via des `Signal` ou `Message Events` BPMN.

Les sources de triggers sont encapsulées dans le **système de connecteurs** (voir section 15). Le `ConnectorTriggerRuntime` publie les événements sur le bus Spring — les triggers bruts (Kafka, webhook, scheduler…) ne sont plus câblés directement dans le moteur.

```
Connecteur Trigger  →  ConnectorTriggerRuntime  →  ApplicationEventPublisher  →  Flowable
```

---

## 3. Génération de documents

### Stack retenue

```
Thymeleaf  →  HTML  →  jsoup (XHTML)  →  Flying Saucer  →  OpenPDF
```

| Librairie | Rôle | Licence |
|---|---|---|
| Thymeleaf | Fusion template + données | Apache 2.0 |
| jsoup | HTML5 → XHTML strict | MIT |
| Flying Saucer (`xhtmlrenderer`) | Rendu XHTML → PDF layout | LGPL |
| OpenPDF | Écriture PDF (`flying-saucer-pdf-openpdf`) | LGPL / MPL |

### Dépendances Maven

```xml
<dependency>
    <groupId>org.xhtmlrenderer</groupId>
    <artifactId>flying-saucer-pdf-openpdf</artifactId>
    <version>9.9.4</version>
</dependency>
<dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
    <version>1.17.2</version>
</dependency>
```

### Alternatives écartées

- **iText 7 / pdfhtml** — licence AGPL incompatible avec un modèle SaaS/commercial fermé sans licence payante
- **Playwright headless** — écart si CSS3 devient bloquant (Flexbox, Grid) ; gardé en option de repli

### Playwright — option de repli

Conserver l'interface `PdfRenderer` pour switcher sans impact :

```java
public interface PdfRenderer {
    byte[] render(String html);
}
```

Activation via `application.yml` :

```yaml
pdf:
  engine: flying-saucer   # ou : playwright
```

---

## 4. CSS print — contraintes Flying Saucer

Flying Saucer implémente **CSS 2.1** avec quelques extensions CSS 3 sélectives.

### Supporté

| Propriété / Feature | Support |
|---|---|
| `@page`, `size`, `margin` | ✅ |
| `page-break-before/after/inside` | ✅ |
| `display: table / table-cell` | ✅ |
| `position: fixed` (répété chaque page) | ✅ |
| `counter(page)` / `counter(pages)` | ✅ |
| `border-radius`, `opacity` | ✅ |
| Polices TTF/OTF embarquées | ✅ |

### Non supporté

| Feature | Alternative |
|---|---|
| Flexbox / Grid | `display: table` |
| CSS custom properties (`--var`) | Valeurs statiques |
| WOFF / WOFF2 | TTF/OTF uniquement |
| `@font-face` via CDN | Classpath local uniquement |

### Pattern en-tête / pied de page

```css
#entete     { position: fixed; top: -20mm; left: 0; right: 0; height: 22mm; }
#pied       { position: fixed; bottom: -14mm; left: 0; right: 0; height: 10mm; }
#pied:after { content: "Page " counter(page) " / " counter(pages); }
```

---

## 5. Performances — génération PDF

### Ordres de grandeur (JVM chaud, `ITextRenderer` en pool)

| Document | Pages | Temps estimé |
|---|---|---|
| Courrier simple | 1–2 | 50–100 ms |
| Facture avec tableau | 1–3 | 100–200 ms |
| Rapport tabulaire dense | 5–15 | 200–500 ms |
| Document avec images | 2–5 | 150–400 ms |
| Rapport long | 20–50 | 500 ms – 2 s |

### Optimisations clés

- `ITextRenderer` **non thread-safe** → pool `ThreadLocal`
- Images embarquées en **base64 inline** (évite les I/O par génération)
- Tableaux longs : fragmenter en sections plutôt qu'un seul tableau monolithique
- Pour du batch : `ExecutorService` parallèle dimensionné selon les CPU disponibles

---

## 6. Preview des documents

### Stratégie retenue : hybride HTML + PDF inline

| Mode | Mécanisme | Usage |
|---|---|---|
| Preview HTML temps réel | Endpoint `/preview` → HTML + CSS print-preview | Pendant la saisie |
| Aperçu PDF | Endpoint `/pdf` → `Content-Disposition: inline` dans `<iframe>` | Validation finale |

### CSS print-preview (simulation A4 en browser)

```css
@media screen {
    body    { background: #e0e0e0; padding: 20px; }
    .page   { background: white; width: 210mm; min-height: 297mm;
              margin: 0 auto 20px; padding: 20mm 15mm;
              box-shadow: 0 2px 8px rgba(0,0,0,0.2); }
}
```

### Rafraîchissement avec debounce (frontend)

```javascript
let debounceTimer;
inputs.forEach(el => el.addEventListener('input', () => {
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => refreshPreview(), 400);
}));
```

---

## 7. Stockage des documents générés

**Recommandation : MinIO** (compatible S3, self-hosted)

- Auto-hébergeable, licence Apache 2.0
- API S3 standard → migration AWS S3 transparente si nécessaire
- Intégration Spring via `software.amazon.awssdk:s3`

Alternative : stockage filesystem local pour les environnements simples.

---

## 8. Frontend — Vue 3 + PrimeVue

**Choix retenu : Vue 3 (Vite) + PrimeVue + Pinia**

| Composant | Rôle |
|---|---|
| Vue 3 | Framework SPA |
| Vite | Bundler / HMR |
| PrimeVue | Librairie de composants UI métier (DataTable, formulaires, timeline…) |
| Pinia | State management (remplaçant officiel Vuex) |
| Axios | Client HTTP — appels vers l'API Spring Boot |

### Alternatives écartées

- **React** — viable, mais Vue 3 plus adapté aux apps métier formulaires/tableaux avec moins de verbosité
- **Thymeleaf SSR / htmx** — insuffisant pour une application riche avec designer BPMN et preview documentaire

---

## 9. Authentification — OAuth2/OIDC + pattern BFF intégré

### Principe

Le pattern **BFF (Backend For Frontend)** est intégré directement dans l'application Spring Boot — pas de service séparé. Spring Boot joue simultanément le rôle de BFF pour la SPA Vue et de Resource Server pour ses APIs.

```
Vue 3 (browser)
    │  cookie httpOnly (session Spring)
    ▼
Spring Boot — BFF + API        ←→   Authorization Server (OAuth2/OIDC)
    │  access_token JWT (serveur → serveur)
    ▼
  Flowable · PDF · Données
```

Le browser ne manipule **jamais** de token JWT. Il communique avec le BFF via un cookie de session `httpOnly` — inaccessible au JavaScript, protégé contre XSS.

### Pourquoi BFF plutôt que JWT direct dans la SPA

| Approche | XSS | Token exposé | Complexité |
|---|---|---|---|
| JWT en `localStorage` | ❌ Vulnérable | ❌ Oui | Faible |
| JWT en mémoire Pinia | ✅ | ✅ Non | Moyenne |
| **BFF + cookie httpOnly** | ✅ | ✅ Non | Faible |

### Provider OAuth2/OIDC — choix ouvert

L'implémentation Spring Security OAuth2 est **provider-agnostique** via la configuration `issuer-uri`. Le provider est interchangeable sans modifier le code :

| Provider | Usage typique |
|---|---|
| **Keycloak** (self-hosted) | Développement, on-premise, contrôle total |
| **Auth0** | SaaS, démarrage rapide |
| **Microsoft Entra ID** | Clients entreprise Microsoft |
| **Google** | Authentification grand public |

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          keycloak:                          # ou auth0, azure, google…
            client-id: mon-app
            client-secret: ${OIDC_SECRET}
            scope: openid, profile, email
            authorization-grant-type: authorization_code
        provider:
          keycloak:
            issuer-uri: ${OIDC_ISSUER_URI}  # URL du provider — seul paramètre à changer
```

### Flux d'authentification complet

```
1. SPA appelle GET /api/auth/me au démarrage
2. Pas de session → Spring redirige vers le provider OAuth2
3. User s'authentifie sur le provider
4. Provider redirige vers /login/oauth2/code/{registrationId}
5. Spring récupère les tokens, crée une session, pose un cookie httpOnly
6. Spring redirige vers /
7. SPA rappelle GET /api/auth/me → reçoit nom, email, rôles
8. Toutes les requêtes suivantes portent le cookie automatiquement
```

### Dépendances Maven

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

### Endpoint BFF exposé à la SPA

```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal OidcUser user) {
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(Map.of(
            "name",  user.getFullName(),
            "email", user.getEmail(),
            "roles", user.getAuthorities()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok().build();
    }
}
```

### Initialisation côté Vue 3 (Pinia)

```javascript
// stores/auth.js
export const useAuthStore = defineStore('auth', {
    state: () => ({ user: null, authenticated: false }),
    actions: {
        async init() {
            try {
                const { data } = await axios.get('/api/auth/me',
                    { withCredentials: true });
                this.user = data;
                this.authenticated = true;
            } catch {
                window.location.href = '/oauth2/authorization/keycloak';
            }
        }
    }
});
```

---

## 10. Synthèse des dépendances principales

```xml
<!-- Workflow -->
<dependency>
    <groupId>org.flowable</groupId>
    <artifactId>flowable-spring-boot-starter</artifactId>
</dependency>

<!-- Génération PDF -->
<dependency>
    <groupId>org.xhtmlrenderer</groupId>
    <artifactId>flying-saucer-pdf-openpdf</artifactId>
</dependency>
<dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
</dependency>

<!-- Templates -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-thymeleaf</artifactId>
</dependency>

<!-- Sécurité / BFF -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>

<!-- Persistence -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
</dependency>
```

---

## 11. Multi-tenant

### Stratégie d'isolation retenue : schéma PostgreSQL par tenant

```
PostgreSQL (instance unique)
├── schema: tenant_a
│   ├── ACT_RU_*, ACT_HI_*   (Flowable runtime + historique)
│   └── tables métier
└── schema: tenant_b
    ├── ACT_RU_*, ACT_HI_*
    └── tables métier
```

Bon équilibre entre isolation des données, simplicité opérationnelle et support natif Flowable. Migration Liquibase exécutée par schéma à la création de chaque tenant — les changesets idempotents et le système de `contexts` permettent de distinguer l'initialisation d'un nouveau tenant des migrations appliquées à l'ensemble des tenants existants lors d'un déploiement.

### Résolution du tenant — claim OIDC

Le `tenant_id` est porté comme **claim custom dans le token OIDC**, ajouté par le provider (mapper Keycloak ou équivalent). Il est résolu une fois à l'entrée de chaque requête et propagé via un bean `@RequestScope`.

```json
{
  "sub": "user-123",
  "email": "user@tenant-a.com",
  "tenant_id": "tenant_a",
  "realm_access": { "roles": ["WORKFLOW_ADMIN"] }
}
```

### Provider OIDC — Keycloak (realm partagé)

Un realm partagé avec un claim `tenant_id` par utilisateur. Évolutif vers des realms dédiés si un tenant exige une configuration SSO spécifique (Azure AD, Google Workspace…).

### Propagation du tenant — `@RequestScope`

`@RequestScope` est le choix retenu plutôt que `ThreadLocal` — bean Spring géré par le conteneur, compatible Virtual Threads Java 21 sans friction, cycle de vie garanti par la requête HTTP.

```java
// Bean request-scoped — une instance par requête HTTP
@Component
@RequestScope
public class TenantContext {
    private String tenantId;

    public String getTenantId()          { return tenantId; }
    public void   setTenantId(String id) { this.tenantId = id; }
}

// Filtre — résolution du tenant depuis le token OIDC en début de requête
@Component
@RequiredArgsConstructor
public class TenantResolutionFilter extends OncePerRequestFilter {

    private final TenantContext tenantContext;

    @Override
    protected void doFilterInternal(HttpServletRequest req,
            HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder
            .getContext().getAuthentication();

        if (auth != null && auth.getPrincipal() instanceof OidcUser user) {
            String tenantId = user.getClaimAsString("tenant_id");
            tenantContext.setTenantId(tenantId);
        }

        chain.doFilter(req, res);
        // Pas de clear() nécessaire — le bean est détruit en fin de requête
    }
}
```

### Routing du schéma PostgreSQL

```java
// CurrentTenantIdentifierResolver Hibernate — lit le bean request-scoped
@Component
@RequiredArgsConstructor
public class TenantIdentifierResolver
        implements CurrentTenantIdentifierResolver<String> {

    private final TenantContext tenantContext;

    @Override
    public String resolveCurrentTenantIdentifier() {
        String id = tenantContext.getTenantId();
        return id != null ? id : "public"; // fallback schéma public
    }

    @Override
    public boolean validateExistingCurrentSessions() { return true; }
}

// MultiTenantConnectionProvider — switche le search_path PostgreSQL
@Component
@RequiredArgsConstructor
public class SchemaMultiTenantConnectionProvider
        implements MultiTenantConnectionProvider<String> {

    private final DataSource dataSource;

    @Override
    public Connection getConnection(String tenantId) throws SQLException {
        Connection conn = dataSource.getConnection();
        conn.createStatement()
            .execute("SET search_path TO " + tenantId + ", public");
        return conn;
    }

    @Override
    public void releaseConnection(String tenantId, Connection conn)
            throws SQLException {
        conn.createStatement().execute("SET search_path TO public");
        conn.close();
    }
}
```

### Flowable — `tenantId` natif

```java
// Déploiement d'un processus pour un tenant
repositoryService.createDeployment()
    .tenantId(tenantContext.getTenantId())
    .addClasspathResource("processes/mon-workflow.bpmn20.xml")
    .deploy();

// Démarrage d'une instance de processus
runtimeService.createProcessInstanceBuilder()
    .processDefinitionKey("mon-workflow")
    .tenantId(tenantContext.getTenantId())
    .start();
```

### Synthèse des choix multi-tenant

| Couche | Choix |
|---|---|
| Isolation données | Schéma PostgreSQL par tenant |
| Résolution tenant | Claim `tenant_id` dans le token OIDC |
| Provider OIDC | Realm Keycloak partagé + claim custom |
| Propagation Spring | Bean `@RequestScope` — compatible Virtual Threads |
| Flowable | `tenantId` natif sur déploiements et instances |

> **Évolution future** — pour les traitements hors contexte HTTP (batch Flowable, jobs asynchrones), `ScopedValue` (Java 22+) est la voie naturelle pour propager le tenant sans `@RequestScope`.

---

## 12. Synthèse des dépendances principales

```xml
<!-- Workflow -->
<dependency>
    <groupId>org.flowable</groupId>
    <artifactId>flowable-spring-boot-starter</artifactId>
</dependency>

<!-- Génération PDF -->
<dependency>
    <groupId>org.xhtmlrenderer</groupId>
    <artifactId>flying-saucer-pdf-openpdf</artifactId>
</dependency>
<dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
</dependency>

<!-- Templates -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-thymeleaf</artifactId>
</dependency>

<!-- Sécurité / BFF -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>

<!-- Persistence -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
</dependency>
```

---

## 13. Stockage des templates de documents

### Stratégie retenue : base de données (PostgreSQL)

Les templates HTML Thymeleaf sont stockés en base — cohérent avec le contenu (TEXT), et permet le versionning natif avec contrôle de statut.

Plusieurs templates peuvent coexister pour un même type de document selon la situation dans le workflow ou les paramètres d'exécution : variant métier (B2B/B2C, standard/premium, niveau d'escalade…), langue du destinataire, étape du processus. La résolution applique une stratégie de fallback du plus spécifique au plus générique.

### Modèle de données

La clé de résolution est un triplet `(templateKey, variantKey, locale)`. `variantKey` et `locale` sont optionnels — `null` signifie "défaut".

```java
@Entity
@Table(uniqueConstraints = @UniqueConstraint(
    columnNames = {"tenant_id", "template_key", "variant_key", "locale"}
))
public class DocumentTemplate {

    @Id @GeneratedValue
    private UUID id;

    private String templateKey;   // type de document  : "contrat", "facture", "relance"
    private String variantKey;    // variante métier    : "standard", "premium", "B2B"
                                  //                      null = défaut
    private String locale;        // langue             : "fr", "nl", "en"
                                  //                      null = défaut

    private String label;
    private int    version;       // 1, 2, 3…

    @Enumerated(EnumType.STRING)
    private TemplateStatus status; // DRAFT, ACTIVE, ARCHIVED

    @Column(columnDefinition = "TEXT")
    private String htmlContent;   // contenu Thymeleaf complet

    private String  tenantId;
    private Instant createdAt;
    private Instant updatedAt;
    private String  updatedBy;
}
```

### Cycle de vie des versions

```
DRAFT → ACTIVE → ARCHIVED
```

- Une seule version `ACTIVE` par `(tenantId, templateKey, variantKey, locale)` à tout instant
- La résolution à la génération cible toujours la version `ACTIVE`
- Le passage en `ACTIVE` archive automatiquement la version précédente

Contrainte PostgreSQL — unicité de la version active par combinaison :

```sql
CREATE UNIQUE INDEX uq_template_active
    ON document_template (
        tenant_id,
        template_key,
        COALESCE(variant_key, ''),
        COALESCE(locale, '')
    )
    WHERE status = 'ACTIVE';
```

### Résolution en cascade

Le resolver applique un fallback du plus spécifique au plus générique :

```
1. (templateKey, variantKey, locale)  — correspondance exacte
2. (templateKey, variantKey, null)    — variant sans contrainte de langue
3. (templateKey, null,       locale)  — template défaut dans la bonne langue
4. (templateKey, null,       null)    — template défaut absolu
```

```java
@Value @Builder
public class TemplateResolutionRequest {
    String templateKey;  // obligatoire
    String variantKey;   // optionnel — issu des variables du processus Flowable
    String locale;       // optionnel — issu du profil du destinataire
}

@Service
@RequiredArgsConstructor
public class TemplateResolver {

    private final DocumentTemplateRepository repository;
    private final TenantContext tenantContext;

    public DocumentTemplate resolve(TemplateResolutionRequest req) {
        String tenant = tenantContext.getTenantId();

        return repository
            .findActive(tenant, req.getTemplateKey(),
                        req.getVariantKey(), req.getLocale())
            .or(() -> repository.findActive(tenant,
                        req.getTemplateKey(), req.getVariantKey(), null))
            .or(() -> repository.findActive(tenant,
                        req.getTemplateKey(), null, req.getLocale()))
            .or(() -> repository.findActive(tenant,
                        req.getTemplateKey(), null, null))
            .orElseThrow(() -> new TemplateNotFoundException(req));
    }
}
```

### Intégration Flowable — résolution depuis les variables d'exécution

Le `ServiceTask` de génération construit la requête de résolution à partir des variables du processus en cours :

```java
@Component("documentGenerationTask")
@RequiredArgsConstructor
public class DocumentGenerationTask implements JavaDelegate {

    private final TemplateResolver         resolver;
    private final DocumentGenerationService generator;
    private final DocumentStorageService    storage;

    @Override
    public void execute(DelegateExecution exec) {

        TemplateResolutionRequest req = TemplateResolutionRequest.builder()
            .templateKey((String) exec.getVariable("documentType"))
            .variantKey ((String) exec.getVariable("contractTier"))   // ex: "premium"
            .locale     ((String) exec.getVariable("recipientLocale"))// ex: "nl"
            .build();

        DocumentTemplate template = resolver.resolve(req);
        byte[] pdf = generator.generate(template, exec.getVariables());

        exec.setVariable("generatedDocumentId", storage.store(pdf));
    }
}
```

---

## 14. Notifications

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

## 15. Connecteurs

### Rôle

Un connecteur est un composant extensible qui peut jouer deux rôles :

- **Trigger** — reçoit un événement externe et démarre un workflow Flowable
- **Service** — exécute un traitement appelé depuis un `ServiceTask` BPMN ou directement via l'API

Un même connecteur peut remplir les deux rôles simultanément.

### Les trois objets fondamentaux

| Objet | Rôle |
|---|---|
| `ConnectorDefinition` | Type de connecteur — défini par la plateforme ou ajouté au classpath. Décrit les capacités et le schéma de configuration. |
| `ConnectorInstance` | Instance configurée par un tenant à partir d'une définition. Persistée en base avec les valeurs réelles. |
| `ConnectorExecution` | Exécution d'un connecteur dans un contexte donné (trigger entrant ou appel ServiceTask / API). |

### Interfaces d'extension

```java
// Capacité trigger — reçoit un événement entrant et produit un déclenchement de workflow
public interface TriggerConnector {
    WorkflowTriggerEvent onEvent(ConnectorInstance instance,
                                 Map<String, Object> rawPayload);
}

// Capacité service — exécute un traitement et retourne des variables
public interface ServiceConnector {
    Map<String, Object> execute(ConnectorInstance instance,
                                Map<String, Object> inputParams);
}

// Base commune — tout connecteur l'implémente
public interface ConnectorDefinition {
    String              getType();            // identifiant unique : "http-rest", "smtp"
    String              getLabel();           // libellé UI
    ConnectorCapability getCapability();      // TRIGGER, SERVICE, BOTH
    List<ConfigField>   getConfigSchema();    // schéma pour génération du formulaire UI
    void                validate(Map<String, Object> config);
}
```

Le schéma de configuration est déclaratif — l'UI génère le formulaire dynamiquement sans connaître le connecteur à l'avance :

```java
@Value @Builder
public class ConfigField {
    String        name;
    String        label;
    FieldType     type;        // STRING, PASSWORD, URL, INTEGER, BOOLEAN, SELECT
    boolean       required;
    String        defaultValue;
    List<String>  options;     // pour type SELECT
    String        helpText;
}
```

### Catalogue de connecteurs fournis

| Type | Capacité | Description |
|---|---|---|
| `http-rest` | TRIGGER + SERVICE | Réception webhook entrant / appel REST sortant |
| `scheduler` | TRIGGER | Déclenchement planifié (cron) |
| `smtp` | SERVICE | Envoi email |
| `s3-minio` | SERVICE | Lecture / écriture fichiers |
| `flowable-signal` | TRIGGER + SERVICE | Émet ou reçoit un signal Flowable |

### Ajout d'un connecteur custom — classpath

Un connecteur custom est un `@Component` Spring qui implémente `ConnectorDefinition` et l'une ou les deux interfaces de capacité. Il est découvert automatiquement au démarrage.

```java
@Component
public class D365ConnectorDefinition
        implements ConnectorDefinition, ServiceConnector {

    @Override public String getType()  { return "d365-fo"; }
    @Override public String getLabel() { return "Microsoft D365 F&O"; }

    @Override
    public ConnectorCapability getCapability() {
        return ConnectorCapability.SERVICE;
    }

    @Override
    public List<ConfigField> getConfigSchema() {
        return List.of(
            ConfigField.builder().name("tenantId")
                .label("Azure Tenant ID").type(FieldType.STRING).required(true).build(),
            ConfigField.builder().name("clientId")
                .label("Client ID").type(FieldType.STRING).required(true).build(),
            ConfigField.builder().name("clientSecret")
                .label("Client Secret").type(FieldType.PASSWORD).required(true).build(),
            ConfigField.builder().name("baseUrl")
                .label("D365 Base URL").type(FieldType.URL).required(true).build()
        );
    }

    @Override
    public Map<String, Object> execute(ConnectorInstance instance,
                                       Map<String, Object> params) {
        // Appel D365 OData / REST
    }
}
```

### ConnectorRegistry — découverte automatique

```java
@Component
@RequiredArgsConstructor
public class ConnectorRegistry {

    // Spring injecte toutes les implémentations présentes dans le classpath
    private final List<ConnectorDefinition> definitions;
    private Map<String, ConnectorDefinition> catalogue;

    @PostConstruct
    public void init() {
        catalogue = definitions.stream()
            .collect(Collectors.toMap(ConnectorDefinition::getType,
                                      Function.identity()));
    }

    public ConnectorDefinition get(String type) {
        return Optional.ofNullable(catalogue.get(type))
            .orElseThrow(() -> new UnknownConnectorException(type));
    }

    public List<ConnectorDefinition> listAll() {
        return List.copyOf(definitions);
    }
}
```

### ConnectorInstance — entité tenant

```java
@Entity
public class ConnectorInstance {

    @Id @GeneratedValue
    private UUID id;

    private String  tenantId;
    private String  name;           // nom donné par l'admin du tenant
    private String  connectorType;  // référence ConnectorDefinition.getType()
    private boolean enabled;

    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> config; // champs PASSWORD chiffrés au repos (AES-GCM)

    private Instant createdAt;
    private Instant updatedAt;
}
```

Les champs de type `PASSWORD` déclarés dans `getConfigSchema()` sont chiffrés au repos via un `AttributeConverter` JPA — clé de chiffrement par tenant.

### Intégration Flowable — rôle service

Un `ServiceTask` BPMN référence une instance de connecteur par son `id` via une variable de processus :

```java
@Component("connectorServiceTask")
@RequiredArgsConstructor
public class ConnectorServiceTask implements JavaDelegate {

    private final ConnectorRegistry            registry;
    private final ConnectorInstanceRepository  instances;
    private final TenantContext                tenantContext;

    @Override
    public void execute(DelegateExecution exec) {
        UUID instanceId = UUID.fromString(
            (String) exec.getVariable("connectorInstanceId"));

        ConnectorInstance instance = instances
            .findByIdAndTenantId(instanceId, tenantContext.getTenantId())
            .orElseThrow();

        ServiceConnector connector =
            (ServiceConnector) registry.get(instance.getConnectorType());

        Map<String, Object> result =
            connector.execute(instance, exec.getVariables());

        // Les résultats sont injectés comme variables du processus
        result.forEach(exec::setVariable);
    }
}
```

### Intégration Flowable — rôle trigger

```java
@Component
@RequiredArgsConstructor
public class ConnectorTriggerRuntime {

    private final RuntimeService  runtimeService;
    private final ConnectorRegistry registry;

    public void onIncomingEvent(ConnectorInstance instance,
                                Map<String, Object> rawPayload) {

        TriggerConnector connector =
            (TriggerConnector) registry.get(instance.getConnectorType());

        WorkflowTriggerEvent event = connector.onEvent(instance, rawPayload);

        runtimeService.createProcessInstanceBuilder()
            .processDefinitionKey(event.getProcessKey())
            .tenantId(instance.getTenantId())
            .variables(event.getVariables())
            .start();
    }
}
```

### Appel direct via API

Les connecteurs de type SERVICE sont également appelables hors contexte workflow :

```java
@RestController
@RequestMapping("/api/connectors")
@RequiredArgsConstructor
public class ConnectorExecutionController {

    private final ConnectorRegistry           registry;
    private final ConnectorInstanceRepository instances;
    private final TenantContext               tenantContext;

    @PostMapping("/{instanceId}/execute")
    public ResponseEntity<Map<String, Object>> execute(
            @PathVariable UUID instanceId,
            @RequestBody Map<String, Object> inputParams) {

        ConnectorInstance instance = instances
            .findByIdAndTenantId(instanceId, tenantContext.getTenantId())
            .orElseThrow();

        ServiceConnector connector =
            (ServiceConnector) registry.get(instance.getConnectorType());

        return ResponseEntity.ok(connector.execute(instance, inputParams));
    }
}
```

---

## 16. Architecture hexagonale — Ports & Adapters

### Principe

Le domaine métier est isolé de toute technologie. Il ne connaît ni Spring, ni JPA, ni Flowable, ni HTTP. Les frameworks et librairies sont des détails d'implémentation branchés sur des **ports** via des **adaptateurs**.

La règle de dépendance est stricte et unidirectionnelle — tout pointe vers le domaine, jamais l'inverse :

```
[Adaptateurs entrants]  →  [Ports entrants]  →  [Domaine]  →  [Ports sortants]  ←  [Adaptateurs sortants]

Exemples entrants :            Exemples sortants :
  REST Controller                WorkflowRepository
  ConnectorTriggerRuntime        PdfRenderer
  Flowable ServiceTask           NotificationPort
  Scheduler                      ConnectorExecutionPort
```

### Structure de packages

```
com.monapp
│
├── domain                          ← zéro dépendance framework
│   │
│   ├── workflow
│   │   ├── model                   # WorkflowInstance, WorkflowStatus, Task…
│   │   ├── usecase                 # StartWorkflowUseCase, CompleteTaskUseCase…
│   │   └── port
│   │       ├── in                  # interfaces appelées par les adaptateurs entrants
│   │       │   ├── StartWorkflowUseCase
│   │       │   └── CompleteTaskUseCase
│   │       └── out                 # interfaces implémentées par les adaptateurs sortants
│   │           ├── WorkflowRepository
│   │           └── WorkflowEventPublisher
│   │
│   ├── document
│   │   ├── model                   # DocumentTemplate, GeneratedDocument, TemplateStatus…
│   │   ├── usecase                 # GenerateDocumentUseCase, PublishTemplateUseCase…
│   │   └── port
│   │       ├── in
│   │       │   ├── GenerateDocumentUseCase
│   │       │   └── PublishTemplateUseCase
│   │       └── out
│   │           ├── DocumentTemplateRepository
│   │           ├── PdfRenderer             ← Flying Saucer ou Playwright
│   │           └── DocumentStoragePort     ← MinIO ou filesystem
│   │
│   ├── connector
│   │   ├── model                   # ConnectorInstance, ConnectorDefinition, ConfigField…
│   │   ├── usecase                 # RegisterConnectorUseCase, ExecuteConnectorUseCase…
│   │   └── port
│   │       ├── in
│   │       │   ├── ExecuteConnectorUseCase
│   │       │   └── RegisterConnectorInstanceUseCase
│   │       └── out
│   │           ├── ConnectorInstanceRepository
│   │           └── ConnectorDefinitionRegistry
│   │
│   └── notification
│       ├── model                   # NotificationEvent, NotificationChannel…
│       ├── usecase                 # SendNotificationUseCase
│       └── port
│           ├── in
│           │   └── SendNotificationUseCase
│           └── out
│               ├── EmailPort
│               ├── WebhookPort
│               └── BrowserPushPort
│
└── infrastructure                  ← dépend du domaine, jamais l'inverse
    │
    ├── web                         # Adaptateurs entrants — Spring MVC REST
    │   ├── workflow
    │   ├── document
    │   ├── connector
    │   └── notification
    │
    ├── persistence                 # Adaptateurs sortants — Spring Data JPA
    │   ├── workflow                # WorkflowJpaAdapter implements WorkflowRepository
    │   ├── document                # DocumentTemplateJpaAdapter
    │   └── connector               # ConnectorInstanceJpaAdapter
    │
    ├── flowable                    # Adaptateur Flowable
    │   ├── FlowableWorkflowAdapter # implements WorkflowEventPublisher
    │   ├── ConnectorServiceTask    # JavaDelegate → ConnectorExecutionPort
    │   └── ConnectorTriggerRuntime # écoute Flowable → publie sur le domaine
    │
    ├── pdf                         # Adaptateurs sortants — rendu PDF
    │   ├── FlyingSaucerPdfAdapter  # implements PdfRenderer (défaut)
    │   └── PlaywrightPdfAdapter    # implements PdfRenderer (repli CSS3)
    │
    ├── storage                     # Adaptateurs sortants — stockage fichiers
    │   ├── MinioStorageAdapter     # implements DocumentStoragePort
    │   └── FilesystemStorageAdapter
    │
    ├── notification                # Adaptateurs sortants — canaux de notification
    │   ├── SmtpEmailAdapter        # implements EmailPort
    │   ├── WebhookRestAdapter      # implements WebhookPort
    │   └── SseNotificationAdapter  # implements BrowserPushPort
    │
    └── connector                   # Infrastructure connecteurs
        ├── registry                # ConnectorRegistry — découverte classpath
        └── builtin                 # Connecteurs fournis (http-rest, smtp, s3…)
```

### Règles d'implémentation

**Le domaine est pur Java.** Aucune annotation Spring (`@Component`, `@Service`, `@Repository`), aucune annotation JPA (`@Entity`, `@Column`), aucune dépendance Flowable dans `com.monapp.domain`.

**Les entités JPA sont distinctes des entités domaine.** Une entité domaine `WorkflowInstance` a son équivalent `WorkflowJpaEntity` dans `infrastructure.persistence`. L'adaptateur JPA assure la conversion.

```java
// domain/workflow/model — pur Java
public class WorkflowInstance {
    private UUID   id;
    private String processKey;
    private String tenantId;
    private WorkflowStatus status;

    public void complete() {
        if (status != WorkflowStatus.RUNNING)
            throw new InvalidStateException("Workflow non actif");
        this.status = WorkflowStatus.COMPLETED;
    }
}

// domain/workflow/port/out — interface définie par le domaine
public interface WorkflowRepository {
    void save(WorkflowInstance instance);
    Optional<WorkflowInstance> findById(UUID id);
}

// infrastructure/persistence/workflow — implémentation JPA
@Repository
@RequiredArgsConstructor
public class WorkflowJpaAdapter implements WorkflowRepository {
    private final WorkflowJpaRepository jpa;

    @Override
    public void save(WorkflowInstance instance) {
        jpa.save(WorkflowJpaEntity.from(instance));
    }

    @Override
    public Optional<WorkflowInstance> findById(UUID id) {
        return jpa.findById(id).map(WorkflowJpaEntity::toDomain);
    }
}
```

**Les use cases sont des services domaine.** Ils reçoivent leurs dépendances (ports sortants) par injection de constructeur. Spring câble l'implémentation concrète sans que le use case le sache.

```java
// domain/workflow/usecase — pur Java, pas @Service Spring
public class StartWorkflowUseCaseImpl implements StartWorkflowUseCase {

    private final WorkflowRepository      repository;    // port sortant
    private final WorkflowEventPublisher  publisher;     // port sortant

    public StartWorkflowUseCaseImpl(WorkflowRepository repository,
                                    WorkflowEventPublisher publisher) {
        this.repository = repository;
        this.publisher  = publisher;
    }

    @Override
    public WorkflowInstance start(StartWorkflowCommand cmd) {
        WorkflowInstance instance = WorkflowInstance.create(cmd);
        repository.save(instance);
        publisher.publish(new WorkflowStartedEvent(instance));
        return instance;
    }
}
```

**Les connecteurs sont des adaptateurs sortants.** `ConnectorDefinition` / `TriggerConnector` / `ServiceConnector` sont des ports sortants du domaine `connector`. Chaque connecteur (D365, HTTP REST, SMTP…) est un adaptateur dans `infrastructure.connector.builtin`.

### Bénéfices concrets pour ce projet

**Testabilité totale du domaine** — les use cases sont testés sans Spring, sans base, sans Flowable. Les ports sortants sont mockés.

```java
@Test
void should_complete_workflow() {
    // Arrange — mocks des ports, pas de Spring context
    WorkflowRepository  repo      = mock(WorkflowRepository.class);
    WorkflowEventPublisher pub    = mock(WorkflowEventPublisher.class);
    StartWorkflowUseCase useCase  =
        new StartWorkflowUseCaseImpl(repo, pub);

    // Act
    WorkflowInstance instance =
        useCase.start(new StartWorkflowCommand("mon-processus", "tenant-a"));

    // Assert
    assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.RUNNING);
    verify(repo).save(instance);
}
```

**Remplacement du moteur PDF transparent** — `FlyingSaucerPdfAdapter` et `PlaywrightPdfAdapter` implémentent tous les deux `PdfRenderer`. Le switch se fait par configuration Spring, le domaine ne change pas.

**Isolation de Flowable** — si Flowable évolue ou est remplacé, seul `infrastructure.flowable` est impacté. Le domaine métier est protégé.

---

## 17. Visualisation des instances de workflow

### Principe

La visualisation est rendue dans le browser via **BPMN.js** (`bpmn-js` — `NavigatedViewer`) à partir du XML BPMN natif Flowable. Aucune conversion de format — le XML produit par Flowable est consommé directement. Les états (actif, terminé, erreur) sont appliqués par **overlays CSS** sur le rendu.

### Accès

La visualisation est accessible aux utilisateurs impliqués dans l'instance (assignés à une tâche, initiateurs, observateurs configurés). Le contrôle d'accès est géré par le port `WorkflowParticipantPort`.

### Endpoints backend

| Méthode | Endpoint | Description |
|---|---|---|
| `GET` | `/api/workflows/{instanceId}/diagram` | XML BPMN + IDs actifs / terminés / en erreur |
| `GET` | `/api/workflows/{instanceId}/history` | Étapes avec timing, durée et assigné |
| `GET` | `/api/workflows/{instanceId}/tasks` | Tâches actives avec assigné courant |
| `POST` | `/api/workflows/{instanceId}/tasks/{taskId}/reassign` | Réassignation de tâche |

### Modèles de réponse

```java
// Réponse diagram
public record WorkflowDiagramView(
    String       bpmnXml,        // XML BPMN natif Flowable → consommé par BPMN.js
    List<String> activeIds,      // activités en cours
    List<String> completedIds,   // activités terminées
    List<String> failedIds       // activités en erreur
) {}

// Entrée d'historique
@Value @Builder
public class ActivityHistoryEntry {
    String   activityId;
    String   name;
    String   assignee;
    Instant  startTime;
    Instant  endTime;       // null si encore en cours
    Long     durationMs;
}
```

### Adaptateur Flowable

```java
@Component
@RequiredArgsConstructor
public class FlowableWorkflowVisualizationAdapter
        implements WorkflowVisualizationPort {

    private final RepositoryService repositoryService;
    private final RuntimeService    runtimeService;
    private final HistoryService    historyService;
    private final TaskService       taskService;

    @Override
    public WorkflowDiagramView getDiagram(String instanceId, String tenantId) {

        HistoricProcessInstance hpi = historyService
            .createHistoricProcessInstanceQuery()
            .processInstanceId(instanceId)
            .processInstanceTenantId(tenantId)
            .singleResult();

        // XML BPMN natif — transmis directement à BPMN.js sans conversion
        BpmnModel model  = repositoryService
            .getBpmnModel(hpi.getProcessDefinitionId());
        String    bpmnXml = new BpmnXMLConverter()
            .convertToXML(model).toString();

        List<String> activeIds = runtimeService
            .getActiveActivityIds(instanceId);

        List<String> completedIds = historyService
            .createHistoricActivityInstanceQuery()
            .processInstanceId(instanceId).finished().list()
            .stream().map(HistoricActivityInstance::getActivityId)
            .distinct().toList();

        List<String> failedIds = historyService
            .createHistoricActivityInstanceQuery()
            .processInstanceId(instanceId).list().stream()
            .filter(a -> a.getDeleteReason() != null
                      && a.getDeleteReason().startsWith("error"))
            .map(HistoricActivityInstance::getActivityId).toList();

        return new WorkflowDiagramView(bpmnXml, activeIds,
                                       completedIds, failedIds);
    }

    @Override
    public List<ActivityHistoryEntry> getHistory(String instanceId,
                                                  String tenantId) {
        return historyService
            .createHistoricActivityInstanceQuery()
            .processInstanceId(instanceId)
            .orderByHistoricActivityInstanceStartTime().asc()
            .list().stream()
            .map(a -> ActivityHistoryEntry.builder()
                .activityId(a.getActivityId())
                .name      (a.getActivityName())
                .assignee  (a.getAssignee())
                .startTime (a.getStartTime().toInstant())
                .endTime   (a.getEndTime() != null
                              ? a.getEndTime().toInstant() : null)
                .durationMs(a.getDurationInMillis())
                .build())
            .toList();
    }

    @Override
    public void reassignTask(String taskId, String assigneeId,
                             String tenantId) {
        taskService.setAssignee(taskId, assigneeId);
    }
}
```

### Frontend Vue 3 — composant WorkflowViewer

**Dépendance npm :**

```bash
npm install bpmn-js
```

**Rendu et coloration :**

```javascript
import BpmnViewer from 'bpmn-js/lib/NavigatedViewer';

const viewer = new BpmnViewer({ container: '#bpmn-canvas' });

async function loadDiagram(instanceId) {
    const { bpmnXml, activeIds, completedIds, failedIds }
        = await api.get(`/api/workflows/${instanceId}/diagram`);

    // Rendu du XML natif Flowable — aucune conversion
    await viewer.importXML(bpmnXml);

    const canvas   = viewer.get('canvas');
    const overlays = viewer.get('overlays');

    completedIds.forEach(id => canvas.addMarker(id, 'highlight-completed'));
    activeIds.forEach   (id => canvas.addMarker(id, 'highlight-active'));
    failedIds.forEach   (id => canvas.addMarker(id, 'highlight-failed'));

    // Badge visuel sur les étapes actives
    activeIds.forEach(id => overlays.add(id, {
        position: { top: -12, right: -12 },
        html: '<div class="bpmn-badge active">●</div>'
    }));
}
```

**CSS des overlays :**

```css
.highlight-completed .djs-visual > :nth-child(1) {
    fill: #dcfce7 !important; stroke: #16a34a !important;
}
.highlight-active .djs-visual > :nth-child(1) {
    fill: #dbeafe !important; stroke: #2563eb !important;
}
.highlight-failed .djs-visual > :nth-child(1) {
    fill: #fee2e2 !important; stroke: #dc2626 !important;
}
.bpmn-badge.active {
    background: #2563eb; color: white;
    border-radius: 50%; width: 16px; height: 16px;
    font-size: 10px; display: flex;
    align-items: center; justify-content: center;
}
```

### Notification de changement d'état — SSE

Le canal SSE existant est réutilisé. Un `FlowableEventListener` notifie les utilisateurs impliqués à chaque transition significative — la vue affiche une bannière de rafraîchissement sans polling.

```java
@Component
@RequiredArgsConstructor
public class WorkflowStateEventListener implements FlowableEventListener {

    private final SendNotificationUseCase notification;
    private final WorkflowParticipantPort participants;

    private static final Set<FlowableEngineEventType> RELEVANT = Set.of(
        FlowableEngineEventType.TASK_ASSIGNED,
        FlowableEngineEventType.ACTIVITY_COMPLETED,
        FlowableEngineEventType.PROCESS_COMPLETED
    );

    @Override
    public void onEvent(FlowableEvent raw) {
        if (!(raw instanceof FlowableEngineEvent event)) return;
        if (!RELEVANT.contains(event.getType()))        return;

        String instanceId = event.getProcessInstanceId();

        participants.findByInstanceId(instanceId).forEach(userId ->
            notification.send(NotificationEvent.builder()
                .channel (NotificationChannel.BROWSER)
                .userId  (userId)
                .type    ("WORKFLOW_STATE_CHANGED")
                .payload (Map.of("instanceId", instanceId))
                .build())
        );
    }

    @Override public boolean isFailOnException()                   { return false; }
    @Override public boolean isFireOnTransactionLifecycleEvent()   { return false; }
}
```

Côté Vue 3, la notification déclenche une bannière — rafraîchissement au clic :

```javascript
watch(notifications, (newNotifs) => {
    const latest = newNotifs[0];
    if (latest?.type === 'WORKFLOW_STATE_CHANGED'
     && latest.payload.instanceId === props.instanceId) {
        showRefreshBanner.value = true;
    }
});
```

### Ports domaine ajoutés

```java
// domain/workflow/port/out
public interface WorkflowVisualizationPort {
    WorkflowDiagramView      getDiagram(String instanceId, String tenantId);
    List<ActivityHistoryEntry> getHistory(String instanceId, String tenantId);
    void reassignTask(String taskId, String assigneeId, String tenantId);
}

// Résolution des utilisateurs impliqués dans une instance
public interface WorkflowParticipantPort {
    List<String> findByInstanceId(String instanceId); // retourne les userIds
}
```

### Mise à jour de la structure de packages

Dans `infrastructure/flowable` :

```
infrastructure/flowable
├── FlowableWorkflowAdapter
├── FlowableWorkflowVisualizationAdapter  ← nouveau
├── WorkflowStateEventListener            ← nouveau
├── ConnectorServiceTask
└── ConnectorTriggerRuntime
```

---

## 18. Synthèse des dépendances principales

```xml
<!-- Workflow -->
<dependency>
    <groupId>org.flowable</groupId>
    <artifactId>flowable-spring-boot-starter</artifactId>
</dependency>

<!-- Génération PDF -->
<dependency>
    <groupId>org.xhtmlrenderer</groupId>
    <artifactId>flying-saucer-pdf-openpdf</artifactId>
</dependency>
<dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
</dependency>

<!-- Templates -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-thymeleaf</artifactId>
</dependency>

<!-- Sécurité / BFF -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>

<!-- Persistence -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
</dependency>

<!-- Notifications -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.retry</groupId>
    <artifactId>spring-retry</artifactId>
</dependency>
```

**Frontend (npm) :**

```bash
# Visualisation BPMN
npm install bpmn-js
```
