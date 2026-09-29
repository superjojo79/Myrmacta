---
phase: testing
status: draft
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
---

# Testing Strategy — `signature-electronique`

> TODO §72. Scénarios dérivés des critères de succès et user stories des
> [requirements](../requirements/2026-09-29-feature-signature-electronique.md) (`reviewed`) et des
> composants du [design](../design/2026-09-29-feature-signature-electronique.md) (`reviewed`,
> Phase 3 close le 2026-09-29). Cases cochées au fil de l'implémentation.

## Test Coverage Goals

- Nouveau code backend : couverture Jacoco lignes/branches au niveau des seuils du dépôt, 100 % des
  branches des use cases signature et des règles de garde du stub.
- Tous les scénarios de sécurité (`T-SEC`, `T-ACCESS`, `T-IDOR`, `T-GUARD`) en tests automatisés.
- Frontend : Vitest pour l'écran signataire et le panneau instructeur ; **Playwright obligatoire**
  (conventions projet) pour le parcours signataire et le panneau instructeur.
- ArchUnit vert : domaine pur, `AccessContext.External` sans dépendance framework.
- Le stub est exercé par les tests d'intégration ; le contrat `SignaturePort` a une suite de
  **tests de contrat** réutilisable par tout futur adaptateur.

## Unit & Integration Tests — backend

### Allowlist et parser BPMN (US-1, D-SIGNATURE-TRIGGER)

- [x] **S-PARSE-1** `AllowedBpmnDelegates` accepte `signatureRequestTask` (formes `${…}` et nue) et rejette toute
      variante (`.`, `(`, espace, `#`).
- [x] **S-PARSE-2** Le parser (`extractSignatureBlocks`) accepte un bloc valide et refuse : fonctionnalité désactivée
      (`provider=none`), `signatureDocument` absent, aucun signataire, aucun requis, `kind` inconnu,
      `source` mal formée, sources identiques, mode invalide, échéance hors bornes, plus de
      `max-signers`, signataire `EXTERNAL` avec portail désactivé.
- [x] **S-PARSE-3** `flowable:signer` sans effet de bord sur `BpmnExpressionSurfaceScanner` ; `${…}` dans `name`
      ou un attribut du bloc ⇒ refusé (§69).
- [x] **S-PARSE-4** Un BPMN sans bloc signature n'est pas affecté (non-régression du parser portail).
- [x] **S-PARSE-5** Aucune expression libre n'est acceptée dans les attributs du bloc (rejeu des cas §69).

### Émission (US-1, US-2, D-SIGNATURE-ABSTRACTION)

- [x] **S-EMIT-1** `RequestSignatureUseCase` crée une demande `PENDING`, ses signataires et leurs liens, appelle
      `SignaturePort.request` une fois.
- [x] **S-EMIT-2** Adresse absente ou invalide : aucune demande envoyée, notification interne, process non bloqué.
- [x] **S-EMIT-3** Document source introuvable ou non `PRODUCED_DOC` : refus explicite, aucune demande.
- [x] **S-EMIT-4** Empreinte du document figée à l'émission ; une modification ultérieure n'altère pas la demande.
- [x] **S-EMIT-5** Émission idempotente (index partiel `(process_instance_id, activity_id)` en `PENDING`) : pas de
      doublon sur rejeu du delegate.
- [x] **S-EMIT-6** Identités résolues identiques (même adresse ou même utilisateur) : demande `FAILED`
      (`DUPLICATE_SIGNER`), notification interne, process non bloqué.
- [x] **S-EMIT-7** `signatureDocument` dont le `storageRef` ne correspond à aucun `PRODUCED_DOC` du dossier :
      `FAILED` (`SOURCE_NOT_FOUND`).
