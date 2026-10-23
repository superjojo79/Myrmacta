---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
---

# Project Planning & Task Breakdown

## Milestones
**What are the major checkpoints?**

- [x] M1 : Infra — nouveau bean `HttpClient` + `ConnectorProperties.Http` (timeouts)
- [x] M2 : Fusion structurelle — `HttpRestTriggerConnector` → `HttpRestConnector` (capability
  `BOTH`, parité GET avec le skeleton), suppression du skeleton, fusion des tests (dépend M1)
- [x] M3 : Comportement SERVICE étendu — méthode/corps dynamiques (D-HTTP-METHODS, dépend M2)
- [x] M4 : `authHeaderValue` — config schema + en-tête `Authorization` + précédence sur
  `connectorInput.headers` (D-HTTP-CONFIG, D-HTTP-CONTENT-TYPE, dépend M2, parallélisable avec M3)
- [x] M5 : Tests d'intégration bout en bout (dépend M2–M4)
- [x] M6 : Documentation — `06-connecteurs.md`, `README.md` (dépend M2–M4, comportement final requis)
- [ ] M7 : Vérification globale + clôture (Phases 7–9)

## Task Breakdown
**What specific work needs to be done?**

### M1 — Infra : `HttpClient` + timeouts configurables

- [x] T1.1 : `ConnectorProperties.java` — nouveau sous-record `Http(Duration connectTimeout,
  Duration requestTimeout)`, défauts 5 s/5 s (même valeurs que `NotificationProperties.Webhook`).
  Dépendance : aucune. Validation : T-PROP-1, T-PROP-2.
- [x] T1.2 : `ConnectorConfig.java` — nouveau `@Bean HttpClient` construit avec
  `HttpClient.newBuilder().connectTimeout(properties.http().connectTimeout())...build()`.
  Dépendance : T1.1. Validation : T-CFG-1 (démarrage contexte Spring).

### M2 — Fusion structurelle (dépend de M1)

- [x] T2.1 : Renommage pur `HttpRestTriggerConnector.java` → `HttpRestConnector.java`
  (`git mv` + mise à jour des imports/références, `HttpRestTriggerConnectorTest.java` renommé en
  écho). Aucun changement de comportement. Dépendance : aucune (peut précéder M1). Validation :
  T-CAP-5 (régression `getType()`), suite `HttpRestTriggerConnectorTest` renommée inchangée.
