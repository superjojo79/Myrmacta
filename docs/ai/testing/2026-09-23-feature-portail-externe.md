---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
---

# Testing Strategy — `portail-externe`

> TODO §68. Scénarios dérivés des critères de succès et user stories des
> [requirements](../requirements/2026-09-23-feature-portail-externe.md) et des composants du
> [design](../design/2026-09-23-feature-portail-externe.md). Rédigé en Phase 4 (2026-09-24) ;
> chaque scénario est rattaché à au moins une tâche du
> [planning](../planning/2026-09-23-feature-portail-externe.md) (table de couverture en fin de
> planning). Cases cochées au fil de l'implémentation.

## Test Coverage Goals

- Nouveau code backend : couverture Jacoco lignes/branches au niveau des seuils du dépôt, 100 %
  des branches des use cases portail et de `BpmnParserAdapter` (règles portail).
- Tous les scénarios de sécurité (préfixe `T-SEC`, `T-ACCESS`, `T-IDOR`) en tests automatisés, pas en
  vérification manuelle.
- Frontend : Vitest pour le portail et le panneau instructeur ; **Playwright obligatoire**
  (conventions projet) pour le parcours tiers et le panneau instructeur.
- Règle ArchUnit inchangée et verte : domaine pur, `AccessContext.External` sans dépendance
  framework.

## Unit & Integration Tests — backend

### Parser et déploiement (US-4, D-PORTAL-BPMN-MARKER, D-PORTAL-ACTIVATION)

- [x] **T-PARSE-1** — `userTask` avec `flowable:portalRecipient="dossierField:email"` et
  `formKey="user-question:avis"` acceptée ; `extractPortalTasks` renvoie schéma, source, TTL.
- [x] **T-PARSE-2** — tâche externe avec `formKey` absent ou autre que `user-question:` rejetée.
- [x] **T-PARSE-3** — `portalRecipient` de forme invalide (`email`, `field:x`, `dossierField:`,
  `variable:a.b`, `dossierField:${x}`) rejeté.
- [x] **T-PARSE-4** — `portalLinkTtlDays` non entier, ≤ 0 ou > plafond rejeté ; absent accepté.
- [x] **T-PARSE-5** — tâche externe avec `assignee`/`candidateUsers`/`candidateGroups` rejetée
  (D-PORTAL-NO-ASSIGNEE).
- [x] **T-PARSE-6** — `portalLinkTtlDays` sans `portalRecipient` rejeté (attribut orphelin).
- [x] **T-DEPLOY-1** — portail désactivé : déploiement d'un BPMN à tâche externe rejeté via
  `DeployBpmnProcessUseCaseImpl` (REST admin **et** MCP `deploy_workflow`) ; brouillon accepté.
- [x] **T-DEPLOY-2** — portail activé : même BPMN déployé.

### Émission (US-4, D-PORTAL-ISSUE, D-PORTAL-NO-ADDRESS, D-PORTAL-TTL)

- [x] **T-ISSUE-1** — création d'une tâche externe (vrai moteur) avec adresse valide dans un
  champ du dossier → lien `PENDING_SEND`, `ttl_days` = défaut.
- [x] **T-ISSUE-2** — champ adresse absent → `NOT_SENT/NO_ADDRESS` + notification
  `PORTAL_LINK_NOT_SENT` aux participants ; process **non** en incident.
- [x] **T-ISSUE-3** — adresse syntaxiquement invalide → `NOT_SENT/INVALID_ADDRESS`.
- [x] **T-ISSUE-4** — schéma `user-question` supprimé entre déploiement et création →
  `NOT_SENT/SCHEMA_MISSING`.
- [x] **T-ISSUE-5** — `portalLinkTtlDays` BPMN > plafond tenant abaissé depuis le déploiement →
  `ttl_days` = plafond.
- [x] **T-ISSUE-6** — tâche non externe → aucun lien créé (non-régression des processus livrés).
- [x] **T-ISSUE-7** — source `variable:contactEmail` lue par nom littéral ; une valeur de
  variable contenant `${…}` est traitée comme une chaîne (adresse invalide), jamais évaluée.
- [x] **T-ISSUE-8** — rollback de la transaction moteur à la création de la tâche → aucun lien
  orphelin.