- [x] **S-EMIT-8** Erreur du provider à l'émission : issue `FAILED`, process non bloqué.
- [x] **S-EMIT-9** Document au-delà de la taille maximale configurée : refus explicite (`FAILED`, `SOURCE_TOO_LARGE`).
- [ ] **S-EMIT-10** Migration Liquibase appliquée sur PostgreSQL réel (Testcontainers) : index partiel `(process_instance_id, activity_id)` en `PENDING` effectif, contraintes et index présents ; variante H2 équivalente.

### Stub (D-SIGNATURE-STUB-BEHAVIOR, D-SIGNATURE-STUB-GUARD)

- [x] **S-STUB-1** Cycle complet : émission, signature du signataire, document signé et preuve produits,
      issue `SIGNED` restituée au process.
- [x] **S-STUB-2** Le document signé porte le filigrane « SIMULATION — sans valeur juridique » (extraction de
      texte du PDF) ; la preuve porte `simulated = true`.
- [x] **S-STUB-3** Refus du signataire : issue `DECLINED`, aucun document signé.
- [x] **S-STUB-4** `T-GUARD-1` : stub actif, profils autres que `local`/`test` (dont aucun profil), sans
      `allow-outside-local` ⇒ échec de démarrage du contexte Spring avec message explicite.
- [x] **S-STUB-5** `T-GUARD-2` : stub actif, hors `local`/`test`, avec `allow-outside-local=true` ⇒ démarrage
      réussi, avertissement journalisé, compteur incrémenté.
- [x] **S-STUB-6** `T-GUARD-5` : stub actif avec profil `local` ou `test` ⇒ démarrage sans drapeau.
- [x] **S-STUB-7** `T-GUARD-3` : stub inactif par défaut ; aucun bean `SignaturePort` actif, tâche « signature »
      rejetée au déploiement.
- [x] **S-STUB-8** `T-GUARD-4` : le second drapeau n'apparaît dans aucun gabarit (`.env.example`,
      `application*.yml` de production).
- [x] **S-STUB-9** Le PDF de preuve contient l'empreinte du document, les signataires (identité masquée), les horodatages et la mention SIMULATION (extraction de texte, D-SIGNATURE-PROOF).
- [x] **S-STUB-10** Le filigrane ne modifie pas le nombre de pages ni la lisibilité du contenu original (comparaison de texte avant/après).

### Accès signataire (US-2, US-3, D-SIGNATURE-PORTAL-KIND)

- [x] **S-ACCESS-1** Jeton valide : contexte, document et état renvoyés ; rien d'autre.
- [x] **S-ACCESS-2** `T-SEC-1` : jeton expiré, révoqué, utilisé, inexistant, demande annulée ou process disparu ⇒ **même** réponse.
- [x] **S-ACCESS-3** `T-IDOR-1` : jeton de la demande A ne donne accès ni au document ni au statut de la demande B.
- [x] **S-ACCESS-4** `T-IDOR-2` : aucun endpoint signature ne renvoie de champ ni d'élément du dossier hors de la demande.
- [x] **S-ACCESS-5** `T-SEC-2` : limite par IP appliquée avant lecture du corps ; corps borné.
- [x] **S-ACCESS-6** `T-SEC-3` : le jeton n'apparaît ni en base, ni dans les logs, ni dans les URL serveur.
- [x] **S-ACCESS-7** Comparaison de jeton en temps constant.
- [x] **S-ACCESS-8** Fonctionnalité désactivée ⇒ routes signature absentes.

### Décision du signataire (US-2, US-6)

- [x] **S-DEC-1** Signature : statut `SIGNED` du signataire ; issue de la demande selon `SignatureProgression`.
- [x] **S-DEC-2** Refus avec motif : motif borné en longueur, échappé à l'affichage.
- [x] **S-DEC-3** Double soumission (double clic, rejeu) : une seule issue, la seconde ignorée (compare-and-set).
- [x] **S-DEC-4** Signature après expiration ou annulation : refus uniforme.
- [x] **S-DEC-5** `SEQUENTIAL` : le signataire N+1 reste `WAITING` (lien et « Mes signatures » inactifs, réponse
      uniforme) jusqu'à la décision de N ; puis il est notifié.
