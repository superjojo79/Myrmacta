---
phase: implementation
title: Implementation Guide
description: Technical implementation notes
status: reviewed
created: 2026-08-25
updated: 2026-08-25
feature: durcissement-securite-post-revue
phase5: 2026-08-25
phase7: 2026-08-25
phase8: 2026-08-25
phase9: 2026-08-25
checked: 2026-08-25
reviewed: 2026-08-25
---

# Implementation Guide — `durcissement-securite-post-revue`

## Phase 5 — done (M1–M7)

TDD : tests processor B2 d'abord (rouge sur l'ébauche A/B1 : `#dates` /
`#vars` rejetés, `${title.class}` encore évalué via `Object.getClass`), puis
évaluateur + checker. M7 : `./mvnw verify` 1173 tests, Jacoco BRANCH
**75,03 %** (1944/2591) / LINE 93,3 % ; Vitest **199/199** ; Playwright
**47/47**. `ObservabilityActuatorIT` vert ; `TenantRoleIsolationIT`
SKIPPED sans Docker.

## Changed files

### SSTI (M1)

- `SafeThymeleafExpressionSupport` — parser allowlist B2, extraction `${}` / `*{}`
- `SafeThymeleafVariableExpressionEvaluator` — délégation
- `SafeHtmlTemplateEngineFactory` — `TemplateEngine` + `StandardDialect` sans SpEL
- `ThymeleafHtmlTemplateProcessor` / `ThymeleafTemplateSyntaxChecker` — même factory ;
  checker `parseString` + allowlist **sans** `process()`
- Tests : `ThymeleafHtmlTemplateProcessorTest`, `ThymeleafTemplateSyntaxCheckerTest`,
  `SafeThymeleafExpressionSupportTest`, `DocumentPreviewApiTest` (400 `T()` live),
  `DocumentTemplateApiTest` (400 à l'**activation** — le checker métier existant,
  pas au POST draft)

### Pin (M2)

- `OutboundUrlGuard.PinnedEndpoint` / `resolveSafe` (1 lookup)
- `WebhookRestAdapter` — URI IP + `Host` + TLS hostname d'origine + redirects NEVER
- `HttpRestServiceConnector` — URI pinnée, non `@Component`
- Tests guard / webhook (dont sender `HttpURLConnection` réel via `HttpServer`) /
  connector pin ; admin `assertSafe` déjà
  `NotificationWebhookAdminApiTest.create_ssrfUrl_returns400`

### Actuator / secrets / GRANT (M3–M5)

- `application.yml` `management.server.address` / `MANAGEMENT_PORT:8090` /
  `DB_USERNAME:flowforge_tenant_dev`
- `application-local.yml` + `SecretsEnvironmentPostProcessor` (denylist L, HMAC)
- `docker/postgres/init/01-init-tenant-schema.sql`, `docker-compose.yml`, `.env.example`
- `TenantRoleIsolationIT` (SKIPPED sans Docker)

### Docs (M6)

- `GETTING-STARTED.md`, `analysis doc/02` / `08` / `09`, TODO §40–44,
  note port 8090 dans le design observabilite

## Design Deviations

Pas d'écart **majeur** (pas de retour Phase 5, pas de relock D-*).
Nuances Phase 7 :

| Sévérité | Écart |
|---|---|
| Moyenne | `WebhookRestAdapter` pose `Host` sur la map ; le JDK `HttpURLConnection` le droppe souvent (restricted headers). Pin IP / TOCTOU tenu ; vhost sur le fil d'air non garanti. |
| Basse / moyenne | DD-SSTI-FAIL « enregistrement » = **activation** (`ActivateDocumentTemplateUseCaseImpl` + checker). POST draft avec `T()` reste **201**. |
| Basse | `HttpRestServiceConnector` pin URI IP seulement (JDK `HttpClient`, pas Host/SNI). Hors V1, non `@Component`. |
| Basse | `readProperty` / `invokeUtility` : `getDeclaredMethods` + `setAccessible(true)`. `findNoArg` s'arrête avant `Object`. Follow-up défense en profondeur, pas un pont `Runtime`. |
| Basse | `*IT.java` absents du `verify` surefire (pas de failsafe). `ObservabilityActuatorIT` / `TenantRoleIsolationIT` se lancent via `-Dtest=…`. |

