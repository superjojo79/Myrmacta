---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
---

# Implementation Guide — `signature-electronique`

> TODO §72. Journal de la Phase 5 (démarrée le 2026-09-29), tenu en parallèle du code. Plan :
> [planning](../planning/2026-09-29-feature-signature-electronique.md) ; scénarios :
> [testing](../testing/2026-09-29-feature-signature-electronique.md).

## Development Setup

- **JDK 25 requis** (le conteneur de session n'a que JDK 21 par défaut) : `apt-get install -y
  openjdk-25-jdk-headless` puis `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64`.
- Tests ciblés sans le build frontend : `./mvnw -q test -Dtest=<Classe> -Dskip.frontend=true
  -Djacoco.skip=true` (le résultat est dans `target/surefire-reports`).
- **Docker indisponible** dans le conteneur de session : les tests Testcontainers PostgreSQL
  (S-EMIT-10, variante PG) et ClamAV ne peuvent pas y tourner ; ils restent à jouer sur une
  machine avec Docker (CI).

## Code Structure

| Couche | Emplacement |
|---|---|
| Domaine | `com.flowforge.domain.signature` (`model`, `port.out`, `port.in`, `usecase`) |
| Persistance | `com.flowforge.infrastructure.persistence.signature` |
| Configuration | `infrastructure.config.SignatureProperties`, `SignatureConfig` |
| Adaptateurs | `com.flowforge.infrastructure.signature` |
| Migration | `db/changelog/changes/026-signature.yaml` (+ `sql/026-…-pending-index-{postgresql,h2}.sql`) |
| Tests | `src/test/java/com/flowforge/domain/signature`, `…/infrastructure/persistence/signature` |

## Implementation Notes

### M1 — Fondation (2026-09-29)

- Records immuables `SignatureRequest` et `Signer`, chaque transition renvoie une copie ; la
  concurrence est un **compare-and-set sur `version`** (`version = version + 1` dans l'`UPDATE`
  JPQL), pas `@Version` JPA — même esprit que `PortalLinkRepository.transition`.
- `SignatureRequestRepository.update(request, signers)` applique demande **et** signataires dans une
  transaction et pose `setRollbackOnly()` si une version a changé : l'agrégat est atomique.
- Index partiel « une demande `PENDING` par (process, activité) » : PostgreSQL = index partiel ;
  H2 = colonne générée `pending_key` + index unique (même technique que `portal_link`).
- Colonne `signer_rank` (et non `position`) pour éviter tout conflit de mot réservé SQL.
- `SignatureProgression` : fonction pure, 14 tests (parallèle / séquentiel × requis / facultatif).
- `SignatureProperties` : `provider` (`none` par défaut), plafonds, périodes des jobs ;
  **`stub.allowOutsideLocal` n'est pas dans `application.yml`** (D-SIGNATURE-STUB-GUARD).

### M0 — Revue documentaire (2026-09-29)

- L'egress du conteneur bloque `developers.yousign.com` et `developers.docusign.com` : le tableau de
  correspondance repose sur la connaissance des API publiques, **à reconfirmer** au premier provider
  réel (voir design § « Revue documentaire du contrat »). Ajouts au port : `requestedAssuranceLevel`,
  `signingUrls`, `parseCallback` / `SignatureCallbackEvent`.

### M2 — Allowlist, delegate et parser (2026-09-29)

- `signatureRequestTask` ajouté à `AllowedBpmnDelegates` ; `unwrapBeanName` rendu `public` pour que le
  parseur (paquet `infrastructure.flowable`) reconnaisse le delegate.
- `FlowableBpmnParserAdapter.signatureDefinition` : premier parsing d'`extensionElements`
  (`flowable:signer` conservé par le convertisseur Flowable comme `ExtensionElement`, vérifié par test).
  Validation appelée dans `assertSafeElement` (donc dès `extractProcessDefinitionKey`, brouillons compris).
- `DeployBpmnProcessUseCaseImpl` reçoit `SignatureAvailabilityPort` (constructeur, `WorkflowConfig` et
  test existant mis à jour atomiquement) : fournisseur actif, portail pour `EXTERNAL`, plafonds.
- Le scanner UEL (§69) n'a pas eu besoin de modification : les nouveaux attributs ne portent aucun `${`.

### M3 — Stub, PDF, garde de profil, contrat (2026-09-29)

- **Spike F7 levé** : `flying-saucer-pdf` 10.5.0 embarque **OpenPDF 3.0.5, paquet `org.openpdf`**
  (`org.openpdf.text.pdf.PdfStamper`, `PdfReader`, `PdfTextExtractor`) — pas `com.lowagie`. Aucune
  dépendance ajoutée.
- `StubSignatureAdapter` est **sans état propre** : référence `stub:<requestId>`, il relit la demande et
  les signataires dans `SignatureRequestRepository` et le document source dans `DocumentStoragePort`.
  **Conséquence pour M4 : `RequestSignatureUseCase` doit insérer la demande `PENDING` *avant*
  d'appeler `SignaturePort.submit`** (puis renseigner `provider_reference`).
- Garde (`SignatureStubGuard`) : liste positive `local`/`test` ; le refus lève à la création du bean
  `SignaturePort` (échec de démarrage). Le compteur du design (`stub.blocked`) n'a pas de sens (le
  contexte ne démarre pas) : remplacé par une jauge `flowforge.signature.stub.active{outside_local}`.
- Suite de contrat réutilisable : `SignaturePortContract` (hook `onSubmitted` pour les adaptateurs dont
  l'état vit dans FlowForge).

### M4 — Émission, signataires, invitations (2026-09-29)

- `RequestSignatureUseCaseImpl` : ordre **insertion `PENDING` → `submit` → mise à jour** (le stub relit
  la demande). Toute erreur (adresse, document, doublon, capacités, fournisseur) ferme la demande
  `FAILED` avec son motif au lieu de lever : le process ne bloque jamais. Les échecs « avant
  insertion » créent directement une demande terminale sans signataire.
- Signataires résolus par `PortalRecipientResolverPort` (existant) : champ du dossier ou variable de
  process, par nom littéral (§69). Externe = `PortalEmails.normalize` ; interne = identifiant
  utilisateur (non vide, ≤ 128, sans caractère de contrôle).
- Delegate `SignatureRequestTask` : relit le bloc dans le BPMN **déployé** (`ProcessDefinitionUtil`,
  `FlowableBpmnParserAdapter.signatureDefinition`), jamais depuis des variables. Bean nommé
  `signatureRequestTask`, présent seulement si `app.signature.provider != none`
  (`SignatureUseCaseConfig`, `@ConditionalOnExpression`).
- Invitations : e-mail direct (`EmailPort`, pas l'outbox), jeton en fragment `/portal/#signature:<jeton>`
  (le préfixe aiguillera le portail vers l'écran de signature, M10). Seul le hash est stocké.
- `SignatureNotifier` (inbox) : participants du workflow (issue, échec) et signataire interne.
- Test d'intégration `SignatureEmissionEngineTest` (moteur Flowable réel, BPMN `signature-smoke`) :
  process en attente du message `signatureCompleted`, jetons ni en base ni dans les journaux.

### M5 — Portail : contexte et document (2026-09-29)

- `SignatureAccessResolver` (jeton → signataire `PENDING` d'une demande `PENDING` non échue, dossier
  existant) lève la même `PortalAccessDeniedException` que le portail. Aucun identifiant client
  accepté (paramètres `requestId`/`dossierId` ignorés, testé).
- `SignaturePortalController` sur `/api/portal/signature` (chaîne `Order(3)` existante, aucune route
  hors `/portal/**`) : `GET` contexte, `GET /document` (`inline`, `nosniff`, `no-store`). Le document
  est relu à chaque appel et son empreinte comparée à celle de l'émission (divergence = refus).
- L'intercepteur pré-authentification et le gestionnaire d'exceptions du portail couvrent aussi le
  nouveau contrôleur (limite par IP, corps 404 uniforme).
- `SignatureDisabledApiTest` : fournisseur `none` ⇒ ni route (404), ni delegate, ni job.

### M8b — Répartiteur d'issue (2026-09-29)

- `DispatchSignatureOutcomesUseCaseImpl` : corrèle `signatureCompleted` par `signatureRequestId`,
  pose `signatureOutcome`; `CallbackNoMatching…`/`Ambiguous…` ⇒ tentative + 1 ; plafond
  `app.signature.dispatch.max-attempts` ⇒ jauge « gave up » et arrêt de la reprise (demande consultable).
- `AfterCommitSignatureOutcomeListener` : tentative immédiate **après commit, dans une transaction
  `REQUIRES_NEW`** — trouvaille de test : en `afterCommit`, une propagation `REQUIRED` rejoint la
  transaction déjà validée et le travail est perdu sans erreur.
- Les échecs à l'émission (`FAILED`) reprennent donc le process immédiatement (`SignatureEmissionEngineTest`).
- Ajout de `SignatureMetricsPort` (`requests`, `outcomes`, `dispatch.retries`, `dispatch.gave_up`,
  `callbacks.rejected`), adaptateur Micrometer.

### M6 / M6b / M6c (domaine) — décisions, suites, reprise (2026-09-29)

- `DecideSignatureUseCaseImpl` couvre **d'un seul tenant** les décisions externes (jeton) et internes
  (identité authentifiée) et les deux modes / requis-facultatifs : la logique est celle de
  `SignatureProgression` ; les milestones M6b (séquentiel, facultatifs) et le domaine de M6c sont donc
  livrés avec M6 ; restent pour M6c l'API « Mes signatures » et pour M6b rien de plus que les tests.
- `SignatureOutcomeHandler` (classe de domaine) regroupe les suites d'une fermeture pour **toutes** les
  origines (décision, annulation, échéance, callback) : artefacts (`PRODUCED_DOC` marqués
  `signatureRole` = `SIGNED_DOCUMENT` / `SIGNATURE_PROOF`, `simulated`, libellé « (SIMULATION) »),
  métriques, notifications des participants, `SignatureOutcomeListener`.
- Une erreur d'artefacts remonte : la transaction de la décision est annulée (le signataire peut
  réessayer) — choix assumé, la reprise du process n'a lieu qu'après validation.
- Réponse publique d'une décision : `{status}` du seul signataire (jamais le statut de la demande).
- `SignatureDecisionFlowEngineTest` (moteur réel, MockMvc) : signature ⇒ `SIGNED` ⇒ process repris ⇒
  tâche « Signature obtenue » + deux `PRODUCED_DOC` marqués (PDF filigrané et preuve) ; refus requis ⇒
  `DECLINED` ; refus d'un facultatif sans effet sur l'issue ; double soumission refusée.

### M6c / M7 — « Mes signatures », gestion instructeur, expiration, annulation (2026-09-29)

- **« Mes signatures »** : `MySignaturesUseCaseImpl` filtre par identité, tour (`PENDING`), demande
  ouverte, dossier non clos et **droit de lecture** (`canViewType`, ACL par utilisateur). Tout autre cas
  = 404 générique. `MySignaturesController` (`/api/signatures/mine`), identité tirée de la session.
- **API instructeur** (`SignatureAdminController`) : liste (VIEW, contact absent sans EDIT, jamais
  l'adresse complète), `cancel` (EDIT, `dryRun`), `resend` (EDIT). Le renvoi **efface le hash du jeton**
  (l'ancien lien cesse de fonctionner tout de suite) ; le job d'invitations en émet un nouveau.
- **Expiration** : `ExpireSignatureRequestsUseCaseImpl` (tolérant au retard, idempotent, compare-and-set).
- **Process disparu** : `SignatureProcessEventListener` (PROCESS_COMPLETED / CANCELLED / ENTITY_DELETED sur
  un process) ⇒ demandes ouvertes `CANCELLED` (`PROCESS_GONE`) **déjà marquées réparties** (rien à reprendre).
- **Dossier clos** : le résolveur du portail et « Mes signatures » refusent (404 uniforme) ; la demande
  s'éteint à l'échéance. L'ajout d'un `PRODUCED_DOC` sur dossier clos aurait sinon été refusé par
  `AddDossierElementUseCaseImpl`.
- **S-DOC-2** : aucune API à ajouter — `GET /api/dossiers/{id}/elements` expose déjà `metadata`, donc
  `signatureRole` / `simulated` / `signatureRequestId` (testé).
- Trouvaille de test : l'ACL est **par utilisateur** (`dossier_type_acl.user_id`), pas par rôle.

- **M8 – connecteur `signature` et callback provider** : entrée de catalogue `SignatureCallbackConnector`
  (`CALLBACK`, `webhookAuthType` HMAC_SHA256/SHARED_SECRET_TOKEN, `webhookSecret` `PASSWORD`). Nouveau contrat
  `CallbackHandlingConnector` : `CallbackConnectorRuntimeImpl` lui délègue le callback au lieu de corréler un
  message. La route générique `/api/callbacks/{instanceId}` (HMAC horodaté, idempotence, taille, rate-limit)
  est donc **réutilisée**, pas de contrôleur dédié (T8.2). `HandleSignatureCallbackUseCase` applique
  l'événement (`SignaturePort.parseCallback`, `findByProviderReference`), clôt via `SignatureOutcomeHandler`
  (raison `PROVIDER_CALLBACK`) ; événement tardif ⇒ ignoré + métrique `already_closed`.

- **M8c – MCP** : `SignatureRequestsMcpTools` (`list_/get_/cancel_signature_request`, `dryRun` sur l'annulation),
  conditionné à `provider != none`, adresses toujours masquées, **aucun tool d'émission** (test de catalogue).

- **M9 – purge** : `ProcessPurgeOutboxUseCaseImpl` appelle `signatureRequestRepository.deleteAllByDossierId`
  (demandes + signataires, donc adresses des tiers) à côté des liens portail. Documents signés et preuves étant
  des `PRODUCED_DOC`, ils partent déjà avec les éléments (stockage puis lignes) : aucun code de plus.

- **M10 – front portail** : `PortalSignature.vue` dans le build portail existant ; lien `#signature:<jeton>`
  (`takeLinkFromLocation`, fragment effacé, jamais stocké) ; le lien de tâche `#<jeton>` est inchangé.
  Document récupéré en `fetch` (en-tête jeton) puis `blob:` (un `<iframe src>` ne peut pas porter l'en-tête) ;
  tout 404 ⇒ écran neutre unique ; bandeau « SIMULATION » si `simulated`. Vitest : 35 verts (portail).

- **M11 – front interne** : `api/signatures.ts`, `SignatureRequestsPanel.vue` (détail dossier, masqué sans demande
  ou API absente, bandeau SIMULATION, annuler/renvoyer selon `canManage`), `MySignaturesView.vue` (route
  `/signatures`, lien « Mes signatures », « À mon tour » / « En attente », signer / refuser). Le document signé et
  la preuve restent visibles dans la liste des pièces du dossier (`PRODUCED_DOC`). S-VT-3 : test bpmn-moddle
  (`bpmnSignatureRoundTrip.spec.ts`) — l'éditeur ne détruit pas le bloc ; aucune UI dédiée. Vitest 424 verts,
  vue-tsc et ESLint propres.

- **M12 – Playwright** : la suite du dépôt simule l'API (pas de backend) ; `e2e/signature-portal.spec.ts`
  (S-PW-4/5/6, fragment effacé, 404 neutre) et `E2E-SIG-1..3` dans `pilotage.spec.ts` (S-PW-7 annuler/renvoyer,
  S-PW-1 « Mes signatures », S-PW-8 fonction absente). **S-PW-2/3 (séquentiel, facultatif qui refuse)** et le
  refus de déploiement de S-PW-8 sont, faute de backend en e2e, couverts au niveau moteur réel :
  `SignatureDecisionFlowEngineTest`, `MySignaturesApiTest`, `SignatureDisabledApiTest`. Écart assumé (E5), **levé ensuite** : `SignatureBrowserE2eTest` + `frontend/e2e-real` (voir ci-dessous).

- **M13 (T13.3) – golden OpenAPI** : `OpenApiContractIT` active `provider=stub` (profil `test`) ; diff
  **purement additif** (11 chemins, 7 schémas). Trouvaille : les records imbriqués `ListResponse`/`DecisionResponse`
  des contrôleurs de signature entraient en collision de nom de schéma avec ceux du portail §68 (le golden
  changeait `PortalLinkResponse`) ; ils sont désormais préfixés `Signature…`, méthodes `list` renommées.

- **M14 – vérification globale** : `./mvnw verify` **2547 tests, 0 échec, BUILD SUCCESS** (Jacoco, ArchUnit
  compris) ; Vitest 424 verts ; Playwright **98 verts** ; vue-tsc / ESLint propres. Le premier `verify` avait
  révélé 12 tests unitaires cassés (mocks `Dossier` sans statut, après l'ajout de « dossier clos ⇒ 404 ») —
  corrigés. T14.3 : `SignaturePortalRealServerTest` (Tomcat réel, modèle `PortalRealServerTest`) — aucune
  redirection de connexion ni session sur erreur de mapping / firewall / corps invalide, tous les refus
  (inconnu, absent, jeton en query, malformé, déjà utilisé) au corps identique, document servi avec
  `nosniff` / `no-store` sans cookie, limite par IP insensible à `Forwarded` : aucun constat. S-DOC-3 : les
  tests existants d'export CSV et d'éléments passent inchangés. **T14.4 (manuel) non fait ici** : pas de
  Docker/PostgreSQL/SMTP réels dans cet environnement ; PDF filigrané, e-mails et refus de démarrage du stub
  sont couverts par `StubPdfFactoryTest`, `EmailSignatureInviteSenderTest`, `SignatureStubGuardTest`.

### Phase 7 — conformité implémentation / design

Revue fichier par fichier contre design et requirements : **aligné**, avec les écarts suivants (aucun majeur).

| # | Écart | Sévérité | Décision |
|---|---|---|---|
| E5 | Playwright de la suite sans backend. | Faible | **Levé** : parcours réel navigateur + Spring Boot (H2) ajouté, cf. Phase 8 |
| E6 | `flowforge.signature.stub.active` (jauge) au lieu du compteur `stub.blocked` du design. | Faible | Assumé (le blocage est un échec de démarrage, pas un événement comptable) |
| E7 | Le document est servi depuis un `byte[]` (borné par `max-document-bytes`, 20 Mo), pas en flux. | Faible | Assumé ; à revoir si la limite augmente |
| E8 | Journaux : seuls des compteurs et l'identifiant de demande sont journalisés (vérifié par grep) — conforme. | — | — |

### Phase 9 — revue de code / sécurité

- **Constat corrigé (moyen) :** le type MIME du document à signer venait des métadonnées de l'élément et
  était reflété au portail, servi en ligne : un `PRODUCED_DOC` HTML/SVG aurait pu s'exécuter depuis l'origine
  de l'application. Correctif : refus dès l'émission de tout contenu qui n'est pas un PDF (`SOURCE_NOT_PDF`,
  signature `%PDF-`), et type de réponse toujours `application/pdf` (+ `nosniff`, `no-store`).
- Vérifié sans constat : anti-IDOR (aucun identifiant en entrée du portail), refus uniformes, comparaison par
  empreinte, compare-and-set des décisions, motif de refus nettoyé et borné (échappé côté Vue), corps borné
  (test 300 Ko), limite par IP, aucun secret ni adresse dans les journaux.

### Phase 9 bis — analyse adversariale par sous-agent (PR #149, HEAD 3e192c8)

Trois constats élevés prouvés, tous corrigés (tests ajoutés) :

| # | Constat | Correctif |
|---|---|---|
| 1 | Un PDF `%PDF-` illisible passait le filtre d'émission ; à la dernière signature le filigrane échouait, la transaction était annulée et le signataire recevait un 500 en boucle. | Le stub relit le PDF dès `submit` (`requireReadable`) : échec immédiat `FAILED`/`PROVIDER_ERROR`, process routé. |
| 2 | Le callback fournisseur n'était pas transactionnel : demande `SIGNED` validée puis échec du rattachement ⇒ `SIGNED` sans document, le rejeu recevait « déjà close ». | `TransactionalHandleSignatureCallbackUseCase` (transaction unique autour du use case). |
| 3 | Deux signataires distincts décidant en même temps : le perdant du compare-and-set voyait « lien invalide ». | `DecideSignatureUseCaseImpl` relit l'état et réessaie (3 tentatives). |

Corrigés aussi : n°7 (le stub refuse de produire le document signé si la source ne correspond plus à l'empreinte
d'émission), n°10 (« Mes signatures » sert toujours `application/pdf`), n°11 (le MCP masque `contact` sans EDIT, comme le REST).

Suivis n°4 à 9 et 12 à 15, **tous traités ensuite** (tests ajoutés) :

| # | Correctif |
|---|---|
| 4 | Invitations : le hash du jeton est réservé (compare-and-set) **avant** l'envoi ; échec d'envoi ⇒ réservation abandonnée, tentative comptée. Un renvoi concurrent ne laisse plus de lien mort dans une boîte. |
| 5 | `SignatureProcessEventListener` écoute `ACTIVITY_CANCELLED` : une attente interrompue (frontière, sous-processus) annule la demande (`WAIT_INTERRUPTED`), liens invalidés — testé sur le moteur réel (`signature-interrupt-smoke`). |
| 6 | `signatureRequestId` posée en variable **locale** à l'exécution ; nouvelle méthode `WorkflowEnginePort.correlateMessageOnExecution` ; deux blocs en branches parallèles se reprennent chacun (`signature-parallel-blocks-smoke`). `signatureOutcome` reste de portée process : à router aussitôt (documenté). |
| 8 | Référence inconnue ⇒ `CallbackRetryLaterException` : 503, clé d'idempotence libérée (`WebhookIdempotencyStore.release`), rejeu possible avec la même clé. |
| 9 | `SignatureOutcomeHandler.afterClosed` supprime les blobs déjà écrits si une étape suivante fait annuler la transaction. |
| 12 | Index `idx_signature_request_provider_ref` (migration 026, non publiée) ; identifiant d'activité limité à 128 caractères au déploiement. |
| 13 | `max-signers` et `max-deadline` revérifiés à l'exécution (`TOO_MANY_SIGNERS`, échéance bornée) ; délai minimal entre deux renvois (`app.signature.send.resend-min-interval`), renvoi déjà en attente = sans effet. |
| 14 | E-mail d'invitation « [SIMULATION] » ; PDF simulés avec métadonnées `Subject`/`Keywords` et protection contre la modification. Le garde du stub reste `local` ou `test` (choix D-SIGNATURE-STUB-GUARD) : le profil `test` est déclaré réservé aux tests dans DEPLOIEMENT.md — pas de durcissement technique supplémentaire. |
| 15 | La purge supprime les demandes **avant** les instances de process (plus d'annulations ni de notifications parasites) ; le fournisseur n'est donc pas prévenu de l'annulation d'un dossier purgé (ses demandes sont closes ou échues). |

Reste ouvert : les tests de concurrence sont unitaires (mocks), aucun test à deux transactions réelles ; le garde du
stub ne distingue pas une recette nommée `test`.

Écarts de traçabilité relevés : S-DISP-2 est couvert par `DispatchSignatureOutcomesUseCaseImplTest` (idempotence
d'un second appel) et S-EMIT-4 par `RequestSignatureUseCaseImplTest` (empreinte de l'émission) et
`StubSignatureAdapterTest.artifactsAreRefusedWhenTheSourceChangedSinceEmission` ; les identifiants n'y figurent pas
littéralement. Les tests de concurrence restent unitaires (mocks) : aucun test à deux transactions réelles.

### Écarts par rapport au design

| # | Écart | Raison |
|---|---|---|
| E1 | **T1.6 supprimée** : pas de variante `AccessContext.External` pour le signataire. | Le signataire est authentifié par son jeton (`SignatureAccessResolver`) et n'appelle aucune politique d'accès dossier ; le document signé est ajouté en contexte système. Évite de toucher ~8 `case External`. |
| E2 | **Pas de `SignatureTokenPort`** : on réutilise `PortalTokenPort`. | Même primitive (256 bits, SHA-256) ; un port de plus n'apportait rien. |
| E3 | **ArchUnit (T1.7)** : aucune règle nouvelle. | `..domain..` est déjà couvert (`domain_has_no_framework_dependency`, `domain_never_depends_on_infrastructure`) ; `ArchitectureRulesTest` reste vert avec le nouveau paquet. |
| E4 | **Aucune validation d'existence d'un utilisateur interne à l'émission** (S-INT-3/S-INT-4 du testing amendés). | FlowForge n'a pas d'annuaire d'utilisateurs (comptes dans Keycloak) et l'ACL de type repose sur les rôles de la session : ni l'existence ni le droit de lecture ne sont vérifiables pour un utilisateur non connecté. Le droit de lecture est donc **contrôlé à l'accès** (« Mes signatures », M6c) ; un identifiant inconnu n'est détecté que par l'expiration à l'échéance (limite connue). |

## Patterns & Best Practices

- TDD par milestone, tests ciblés puis `./mvnw verify` avant clôture.
- Aucune donnée personnelle dans les journaux : `Signer.maskedEmail()`.
- Le domaine ne dépend ni de Spring, ni de JPA, ni de Flowable.

## Integration Points

- Portail : jeton (`PortalTokenPort`), intercepteur pré-authentification, chaîne `Order(3)`.
- Moteur : `WorkflowEnginePort.correlateMessage`, `AllowedBpmnDelegates`.
- Documents : `DocumentStoragePort`, `AddDossierElementUseCase` (`PRODUCED_DOC` marqués).

## Error Handling & Security Considerations

Voir le design (§ Non-Functional Requirements) ; rappel : réponses uniformes côté public, jetons
jamais journalisés, second drapeau du stub absent des gabarits.

## Performance Considerations

Documents lus en flux ou bornés par `app.signature.max-document-bytes` ; index sur
`(status, deadline_at)` et `(status, outcome_dispatched_at)`.