- [x] **S-DEC-6** `PARALLEL` : tous notifiés à l'émission, ordre de décision indifférent.
- [x] **S-DEC-7** Invitations : e-mail envoyé au(x) signataire(s) `PENDING` (parallèle : tous ; séquentiel : le suivant seulement), reprise sur échec d'envoi, plafond de tentatives ⇒ signataire signalé, process non bloqué.

### Issue et reprise du process (US-6, US-7)

- [x] **S-OUT-1** Le process reprend avec `signatureOutcome` et `signatureRequestId` ; une passerelle route sur
      les cinq issues (`SIGNED`, `DECLINED`, `EXPIRED`, `FAILED`, `CANCELLED`).
- [x] **S-OUT-2** Notification inbox des participants pour chaque issue.
- [x] **S-OUT-3** Reprise idempotente : issue appliquée deux fois ⇒ une seule reprise.
- [x] **S-OUT-4** Callback du provider : HMAC horodaté invalide, rejeu, corps trop grand, rate-limit dépassé ⇒
      rejeté sans effet (rejeu des cas F5–F8).
- [x] **S-OUT-5** Callback après expiration ou annulation : ignoré et journalisé.

### Progression multi-signataires (US-2, US-10, D-SIGNATURE-ORDER, D-SIGNATURE-REQUIRED, D-SIGNATURE-SEQ-OPTIONAL)

- [x] **S-PROG-1** Table de vérité de `SignatureProgression` : mode × (requis / facultatif) × (signe / refuse /
      expire) ⇒ issue et prochain signataire.
- [x] **S-PROG-2** Refus d'un requis (parallèle et séquentiel) : demande `DECLINED`, liens restants révoqués, aucun
      document signé.
- [x] **S-PROG-3** Expiration d'un requis : `EXPIRED`, mêmes effets.
- [x] **S-PROG-4** Refus ou expiration d'un facultatif : marqué, la demande continue ; en séquentiel, on passe au suivant.
- [x] **S-PROG-5** Tous les signataires facultatifs : refusé au déploiement.
- [x] **S-PROG-6** Mêmes sources déclarées deux fois : refusé au déploiement (A5) ; identités résolues identiques : voir S-EMIT-6.
- [x] **S-PROG-7** Le dernier requis signe alors qu'un facultatif reste en attente : `SIGNED`, le facultatif est révoqué.

### Signataire interne (US-10, D-SIGNATURE-INTERNAL-UX, D-SIGNATURE-SIGNER-SOURCE)

- [x] **S-INT-1** `GET /api/signatures/mine` ne renvoie que les demandes adressées à l'utilisateur courant.
- [x] **S-INT-2** `T-IDOR-3` : un utilisateur ne peut ni lire le document ni agir sur la demande d'un autre.
- [x] **S-INT-3** Le droit de lecture du dossier reste exigé ; sans lui, la demande n'est pas émise pour ce
      signataire (notification interne, process non bloqué).
- [x] **S-INT-4** Utilisateur désigné inconnu, désactivé ou identifiant invalide : traité comme adresse absente.
- [x] **S-INT-5** En séquentiel, signature avant son tour : refus uniforme.
- [x] **S-INT-6** Notification inbox à l'émission et à la décision.

### Gestion instructeur (US-4)

- [x] **S-ADM-1** Liste des demandes : VIEW suffit, adresse masquée sans EDIT.
- [x] **S-ADM-2** Annuler / renvoyer : EDIT requis ; l'ancien lien est révoqué au renvoi ; annulation propagée
      au port.
- [x] **S-ADM-3** Tâche complétée en interne, process annulé, dossier clôturé ⇒ demande `CANCELLED`, liens invalides.
- [x] **S-ADM-4** Renvoi d'une invitation : nouveau jeton, ancien jeton rejeté immédiatement (réponse uniforme).

### Documents (US-5, D-SIGNATURE-ELEMENT)