## Edge cases

- Checker : `${dossier.reference}` passe sans variables métier
- Denylist : trim + casse ignorée
- Volume PG existant : `compose down -v`
- `HttpURLConnection` côté webhook (JDK `HttpClient` refuse `Host`). Le JDK
  peut aussi **ignorer** le header `Host` restreint (`sun.net.http.allowRestrictedHeaders`)
  une fois `HttpURLConnection` chargé : l'adaptateur pose bien `Host` sur la
  map (tests unitaires) ; le fil d'air peut présenter l'IP pinnée.
- `./mvnw verify -Dskip.frontend=true` exige `static/index.html` (build Vite
  ou dist déjà copié) — `OpenApiDisabledApiTest` échoue après `clean` sans SPA.

## Phase 7 — Check Implementation (2026-08-25)

Revue fichier par fichier du diff `origin/main...HEAD` contre le design
(`docs/ai/design/2026-08-25-feature-durcissement-securite-post-revue.md`)
et les requirements. `git diff origin/main...HEAD -- frontend src/main/java/com/flowforge/domain`
vide (A-NO-SPA, hexagone). Plus de `SpringTemplateEngine` dans `src/main`.

### Alignement

| Décision | Code |
|---|---|
| DD-THYME-B2 = **E** | `SafeHtmlTemplateEngineFactory` : `TemplateEngine` + `StandardDialect` + évaluateur custom ; pas de SpEL |
| D-40 / B2 | `SafeThymeleafExpressionSupport` : `#` hors `vars`/`dates`/`numbers`/`strings`/`lists` refusé ; `T(` / `new` / `class`/`getClass`/`wait`/`notify` bloqués |
| A-EXPR-METHODS | méthodes **déclarées** sur `Dates`/`Numbers`/`Strings`/`Lists` ; pas `Object.getClass` |
| Checker = même allowlist | `ThymeleafTemplateSyntaxChecker` : même factory ; `parseString` + `assertAllowed` **sans** `process()` |
| DD-SSTI-FAIL = **C** | preview live **400** (`DocumentPreviewApiTest`) ; activation **400** (`DocumentTemplateApiTest`) ; generate : même processor + `DocumentGenerationExceptionHandler` → 400 |
| D-44 / A-PIN-CALLERS | `resolveSafe` 1 lookup + `PinnedEndpoint` ; webhook URI IP + `Host` map + TLS hostname d'origine + redirects `false` ; skeleton pin URI IP ; admin `assertSafe` |
| D-42 = **L** | `management.server.address` `${MANAGEMENT_ADDRESS:127.0.0.1}`, port `${MANAGEMENT_PORT:8090}` ; `permitAll` Order 0 inchangé |
| D-43 / DD-DENYLIST = **L** | EPP enregistré (`META-INF/spring/…EnvironmentPostProcessor`) ; denylist L + HMAC interdit hors `local`/`test` |
| D-41 = **A/S** | init SQL rôle `flowforge_tenant_dev` LOGIN ; Compose `POSTGRES_USER` ≠ `DB_USERNAME` |

### Gaps / follow-ups (pas des D-*)

- Header `Host` webhook : voir Design Deviations (moyenne). Pin IP tient le TOCTOU.
- Pas de test HTTP generate dédié `T()` : l'activation empêche un template ACTIVE hors allowlist ; le handler generate mappe déjà `TemplateProcessingException` → 400. Candidat Phase 8 (injecter un template ACTIVE de test).
- `*IT.java` hors `verify` surefire — lancer explicitement ou ajouter failsafe.
- Docs `analysis doc/08` / TODO §40 : « enregistrement » précisé en **activation**.

### Verify Phase 7 (cette session)

```
./mvnw -Dskip.frontend=true -Dmaven.compiler.release=21 -Djacoco.skip=true \
  test -Dtest=ThymeleafHtmlTemplateProcessorTest,ThymeleafTemplateSyntaxCheckerTest,\
SafeThymeleafExpressionSupportTest,SecretsEnvironmentPostProcessorTest,\
OutboundUrlGuardTest,WebhookRestAdapterTest,HttpRestServiceConnectorTest,\
DocumentPreviewApiTest,DocumentTemplateApiTest,ArchitectureRulesTest
```