- [x] T2.2 : `HttpRestConnector` — ajoute `implements ServiceConnector`, `getCapability()` →
  `BOTH`, constructeur étendu (`OutboundUrlGuard`, `HttpClient`, `ObjectMapper` en plus de
  `SimpleJsonPathMapper`), méthode `execute(...)` reprenant la logique GET du skeleton à
  l'identique (parité de comportement avant extension), `getConfigSchema()` gagne
  `authHeaderValue` (`PASSWORD`, optionnel — champ ajouté ici mais logique d'en-tête auth pas
  encore branchée, cf. M4). `ConnectorConfig` : bean `HttpRestConnector` explicite si nécessaire
  pour l'injection des 3 nouvelles dépendances (à confirmer en implémentation — `@Component` avec
  constructeur unique suffit peut-être sans `@Bean` dédié). Dépendance : M1 (a besoin du bean
  `HttpClient`), T2.1. Validation : T-CAP-1 à T-CAP-4, T-CFG-2, T-EXEC-1/T-EXEC-2 (GET par défaut/explicite),
  T-SSRF-1..N (migrés depuis le skeleton, comportement identique), T-SCHEMA-1/T-SCHEMA-2,
  T-AUTH-2/T-AUTH-4 (pas d'en-tête auth encore ajouté à ce stade — régression de non-comportement).
- [x] T2.3 : Supprime `HttpRestServiceConnector.java` (logique absorbée par T2.2). Fusionne
  `HttpRestServiceConnectorTest.java` dans `HttpRestConnectorTest.java` (renommé à T2.1) :
  `connector_doesNotImplementConnectorDefinition`/`connector_isNotASpringBean` inversés en
  assertions positives (déjà couvertes par T-CAP-1/T-CAP-4), reste des tests SSRF/anti-fuite migré
  sans changement d'assertion. Dépendance : T2.2. Validation : D-HTTP-TESTS — un seul fichier de
  test survit pour la classe fusionnée, `./mvnw test -Dtest=HttpRestConnectorTest` vert.

### M3 — Comportement SERVICE étendu : méthode/corps (D-HTTP-METHODS, dépend de M2)

- [x] T3.1 : `HttpRestConnector.execute` — lit `method` (défaut `GET`, insensible à la casse),
  valide contre `{GET, POST, PUT}` (sinon `IllegalArgumentException`), construit la requête avec
  `HttpRequest.Builder.GET()`/`.POST(...)`/`.PUT(...)` selon le cas. Dépendance : T2.2/T2.3.
  Validation : T-EXEC-3 à T-EXEC-6.
- [x] T3.2 : `HttpRestConnector.execute` — lit `body` (optionnel), rejette avec `GET`
  (`IllegalArgumentException`), sérialise via `ObjectMapper.writeValueAsString` sauf si déjà une
  `String` (envoyée telle quelle), `BodyPublishers.noBody()` si absent sur `POST`/`PUT`.
  Dépendance : T3.1. Validation : T-EXEC-7, T-BODY-1 à T-BODY-3.

### M4 — `authHeaderValue` (D-HTTP-CONFIG, D-HTTP-CONTENT-TYPE, dépend de M2, parallélisable avec M3)

- [x] T4.1 : `HttpRestConnector.execute` — si `instance.config().get("authHeaderValue")` non
  blanc, `HttpRequest.Builder.setHeader("Authorization", value)` **avant** l'application des
  `headers` de `connectorInput` (précédence design). Dépendance : T2.2/T2.3. Validation : T-AUTH-1
  (remplace le comportement encore no-op de T2.2).
- [x] T4.2 : `HttpRestConnector.execute` — lit `headers` (`Map<String,Object>` optionnel) depuis
  `connectorInput`, applique chaque entrée via `.setHeader(...)` (peut écraser `Authorization` posé
  en T4.1), rejette si présent mais pas une `Map` (`IllegalArgumentException`). Aucun
  `Content-Type` par défaut ajouté (D-HTTP-CONTENT-TYPE — absence de comportement, pas de code
  supplémentaire à écrire pour ça, juste un test qui le prouve). Dépendance : T4.1, T3.1 (partage
  la construction de `HttpRequest.Builder`). Validation : T-HDR-1 à T-HDR-4, T-AUTH-3.

### M5 — Tests d'intégration bout en bout (dépend de M2–M4)

- [x] T5.1 : `ExecuteConnectorUseCaseImplTest` (ou test dédié identifié en implémentation) —
  instance `http-rest`, `execute(...)` réussit (ne lève plus `UnsupportedOperationException`).
  Dépendance : M2–M4. Validation : T-INT-1.
- [x] T5.2 : test JavaDelegate `ConnectorServiceTask` (ou IT Flowable existant étendu) — instance
  `http-rest`, `connectorResult` posé en variable de process. Dépendance : T5.1. Validation :
  T-INT-2.
- [x] T5.3 : tests `ConnectorExecuteController`/`M2mConnectorExecuteController` (MockMvc ou
  équivalent déjà en place pour `flowable-signal`) — instance `http-rest` → `200` sur les deux
  contrôleurs ; `connectorInput` malformé → `400` (régression du gestionnaire générique).
  Dépendance : T5.1. Validation : T-INT-3 à T-INT-5.
- [x] T5.4 (régression croisée) : suite `flowable-signal` existante toujours verte après les
  changements de `ConnectorCatalog`/`ExecuteConnectorUseCaseImpl` (non modifiés, mais à vérifier
  explicitement). Dépendance : aucune (peut être vérifié à tout moment, formalisé ici). Validation :
  T-INT-6.
- [x] T5.5 (**tâche découverte en implémentation, absente du plan initial**) : `ExecuteConnectorRequest`
  (`ConnectorApiDtos.java`) étendu (`url`, `method`, `body`, `headers`) — le DTO REST du BFF/M2M
  était câblé en dur sur les champs `flowable-signal`, rendant `http-rest` SERVICE inatteignable
  via HTTP malgré la capacité activée côté connecteur. Trouvé par T5.3 (test délibérément conçu
  pour distinguer « url reçue et bloquée » de « url jamais transmise »). Dépendance : T2.2/T2.3.
  Validation : T-INT-7.
- [x] T5.6 (conséquence de T5.5) : golden OpenAPI (`openapi/openapi.yaml`) régénéré
  (`-Dopenapi.update=true`) — 4 propriétés optionnelles ajoutées au schéma de requête partagé par
  les deux endpoints `execute`. Dépendance : T5.5. Validation : `OpenApiContractIT` vert.

### M6 — Documentation (dépend de M2–M4, comportement final requis)

- [x] T6.1 : `docs/specs/06-connecteurs.md` — tableau catalogue, colonne « V1 livré » pour
  `http-rest` : « TRIGGER seulement... skeleton SERVICE non enregistré » → « TRIGGER + SERVICE ».
  Dépendance : M2–M4 livrés (comportement final à décrire). Validation : revue manuelle, cohérence
  avec le design.
- [x] T6.2 : `README.md` — § Système de connecteurs, retire la mention « TRIGGER seulement » pour
  `http-rest`. Dépendance : T6.1 (même passage de revue). Validation : revue manuelle.

### M7 — Vérification globale + clôture (Phases 7–9)

- [x] T7.1 : Phase 7 (check implementation) — relecture fichier par fichier de `HttpRestConnector`,
  `ConnectorConfig`, `ConnectorProperties`, `HttpRestConnectorTest` contre le design ; chercher les
  écarts réels (pattern historique de ce projet : `edition-bpmn`, `schema-champs-dynamiques` ont
  tous trouvé 1-2 bugs réels à cette étape). Dépendance : M1–M6.
- [x] T7.2 : Phase 8 (couverture) — balayage Jacoco classe par classe (pas seulement le total
  bundle) sur les 4 fichiers touchés/nouveaux ; combler les lacunes réelles trouvées. Dépendance :
  T7.1. Validation : seuils bundle maintenus (LINE ≥ 85% / BRANCH ≥ 75%).
- [x] T7.3 : régression frontend complète (`npm run test`, `npm run test:e2e`) — preuve de non-impact,
  pas de nouveau scénario attendu. Dépendance : T7.1.
- [ ] T7.4 : Phase 9 (revue finale) — alignement design confirmé, pas de duplication, rollback sûr
  (aucune migration DB), `CHANGELOG.md` mis à jour (convention `flowforge-conventions` §7), PR prête.
  Dépendance : T7.1–T7.3.

## Dependencies
**What needs to happen in what order?**

```
T1.1 → T1.2 ─┐
             ├→ T2.2 → T2.3 ─┬→ T3.1 → T3.2 ─┐
      T2.1 ──┘               └→ T4.1 → T4.2 ─┼→ T5.1 → T5.2 → T5.3
                                              │                    (T5.4 indépendant)
                                              └──────────────────→ T6.1 → T6.2
                                                                          │
                                                          T7.1 → T7.2 / T7.3 → T7.4
```

- M3 et M4 sont parallélisables entre eux (tous deux dépendent de M2, pas l'un de l'autre) mais
  partagent la construction du `HttpRequest.Builder` dans `execute()` — à séquencer dans le même
  commit/PR si fait par deux agents pour éviter un conflit de merge sur la même méthode.
- Aucune dépendance externe (pas de nouvelle librairie — `HttpClient`/`ObjectMapper` déjà sur le
  classpath ; `tools.jackson.databind.ObjectMapper`, pas `com.fasterxml.jackson`, cf. design).
- Aucune migration Liquibase, aucun changement de schéma de données.

## Timeline & Estimates
**When will things be done?**

- Feature de taille réduite comparée aux précédentes (une classe fusionnée + config, pas de nouvel
  écran) — pas d'estimation en jours/points, découpage en 7 milestones/17 tâches TDD suffisant pour
  le suivi. M1–M2 (fondation) avant tout le reste ; M3/M4 le cœur fonctionnel ; M5 la preuve
  d'intégration ; M6 la doc ; M7 la clôture standard du projet.

## Risks & Mitigation
**What could go wrong?**

- **Risque identifié en design (Non-Functional Requirements) :** connexion HTTPS vers l'IP pinnée
  plutôt que le nom d'hôte original peut échouer la vérification TLS (SNI/hostname mismatch) selon
  la configuration du `HttpClient` — comportement **déjà présent dans le skeleton actuel**, jamais
  exercé en conditions réelles (tests unitaires avec `HttpClient` mocké). Mitigation : le test
  manuel M7/optionnel (`docs/ai/testing` § Manual Testing) contre un vrai endpoint HTTPS externe
  est le seul moyen de le détecter avant Phase 9 — si un échec TLS apparaît, il préexiste à cette
  feature (skeleton), pas une régression introduite ici, mais reste à traiter si trouvé.
- **Risque de merge M3/M4 parallèles** (cf. Dependencies) — même méthode `execute()` modifiée par
  deux tâches indépendantes ; mitigation : séquencer dans un seul agent/commit plutôt que
  paralléliser réellement si un seul agent traite cette feature (cas attendu ici).
- **Écart doc/code préexistant (D-FRONTEND-GAP, design)** — `GUIDE-CONCEPTION-BPMN.md` §4 décrit un
  formulaire schema-driven qui n'existe pas ; hors périmètre de cette feature (tâche suggérée
  séparée déjà créée), mais à ne pas aggraver : ne pas ajouter de nouvelle documentation qui
  perpétue l'inexactitude en décrivant `authHeaderValue` comme « apparaissant dans le formulaire
  généré » (T6.1/T6.2 à rédiger factuellement — JSON libre, pas de formulaire dédié).

## Resources Needed
**What do we need to succeed?**

- Aucune ressource externe — bibliothèques déjà sur le classpath (`java.net.http.HttpClient`,
  `tools.jackson.databind.ObjectMapper`), aucun nouveau service tiers, aucune infrastructure
  supplémentaire (Testcontainers déjà en place pour les IT si nécessaire, pas attendu ici).
- Référence de comportement pour les tests d'intégration : suite existante de `flowable-signal`
  (seul autre connecteur `BOTH`) comme modèle pour T5.1–T5.3.