### Envoi (D-PORTAL-INVITE-DELIVERY, D-PORTAL-AUTH)

- [x] **T-SEND-1** — job d'envoi : jeton 43 caractères base64url, seul son SHA-256 persisté,
  statut `ACTIVE`, `expires_at = sent_at + ttl` ; l'e-mail contient
  `{public-base-url}/portal/#<jeton>` et aucune autre donnée du dossier que la référence.
- [x] **T-SEND-2** — échec SMTP : nouvelle tentative avec **nouveau** jeton (l'ancien hash
  remplacé) ; au-delà de `max-attempts` → `NOT_SENT/SEND_FAILED` + notification.
- [x] **T-SEND-3** — aucune colonne de `portal_link` ni de `notification_outbox` ne contient le
  jeton en clair après envoi (scan des valeurs) ; aucun log capturé ne le contient.
- [x] **T-SEND-4** — l'adresse du destinataire est masquée (`a***@domaine.fr`) dans les logs.

### Accès public (US-1, US-3, D-PORTAL-DATA, D-PORTAL-CSRF-SESSION)

- [x] **T-ACCESS-1** — jeton valide → `GET /api/portal/task` renvoie **exactement** les clés
  `dossierReference, taskName, instructions, schema, expiresAt, attachments` (assertion sur
  l'ensemble des clés JSON, pas seulement leur présence) ; `last_accessed_at` mis à jour.
- [x] **T-ACCESS-2** — jeton inconnu, expiré (avant passage du job), révoqué, consommé, tâche
  disparue, en-tête absent : **même** statut `404` et **même** corps, octet pour octet.
- [x] **T-ACCESS-3** — jeton passé en query string ou en cookie ignoré (404).
- [x] **T-ACCESS-4** — au-delà de `rate-limit.per-ip-per-minute` : `429` **avant** toute requête
  en base (vérifié par mock du repository non appelé).
- [x] **T-ACCESS-5** — chaque refus incrémente `flowforge.portal.access.denied` et journalise IP +
  empreinte tronquée, jamais le jeton.
- [x] **T-ACCESS-6** — lien réutilisable (D-PORTAL-REUSE) : plusieurs `GET` et dépôts successifs
  avec le même jeton tant que `ACTIVE`.
- [x] **T-ACCESS-7** *(ajouté en Phase 9, F-PORTAL-1)* — dépôt avec jeton inconnu ou absent :
  `404` neutre, et au-delà de la limite : `429`, **avant** lecture du corps multipart (une requête
  sans partie `file` le prouve : 404/429 et non 400) ; jeton valide sans fichier : toujours `400`
  (`PortalApiTest.uploadWithUnusableTokenOrOverLimit_isRefusedBeforeTheMultipartBodyIsRead`,
  `PortalAccessResolverTest` pour le contrôle sans effet de bord).

### Dépôt de pièces (US-1, US-2, H4, D-PORTAL-SCAN)

- [x] **T-UPLOAD-1** — dépôt valide → `DossierElement` `ATTACHMENT`, `addedBy = portal:<linkId>`.
- [x] **T-UPLOAD-2** — type non autorisé ou magic bytes incohérents → `400`, rien stocké.
- [x] **T-UPLOAD-3** — antivirus indisponible → pièce **acceptée** (fail-open) + compteur
  existant incrémenté.
- [x] **T-UPLOAD-4** — dossier `CLOTURE`/`ARCHIVE` → refus.
- [x] **T-IDOR-1** — `GET /api/portal/task` ne liste que les pièces du lien courant, pas celles
  d'un autre lien du même dossier ni celles des internes.

### Soumission (US-1, US-6, US-7, D-PORTAL-FORM)

- [x] **T-SUBMIT-1** — réponses valides → `TaskQuestionAnswer.submittedBy = portal:<linkId>`,
  tâche complétée sur le vrai moteur, lien `COMPLETED`, notification
  `PORTAL_TASK_SUBMITTED` aux participants.
- [x] **T-SUBMIT-2** — réponses invalides pour le schéma → `400` détaillé, lien toujours `ACTIVE`.
- [x] **T-SUBMIT-3** — deux soumissions concurrentes (vrais threads) → une seule complétion, la
  seconde `404` neutre.
- [x] **T-SUBMIT-4** — clé de variable réservée dans les réponses → refus (même règle que
  l'interne).
- [x] **T-SUBMIT-5** — non-régression : `CompleteTaskUseCaseImpl` (interne) inchangé après
  extraction de `UserQuestionAnswerRecorder` (suite existante verte).

### Invalidation, expiration, gestion (US-5, US-6, D-PORTAL-EXPIRY, H2, H6)

- [x] **T-INVAL-1** — tâche complétée en interne → lien `REVOKED/TASK_GONE`, jeton `404`.
- [x] **T-INVAL-2** — process annulé / instance supprimée → idem.
- [x] **T-EXPIRY-1** — job : `ACTIVE` expiré → `EXPIRED` + **une seule** notification
  `PORTAL_LINK_EXPIRED` (deux passages du job n'en émettent pas deux).
- [x] **T-MANAGE-1** — renvoyer : ancien lien `REVOKED`, nouveau `PENDING_SEND`, ancien jeton `404`.
- [x] **T-MANAGE-2** — révoquer : lien `REVOKED`, jeton `404` immédiatement.
- [x] **T-MANAGE-3** — « Envoyer » depuis `NOT_SENT` après correction du champ adresse → nouveau
  lien ; adresse toujours invalide → `409` explicite.
- [x] **T-MANAGE-4** — `DOSSIER_VIEWER` ou utilisateur sans EDIT → `403` sur envoyer/révoquer ;
  lecture `GET /portal-links` avec VIEW.
- [x] **T-MANAGE-5** — index unique partiel : deux liens vivants pour la même tâche impossibles
  (H2 ; et PostgreSQL via le smoke Testcontainers existant si le temps le permet).

### Acteur externe et sécurité transverse (D-PORTAL-ACTOR, D-PORTAL-ACTIVATION)

- [x] **T-ACTOR-1** — `AccessContext.External` refusé par `DossierAccessPolicy`,
  `SearchDossiersUseCaseImpl`, `ListDossierTypesUseCaseImpl`, `SlaReportAccess` ; redaction
  appliquée par `DossierFieldRedactor`.
- [x] **T-SEC-1** — portail désactivé : `/api/portal/**` non exposé (retombe dans la chaîne BFF),
  `/portal/` exige l'authentification.
- [x] **T-SEC-2** — portail activé : en-têtes A4 (CSP enforce, `X-Frame-Options`, `nosniff`,
  `Referrer-Policy`) présents sur `/portal/` et `/api/portal/**`.
- [x] **T-SEC-3** — démarrage refusé si `enabled=true` sans `public-base-url`.
- [x] **T-SEC-4** — corps JSON > `max-body-bytes` rejeté avant bufférisation.
- [x] **T-SEC-5** — `SpaFallbackConfig` : `/portal/xyz` sert `static/portal/index.html`, jamais le
  SPA interne ; `/api/portal/inexistant` → 404 JSON. **Vrai seulement en MockMvc jusqu'à la revue
  adversariale** : sur un vrai Tomcat, ce 404 partait vers `/error` et finissait en redirection
  Keycloak (C1) — désormais prouvé sur serveur réel (T-ADV-1).

### Revue adversariale — sur serveur réel (`PortalRealServerTest`, Tomcat `RANDOM_PORT`)

MockMvc n'exécute ni le dispatch d'erreur vers `/error`, ni le suivi de session par URL : ces
scénarios ne sont prouvables qu'avec un vrai serveur. Tous rouges avant correctif.

- [x] **T-ADV-1** (C1) — 405 (mauvaise méthode, sous `/api/portal/**` comme `/api/webhooks/**`),
  404 (URL inconnue sous `/api/portal/**`), 415 (`Content-Type` refusé au mapping), 400 (rejet du pare-feu HTTP, `;`
  dans le chemin) : statut propre, **aucune** redirection de connexion, aucun `JSESSIONID`
  (rouge : `302 …/oauth2/authorization/oidc;jsessionid=…`).
- [x] **T-ADV-2** (C3) — `/submit` en `text/plain` avec ou sans jeton → 415 ; multipart mal formé
  → 400 ; ni 500 ni log ERROR (rouge : 500).
- [x] **T-ADV-3** (C2) — `X-Forwarded-For` fixe posé par le proxy + `Forwarded: for=…` différent à
  chaque requête : 5 réponses 429 sur 35 (rouge : 0). Règle unitaire : `ClientIpResolverTest`
  (pair non fiable = client ; entrée la plus à droite non fiable ; `Forwarded` ignoré ; pas de DNS).
- [x] **T-ADV-4** (H1) — `PortalPreAuthRateLimiterTest` : table plafonnée à 10 000 clients même au
  sein d'une minute, seau de débordement partagé par les nouveaux venus, clients suivis intacts,
  IPv6 regroupé par /64.

### Purge RGPD (D-PORTAL-PURGE)

- [x] **T-PURGE-1** — purge d'un dossier : ses lignes `portal_link` **et** `task_question_answer`
  supprimées ; celles d'un autre dossier intactes (couvre l'écart préexistant §22).

### Contrat

- [x] **T-OPENAPI-1** — golden OpenAPI régénéré : endpoints instructeur + `GET`/`submit` portail ;
  multipart portail `@Hidden`.

## End-to-End & Frontend Tests

### Vitest — build portail

- [x] **V-PORTAL-1** — `PortalApp` lit le jeton dans `location.hash`, l'efface via
  `history.replaceState`, l'envoie en `X-Portal-Token`, jamais en URL.
- [x] **V-PORTAL-2** — toute erreur `404` → écran neutre unique (texte identique quel que soit le
  cas).
- [x] **V-PORTAL-3** — formulaire rendu par `DynamicFields` depuis le schéma reçu ; soumission →
  écran de confirmation ; erreurs `400` affichées par champ.
- [x] **V-PORTAL-4** — dépôt de pièce : liste mise à jour, erreurs de type/taille affichées.
- [x] **V-PORTAL-5** — le code sous `frontend/portal/**` n'importe ni `@/stores/auth`, ni le
  routeur interne, ni `@/api/http` (règle ESLint `no-restricted-imports` testée par `npm run lint`).

### Vitest — SPA interne

- [x] **V-INT-1** — panneau « Tiers externes » : statuts, destinataire, expiration, actions
  selon droits (EDIT vs VIEW).
- [x] **V-INT-2** — `flowablePropertiesProvider` : champs `flowable:portalRecipient` et
  `flowable:portalLinkTtlDays` sur `bpmn:UserTask`, lecture/écriture/suppression.
- [x] **V-INT-3** — tiroir de notifications : rendu des types `PORTAL_TASK_SUBMITTED`,
  `PORTAL_LINK_EXPIRED`, `PORTAL_LINK_NOT_SENT` avec lien vers le dossier.

### Playwright

- [x] **E2E-PORTAL-1** — parcours nominal : `/portal/#<jeton>` → formulaire + dépôt → soumission →
  confirmation ; le fragment a disparu de l'URL.
- [x] **E2E-PORTAL-2** — lien invalide : écran neutre ; aucun appel à `/api/auth/**`.
- [x] **E2E-PORTAL-3** — instructeur : détail dossier, renvoyer puis révoquer un lien.
- [x] **E2E-PORTAL-4** — éditeur BPMN : saisie de `portalRecipient` / `portalLinkTtlDays`,
  présents dans le XML sauvegardé.
- [x] **E2E-PORTAL-5** *(ajouté en Phase 5)* — la page portail s'affiche sous la CSP enforce A4
  injectée, sans aucune violation (critère de succès « CSP A4 sur les pages portail »).
- [x] **E2E-REG** — les 81 scénarios existants restent verts.

## Test Data

- BPMN de test `portal-smoke.bpmn20.xml` (test-resources) : `userTask` externe
  `user-question:portal-smoke` + `portalRecipient="dossierField:contactEmail"`.
- Schéma `user-question` `portal-smoke` et type de dossier avec champ `contactEmail`.
- `EmailPort` : double de test capturant les messages (jeton extrait de l'e-mail capturé pour les
  tests d'accès — seul chemin légitime vers le jeton en clair).
- `Clock` injectable pour les tests d'expiration.
- Frontend : mocks Playwright `frontend/e2e/**` des routes `/api/portal/**` et
  `/api/dossiers/*/portal-links`.

## Test Reporting & Coverage

- `./mvnw verify -Dmaven.compiler.release=21` (Jacoco + ArchUnit) ; `cd frontend && npm run test:all`.
- Résultats et écarts consignés dans ce document et TODO §68 à chaque clôture de phase.

### Résultats — Phases 8 et 9 (2026-09-24, après intégration de `main`)

Preuves fraîches, sur le code final (merge de `main` : PR #134 DMN et #136 §69 ; correctif
F-PORTAL-1 inclus) :

| Commande | Résultat |
|---|---|
| `./mvnw -o -Dmaven.compiler.release=21 -Dskip.frontend=true verify` (après `npm run build` et copie de `frontend/dist/.` dans `target/classes/static/`) | exit 0 — **2347 tests, 0 échec, 0 ignoré** ; seuils Jacoco (85 % lignes / 75 % branches) et ArchUnit verts |
| `./mvnw … test -Dtest=OpenApiContractIT` (golden fusionné automatiquement des deux côtés) | exit 0 — contrat inchangé |
| `cd frontend && npm run lint && npm run build && npx vitest run` | exit 0 — **86 fichiers, 402 tests** |
| `cd frontend && PLAYWRIGHT_CHROMIUM_EXECUTABLE=/opt/pw-browsers/chromium npm run test:e2e` | exit 0 — **92/92** (dont E2E-PORTAL-1…5 et les 6 scénarios DMN venus de `main`) |

Couverture Jacoco (`target/site/jacoco/jacoco.csv`) :

| Périmètre | Lignes | Branches |
|---|---|---|
| Projet | 94,2 % | 79,5 % |
| Code du portail (domaine, infra, web, scanner §69, `UserQuestionAnswers`) | 96,5 % (805/834) | 87,5 % (286/327) |
| Domaine `workflow.portal` | 99,0 % (406/410) | 92,3 % (143/155) |
| Use cases portail | **100 %** (228/228) | 95,1 % (78/82) |

**Objectif « 100 % des branches des use cases » non atteint (78/82)** : les quatre branches
restantes sont des moitiés de conditions composées défensives — lien d'un autre tenant *sur le bon
dossier* (`ManagePortalLinkUseCaseImpl:92`), contenu `null` (le contrôleur transmet toujours un
tableau, `AddPortalAttachmentUseCaseImpl:60`), élément non-pièce du même acteur
(`GetPortalTaskUseCaseImpl:54`), adresse non textuelle non vide (`IssuePortalLinkUseCaseImpl:85`).
Jugées sans valeur de test supplémentaire ; consigné plutôt que masqué.

**Tests ajoutés en Phase 8** (branches que les tests moteur n'atteignent pas) :
`PortalParticipantNotifierTest` (aucun lien de workflow dans les tests moteur : le notificateur
n'y était jamais exercé), `EmailPortalInviteSenderTest` (CR/LF aplatis, valeurs par défaut, date),
`ManagePortalLinkUseCaseImplTest`, `AddPortalAttachmentUseCaseImplTest`,
`PortalRaceAndMissingDataTest` (transition compare-and-set **perdue** : le perdant ne trace, ne
complète ni ne notifie rien ; schéma supprimé après émission → refus neutre), côté frontend
`api/portalLinks.spec.ts`, `DynamicFieldInput.spec.ts` (garde d'import), cas `PortalApp`
supplémentaires. **Phase 9** : `PortalAccessResolverTest` et T-ACCESS-7 (F-PORTAL-1).
Aucun de ces tests n'a révélé de défaut de production, sauf T-ACCESS-7 (rouge avant correctif).

## Manual Testing

- Smoke manuel avec un outil de capture SMTP (Mailpit/Mailhog lancé à part — **non fourni** par
  `docker-compose.yml`, voir `GETTING-STARTED.md` §6) : réception de l'e-mail, ouverture du lien,
  parcours complet.
- Vérification visuelle mobile de la page portail (le tiers ouvre souvent le lien sur téléphone).

## Performance Testing

Hors périmètre (volumétrie faible : un lien par tâche externe) ; le lookup par hash est indexé.

## Bug Tracking

Findings de revue suivis dans TODO §68 (identifiants `F-PORTAL-n`), comme les cycles précédents.
