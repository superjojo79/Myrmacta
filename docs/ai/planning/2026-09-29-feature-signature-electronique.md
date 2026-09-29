---
phase: planning
status: reviewed
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
---

# Project Planning & Task Breakdown — `signature-electronique`

> TODO §72. **Phase 4 (planning) close le 2026-09-29.** Plan dérivé des
> [requirements](../requirements/2026-09-29-feature-signature-electronique.md) (`reviewed`), du
> [design](../design/2026-09-29-feature-signature-electronique.md) (`reviewed`) et des 94 scénarios
> du [testing](../testing/2026-09-29-feature-signature-electronique.md) (identifiants `S-<groupe>-<n>`).
> **Toutes les tâches sont `not started`.** Implémentation en TDD (test rouge → code → vert),
> `./mvnw verify` avant toute clôture de milestone backend, `npm run test:all` pour les milestones
> frontend. Chaque tâche : identifiant, dépendances, **preuve de validation**, scénarios couverts.
> La table finale vérifie que **chaque scénario** a au moins une tâche.

## Jalons de livraison (D-SIGNATURE-DELIVERY)

Un lot, une PR, trois jalons internes ordonnés. **Le contrat, les tables et le modèle de domaine
sont posés en entier dès M1** (séquentiel, facultatifs et internes inclus) pour éviter une seconde
migration ; seuls les comportements sont livrés par jalons.

| Jalon | Contenu | Milestones | Dépôt à la fin du jalon |
|---|---|---|---|
| **J1** | Socle, externes, parallèle, requis, stub, MCP, purge | M0–M5, M6, M7, M8, M8b, M8c, M9, M10, M11a, M12a | vert, utilisable de bout en bout avec signataires externes en parallèle |
| **J2** | Séquentiel et facultatifs | M6b (+ M12b) | vert, deux modes et requis / facultatifs |
| **J3** | Signataires internes, « Mes signatures » | M6c, M11b, M12c | vert, périmètre complet |
| **Clôture** | Documentation, vérification globale, revue adversariale | M13, M14 | prêt pour PR |

## Milestones

| # | Milestone | Dépend de | Parallélisable avec |
|---|---|---|---|
| M0 | Revue documentaire des API publiques et gel du contrat | — | M1 (modèle sans le port) |
| M1 | Fondation : domaine, contrat, persistance, configuration | — (`SignaturePort` final après M0) | M0 |
| M2 | Allowlist, delegate et parser BPMN | M1 | M3 |
| M3 | Stub, PDF (filigrane, preuve), garde de profil, suite de contrat | M0, M1 | M2 |
| M4 | Émission, résolution des signataires, invitations | M1, M3 | M2 |
| M5 | Portail : résolveur, contrôleur, pré-authentification | M1, M4 | M8 |
| M6 | Décision externe (parallèle, requis), documents, reprise | M4, M5, M8b | M7 |
| M6b | Séquentiel et facultatifs | M6 | M7, M8c |
| M6c | Signataires internes (API « Mes signatures ») | M6 | M6b, M7 |
| M7 | Gestion instructeur, annulation, expiration, API documents | M4 | M5 |
| M8 | Entrée de catalogue et callback provider | M1 | M5 |
| M8b | Répartiteur d'issue avec reprise, métriques | M1, M4 | M5 |
| M8c | Tools MCP (lecture + annulation `dryRun`) | M7 | M10 |
| M9 | Purge | M1 | M5 |
| M10 | Frontend portail (écran signataire) | M5 | M11a |
| M11a | Frontend interne : panneau « Signatures » | M7 | M10 |
| M11b | Frontend interne : « Mes signatures » | M6c | M12b |
| M12a/b/c | Playwright (J1 / J2 / J3) | M10 + M11a / M6b / M11b | — |
| M13 | Documentation | M12c | — |
| M14 | Vérification globale et revue adversariale | M13 | — |

## Task Breakdown

Notation : `Tx.y` — outcome ; *dép.* dépendances ; *preuve* validation ; *scénarios* identifiants du testing.

