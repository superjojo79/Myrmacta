---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
---

# Implementation Guide — `portail-externe`

> TODO §68. **Phase 5 (implémentation) livrée le 2026-09-24**, milestones M1 à M13 enchaînés sur
> demande explicite de l'utilisateur, en TDD. Écarts au design :
> [design § Écarts constatés à l'implémentation](../design/2026-09-23-feature-portail-externe.md).
> Précédée le même jour par le correctif de sécurité TODO §69 (surfaces UEL du BPMN), sur lequel le
> parser portail s'appuie.

## Development Setup

- JDK 21 suffit dans un conteneur sans JDK 25 : `./mvnw verify -Dmaven.compiler.release=21`.
- Sans build frontend (`-Dskip.frontend=true`), `OpenApiDisabledApiTest` échoue faute de
  `static/index.html` (artefact connu) : `cd frontend && npm ci && npm run build`, puis copier
  `frontend/dist/.` dans `target/classes/static/` avant `verify` (sans `clean`).
- Playwright dans ce conteneur : `PLAYWRIGHT_CHROMIUM_EXECUTABLE=/opt/pw-browsers/chromium`
  (révision de Chromium pré-installée ≠ celle attendue par `@playwright/test` 1.62).
- Portail en local : `APP_PORTAL_ENABLED=true`, `APP_PORTAL_PUBLIC_BASE_URL=http://localhost:8080`
  (GETTING-STARTED §14).

## Code Structure

| Couche | Emplacement |
|---|---|
| Domaine | `com.flowforge.domain.workflow.portal` : `model/` (PortalLink et statuts, source de destinataire, e-mails), `port/in/` (8 use cases), `port/out/` (dépôt, jeton, envoi, résolution d'adresse, lecture de tâche, disponibilité), `usecase/` (8 `*Impl`), `PortalAccessResolver`, `PortalParticipantNotifier` |
| Domaine partagé | `domain.workflow.UserQuestionAnswers` (validation + trace `user-question`), `AccessContext.External` |
| Persistance | `infrastructure.persistence.workflow` : `PortalLinkJpaEntity`, `SpringDataPortalLinkRepository` (transition compare-and-set), `JpaPortalLinkRepositoryAdapter` ; migration `025-portal-link` (+ SQL PostgreSQL/H2 de l'index unique partiel) |
| Moteur | `infrastructure.flowable` : `PortalTaskEventListener` (conditionnel), `FlowablePortalTaskLookupAdapter`, `PortalRecipientResolverAdapter` ; `FlowableBpmnParserAdapter.portalDefinition` |
| Envoi / jobs | `infrastructure.portal` : `SecurePortalTokenAdapter`, `EmailPortalInviteSender`, `PortalInviteSenderJob`, `PortalLinkExpiryJob` |
| Web | `infrastructure.web.portal` : `PortalController` (public), `PortalPreAuthInterceptor` (limite par IP + jeton avant lecture du corps), `PortalLinkAdminController` (BFF), limiteur par IP, gestionnaires d'erreurs ; `infrastructure.security.PortalSecurityConfig` ; `SpaFallbackConfig.SpaResourceResolver` |
| Config | `PortalProperties` (`app.portal.*`), `PortalConfig` (câblage) |
| Frontend public | `frontend/portal/` (build `vite.portal.config.ts` → `dist/portal/`) |
| Frontend interne | `PortalLinksPanel.vue`, `api/portalLinks.ts`, `DynamicFieldInput.vue`, champs BPMN dans `flowablePropertiesProvider.ts` |

## Implementation Notes

- **Transitions sûres** : toute mutation de lien passe par `PortalLinkRepository#transition(current,
  next)`, un `UPDATE … WHERE id = ? AND status = ?` : double soumission, renvoi concurrent et
  expiration ne peuvent pas s'écraser (T-SUBMIT-3 prouvé avec de vrais threads).
- **Soumission** : validation → `ACTIVE→COMPLETED` → trace → complétion de la tâche → notification,
  dans une transaction ; le lien n'étant plus vivant au moment de `TASK_COMPLETED`, le listener
  d'invalidation ne le révoque pas.
- **Émission** dans la transaction du moteur (`isFailOnException`) : un rollback de la création de
  tâche ne laisse aucun lien (T-ISSUE-8, prouvé).
- **Accès** : le jeton n'est accepté qu'en en-tête ; hash → lien → `isUsable(now)` (expiration
  vérifiée à chaque requête, indépendamment du job) → tâche ouverte → dossier existant ;
  `last_accessed_at` mis à jour. Tout refus → la même `PortalAccessDeniedException` → même 404.
- **`AccessContext.External`** : refusé par toutes les politiques ; accepté seulement par
  `AddDossierElementUseCaseImpl` pour une pièce jointe (`addedBy` forcé) ; règle ArchUnit : seul
  `..workflow.portal..` peut en construire un.
- **Chaînes Spring Security** : portail `@Order(3)` (conditionnel), webhook `@Order(4)`, BFF
  `@Order(5)`.

## Integration Points

- Notifications BROWSER via `SendNotificationUseCase` (payloads `PORTAL_TASK_SUBMITTED`,
  `PORTAL_LINK_EXPIRED`, `PORTAL_LINK_NOT_SENT`, mêmes clés que les producteurs existants).
- E-mail via `EmailPort` (SMTP), hors outbox.
- Upload : `AttachmentContentSniffer`, `AttachmentScanPort`, `DocumentStoragePort`,
  `AddDossierElementUseCase` réutilisés tels quels.
- Purge : `ProcessPurgeOutboxUseCaseImpl` supprime aussi `task_question_answer` (écart préexistant
  §22) et `portal_link`.

## Error Handling

| Cas | Réponse |
|---|---|
| Jeton absent, inconnu, trop long, expiré, révoqué, consommé, tâche/dossier/schéma disparu | `404` corps identique « Lien invalide ou expiré » |
| Plus de 30 requêtes/min depuis une IP | `429` avant toute requête en base et avant lecture du corps (`PortalPreAuthInterceptor`, F-PORTAL-1) |
| Dépôt avec jeton absent/invalide | `404` neutre **avant** lecture du corps multipart (F-PORTAL-1) |
| Réponses invalides, champ inconnu, variable réservée, fichier refusé | `400` (le lien reste `ACTIVE`) |
| Dossier clos | `409` |
| Corps JSON > 64 Ko | `413` avant bufférisation |
| Instructeur : adresse toujours invalide à l'envoi | `409` + lien `NOT_SENT` dans le corps |

## Performance Considerations

Lookup par index unique sur le hash ; définitions portail mises en cache par définition de process
dans le listener ; jobs par lots de 50.

## Security Notes

Voir design § NFR. Points vérifiés par test : jeton absent de la base, de l'outbox et des logs
(T-SEND-3) ; vue publique limitée à un ensemble exact de clés, sans `defaultValue` (T-ACCESS-1) ;
IDOR sur les pièces (T-IDOR-1) ; en-têtes A4 et rendu sous CSP enforce sans violation (T-SEC-2,
E2E-PORTAL-5) ; portail désactivé = aucune route publique (T-SEC-1) ; fail-open ClamAV réel
(T-UPLOAD-3, risque accepté).

## Phase 7 — contrôle de l'implémentation (2026-09-24)

Relecture fichier par fichier du diff complet contre le design et les requirements. **3 défauts
réels trouvés et corrigés (TDD, test rouge d'abord)** :

| # | Défaut | Correctif |
|---|---|---|
| P7-1 | `/portal` sans slash final servait `portal/index.html` dont les assets relatifs se résolvaient vers `/assets/…` (bundle du SPA interne, chaîne authentifiée) : page blanche | `PortalPageRedirectController` : `/portal` → `/portal/` (le fragment `#jeton` survit à la redirection) |
| P7-2 | `apiBase()` prenait le **premier** `/portal` du chemin : un tenant dont le chemin contient `/portal` appelait une mauvaise URL | `lastIndexOf('/portal/')` + cas de test |
| P7-3 | Une erreur imprévue (panne de stockage…) quittait le contrôleur vers `/error`, hors de la chaîne portail (réponse BFF possible ou détail technique exposé) | Gestionnaire attrape-tout dans `PortalExceptionHandler` : 500 JSON générique, détail au log serveur |

**Vérification complémentaire** : la règle ArchUnit « seul `..workflow.portal..` construit
`AccessContext.External` » est prouvée effective par un fixture qui la viole via la fabrique **et**
le constructeur (`ArchitectureRulesFixtureTest`).

**Limitation connue, non corrigée (documentée)** : les tentatives d'envoi SMTP se succèdent au
rythme du job (10 s) sans temporisation croissante — une panne SMTP de plus d'environ 50 s fait
passer le lien en *Non envoyé / échec d'envoi* ; l'instructeur est notifié et peut renvoyer. Un
backoff exigerait une colonne `next_attempt_at` (suivi possible).

Alignement avec le design : conforme, hors écarts déjà listés au design (§ Écarts constatés).

## Phase 9 — revue holistique (2026-09-24)

Revue du diff complet **après intégration de `main`** (PR #134 outillage DMN, PR #136 §69 ;
conflits de documentation seulement, code fusionné automatiquement — `FlowableBpmnParserAdapter`,
`WorkflowConfig`, golden OpenAPI, `pilotage.spec.ts`). Points contrôlés : exhaustivité des
`switch` sur `AccessContext` (scellé, sans `default` : le compilateur l'impose), ordre et
périmètre des chaînes Spring Security, cohérence du parsing BPMN (scanner §69 avant le modèle
Flowable, DTD refusée), e-mail d'invitation (texte brut `setText(body, false)`, CR/LF aplatis),
contrôleurs (aucun identifiant accepté en entrée), migration `025` (rollback présent, pas de
collision de numéro avec `main`).

| # | Sévérité | Constat | Suite |
|---|---|---|---|
| F-PORTAL-1 | **Moyenne** | La limite par IP et le contrôle du jeton étaient dans le corps des méthodes du contrôleur, donc **après** la résolution des arguments : pour `POST /attachments`, le `MultipartFile` fait lire et parser tout le corps (jusqu'à 25 Mo, `resolve-lazily` ne diffère que jusque-là). Un anonyme sans jeton faisait ingérer un upload complet à chaque requête avant d'obtenir 404 ou 429 | **Corrigé (TDD, rouge d'abord : 400 au lieu de 404)** — `PortalPreAuthInterceptor` (`preHandle`, avant la résolution des arguments) : limite par IP sur toutes les routes du contrôleur ; jeton vivant exigé avant lecture du corps pour le dépôt (`PortalAccessResolver#assertUsableToken`, sans effet de bord), même 404 neutre |
| F-PORTAL-2 | Faible | Un corps multipart **mal formé** (`MultipartException`) tombe dans le filet attrape-tout de la Phase 7 : 500 générique journalisé en erreur au lieu d'un 400. Les cas courants (JSON illisible, partie ou paramètre manquant, fichier trop gros) ont bien leur 400/413 | Confirmé par la revue adversariale sur serveur réel (C3) et **corrigé** — voir ci-dessous |
| — | Info | Envoi de l'e-mail *puis* activation compare-and-set : si la course est perdue (révocation pendant l'envoi), le destinataire reçoit un lien déjà mort — échec sûr (fail-closed), déjà listé dans les écarts au design | Aucune |
| — | Info | La limite par IP s'appuie sur `getRemoteAddr()` avec `forward-headers-strategy: framework` : le reverse-proxy **doit** écraser `X-Forwarded-For` (précondition déjà exigée par A4, DEPLOIEMENT.md) | Aucune (documenté) |

Réutilisation : aucun doublon trouvé ; `WebhookBodySizeLimitFilter` et `SecurityHeadersCustomizer`
réutilisés tels quels, `UserQuestionAnswers` et `DynamicFieldInput.vue` extraits plutôt que
copiés.

## Revue adversariale (2026-09-24, subagent) et correctifs

Revue menée dans un worktree isolé, chaque constat prouvé sur un **vrai Tomcat** (`RANDOM_PORT`).
Aucun constat critique ni haut ; ni IDOR, ni escalade de l'acteur externe, ni fuite du jeton, ni
XSS. Trois constats confirmés et une hypothèse, **tous corrigés en TDD** (tests rouges d'abord,
`PortalRealServerTest`) :

| # | Sévérité | Constat | Correctif |
|---|---|---|---|
| C1 | Moyenne | Une erreur levée au mapping (405, 404, 415, rejet du `StrictHttpFirewall`) part vers `/error` hors de la chaîne portail : la chaîne BFF répondait par une **redirection Keycloak** (révèle l'application interne et l'IdP) et créait une **session anonyme**, `;jsessionid` dans l'URL. Même mécanisme pour les webhooks | `SecurityConfig` : `dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()` dans la chaîne BFF (une requête anonyme vers une route protégée est refusée au dispatch initial, avant tout `/error`) ; `server.servlet.session.tracking-modes: cookie` |
| C2 | Moyenne | La limite par IP suivait `getRemoteAddr()`, réécrit par `ForwardedHeaderFilter` qui **préfère `Forwarded`** (RFC 7239) — non supprimé par le proxy documenté : 35 requêtes, 0 réponse 429 | `ClientIpResolver` (infrastructure.security) : pair TCP sous les wrappers ; s'il est dans `trusted-proxy-cidrs`, entrée la plus à droite non fiable de `X-Forwarded-For` ; `Forwarded` jamais lu ; littéraux IP seulement (aucun DNS). Utilisé par le portail **et** le limiteur des rapports CSP (même cause). DEPLOIEMENT.md : le proxy supprime aussi `Forwarded` et `X-Forwarded-Prefix` (qui portent aussi schéma et hôte) |
| C3 | Faible | = F-PORTAL-2 : le filet attrape-tout de la Phase 7 transformait les exceptions MVC standard (415…) et `MultipartException` en 500 + pile au niveau ERROR, déclenchable sans jeton | `PortalExceptionHandler` : une `ErrorResponse` 4xx garde son statut ; `MultipartException` → 400 ; log INFO sans pile |
| H1 | (hypothèse) | La table du limiteur n'était purgée que des minutes passées : des clés distinctes dans la minute courante n'étaient jamais bornées, et chaque requête au-delà du seuil la parcourait entière | Plafond dur de 10 000 clients ; purge au plus une fois par minute ; au-delà, seau de débordement commun aux nouveaux venus ; IPv6 par /64 |

Hypothèses non retenues (non reproduites, sans destination exploitable trouvée) : plafond de 64 Ko
contourné par un formulaire urlencoded *chunked* (H2) ; libellé/nom de fichier stockés bruts (H3,
aucun `v-html`, téléchargement en `attachment` + `nosniff`) ; historique du navigateur (H4) ;
oracle temporel « tâche disparue » (H5, un lien actif est révoqué dès la fin de sa tâche).

## Résultats de vérification

Voir [testing](../testing/2026-09-23-feature-portail-externe.md) § Résultats (chiffres Phase 8/9,
après intégration de `main`) et TODO §68.