- [x] **S-DOC-1** Document signé et preuve = `PRODUCED_DOC` avec marqueur ; aucune valeur d'`ElementType` ajoutée.
- [x] **S-DOC-2** L'API distingue document source, document signé, preuve (filtre sur le marqueur).
- [x] **S-DOC-3** L'export CSV et l'affichage existants restent inchangés (non-régression).

### Répartiteur d'issue (D-SIGNATURE-DISPATCH)

- [x] **S-DISP-1** Issue produite avant l'abonnement du process (échec à l'émission) : `correlateMessage` lève
      `CallbackNoMatchingInstanceException`, la demande reste non répartie, le job reprend et réussit
      une fois le process abonné.
- [x] **S-DISP-2** Reprise idempotente : `outcome_dispatched_at` posé une seule fois, aucune double reprise.
- [x] **S-DISP-3** Plafond `dispatch-max-attempts` atteint : alerte (métrique + journal), la demande reste consultable.
- [x] **S-DISP-4** Plusieurs demandes dans un même process : la corrélation par `signatureRequestId` ne mélange pas les issues.
- [x] **S-DISP-5** Métriques Micrometer : demandes, issues par type, `stub.blocked`, callbacks rejetés, `dispatch.retries` incrémentées aux bons endroits ; aucun libellé ne contient d'adresse ni de jeton.

### MCP (D-SIGNATURE-MCP)

- [x] **S-MCP-1** `list_signature_requests` / `get_signature_request` : adresses masquées, droits respectés.
- [x] **S-MCP-2** `cancel_signature_request` : `dryRun` sans effet ; annulation réelle propagée au port.
- [x] **S-MCP-3** Aucun tool d'émission n'est exposé (test de catalogue des tools).

### Expiration (H6)

- [x] **S-EXP-1** Job d'expiration : demandes et liens échus passent en `EXPIRED`, notification émise ; le job
      tolère un retard et reste idempotent.

### Purge (US-9)

- [x] **S-PURGE-1** La purge d'un dossier supprime demandes, signataires (adresses incluses), liens, documents
      signés et preuves (extension du test de purge existant).

### Contrat (D-SIGNATURE-CONTRACT-CHECK)

- [x] **S-CONTRACT-1** Suite de tests de contrat `SignaturePort` (émission, statut, annulation, artefacts, issues)
      exécutée contre le stub et réutilisable par un futur adaptateur.
- [x] **S-CONTRACT-2** Tableau de correspondance avec 2 à 3 API publiques relu ; chaque écart est traité ou
      consigné comme limite connue.
- [x] **S-CONTRACT-3** Golden OpenAPI mis à jour (portail, admin, callback).

### Connecteur (D-SIGNATURE-ABSTRACTION)

- [x] **S-CONN-1** L'entrée de catalogue expose son `configSchema` (secrets en `PASSWORD`, chiffrés au repos).
- [x] **S-CONN-2** Sélection de l'adaptateur par propriété ; adaptateur inconnu ⇒ échec de démarrage explicite.
- [x] **S-CONN-3** ArchUnit : `domain.signature` sans dépendance Spring/JPA/Flowable ; `AccessContext.External` inchangé côté dépendances.
- [x] **S-CONN-4** Provider `none` : aucune route de callback, aucun contrôleur portail signature, aucun bean `SignaturePort` ; `stub` : beans présents.

## End-to-End & Frontend Tests

### Vitest

- [x] **S-VT-1** Écran signataire : états chargement, valide, invalide (message neutre), signé, refusé.
- [x] **S-VT-2** Panneau « Signatures » : statuts, actions selon droits, bandeau « SIMULATION ».
- [x] **S-VT-3** Éditeur BPMN : import / export d'un BPMN contenant le bloc signature préserve les attributs `flowable:signature*` et les éléments `flowable:signer` (aucune UI dédiée, réservée à LC1).
- [x] **S-VT-4** Vue « Mes signatures » : liste, tour actif / en attente, signer, refuser, erreurs neutres.

### Playwright