### M0 — Revue documentaire et gel du contrat
- [x] **T0.1** Choisir 2 à 3 API publiques de référence avec l'utilisateur (aucun compte requis, lecture de la documentation publique). *preuve :* liste consignée dans le design. *scénarios :* S-CONTRACT-2.
- [x] **T0.2** Extraire pour chacune : demande, signataires, ordre, facultatifs, statuts, preuve, webhooks, échéance. *dép. :* T0.1.
- [x] **T0.3** Produire le tableau de correspondance avec `SignaturePort` ; traiter ou consigner chaque écart, dont : provider sans ordre imposé ou sans facultatifs (le port refuse, ou FlowForge orchestre). *preuve :* tableau relu et validé par l'utilisateur. *scénarios :* S-CONTRACT-2.
- [x] **T0.4** Geler la forme finale de `SignaturePort` et des capacités dans le design. *dép. :* T0.3.

### M1 — Fondation
- [x] **T1.1** Records et enums de domaine : `SignatureRequest`, `Signer`, statuts, `SignatureMode`, `SignerKind`, `SignatureOutcome`, `SignerSource`, `SignatureBlockDefinition`. *preuve :* tests unitaires des invariants.
- [x] **T1.2** Ports `in`/`out` (`SignaturePort` provisoire, repositories, token, résolution de signataire, invitations, documents, disponibilité). *dép. :* T1.1.
- [x] **T1.3** `SignatureProgression` (fonction pure) : règles 1–5 du design. *preuve :* table de vérité exhaustive mode × requis × événement. *scénarios :* S-PROG-1, S-PROG-2, S-PROG-3, S-PROG-4, S-PROG-7.
- [x] **T1.4** Migration Liquibase (`signature_request`, `signature_signer`, index dont l'index partiel PostgreSQL et son équivalent H2) et test sur PostgreSQL réel (Testcontainers). *preuve :* IT vert sur PG. *scénarios :* S-EMIT-10.
- [x] **T1.5** Entités JPA et adaptateurs de repository, verrou optimiste, compare-and-set. *dép. :* T1.4.
- [x] **T1.6** ~~`AccessContext.External` pour le signataire (`signature:<id>`) et mise à jour des `case External`. *preuve :* tests des politiques d'accès existantes inchangés.~~ *Supprimée (écart D1 du design)* : le signataire n'appelle aucune politique d'accès dossier ; il est authentifié par son jeton et le use case agit en contexte système. Aucun `AccessContext` nouveau.
- [x] **T1.7** Règle ArchUnit `domain.signature` pure. *scénarios :* S-CONN-3.
- [x] **T1.8** `SignatureProperties` (`app.signature.*`), validation des bornes, valeurs par défaut. *dép. :* T1.1.

### M2 — Allowlist, delegate et parser
- [x] **T2.1** Ajouter `signatureRequestTask` à `AllowedBpmnDelegates`. *scénarios :* S-PARSE-1.
- [x] **T2.2** `extractSignatureBlocks` : premier parsing d'`extensionElements` (`flowable:signer`) et de tous les attributs du bloc, toutes les règles de validation du design (dont sources distinctes, ≥ 1 requis, portail requis pour `EXTERNAL`, bornes). *scénarios :* S-PARSE-2, S-PROG-5, S-PROG-6.
- [x] **T2.3** Compatibilité avec `BpmnExpressionSurfaceScanner` : aucun `${…}` toléré dans le bloc. *scénarios :* S-PARSE-3, S-PARSE-5.
- [x] **T2.4** Non-régression du parser portail et des BPMN livrés. *scénarios :* S-PARSE-4.
- [x] **T2.5** Garde de déploiement branchée sur `SignatureAvailabilityPort` (`provider=none` ⇒ bloc refusé). *dép. :* T1.2, T2.2. *scénarios :* S-STUB-7 (partie déploiement), S-PW-8.

### M3 — Stub, PDF, garde de profil, contrat
- [x] **T3.1** **Spike F7** : confirmer l'accès à l'API OpenPDF (`PdfStamper`) via `flying-saucer-pdf` 10.5.0. *preuve :* test d'essai qui estampille un PDF existant ; sinon, décision de dépendance à remonter à l'utilisateur.
- [x] **T3.2** `StubPdfFactory` : filigrane « SIMULATION — sans valeur juridique » sur le document signé. *dép. :* T3.1. *scénarios :* S-STUB-2, S-STUB-10.
- [x] **T3.3** PDF de preuve : empreinte, signataires (identité masquée), horodatages, mention. *dép. :* T3.1. *scénarios :* S-STUB-9.
- [x] **T3.4** `StubSignatureAdapter` : cycle complet, issues forcées pour les tests, applique ses issues en interne. *dép. :* T0.4, T1.2, T3.2, T3.3. *scénarios :* S-STUB-1, S-STUB-3.
- [x] **T3.5** `SignatureConfig` : sélection de l'adaptateur (`none` \| `stub` \| inconnu ⇒ échec de démarrage), garde par liste positive de profils, second drapeau, avertissement et compteur ; absence du drapeau des gabarits. *dép. :* T1.8. *scénarios :* S-STUB-4, S-STUB-5, S-STUB-6, S-STUB-7, S-STUB-8, S-CONN-2, S-CONN-4 (partie beans).
- [x] **T3.6** Suite de **tests de contrat** `SignaturePort`, exécutée contre le stub, réutilisable. *dép. :* T3.4. *scénarios :* S-CONTRACT-1.

### M4 — Émission
- [x] **T4.1** Adaptateur de résolution des signataires : champ de dossier / variable de process → adresse (externe) ou utilisateur (interne, existence et statut). *scénarios :* S-EMIT-2, S-INT-4.
- [x] **T4.2** `RequestSignatureUseCase` : validation, document retrouvé par `storageRef`, taille, empreinte, création demande + signataires, appel du port, erreurs en `FAILED`. *dép. :* T1.5, T3.4, T4.1. *scénarios :* S-EMIT-1, S-EMIT-3, S-EMIT-4, S-EMIT-7, S-EMIT-8, S-EMIT-9.
- [x] **T4.3** Idempotence : rejeu du delegate sans doublon. *dép. :* T4.2. *scénarios :* S-EMIT-5.
- [x] **T4.4** Détection des identités résolues identiques ⇒ `FAILED` (`DUPLICATE_SIGNER`). *dép. :* T4.2. *scénarios :* S-EMIT-6.
- [x] **T4.5** Delegate `SignatureRequestTask` : pose `signatureRequestId`, ne bloque jamais le process. *dép. :* T2.1, T4.2.
- [x] **T4.6** Jetons (`SignatureTokenPort` sur `PortalTokenPort`) et `SignatureInviteSenderJob` : invitation e-mail aux externes `PENDING`, reprises, plafond. *dép. :* T4.2. *scénarios :* S-DEC-7 (parallèle).

### M5 — Portail
- [x] **T5.1** `SignatureAccessResolver` : jeton → signataire `PENDING` d'une demande `PENDING` non échue ; réponses uniformes, comparaison en temps constant. *dép. :* T1.5, T4.6. *scénarios :* S-ACCESS-2, S-ACCESS-7.
- [x] **T5.2** `SignaturePortalController` (contexte, document, signer, refuser), gestionnaire d'exceptions uniforme ; aucun identifiant client. *dép. :* T5.1. *scénarios :* S-ACCESS-1, S-ACCESS-3, S-ACCESS-4.
- [x] **T5.3** Extension de l'intercepteur pré-authentification : limite par IP et jeton vivant avant lecture du corps, corps borné. *dép. :* T5.2. *scénarios :* S-ACCESS-5.
- [x] **T5.4** Le jeton n'apparaît ni en base, ni dans les journaux, ni dans les URL serveur. *scénarios :* S-ACCESS-6.
- [x] **T5.5** Routes et beans absents quand la fonctionnalité est désactivée. *scénarios :* S-ACCESS-8, S-CONN-4 (partie routes).

### M6 — Décision externe (J1)
- [x] **T6.1** `DecideSignatureUseCase` (externe, parallèle, requis) : signature, refus borné et échappé, compare-and-set, refus si expirée / annulée. *dép. :* T1.3, T5.2. *scénarios :* S-DEC-1, S-DEC-2, S-DEC-3, S-DEC-4, S-DEC-6.
- [x] **T6.2** `HandleSignatureOutcomeUseCase` : documents signés et preuves (`PRODUCED_DOC` marqués), révocation des non décidés, notifications inbox. *dép. :* T6.1, T3.4. *scénarios :* S-OUT-2, S-DOC-1.
- [x] **T6.3** Reprise du process avec `signatureOutcome` et `signatureRequestId` (via le répartiteur), routage par passerelle sur les cinq issues, reprise idempotente. *dép. :* T6.2, T8b.1. *scénarios :* S-OUT-1, S-OUT-3.

### M6b — Séquentiel et facultatifs (J2)
- [x] **T6b.1** Mode `SEQUENTIAL` : `WAITING` → `PENDING` dans la transaction de décision, réponse uniforme avant son tour. *dép. :* T6.1. *scénarios :* S-DEC-5.
- [x] **T6b.2** Facultatifs : refus / échéance d'un facultatif ne ferme pas la demande ; dernier requis signé ⇒ facultatif restant révoqué ; intégration de `SignatureProgression` aux use cases. *scénarios :* S-PROG-2, S-PROG-4, S-PROG-7 (intégration).
- [x] **T6b.3** Invitation du suivant en séquentiel. *dép. :* T4.6. *scénarios :* S-DEC-7 (séquentiel).

### M6c — Signataires internes (J3)
- [x] **T6c.1** `ListMySignaturesUseCase` et `MySignaturesController` (liste, document, signer, refuser) filtrés par identité et tour. *dép. :* T6.1. *scénarios :* S-INT-1, S-INT-2, S-INT-5.
- [x] **T6c.2** Droit de lecture du dossier exigé ; à l'émission, absence de droit ⇒ traité comme adresse absente. *dép. :* T4.1. *scénarios :* S-INT-3.
- [x] **T6c.3** Notifications inbox à l'émission et à la décision. *scénarios :* S-INT-6.

### M7 — Gestion instructeur (J1)
- [x] **T7.1** Liste des demandes (statuts par signataire) : VIEW, adresse masquée sans EDIT. *dép. :* T4.2. *scénarios :* S-ADM-1.
- [x] **T7.2** Annuler et renvoyer : EDIT requis, propagation au port, nouveau jeton et ancien révoqué. *scénarios :* S-ADM-2, S-ADM-4.
- [x] **T7.3** Annulation sur disparition de la tâche / du process / du dossier (écouteur moteur). *scénarios :* S-ADM-3.
- [x] **T7.4** `SignatureExpiryJob` idempotent tolérant le retard ; expiration d'un requis ⇒ `EXPIRED`. *scénarios :* S-EXP-1, S-PROG-3 (intégration).
- [x] **T7.5** API : filtre sur `metadata.signatureRole` pour distinguer source, signé, preuve. *scénarios :* S-DOC-2.

### M8 — Connecteur et callback
- [x] **T8.1** Entrée de catalogue `signature` (`CALLBACK`, `configSchema`, secrets `PASSWORD`). *scénarios :* S-CONN-1.
- [x] **T8.2** `SignatureCallbackController` : HMAC horodaté, idempotence, taille, rate-limit ; callback tardif ignoré et journalisé. *dép. :* T8.1, T6.2. *scénarios :* S-OUT-4, S-OUT-5.

### M8b — Répartiteur et métriques
- [x] **T8b.1** `SignatureOutcomeDispatcher` : tentative immédiate, reprise sur `CallbackNoMatchingInstanceException`, `outcome_dispatched_at`, plafond et alerte. *dép. :* T1.5. *scénarios :* S-DISP-1, S-DISP-2, S-DISP-3, S-DISP-4.
- [x] **T8b.2** Métriques Micrometer, sans donnée personnelle dans les libellés. *scénarios :* S-DISP-5.

### M8c — MCP
- [x] **T8c.1** `list_signature_requests`, `get_signature_request` (adresses masquées). *dép. :* T7.1. *scénarios :* S-MCP-1.
- [x] **T8c.2** `cancel_signature_request` avec `dryRun`. *dép. :* T7.2. *scénarios :* S-MCP-2.
- [x] **T8c.3** Test de catalogue : aucun tool d'émission. *scénarios :* S-MCP-3.

### M9 — Purge
- [x] **T9.1** Étendre le graphe de purge (`ProcessPurgeOutboxUseCaseImpl`) aux demandes, signataires (adresses), documents signés et preuves. *dép. :* T1.5. *scénarios :* S-PURGE-1.

### M10 — Frontend portail
- [x] **T10.1** Écran signataire dans le build portail : consulter le document, signer, refuser ; états neutres (invalide / expiré / signé / refusé). *dép. :* T5.2. *scénarios :* S-VT-1.

### M11 — Frontend interne
- [x] **T11a.1** Panneau « Signatures » du détail du dossier : statuts par signataire, annuler, renvoyer, document signé et preuve, bandeau « SIMULATION ». *dép. :* T7.1. *scénarios :* S-VT-2.
- [x] **T11a.2** Import / export d'un BPMN contenant le bloc préserve attributs et éléments (aucune UI dédiée, réservée à LC1). *scénarios :* S-VT-3.
- [x] **T11b.1** Vue « Mes signatures » : liste, tour actif / en attente, signer, refuser, erreurs neutres. *dép. :* T6c.1. *scénarios :* S-VT-4.

### M12 — Playwright
- [x] **T12a.1** J1 : parcours nominal, refus, lien invalide, annulation et renvoi, fonctionnalité désactivée. *dép. :* T10.1, T11a.1. *scénarios :* S-PW-4, S-PW-5, S-PW-6, S-PW-7, S-PW-8.
- [x] **T12b.1** J2 : séquentiel, facultatif qui refuse. *dép. :* T6b.1, T6b.2. *scénarios :* S-PW-2, S-PW-3.
- [x] **T12c.1** J3 : signataire interne. *dép. :* T11b.1. *scénarios :* S-PW-1.

### M13 — Documentation
- [x] **T13.1** `GUIDE-CONCEPTION-BPMN.md` (bloc signature, message `signatureCompleted`, variables, exemple de routage), `GUIDE-UTILISATEUR.md` (instructeur, signataires, « Mes signatures »).
- [x] **T13.2** `GETTING-STARTED.md` et `.env.example` (propriétés, **sans** le second drapeau), `DEPLOIEMENT.md` (activation, garde du stub, portail requis pour les externes), `CHANGELOG.md`.
- [x] **T13.3** Golden OpenAPI régénéré pour les nouveaux chemins uniquement. *scénarios :* S-CONTRACT-3.
- [x] **T13.4** Statuts : TODO §72, `FILE-ATTENTE.md`, `ROADMAP.md`, `DEFERRED-FEATURES.md`, analyse 30.

### M14 — Vérification globale
- [x] **T14.1** `./mvnw verify` (Jacoco, ArchUnit), Vitest, Playwright verts. *preuve :* chiffres consignés dans le testing.
- [x] **T14.2** Non-régression : export CSV et affichage des éléments inchangés. *scénarios :* S-DOC-3.
- [x] **T14.3** Revue adversariale du portail rejouée sur le nouveau contrôleur (Tomcat réel, sur le modèle de `PortalRealServerTest`) ; corriger les constats avant PR.
- [ ] **T14.4** Vérifications manuelles : PDF filigrané, e-mails, démarrage réel avec et sans le second drapeau.

## Dependencies

- **Livré :** portail (PR #138), `flowable-callback`, `DocumentStoragePort`, `GenerateDocumentUseCase`,
  inbox de notifications, `AttachmentScanPort`, `PortalTokenPort`, intercepteur pré-authentification.
- **Coutures partagées :** `ConnectorCatalog` et enregistrement des tools MCP (pas de PR concurrente
  avec D3c/D3d ou un lot `LC*`) ; `AllowedBpmnDelegates` (pas de PR concurrente avec LC2 ou D3b) ;
  `FlowableBpmnParserAdapter` ; graphe de purge ; contrôleurs du portail.
- **Externes :** accès à la documentation publique de 2 à 3 fournisseurs (M0) ; JDK 25, Docker
  (Testcontainers PostgreSQL, ClamAV) et Chromium pour `verify` et Playwright.
- **Ordre critique :** M0 → gel du port → M3 ; M1 → M4 → M5 → M6 ; M8b avant T6.3.

## Timeline & Estimates

Estimation indicative en unités relatives : M0 **S** ; M1 **M** ; M2 **M** ; M3 **M** (spike PDF
incertain) ; M4 **M** ; M5 **M** ; M6 **M** ; M6b **M** ; M6c **M** ; M7 **M** ; M8 **S** ; M8b **S** ;
M8c **S** ; M9 **S** ; M10 **S** ; M11 **M** ; M12 **S** ; M13 **S** ; M14 **M**. Total : lot **XL**
(comparable à deux lots « portail »). Chemin critique : M0 → M1 → M3 → M4 → M5 → M6 → M8b → J1.

## Risks & Mitigation

| Risque | Mitigation |
|---|---|
| **R-PORTAIL** — nouveau contrôleur public | réutilisation du jeton, de l'intercepteur et du limiteur ; T14.3 (revue adversariale sur serveur réel) |
| **R-SIM-EN-PROD** — stub actif hors `local`/`test` | T3.5 testé (S-STUB-4 à 8), drapeau absent des gabarits, marquage indélébile |
| **R-CONTRAT-TROP-ETROIT / TROP-LARGE** | M0 avant le gel ; ne sortir du port que ce que le stub ou un provider cible exerce |
| **Spike PDF (F7)** — API OpenPDF inaccessible | T3.1 en premier de M3 ; repli : dépendance explicite à soumettre à l'utilisateur |
| **Volume XL** — PR volumineuse | jalons J1/J2/J3 verts à chaque étape ; commits par milestone ; possibilité de couper avant J3 |
| **Reprise du process** — issue avant abonnement | T8b.1 avant T6.3, scénarios S-DISP-1 à 4 |
| **Coutures partagées** — conflits avec d'autres lots | lot planifié hors des PR concurrentes sur catalogue, MCP, allowlist |
| **Première lecture d'`extensionElements`** | T2.2 et T2.3 avec cas limites (éléments inconnus, attributs inattendus, XXE déjà neutralisé par le scanner) |
| **Migration** — H2 vs PostgreSQL | T1.4 avec IT PostgreSQL réel |

## Resources Needed

- Accès en lecture à la documentation publique de 2 à 3 fournisseurs (M0).
- Environnement avec JDK 25, Docker, Chromium.
- Relecture de l'utilisateur sur le tableau de correspondance (T0.3) et sur les libellés
  utilisateur (e-mails de sollicitation, écrans).

## Couverture scénarios → tâches

Les 94 scénarios du testing sont couverts (contrôle croisé effectué le 2026-09-29).

| Groupe | Scénarios | Tâches |
|---|---|---|
| PARSE | 1 · 2 · 3 · 4 · 5 | T2.1 · T2.2 · T2.3 · T2.4 · T2.3 |
| EMIT | 1 · 2 · 3 · 4 · 5 · 6 · 7 · 8 · 9 · 10 | T4.2 · T4.1 · T4.2 · T4.2 · T4.3 · T4.4 · T4.2 · T4.2 · T4.2 · T1.4 |
| STUB | 1 · 2 · 3 · 4 · 5 · 6 · 7 · 8 · 9 · 10 | T3.4 · T3.2 · T3.4 · T3.5 · T3.5 · T3.5 · T3.5 + T2.5 · T3.5 · T3.3 · T3.2 |
| ACCESS | 1 · 2 · 3 · 4 · 5 · 6 · 7 · 8 | T5.2 · T5.1 · T5.2 · T5.2 · T5.3 · T5.4 · T5.1 · T5.5 |
| DEC | 1 · 2 · 3 · 4 · 5 · 6 · 7 | T6.1 · T6.1 · T6.1 · T6.1 · T6b.1 · T6.1 · T4.6 + T6b.3 |
| OUT | 1 · 2 · 3 · 4 · 5 | T6.3 · T6.2 · T6.3 · T8.2 · T8.2 |
| PROG | 1 · 2 · 3 · 4 · 5 · 6 · 7 | T1.3 · T1.3 + T6b.2 · T1.3 + T7.4 · T1.3 + T6b.2 · T2.2 · T2.2 · T1.3 + T6b.2 |
| INT | 1 · 2 · 3 · 4 · 5 · 6 | T6c.1 · T6c.1 · T6c.2 · T4.1 · T6c.1 · T6c.3 |
| ADM | 1 · 2 · 3 · 4 | T7.1 · T7.2 · T7.3 · T7.2 |
| DOC | 1 · 2 · 3 | T6.2 · T7.5 · T14.2 |
| DISP | 1 · 2 · 3 · 4 · 5 | T8b.1 · T8b.1 · T8b.1 · T8b.1 · T8b.2 |
| MCP | 1 · 2 · 3 | T8c.1 · T8c.2 · T8c.3 |
| EXP · PURGE | 1 · 1 | T7.4 · T9.1 |
| CONTRACT | 1 · 2 · 3 | T3.6 · T0.3 · T13.3 |
| CONN | 1 · 2 · 3 · 4 | T8.1 · T3.5 · T1.7 · T3.5 + T5.5 |
| VT | 1 · 2 · 3 · 4 | T10.1 · T11a.1 · T11a.2 · T11b.1 |
| PW | 1 · 2 · 3 · 4 · 5 · 6 · 7 · 8 | T12c.1 · T12b.1 · T12b.1 · T12a.1 · T12a.1 · T12a.1 · T12a.1 · T12a.1 |

**Scénarios sans tâche : aucun.** **Tâches sans scénario** (justifiées) : T0.1/T0.2/T0.4 (préparation
du contrat, validées par T0.3), T1.1/T1.2/T1.5/T1.6/T1.8 (fondation, couvertes indirectement par
tous les tests d'intégration), T4.5 (delegate, exercé par S-PW-4 et S-OUT-1), T3.1 (spike), T13.1,
T13.2, T13.4 (documentation), T14.1, T14.3, T14.4 (vérification).