**130** tests, 0 échec, exit **0** (`BUILD SUCCESS`). Ne pas enchaîner
`jacoco:report` après ce sous-ensemble (écrase `jacoco.exec`).

M7 Phase 5 inchangé : `./mvnw verify` 1173 / Jacoco BRANCH 75,03 % ;
Vitest 199 ; Playwright 47.

Next : `dev-testing` (Phase 8), puis `dev-review` (Phase 9). **Non
enchaîné.**

## Phase 8 — Write Tests (2026-08-25)

Couverture relue **classe par classe** (`jacoco.csv` + HTML), pas le
seul agrégat BUNDLE.

**Lacunes comblées :** nested `#vars` / extract / long literal / getter
bean ; `process(…, null)` ; EPP profil `prod` ; DNS vide + schémas
`ftp`/`file` + userInfo sur URI pinnée ; **generate HTTP `T()` → 400**
(follow-up Phase 7) ; POST draft `T()` → 201.

**100 %** ligne/branche : factory, évaluateur, processor, EPP, skeleton
SERVICE.

**Laissé :** catch checker `InvalidTemplateSyntaxException` (mort) ;
coerce primitifs du parser ; `extractAuthorityHost` préexistant ;
Jackson/HMAC/TLS HTTPS webhook.

**Verify (cette session) :** `./mvnw verify` **1185** tests, 0 échec,
Jacoco LINE 93,52 % / BRANCH 75,57 %. Vitest **199/199**. Playwright
**47/47**.

Next : `dev-review` (Phase 9). **Non enchaîné.**

## Phase 9 — revue finale (2026-08-25)

Revue holistique du diff `origin/main...HEAD` (42 fichiers, +2753/−118
avant le test inlining ; +13 lignes de test ensuite). Stance findings
first. Aucun D-* relocké.

### Alignement design

| Décision | Implémentation | Écart |
|---|---|---|
| DD-THYME-B2 = **E** | `SafeHtmlTemplateEngineFactory` : `TemplateEngine` + `StandardDialect` + évaluateur custom | aucun |
| D-40 / B2 | `#vars` `#dates` `#numbers` `#strings` `#lists` + chemins modèle ; refus `T()` `#beans` `new` `#request` | aucun |
| A-EXPR-METHODS | méthodes **déclarées** sur `Dates`/`Numbers`/`Strings`/`Lists` ; pas `Object.getClass` / `wait` / `notify` | aucun |
| DD-SSTI-FAIL = **C** | generate / preview / **activation** → **400** ; POST draft `T()` → **201** | nuance Phase 7 : « enregistrement » = activation |
| D-44 / A-PIN-CALLERS | `resolveSafe` 1 lookup ; webhook + skeleton ; admin `assertSafe` | Host JDK restricted headers (moyenne) |
| D-42 = **L** | bind `127.0.0.1`, port **8090**, `permitAll` Order 0 (DD-MGMT-SEC) | aucun |
| D-43 / DD-DENYLIST = **L** | EPP fail-fast hors `local`/`test` ; HMAC M2M interdit ; denylist L | aucun |
| D-41 = **A/S** | rôle LOGIN `flowforge_tenant_dev` + init Compose + IT | IT rejoue GRANT en Java, pas le SQL Compose |
| A-NO-SPA | `git diff origin/main...HEAD -- frontend` vide | aucun |

Hexagone : `git diff origin/main...HEAD -- src/main/java/com/flowforge/domain` vide. Plus de `SpringTemplateEngine` / OGNL dans `src/main`.

### Appelants

| Symbole | Sites | Contrat |
|---|---|---|
| `HtmlTemplateProcessor.process` | `GenerateDocumentUseCaseImpl`, `PreviewDocumentUseCaseImpl` ; câblé `DocumentConfig` | signature inchangée |
| `TemplateSyntaxChecker.check` | `ActivateDocumentTemplateUseCaseImpl` ; câblé `DocumentConfig` | signature inchangée |
| `OutboundUrlGuard.resolveSafe` | `WebhookRestAdapter.send`, `HttpRestServiceConnector.execute` | 1 lookup + URI IP |
| `OutboundUrlGuard.assertSafe` | `NotificationWebhookAdminController` create/update | pas de connect |
| `FilesystemStorageAdapter.resolveSafe` | privé `Path` | **autre** garde, pas l’URL |
| `SecretsEnvironmentPostProcessor` | `META-INF/spring/…EnvironmentPostProcessor` (voisin de `TenantIdEnvironmentValidator`) | Boot 4, pas `spring.factories` |

