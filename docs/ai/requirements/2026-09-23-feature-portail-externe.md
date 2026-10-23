---
phase: requirements
status: reviewed
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
---

# Requirements & Problem Understanding

> Origine : `ROADMAP.md` item **3.1** (« Produit, pas cadré »), analyse initiale
> [docs/specs/26-portail-externe.md](../../specs/26-portail-externe.md) — scindée le
> 2026-09-23 de la signature électronique (désormais lot distinct,
> [30-signature-electronique.md](../../specs/30-signature-electronique.md), démarrable
> après ce lot). Cadrage Phase 1 mené le 2026-09-23, **10 décisions tranchées par l'utilisateur**
> via `AskUserQuestion` (voir « Décisions »). TODO §68. **Phase 2 (revue) close le même jour** :
> hypothèses H1–H6 validées en exigences, 2 trous comblés (adresse absente, purge RGPD) dont un
> écart préexistant de la purge de dossier (voir « Décisions Phase 2 »).

## Problem Statement

**Constat vérifié dans le code (`main` @ `b3f03f9`, 2026-09-23) :**

- Un tiers sans compte Keycloak (citoyen, fournisseur, client final, partenaire) ne peut
  aujourd'hui interagir avec **aucune** partie de FlowForge. La chaîne Spring Security BFF
  (`SecurityConfig.bffSecurityFilterChain`, `@Order(4)`) se termine par
  `anyRequest().authenticated()` : même `index.html` et les bundles JS servis par
  `SpaFallbackConfig` redirigent vers le login OIDC.
- Les seules routes anonymes existantes sont serveur-à-serveur (`/api/webhooks/**`,
  `/api/callbacks/**`, chaîne `@Order(3)`, HMAC) ou techniques (rapports CSP, `@Order(2)`).
- La complétion d'une `userTask` exige un `AccessContext.User` OIDC et applique
  `TaskAssignmentGuard` (TODO §57 H2) : le modèle ne connaît aucun acteur externe.
