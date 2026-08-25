---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: verified
created: 2026-08-25
updated: 2026-08-25
feature: durcissement-securite-post-revue
verified: 2026-08-25
phase8: 2026-08-25
phase9: 2026-08-25
---

# Testing Strategy — `durcissement-securite-post-revue`

Scénarios dérivés des success criteria (Phase 2) et du design (Phase 3).
Phase 5 TDD **done**. Phase 7 **checked**. Phase 8 **verified** 2026-08-25.
Phase 9 **reviewed** 2026-08-25.

TDD obligatoire (skill `tdd`). Playwright = **régression** SPA (A-NO-SPA).

## Contrôles

- [x] **T-SSTI-PROC** Processor : `${title}` OK ; `#vars` / `#dates` /
      `#numbers` / `#strings` / `#lists` (méthode déclarée) OK ;
      `T()` / `#beans` / `new` / request / `getClass` →
      `TemplateProcessingException`.
- [x] **T-SSTI-CHECK** Checker : hors allowlist →
      `InvalidTemplateSyntaxException`.
- [x] **T-SSTI-HTTP** Generate/preview **et** activation → **400**
      (preview live ; activation ; generate via template ACTIVE injecté
      hors checker). POST draft `T()` reste **201**.
- [x] **T-SSTI-EACH** `th:each` + `th:text` non régression.
- [x] **T-PIN-GUARD** Un lookup ; URI client = IP validée ; rebinding.
- [x] **T-PIN-WH** Webhook : URI IP + `Host` + TLS hostname d'origine.
- [x] **T-PIN-SK** Skeleton SERVICE pinné, non enregistré.
- [x] **T-PIN-ADMIN** `assertSafe` à l'enregistrement, pas de connect.
- [x] **T-MGMT** Address `127.0.0.1`, port défaut 8090 ;
      `ObservabilityActuatorIT` (`MANAGEMENT_PORT=0`).
- [x] **T-SECRETS** Hors local/test : denylist L (vide, `flowforge`,
      `postgres`, `admin`, `password`, casse ignorée), master-key vide,
      OIDC secret vide → fail-fast ; profil `test` OK ; profil `prod`
      non relâché.
- [x] **T-HMAC** `M2M_JWT_SECRET` posé hors local/test → fail-fast.
- [x] **T-GRANT** Rôle app SELECT son schéma OK ; autre schéma denied
      (Testcontainers, SKIPPED sans Docker).
- [x] **T-DOCS** GETTING-STARTED / `.env.example` / `02` / `08` / `09` /
      TODO §40–44.
- [x] **T-VERIFY** `./mvnw verify` vert, Jacoco tenu.
- [x] **T-FE** Vitest + Playwright régression.

## Coverage target

Nouvelles classes : branches allowlist B2, denylist L, pin, GRANT.
Bundle Jacoco existant tenu.

## Phase 8 — couverture classe par classe (2026-08-25)

Relecture `target/site/jacoco/jacoco.csv` + HTML, pas seulement le
BUNDLE. Tests ajoutés (commit `436e5ed`) :

| Fichier de test | Lacune comblée |
|---|---|
| `SafeThymeleafExpressionSupportTest` | `#vars` imbriqué, map null, getter `getLabel`, extract `*{` / braces dans strings, long literal, `wait`/`notify`, `#lists.contains` |
| `ThymeleafHtmlTemplateProcessorTest` | `process(html, null)` (branche `variables != null`) |
| `SecretsEnvironmentPostProcessorTest` | profil `prod` (profil actif ni `local` ni `test`) |
| `OutboundUrlGuardTest` | `ftp`/`file`, host inconnu, DNS vide/`null`, userInfo/query/fragment sur URI pinnée |
| `DocumentGenerationApiTest` | generate HTTP `T()` → **400** (template ACTIVE injecté) |
| `DocumentTemplateApiTest` | POST draft `T()` → **201** |

**100 % ligne/branche (cette feature) :** `SafeHtmlTemplateEngineFactory`,
`SafeThymeleafVariableExpressionEvaluator`, `ThymeleafHtmlTemplateProcessor`,
`SecretsEnvironmentPostProcessor`, `HttpRestServiceConnector`.

**Accepté, non forcé :**

- `ThymeleafTemplateSyntaxChecker` 2 lignes : `catch (InvalidTemplateSyntaxException)`
  défensif (`assertAllowed` lève `TemplateProcessingException`).
- `SafeThymeleafExpressionSupport.Parser` coerce/`box` primitifs et catch
  réflexion : chemins d'échec de dispatch de méthodes, pas un pont `Runtime`.
- `OutboundUrlGuard` : `extractAuthorityHost` / parse IPv4 décimal (préexistant) ;
  `URISyntaxException` de `PinnedEndpoint.requestUrl` (URI déjà validée).
- `WebhookRestAdapter` : `JacksonException`, HMAC `Mac` en échec, vérif TLS
  HTTPS (fil d'air HTTP testé ; Host JDK restricted headers noté Phase 7).

## Frontend

Aucun fichier `frontend/**`. Preuve régression Phase 8 (cette session) :
`npm run test` **199/199** ; `npm run test:e2e` **47/47**.

## Manual Testing

GETTING-STARTED : Keycloak `:8081`, actuator `:8090`, profil `local`.
Volume PG neuf (`compose down -v`) pour l'init SQL.

## Verify Phase 8 (cette session)

```
mkdir -p target/classes/static && cp -a frontend/dist/. target/classes/static/
./mvnw -Dskip.frontend=true -Dmaven.compiler.release=21 verify
```

**1185** tests, 0 échec, exit 0. Jacoco BUNDLE LINE **93,52 %** (6490/6940),
BRANCH **75,57 %** (1958/2591) — `All coverage checks have been met`.

`cd frontend && npm run test` → 199/199. `npm run test:e2e` → 47/47.

## Phase 9 — sonde SSTI + verify (cette session)

Test ajouté `ThymeleafHtmlTemplateProcessorTest.inlinedIfAndPreprocessedSpel_throwTemplateProcessingException`
(commit `18bf98f`) : `[[${T(...)}]]`, `th:if="${T(...)}"`, `__${T(...)}__`
→ `TemplateProcessingException`.

```
mkdir -p target/classes/static && cp -a frontend/dist/. target/classes/static/
./mvnw -Dskip.frontend=true -Dmaven.compiler.release=21 verify
```

**1186** tests, 0 échec, exit 0. Jacoco BUNDLE LINE **93,52 %** (6490/6940),
BRANCH **75,57 %** (1958/2591) — `All coverage checks have been met`.

`cd frontend && npm run test` → 199/199. `npm run test:e2e` → 47/47.