Une seule impl `HtmlTemplateProcessor` / `TemplateSyntaxChecker` dans `src/main`.

### Contrats / dépendances / rollback

- **pom.xml, OpenAPI, Liquibase, frontend/** : absents du diff.
- Chemins HTTP generate / preview / activate inchangés ; 400 via handlers existants.
- Breaking ops : défaut `DB_USERNAME=flowforge_tenant_dev` + volume PG (`compose down -v`) — documenté GETTING-STARTED / `.env.example`.
- Rollback = revert git + éventuel `compose down -v`. Pas de migration irréversible.
- EPP : denylist + mot de passe local init SQL `PASSWORD 'flowforge'` n’est **pas** un secret prod (profil `local` relâché).

### Sécurité (skill `security-review`)

- Pas de secret prod hardcodé dans le diff.
- `TenantRoleIsolationIT` concatène SQL avec suffixe UUID hex — OK tests.
- Actuator `permitAll` Order 0 **by design** sur loopback 8090.
- Scan : pas de `.only` / `debugger` ; OGNL absent du tree Maven.
- Sonde Phase 9 : inlining `[[${T(...)}]]`, `th:if="${T(...)}"`, preprocessing `__${T(...)}__` → `TemplateProcessingException` (commit `18bf98f`).

### Findings (non bloquants)

| Sévérité | Fichier | Issue | Recommandation |
|---|---|---|---|
| Moyenne | `WebhookRestAdapter` | Header `Host` posé sur la map, souvent droppé par JDK `HttpURLConnection` (restricted headers). Pin IP tient le TOCTOU. | Follow-up : client qui honore Host/SNI. Pas un D-*. |
| Basse | `HttpRestServiceConnector` | Pin URI IP seulement (JDK `HttpClient`) ; pas Host/SNI ; non `@Component`. | Hors V1 SERVICE. |
| Basse | `SafeThymeleafExpressionSupport` | `setAccessible(true)` sur getters / méthodes **déclarées**. `findNoArg` s’arrête avant `Object`. | Défense en profondeur, pas un pont `Runtime`. |
| Basse | `*IT.java` | Hors `verify` surefire (pas de failsafe). `TenantRoleIsolationIT` rejoue GRANT en Java, n’exécute pas le SQL Compose. | Lancer `-Dtest=…` en CI ; follow-up failsafe. |
| Basse | `ThymeleafTemplateSyntaxChecker` | `catch (InvalidTemplateSyntaxException)` mort (`assertAllowed` lève `TemplateProcessingException`). | Laisser défensif. |
| Basse | `OutboundUrlGuard.PinnedEndpoint` | `URISyntaxException` défensif (URI déjà validée). | Laisser. |

Aucun blocage. Pas d’écart majeur vs design. Pas de retour Phase 5/8.

### Checklist finale

- Design match ✅ (D-40…D-44, DD-THYME-B2, DD-SSTI-FAIL, A-PIN-CALLERS, A-NO-SPA)
- Pas de gap logique ✅ (ports inchangés, câblage par injection)
- Sécurité ✅ (SSTI B2 + sonde inlining ; pin IP ; EPP ; GRANT ; actuator loopback)
- Intégration ✅ (generate/preview/activate 400 ; POST draft 201 ; webhook admin `assertSafe`)
- Tests ✅ (cette session : `./mvnw verify` **1186** tests, 0 échec, Jacoco LINE **93,52 %** / BRANCH **75,57 %** ; Vitest **199/199** ; Playwright **47/47**)
- Docs ✅ (implementation `reviewed`, FILE-ATTENTE Phase 9 READY, planning next = merge)

Traçage `npx ai-devkit@latest task` : **indisponible** (`error: unknown command 'task'`). Lint feature docs OK ; git `[MISS]` `feature-*` accepté (branche cloud `cursor/…-a95d`).

**Prêt pour revue humaine / merge.** Ne pas merger depuis l’agent. Ne pas enchaîner d’autre feature.
