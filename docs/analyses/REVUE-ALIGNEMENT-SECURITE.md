# Revue alignement × sécurité — FlowForge

> Revue **post-implémentation** (2026-08-25) : alignement du code livré sur la
> spécification de bout en bout (`docs/specs/` → `docs/analyses/REVUE-PRE-IMPLEMENTATION.md` /
> `TODO.md` → artefacts AI-devkit `docs/ai/**` → sources), et audit de sécurité
> (OWASP / checklist skill `security-review`).
>
> Ce document porte le **contexte détaillé** des findings — constat, preuves
> fichier:ligne, options, documents impactés.
> [registre des décisions](../decisions/README.md) porte l'**énoncé et le suivi** des points **40–46**.
> Les deux se lisent ensemble, comme [docs/analyses/REVUE-PRE-IMPLEMENTATION.md](REVUE-PRE-IMPLEMENTATION.md)
> pour les points 1–39.
>
> **Aucun correctif applicatif n'est livré avec cette revue.** Les options
> ci-dessous ne sont pas tranchées tant qu'elles ne sont pas actées dans
> `TODO.md` (ne pas « trancher silencieusement »).

---

## 1. Périmètre et méthode

| | |
|---|---|
| Cible | Dépôt complet au commit `797df03` (`main`, PR #71 `edition-bpmn` mergée) |
| Specs | `docs/specs/00` → `14`, `README.md`, `TODO.md` (points 1–39 tous `[x]`), [`FILE-ATTENTE.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/FILE-ATTENTE.md), [`DEFERRED-FEATURES.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/DEFERRED-FEATURES.md) |
| Lifecycle AI-devkit | `docs/ai/{requirements,design,planning,implementation,testing,monitoring,deployment}/` |
| Code | `com.flowforge.{domain,application,infrastructure}`, `frontend/src`, `application.yml`, `pom.xml`, `docker-compose.yml` |
| Angle | (1) décisions d'architecture vs implémentation ; (2) dérive documentaire ; (3) sécurité réelle, pas de finding écarté sans preuve d'inexploitabilité |
| Hors scope | pentest dynamique, scan CVE des dépendances, durcissement Keycloak / TLS du reverse-proxy, fuzz du parser BPMN Flowable |

> **Actualisation (2026-10-25), ancienne ligne 27.** La racine citée dans la ligne Code était
> `com.flowforge`. Elle est `be.myrmacta`. La phrase datée n'est pas réécrite.

Les numéros de ligne cités correspondent à l'état du dépôt à cette revue.

---

## 2. Verdict

Le code **suit les décisions structurantes** (hexagone, instance = tenant, BFF,
RBAC + ACL de type, stack figée, lots TODO 1–39 clos). Le cycle AI-devkit a
couvert les 14 blocs d'analyse et les follow-ups V1. L'écart restant est :

| Catégorie | Constat | Conséquence |
|---|---|---|
| **Sécurité à trancher avant prod internet** | SSTI Thymeleaf/SpEL (point 40) × isolation PG sans GRANT par tenant (point 41) | Chaîne d'attaque High → Critical sur un cluster PG partagé |
| **Sécurité ops / config** | Actuator `permitAll` (42), secrets par défaut / HMAC M2M (43), SSRF DNS TOCTOU (44) | Exploitables si le déploiement copie le local ou expose 8081 |
| **Dérive documentaire** | README, `docs/specs/01`/`12`, catalogue connecteurs, DMN (point 45) | Agents et humains relisent une spec fausse ; le code a raison |
| **Hygiène architecture** | Couche `application` non gardée par ArchUnit (point 46) | Fuite `application` → `infrastructure` déjà réelle |
| **Reliquats V1 assumés** | AV no-op, rate-limit JVM, headers HTTP, `allowUnknownFields` | Déjà documentés comme hors V1 / décision de feature — pas rouverts ici |

**Recommandation de séquencement :** chore documentaire (45) peut partir sans
arbitrage produit ; 40 et 41 **avant** toute exposition internet multi-tenant ;
42–44 au provisioning (point 31).

---

## 3. Alignement spécifications → AI-devkit → code

### 3.1 Chaîne documentaire

| Couche | Rôle | État à la revue |
|---|---|---|
| `docs/specs/00`–`14` | Spécification initiale + ajouts 13/14 | 14 thèmes ; `com.monapp` dans `01` **clos** PR #72 |
| `docs/analyses/REVUE-PRE-IMPLEMENTATION.md` + `TODO.md` 1–39 | Revue avant code + registre | Tous `[x]` ; §9 « avant prod » (21/22/23) **livrée** |
| `docs/ai/{requirements,design,planning,implementation,testing}` | Lifecycle par feature | Une série datée par feature livrée |
| `docs/ai/review/` | Artefact Phase 9 dédié | **Absent** — Phase 9 dans le YAML / le corps des docs `implementation` (`phase9:`) |
| `docs/ai/monitoring/README.md`, `implementation/README.md` | Guides de phase | Squelettes vides (templates AI-devkit) |
| [`FILE-ATTENTE.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/FILE-ATTENTE.md) | Prochaine feature | Stale à l'ouverture de cette revue : `edition-bpmn` encore « En revue » alors que PR #71 est mergée sur `main` |
| [`DEFERRED-FEATURES.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/DEFERRED-FEATURES.md) | Inventaire reporté | Stale : masquage encore « en attente de PR » (PR #70 mergée) |

### 3.2 Décisions structurantes — alignées

| Décision / spec | Preuve code | Statut |
|---|---|---|
| Domaine pur, infra → domain, `*UseCaseImpl` | `ArchitectureRulesTest` ; 0 import Spring/JPA/Flowable dans `domain` main | Aligné |
| Instance = tenant (`app.tenant.id`), pas de `ThreadLocal` tenant | `TenantProperties`, Hikari `connection-init-sql`, `TenantIdEnvironmentValidator` | Aligné |
| OIDC `tenant_id` = **garde**, pas sélecteur de schéma | `TenantResolutionFilter`, `M2mTenantValidationFilter` | Aligné |
| JPA ≠ domaine (décision 17) | 18 `*JpaEntity` sous `infrastructure.persistence`, `fromDomain`/`toDomain` | Aligné |
| BFF, pas de JWT navigateur | `SecurityConfig` Order 3 + `frontend/src/api/http.ts` `withCredentials` | Aligné |
| RBAC V1 + ACL de type (19, 20) | `@PreAuthorize` + `DossierAccessPolicy` + `dossier_type_acl` | Aligné |
| PDF Flying Saucer, pas iText ; SSRF rendu | `RestrictedResourceUserAgent` (seul `data:`) | Aligné |
| SPA même origine, CSRF `SameSite=Lax` | cookie CSRF + Axios `X-XSRF-TOKEN` | Aligné |
| Masquage champs sensibles (23) | `DossierFieldRedactor`, `toString()` sûr, ArchUnit allowlist, masquage outbox | Aligné |
| OpenAPI + pagination (24) | golden `openapi/openapi.yaml`, `PageResponse` | Aligné |

### 3.3 Écarts spec ↔ code (le code a raison, sauf mention)

Voir point **45** pour le suivi. Synthèse :

1. **`README.md` § Multi-tenant** — « Le tenant est résolu depuis le token OIDC à chaque requête » : **faux**. Le même README décrit plus bas l'instance dédiée (décision 13). Le code suit `02-multi-tenant.md`.
2. **`docs/specs/01-architecture-hexagonale.md`** — `com.monapp` **clos** (PR #72, 2026-08-25) : 3 occurrences → `com.flowforge`. Ne plus replanifier (TODO §45).

> **Actualisation (2026-10-25), ancienne ligne 89.** La cible `com.flowforge` de cette phrase
> est devenue `be.myrmacta`. Le passage `com.monapp` → `com.flowforge` décrit l'état de la revue
> et n'est pas réécrit.
3. **`docs/specs/12-autorisation-conformite.md`** — `WORKFLOW_ADMIN` a « accès au cockpit Flowable » : **hors V1** (`05-workflow-engine.md`, `DEFERRED-FEATURES` §1.5). L'éditeur `/admin/bpmn-editor` couvre le besoin métier.
4. **jsoup** — analyse/README `1.22.x`, `pom.xml` `1.23.1`.
5. **`application.yml` L1–3** — commentaire « disable infra autoconfig until real adapters land » : **périmé**.
6. **Périmètre V1 plus étroit que le marketing README / `06-connecteurs.md`** — déjà scopé dans les requirements `connecteurs` / `durcissement-donnees-sensibles`, **pas repris** dans le README : pas de DMN (auto-config exclue), pas de `scheduler`/`smtp`/`s3-minio`/`Kafka`, `http-rest` TRIGGER seulement (`HttpRestServiceConnector` skeleton non enregistré), `context-path` commenté (ops, point 31).
7. **MinIO** — README stack encore couplé MinIO ; le code expose `DocumentStoragePort` S3/filesystem (conforme conventions).
8. **Couche `application`** — voir point **46**.

Les écarts 1 et 3–7 sont **clos** par `derive-documentaire`
([PR #75](https://github.com/superjojo79/FlowForge/pull/75), TODO §45).
L'écart 2 (`com.monapp`) était déjà clos PR #72. L'écart 8 reste le point **46**.

---

## 4. Findings sécurité

Sévérité selon le skill `security-review` (exposition internet-facing, données
métier). Les IDs `S-*` sont stables ; le registre TODO utilise 40–46.

### High

#### S-1 / point 40 — SSTI Thymeleaf / SpEL dans les templates stockés

**Catégorie :** injection (template).

`ThymeleafHtmlTemplateProcessor` construit un `SpringTemplateEngine` (dialecte
SpringEL). Un `TENANT_ADMIN` peut enregistrer du HTML contenant
`${T(java.lang.Runtime).getRuntime().exec(...)}`. `POST /api/documents/generate`
et le live preview (`htmlContent`, réservé `TENANT_ADMIN`) l'exécutent **sur la
JVM**. L'iframe SPA `sandbox=""` ne protège que le navigateur. Le testing
`templates-documents` a **reporté** le « sandboxing Thymeleaf avancé ».

**Preuves :**

- `src/main/java/be/myrmacta/infrastructure/document/ThymeleafHtmlTemplateProcessor.java` (engine SpringEL, `TemplateMode.HTML`)
- `DocumentTemplateController` — `@PreAuthorize("hasRole('TENANT_ADMIN')")`
- `DocumentGenerationController.denyLiveUnlessTenantAdmin` — live preview = admin tenant
- [`docs/ai/testing/2026-07-31-feature-templates-documents.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/testing/2026-07-31-feature-templates-documents.md) — sandboxing Thymeleaf avancé hors scope

**Scénario :** compte `TENANT_ADMIN` compromis (ou admin malveillant d'un tenant
SaaS) → RCE sur l'instance → `APP_CONNECTOR_MASTER_KEY`, `OIDC_CLIENT_SECRET`,
JDBC.

**Options (à trancher) :**

| Option | Effet | Coût |
|---|---|---|
| **A — `StandardTemplateEngine` sans SpEL** (recommandée) | Plus de `T()`, preprocessing ; variables = interpolation | Moyen ; templates existants à retester |
| B — Allowlist d'expressions / sandbox | Garde SpEL utile (`#vars`) | Élevé, facile à rater |
| C — Assumer `TENANT_ADMIN` = opérateur de confiance | OK si instance on-prem dédiée à une org de confiance | Incompatible SaaS multi-tenant (chaîne S-2) |

**Détection :** test unitaire payload `${T(java.lang.Runtime)}` → 400 / contenu
non évalué ; log d'audit activate template.

---

#### S-2 / point 41 — Isolation schéma sans GRANT par tenant

**Catégorie :** authz / configuration. **Chaîne avec S-1.**

`search_path` n'est pas une barrière de sécurité. Le rôle PG unique (Compose :
`POSTGRES_USER=flowforge`, superuser de la base) peut lire `tenant_b.*`.
Décision 13 isole les **processus**, pas les **privilèges SQL**. Un RCE ou une
future injection SQL sur une instance traverse les autres schémas du cluster
partagé (`README.md` : « Toutes les instances partagent la même base PostgreSQL »).

**Preuves :**

- `application.yml` — `SET search_path TO ${app.tenant.id}, public`
- `docker/postgres/init/01-init-tenant-schema.sql` — `CREATE SCHEMA` seulement, aucun `GRANT` / rôle dédié
- `docker-compose.yml` — un seul `POSTGRES_USER`

**Options (à trancher) :**

| Option | Effet |
|---|---|
| **A — Un rôle PG par tenant** (`CONNECT` + `USAGE`/`CRUD` sur son schéma uniquement) | Vraie isolation ; à câbler au provisioning (point 31) |
| B — Une base (database) par tenant | Plus fort, plus cher ops |
| C — Assumer le réseau d'instances comme confiance unique | Incompatible avec S-1 en SaaS |

**Détection :** IT : user A `SELECT` sur `tenant_b.dossier` → permission denied.

---

### Medium

#### S-3 / point 42 — Actuator `permitAll` sur le port management

**Catégorie :** configuration / authz. **Décision de feature assumée**, pas un
accident : `observabilite` DD-MGMT-SEC (scrape Prometheus interne sans OIDC).

**Preuves :**

- `SecurityConfig.java` Order 0 — `EndpointRequest.toAnyEndpoint()`, `permitAll()`, CSRF off
- `application.yml` — `management.server.port: ${MANAGEMENT_PORT:8081}`, `include: health,prometheus`
- [`docs/ai/design/2026-08-20-feature-observabilite.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/design/2026-08-20-feature-observabilite.md) — DD-MGMT-SEC
- `docs/guides/GETTING-STARTED.md` / `docker-compose.yml` — Keycloak hôte **8081** (conflit local avec l'actuator)

Aucun `management.server.address`. Bind par défaut = toutes les interfaces.

**Scénario :** 8081 joignable depuis l'extérieur → métriques (`flowforge_*`,
profondeur outbox, DLQ Flowable).

**Options :** bind `127.0.0.1` / réseau scrape ; mTLS ; ne pas publier 8081 ;
départager le port Keycloak local.

---

#### S-4 / point 43 — Secrets par défaut et HMAC M2M hors profil local

**Catégorie :** secrets / crypto.

- `application.yml` — `${DB_PASSWORD:flowforge}`, `${DB_USERNAME:flowforge}`
- `docker-compose.yml` — Postgres / Keycloak `admin` / MinIO mots de passe par défaut
- `SecurityConfig.m2mJwtDecoder` — si `M2M_JWT_SECRET` est posé (y compris court), HMAC HS256 accepte des JWT `API_SERVICE` sur `/api/m2m/**`

OK en local. Dangereux si l'artefact est copié en prod sans override, ou si
HMAC remplace l'issuer OIDC.

**Options :** fail-fast si `DB_PASSWORD` / master key / issuer M2M absents hors
profil `local`/`test` ; refuser HMAC hors ces profils ; secrets Compose
obligatoires.

---

#### S-5 / point 44 — SSRF webhook sortant : DNS TOCTOU

**Catégorie :** SSRF.

`OutboundUrlGuard` résout le DNS et bloque privé / metadata / ULA (correct),
mais le client HTTP n'est **pas pinté** sur l'IP vérifiée — javadoc explicite.

**Preuve :** `OutboundUrlGuard.java` L18–23 ; `WebhookRestAdapter`.

**Scénario :** URL HTTPS dont le DNS bascule entre une IP publique (passe
`assertSafe`) et `169.254.169.254` au `connect`.

**Options :** `HttpClient` qui connecte uniquement à l'IP déjà validée ; proxy
egress allowlist.

---

### Low / reliquats V1 (non rouverts dans TODO)

Déjà assumés par des features livrées ; listés ici pour ne pas les perdre.
Suivi : [DEFERRED-FEATURES.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/DEFERRED-FEATURES.md) §4.

| ID | Sujet | Pourquoi pas un nouveau point TODO |
|---|---|---|
| S-6 | `NoOpAttachmentScanAdapter` + ZIP Office au magic `PK` | Décision explicite `durcissement-donnees-sensibles` (port prêt, pas d'AV dans la stack) |
| S-7 | Rate-limit webhooks/PDF en mémoire (par JVM) | Cohérent hors V1 multi-répliques (SSE multi-pod déjà différé) |
| S-8 | CSRF cookie `HttpOnly=false` | Pattern BFF double-submit, décision 15 |
| S-9 | Pas de CSP / HSTS / `X-Frame-Options` applicatifs | Défense en profondeur ; SPA same-origin |
| S-10 | `allowUnknownFields` | Option admin, défaut API `Boolean.TRUE.equals` → false ; hors flag `sensitive` si activé |

`processKeyPath` / mapping webhook : **feature voulue** (requirements
`connecteurs`, tests « processKeyPath override »). Ce n'est pas une
échappatoire par défaut (mapping vide → `processKey` statique). Footgun si un
admin active le mapping **et** que le secret webhook fuit — hardening
allowlist possible plus tard, pas un finding High.

---

## 5. Chaînes d'attaque

1. **S-1 + S-2 (Critical combiné) :** SSTI admin → RCE → même user PG → lecture/écriture des autres schémas tenants sur le cluster partagé. Risque SaaS le plus grave.
2. **S-3 + S-5 :** métriques 8081 pour caler un rebinding DNS sur un webhook de notification.
3. **S-4 :** Compose/env par défaut exposé → IdP + PG sans passer par l'appli.

---

## 6. Contrôles solides (faux positifs écartés)

| Élément | Pourquoi ce n'est pas un finding |
|---|---|
| `/api/webhooks/**` `permitAll` | HMAC / token + taille max + rate + `Idempotency-Key` dans le contrôleur |
| CSRF off webhooks / M2M | Stateless, pas de cookie de session |
| SQL injection / `Runtime.exec` | JPA Criteria ; pas d'`exec` / `ProcessBuilder` |
| Path traversal storage | Préfixe tenant + rejet `..` (`FilesystemStorageAdapter`) |
| SSRF PDF | `RestrictedResourceUserAgent` |
| JWT dans le SPA | Aucun (`localStorage` / `access_token` absents de `frontend/src`) |
| OpenAPI / Swagger | Désactivés par défaut ; rôle admin si allumés |
| Fuite `Dossier.fields` logs / webhooks | Feature masquage + ArchUnit allowlist + `toString()` sûr |
| Secrets connecteurs | AES-GCM + DEK ; fail si master key absente à l'usage |
| IDOR dossier par UUID | ACL de **type** (décision 20), pas d'ACL d'instance — produit |

---

## 7. Correspondance revue ↔ registre

| Point | Sujet | Nature | Sévérité | Documents / code impactés |
|---|---|---|---|---|
| 40 | SSTI Thymeleaf SpringEL | Sécurité — à trancher | High | `ThymeleafHtmlTemplateProcessor`, `08-templates-documents.md`, testing templates |
| 41 | Isolation PG sans GRANT par tenant | Sécurité / ops — à trancher | High (chaîne) | `02-multi-tenant.md`, init SQL, point 31 provisioning |
| 42 | Actuator `permitAll` + port 8081 | Config — à durcir ops | Medium | `SecurityConfig`, `application.yml`, design `observabilite`, GETTING-STARTED |
| 43 | Secrets défaut + HMAC M2M | Secrets — fail-fast | Medium | `application.yml`, `docker-compose.yml`, `SecurityConfig` |
| 44 | SSRF sortant DNS TOCTOU | SSRF — pin IP | Medium | `OutboundUrlGuard`, `WebhookRestAdapter`, `06` / `09` |
| 45 | Dérive documentaire analyse / README / code | Docs — **clos** PR #75 | — | `README.md`, `docs/specs/00` `06` `07` `11` `12`, `application.yml` L1 (`01` `com.monapp` déjà PR #72) |
| 46 | Couche `application` non gardée par ArchUnit | Architecture | Low | `ManualStartOrchestrator`, `ArchitectureRulesTest`, `01-architecture-hexagonale.md` |

Reliquats S-6…S-10 : pas de nouveau point — [DEFERRED-FEATURES.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/DEFERRED-FEATURES.md) §4.

---

## 8. Séquencement recommandé

| Étape | Points | Justification |
|---|---|---|
| **Sans arbitrage produit** | 45 | **Clos** PR #75 (`derive-documentaire`) |
| **Avant exposition internet multi-tenant** | 40, 41 | Chaîne RCE → PG partagé |
| **Au provisioning (point 31)** | 42, 43 | Bind actuator, secrets, issuer M2M, rôle PG |
| **Defense in depth** | 44, 46 | Pin IP ; règle ArchUnit `application` ↛ `infrastructure` (ou port domaine pour `WorkflowStartConfirmer`) |
| **V1.x / ops** | S-6, S-7, S-9 | AV, rate-limit distribué, headers — déjà hors V1 |

---

## 9. Risque résiduel (inchangé)

- Outillage provisioning tenant (schéma, instance, proxy, `app.tenant.id`) — point 31, hors file produit.
- Parser BPMN Flowable (XXE / entity expansion) non fuzzé.
- Durcissement Keycloak / TLS reverse-proxy hors dépôt.
- Pas de scan CVE CVSS 9+ dans cette passe.
- « Aucun finding dans une catégorie » ≠ « pleinement sûr » : catégories non applicables ou hors scope listées en §1.