- [x] **S-PW-1** Signataire interne : « Mes signatures » affiche la demande à son tour, signature, statut mis à jour.
- [x] **S-PW-2** Séquentiel : le second signataire ne peut agir qu'après le premier.
- [x] **S-PW-3** Facultatif qui refuse : la demande aboutit quand même à `SIGNED`.
- [x] **S-PW-4** Parcours nominal : le process demande la signature, le signataire ouvre le lien, signe,
      l'instructeur voit `SIGNED`, le document signé et la preuve « SIMULATION ».
- [x] **S-PW-5** Refus : le signataire refuse, le process route sur `DECLINED`.
- [x] **S-PW-6** Lien invalide : message neutre.
- [x] **S-PW-7** Annulation et renvoi par l'instructeur.
- [x] **S-PW-8** Fonctionnalité désactivée : routes absentes, bloc refusé au déploiement.

## Test Data

- Dossier avec un `PRODUCED_DOC` PDF (généré par un template de test).
- BPMN de test : génération de document → demande de signature → routage sur l'issue.
- Adaptateur de test contrôlable (issues forcées) pour les tests de contrat et de robustesse.
- Adresses valides / invalides / absentes ; plusieurs signataires, requis et facultatifs, internes et
  externes, deux modes.

## Test Reporting & Coverage

- `./mvnw verify` (Jacoco, ArchUnit), `npm run test:all` côté frontend, Playwright.
- Chiffres à consigner à la clôture des Phases 8 et 9.

## Manual Testing

- Lecture visuelle du PDF filigrané (lisibilité, non-suppression triviale).
- Lecture des e-mails de sollicitation du signataire.
- Démarrage réel en profil production avec et sans le second drapeau.

## Performance Testing

- Document à signer de taille maximale ; téléchargement par le signataire sans charge mémoire
  excessive.

## Bug Tracking

Aucun à ce stade.


## Phase 8 — résultats et couverture

- `./mvnw verify` : **2549 tests, 0 échec, BUILD SUCCESS** (après le correctif PDF de la Phase 9). Vitest 424, Playwright 98.
- Couverture Jacoco des paquets signature : lignes **95 à 100 %** (domaine `usecase` 95 %, `model` 98 %,
  infrastructure `signature` 95 %, `persistence` 97 %, `flowable` 88 %) ; branches 78–95 %.
- Scénarios ajoutés en Phase 8/9 : corps de refus de 300 Ko (4xx, jeton toujours utilisable), document non PDF
  refusé à l'émission (`SOURCE_NOT_PDF`).
- Restent non exécutables ici : S-EMIT-10 (PostgreSQL réel, pas de Docker) et vérifications manuelles T14.4.

### E2E réel (sans API simulée)

`SignatureBrowserE2eTest` (JUnit, `-Dsignature.e2e=true`, ignoré par défaut) démarre Spring Boot sur H2 (vrai
Flowable, portail statique construit), sème un dossier et un PDF, capture le **vrai lien** de l'e-mail
d'invitation et pilote Chromium via `frontend/e2e-real` (`playwright.real.config.ts`). Il vérifie ensuite la
demande, la tâche atteinte par le process et les pièces marquées `SIMULATION` :

- S-PW-3 + S-PW-4 : le facultatif refuse, le requis signe ⇒ `SIGNED`, tâche « Signature obtenue », document signé
  et preuve rattachés ;
- S-PW-5 : le requis refuse ⇒ `DECLINED`, tâche « Signature non obtenue », lien du facultatif refusé (écran neutre) ;
- S-PW-2 : séquentiel — le second n'est invité (e-mail) qu'après la signature du premier.

Dans chaque parcours le lien utilisé est ensuite mort (écran neutre) et le jeton a quitté la barre d'adresse.
Résultat : 3 tests verts (~40 s). Étape ajoutée au job `build` de la CI. Reste hors couverture réelle : les
parcours **internes** (« Mes signatures », panneau du dossier), qui exigent une session OIDC.