- Les briques nécessaires existent déjà côté interne : formulaires `user-question:<schemaKey>`
  (TODO §55, réponses tracées dans `TaskQuestionAnswer`), dépôt de pièce jointe avec magic bytes
  + `AttachmentScanPort` (ClamAV opt-in, PR #131), canal e-mail (outbox + `smtp`), inbox de
  notifications persistée (PR #135), rate-limiters JVM-locaux (`SlidingWindowRateLimiter`).

**Qui est affecté :**

- **Le tiers externe** : il doit aujourd'hui répondre hors outil (e-mail, téléphone, papier).
- **L'instructeur** (`DOSSIER_MANAGER`) : il ressaisit dans FlowForge ce que le tiers a envoyé par
  e-mail, joint les pièces à la main, et relance sans visibilité.
- **Le concepteur de process** (`WORKFLOW_ADMIN`) : il ne peut pas modéliser une étape « le tiers
  répond » ; il la simule par une tâche interne.

**Contournement actuel :** ressaisie manuelle par l'instructeur, pièces reçues par e-mail puis
déposées à la main (sans que le tiers ne passe par le scan au moment de l'envoi), aucune trace
structurée de la réponse du tiers.

## Goals & Objectives

**Objectif principal :** permettre à un tiers **sans compte** de répondre à une **tâche BPMN qui
lui est destinée** — remplir un formulaire de questions et déposer des pièces — via un **lien
magique** reçu par e-mail, sans ouvrir d'autre accès au dossier.

**Objectifs secondaires :**

- Émission automatique du lien par le process, envoi par e-mail via le canal existant.
- Gestion manuelle par l'instructeur : renvoyer (nouveau lien) et révoquer depuis le détail du
  dossier.
- Traçabilité : la réponse et les pièces sont attribuées à un acteur externe identifiable
  (tâche + destinataire), jamais à un utilisateur interne.
- Notification des participants internes à la complétion par le tiers et à l'expiration du lien
  (inbox, PR #135).

**Non-goals (explicitement hors périmètre de ce lot) :**

- Signature électronique — lot `signature-electronique` distinct, après celui-ci.
- Espace de consultation du dossier par le tiers (lecture des champs, documents produits,
  historique) — écarté par D-PORTAL-SCOPE / D-PORTAL-DATA.
- Écriture directe dans les champs du dossier (`dossier-fields`) ou génération de document
  (`document-generation`) par le tiers — écarté par D-PORTAL-FORM.
- Compte partenaire, second facteur (code OTP), SSO externe — écartés par D-PORTAL-AUTH.
- Brouillon côté serveur : le tiers peut revenir sur le lien tant qu'il est valide, mais une
  saisie non soumise n'est pas conservée par le serveur.
- Personnalisation graphique par tenant (logo, couleurs), multilingue : interface en français.
- Rate-limit distribué / multi-réplique (pas de Redis, décision n° 13, S-7 assumé).
- Plusieurs destinataires pour une même tâche : un lien actif = un destinataire.

## Décisions (Phase 1, tranchées par l'utilisateur le 2026-09-23)

| ID | Décision | Alternatives écartées |
|---|---|---|
| **D-PORTAL-SCOPE** | **Tâche externe** : un lien = une `userTask` destinée au tiers ; il voit le contexte utile, remplit le formulaire et dépose des pièces ; le lien expire à la complétion. | Espace dossier partagé (un lien par dossier) ; les deux. |
| **D-PORTAL-AUTH** | **Lien magique seul** : jeton opaque aléatoire (≥ 256 bits), stocké **haché**, lié à une tâche, expirant, révocable. Posséder le lien = avoir l'accès. | Lien + code OTP par e-mail ; compte partenaire Keycloak. |
| **D-PORTAL-ISSUE** | **BPMN + gestion manuelle** : une `userTask` marquée « externe » crée le lien et l'envoie par e-mail (canal SMTP existant) à une adresse issue du dossier/process ; l'instructeur peut **renvoyer** ou **révoquer** depuis le détail du dossier. | BPMN seul ; instructeur seul. |
| **D-PORTAL-TTL** | **7 jours par défaut, surchargeable par tâche** dans le BPMN, **plafonné** par une valeur tenant. Le lien meurt aussi à la complétion ou à la révocation. | 72 h ; 30 jours. |
| **D-PORTAL-CSRF-SESSION** | **Sans session** : jeton dans le **fragment d'URL** (`#`), jamais transmis au serveur dans l'URL (absent des logs proxy et du `Referer`) ; la page le relit et l'envoie en en-tête à chaque appel API. Pas de cookie, donc pas de CSRF. | Échange contre un cookie de session portail dédié. |
| **D-PORTAL-DATA** | **Contexte de la tâche seul** : référence du dossier, libellé et consignes de la tâche, formulaire, et les pièces **déposées par le tiers lui-même**. Aucun autre champ ni document du dossier. | + champs non sensibles ; + documents produits. |
| **D-PORTAL-FORM** | **`user-question` uniquement** : réponses tracées dans `TaskQuestionAnswer` ; le tiers n'écrit jamais directement dans les champs du dossier. | `user-question` + `dossier-fields`. |
| **D-PORTAL-ACTIVATION** | **Opt-in, désactivé par défaut** (propriété de démarrage) : désactivé ⇒ aucune route publique exposée **et** une tâche externe est **rejetée au déploiement BPMN**. Même patron que ClamAV (PR #131) et l'alerting SLA (PR #132). | Actif par défaut. |
| **D-PORTAL-SCAN** | **Même politique fail-open qu'en interne** : si l'antivirus est `noop` ou si `clamd` ne répond pas, la pièce déposée par le tiers est **acceptée** (log + compteur Micrometer existants). **Risque accepté explicitement par l'utilisateur**, contre la recommandation fail-closed — voir « Risques acceptés ». | Fail-closed portail ; fail-closed + dépôt désactivable par tâche. |
| **D-PORTAL-EXPIRY** | **Tâche ouverte + alerte** : à l'expiration, le lien devient invalide, la tâche reste ouverte et les participants internes reçoivent une notification (inbox) pour renvoyer un lien ou décider. Relances/délais métier restent du ressort du BPMN (timer boundary) si le concepteur le souhaite. | Lien invalide sans autre effet. |

## Décisions Phase 2 (revue, tranchées par l'utilisateur le 2026-09-23)

| ID | Décision | Alternative écartée |
|---|---|---|
| **D-PORTAL-REUSE** (ex-H1) | Le lien est **réutilisable** jusqu'à soumission, expiration ou révocation. | Usage unique à l'ouverture. |
| **D-PORTAL-NO-ADDRESS** | Adresse du destinataire **absente ou invalide** à l'activation de la tâche : **aucun lien émis**, tâche ouverte, notification inbox des participants ; l'instructeur corrige la source puis déclenche l'envoi (même action que « Renvoyer »). Le process n'est jamais mis en incident pour ce motif. | Erreur du process (incident Flowable). |
| **D-PORTAL-PURGE** | La purge de dossier (`ProcessPurgeOutboxUseCaseImpl`, TODO §22) supprime les **liens portail** (adresse du tiers incluse) **et** les réponses `TaskQuestionAnswer` du dossier. Ce second point corrige un **écart préexistant** trouvé en revue : la table `task_question_answer` porte un `dossier_id` mais n'est pas dans le graphe de purge (vérifié dans `ProcessPurgeOutboxUseCaseImpl`, 2026-09-23). L'adresse du tiers est masquée dans les logs. | Liens seulement, écart `TaskQuestionAnswer` dans un lot séparé. |
| **H2, H4, H5, H6** | Validées telles quelles, deviennent des exigences (voir « Constraints & Assumptions »). | — |

## User Stories & Use Cases

- **US-1 (tiers)** — En tant que tiers sans compte, je reçois un e-mail contenant un lien ; en
  l'ouvrant, je vois la référence du dossier, le libellé et les consignes de ce qu'on attend de
  moi, un formulaire de questions, et je peux déposer des pièces, puis soumettre.
- **US-2 (tiers)** — En tant que tiers, je peux rouvrir le lien plus tard (tant qu'il est valide
  et que je n'ai pas soumis) pour déposer des pièces supplémentaires avant de soumettre.
- **US-3 (tiers)** — En tant que tiers, si mon lien a expiré, a été révoqué ou a déjà servi, je
  vois un message neutre m'invitant à contacter mon interlocuteur — sans indication permettant de
  distinguer ces cas d'un lien inexistant.
- **US-4 (concepteur BPMN)** — En tant que `WORKFLOW_ADMIN`, je marque une `userTask` comme
  externe dans l'éditeur BPMN, je lui associe un schéma `user-question`, la source de l'adresse
  e-mail du destinataire et, si besoin, une durée de validité ; un déploiement incohérent
  (portail désactivé, schéma absent, `formKey` autre que `user-question`, durée au-delà du
  plafond) est refusé avec un message explicite.
- **US-5 (instructeur)** — En tant que `DOSSIER_MANAGER` avec droit EDIT sur le type, je vois sur
  le détail du dossier qu'une tâche externe est en attente (destinataire, date d'envoi,
  expiration, statut) ; je peux **renvoyer** un nouveau lien (l'ancien est révoqué) ou
  **révoquer** le lien.
- **US-6 (participants internes)** — En tant que participant au workflow, je reçois une
  notification dans l'inbox quand le tiers a soumis sa réponse, et quand un lien expire sans
  réponse.
- **US-7 (audit)** — En tant qu'administrateur, je peux retrouver qui (destinataire externe, via
  quelle tâche et quel lien) a répondu et déposé quelles pièces.

**Cas limites à couvrir :**

- Tâche complétée en interne (ou process annulé, dossier clôturé/purgé) pendant que le lien est
  valide ⇒ lien invalide immédiatement.
- Double soumission (double clic, rejeu) ⇒ une seule complétion, la seconde échoue proprement.
- Renvoi pendant que le tiers a la page ouverte avec l'ancien lien ⇒ l'ancien est rejeté.
- Adresse e-mail source absente ou invalide au moment de l'activation de la tâche ⇒ aucun lien
  émis, notification interne, process non bloqué ; envoi manuel après correction
  (D-PORTAL-NO-ADDRESS).
- Purge du dossier ⇒ liens portail et réponses `TaskQuestionAnswer` supprimés (D-PORTAL-PURGE).
- Dépôt de pièce : même limites de taille/type et même contrôle magic bytes qu'en interne ;
  antivirus selon D-PORTAL-SCAN.
- Portail désactivé après coup alors que des liens existent ⇒ routes publiques absentes, liens
  inutilisables ; tâches externes toujours complétables en interne par un admin.
- Tentatives d'énumération / force brute sur les jetons ⇒ rate-limit pré-authentification et
  journalisation des échecs (leçons F5–F8, TODO §54).

## Success Criteria

- Un tiers peut, à partir du seul e-mail reçu, remplir le formulaire `user-question` d'une tâche
  externe, déposer des pièces et soumettre ; le process avance ; les réponses sont dans
  `TaskQuestionAnswer` et les pièces rattachées au dossier, attribuées à l'acteur externe.
- Aucune route publique n'est exposée quand le portail est désactivé (vérifié par test), et un
  BPMN contenant une tâche externe est alors refusé au déploiement.
- Le jeton n'apparaît en clair ni en base, ni dans les logs applicatifs (MDC/ECS), ni dans l'URL
  envoyée au serveur ; il a ≥ 256 bits d'entropie ; comparaison en temps constant.
- Un jeton expiré, révoqué, déjà consommé ou inexistant produit la **même** réponse (pas
  d'oracle).
- Un lien donne accès **uniquement** au contexte de sa tâche : aucun endpoint portail ne renvoie
  d'autre champ, pièce, document ou tâche du dossier (tests d'IDOR explicites).
- La CSP enforce (A4) s'applique aux pages et API portail ; aucune ressource portail ne nécessite
  `unsafe-eval` ni un élargissement de CSP.
- Les participants internes reçoivent une notification inbox à la soumission, à l'expiration et
  quand aucun lien n'a pu être émis faute d'adresse valide.
- Après purge d'un dossier, aucune ligne de lien portail ni de `TaskQuestionAnswer` ne subsiste
  pour ce dossier (test de purge étendu).
- Renvoyer révoque l'ancien lien ; révoquer rend le lien inutilisable immédiatement.
- Tests : `./mvnw verify` vert (ArchUnit inclus, domaine pur), Vitest et Playwright verts avec
  scénarios portail dédiés (parcours nominal, lien invalide, IDOR, portail désactivé).
- Documentation : `GUIDE-UTILISATEUR.md` (instructeur), `GUIDE-CONCEPTION-BPMN.md` (tâche
  externe), `GETTING-STARTED.md` + `.env.example` (nouvelles variables), `DEPLOIEMENT.md`
  (exposition des routes publiques derrière le reverse-proxy), `CHANGELOG.md`.

## Constraints & Assumptions

**Contraintes techniques (stack figée, conventions projet) :**

- Architecture hexagonale : domaine pur, ports `in`/`out`, ArchUnit en CI.
- Une instance = un tenant ; le lien porte le chemin de l'instance (routage reverse-proxy par
  chemin, décision n° 14).
- SPA même origine (décision n° 27), pas de CORS applicatif ; les ressources du portail doivent
  être servies **sans** passer par la chaîne BFF authentifiée, sans ouvrir le reste du SPA.
- Nouvelle chaîne Spring Security dédiée hors OIDC, stateless, sur le modèle `@Order(3)`, avec
  limite de taille du corps avant bufférisation et rate-limit pré-authentification.
- `userTask` : `assignee`/`candidateUsers`/`candidateGroups` littéraux uniquement (allowlist
  BPMN, TODO §57 H1-bis) — le marquage « externe » doit rester compatible avec l'allowlist.
- Rate-limit JVM-local uniquement (mono-réplique, pas de Redis).

**Hypothèses — validées en exigences en Phase 2 (2026-09-23) :**

- **H1** — Le lien est **réutilisable** jusqu'à complétion, révocation ou expiration (US-2) ; il
  n'est pas à usage unique à l'ouverture. → **D-PORTAL-REUSE**.
- **H2** — **Un seul lien actif par tâche** ; un renvoi révoque le précédent.
- **H3** — L'adresse du destinataire est désignée dans le BPMN (champ du dossier ou variable de
  process) ; la syntaxe exacte est tranchée en Phase 3 (design).
- **H4** — Les pièces déposées par le tiers suivent les mêmes limites (taille, types, magic
  bytes) que l'upload interne.
- **H5** — L'acteur externe n'obtient **aucun** rôle V1 ; il est représenté par une identité
  dédiée (tâche + destinataire) pour la traçabilité, distincte des `userId` OIDC.
- **H6** — ~~Les droits requis pour renvoyer/révoquer sont ceux de la réassignation de tâche
  (EDIT sur le type de dossier, TODO §20/§57).~~ **Corrigé en Phase 5 (2026-09-24)** : la
  réassignation exige en réalité `TENANT_ADMIN`/`WORKFLOW_ADMIN`, pas EDIT — contradiction trouvée
  à l'implémentation et **tranchée par l'utilisateur** : envoyer/renvoyer/révoquer = **EDIT sur le
  type du dossier** (admins compris), lecture de la liste = VIEW (adresse masquée sans EDIT).

## Risques acceptés

- **R-SCAN-FAIL-OPEN (D-PORTAL-SCAN)** — un tiers **anonyme** peut déposer un fichier non scanné
  si l'antivirus est `noop` (défaut) ou indisponible. Compensations prévues : magic bytes
  (inchangés), compteur Micrometer de fail-open existant, et documentation explicite dans
  `DEPLOIEMENT.md` recommandant d'activer ClamAV sur tout tenant qui active le portail. Décision
  réversible sans rupture de contrat (passage en fail-closed = changement de politique côté
  serveur).
- **R-LINK-LEAK (D-PORTAL-AUTH)** — un lien transféré ou intercepté donne l'accès jusqu'à
  expiration. Atténuations : durée courte par défaut, révocation, périmètre limité à une tâche
  (D-PORTAL-DATA), jeton hors URL serveur (D-PORTAL-CSRF-SESSION).

## Questions & Open Items

**Toutes les décisions produit de Phase 1 et 2 sont tranchées** (tableau « Décisions »). Les questions
de **conception** ci-dessous sont **toutes tranchées en Phase 3** — voir
[design](../design/2026-09-23-feature-portail-externe.md) § *Design Decisions* :

- Marquage BPMN d'une tâche externe (attribut `flowable:*` dédié, `candidateGroups` réservé,
  préfixe de `formKey`) et désignation de la source de l'adresse e-mail.
- Service de la page portail sans ouvrir le SPA : entrée Vite dédiée (page séparée légère) vs
  route publique dans le SPA existant.
- Représentation de l'acteur externe dans `AccessContext` / `TaskAssignmentGuard` et dans
  `TaskQuestionAnswer` / éléments de dossier.
- Stockage du jeton (hash, sel, table dédiée), génération, comparaison en temps constant ;
  détection d'expiration (job planifié vs évaluation paresseuse) pour D-PORTAL-EXPIRY.
- Réutilisation vs extension du rate-limiter pré-auth des webhooks ; journalisation des échecs.
- Contenu et modèle de l'e-mail d'invitation (template, expéditeur).
