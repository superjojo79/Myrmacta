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

- [ ] `AllowedBpmnDelegates` accepte `signatureRequestTask` (formes `${…}` et nue) et rejette toute
      variante (`.`, `(`, espace, `#`).
- [ ] Le parser (`extractSignatureBlocks`) accepte un bloc valide et refuse : fonctionnalité désactivée
      (`provider=none`), `signatureDocument` absent, aucun signataire, aucun requis, `kind` inconnu,
      `source` mal formée, sources identiques, mode invalide, échéance hors bornes, plus de
      `max-signers`, signataire `EXTERNAL` avec portail désactivé.
- [ ] `flowable:signer` sans effet de bord sur `BpmnExpressionSurfaceScanner` ; `${…}` dans `name`
      ou un attribut du bloc ⇒ refusé (§69).
- [ ] Un BPMN sans bloc signature n'est pas affecté (non-régression du parser portail).
- [ ] Aucune expression libre n'est acceptée dans les attributs du bloc (rejeu des cas §69).

### Émission (US-1, US-2, D-SIGNATURE-ABSTRACTION)

- [ ] `RequestSignatureUseCase` crée une demande `PENDING`, ses signataires et leurs liens, appelle
      `SignaturePort.request` une fois.
- [ ] Adresse absente ou invalide : aucune demande envoyée, notification interne, process non bloqué.
- [ ] Document source introuvable ou non `PRODUCED_DOC` : refus explicite, aucune demande.
- [ ] Empreinte du document figée à l'émission ; une modification ultérieure n'altère pas la demande.
- [ ] Émission idempotente (index partiel `(process_instance_id, activity_id)` en `PENDING`) : pas de
      doublon sur rejeu du delegate.
- [ ] Identités résolues identiques (même adresse ou même utilisateur) : demande `FAILED`
      (`DUPLICATE_SIGNER`), notification interne, process non bloqué.
- [ ] `signatureDocument` dont le `storageRef` ne correspond à aucun `PRODUCED_DOC` du dossier :
      `FAILED` (`SOURCE_NOT_FOUND`).
- [ ] Erreur du provider à l'émission : issue `FAILED`, process non bloqué.

### Stub (D-SIGNATURE-STUB-BEHAVIOR, D-SIGNATURE-STUB-GUARD)

- [ ] Cycle complet : émission, signature du signataire, document signé et preuve produits,
      issue `SIGNED` restituée au process.
- [ ] Le document signé porte le filigrane « SIMULATION — sans valeur juridique » (extraction de
      texte du PDF) ; la preuve porte `simulated = true`.
- [ ] Refus du signataire : issue `DECLINED`, aucun document signé.
- [ ] `T-GUARD-1` : stub actif, profils autres que `local`/`test` (dont aucun profil), sans
      `allow-outside-local` ⇒ échec de démarrage du contexte Spring avec message explicite.
- [ ] `T-GUARD-2` : stub actif, hors `local`/`test`, avec `allow-outside-local=true` ⇒ démarrage
      réussi, avertissement journalisé, compteur incrémenté.
- [ ] `T-GUARD-5` : stub actif avec profil `local` ou `test` ⇒ démarrage sans drapeau.
- [ ] `T-GUARD-3` : stub inactif par défaut ; aucun bean `SignaturePort` actif, tâche « signature »
      rejetée au déploiement.
- [ ] `T-GUARD-4` : le second drapeau n'apparaît dans aucun gabarit (`.env.example`,
      `application*.yml` de production).

### Accès signataire (US-2, US-3, D-SIGNATURE-PORTAL-KIND)

- [ ] Jeton valide : contexte, document et état renvoyés ; rien d'autre.
- [ ] `T-SEC-1` : jeton expiré, révoqué, utilisé, inexistant, tâche disparue ⇒ **même** réponse.
- [ ] `T-IDOR-1` : jeton de la demande A ne donne accès ni au document ni au statut de la demande B.
- [ ] `T-IDOR-2` : aucun endpoint signature ne renvoie de champ ni d'élément du dossier hors de la demande.
- [ ] `T-SEC-2` : limite par IP appliquée avant lecture du corps ; corps borné.
- [ ] `T-SEC-3` : le jeton n'apparaît ni en base, ni dans les logs, ni dans les URL serveur.
- [ ] Comparaison de jeton en temps constant.
- [ ] Fonctionnalité désactivée ⇒ routes signature absentes.

### Décision du signataire (US-2, US-6)

- [ ] Signature : statut `SIGNED` du signataire ; issue de la demande selon `SignatureProgression`.
- [ ] Refus avec motif : motif borné en longueur, échappé à l'affichage.
- [ ] Double soumission (double clic, rejeu) : une seule issue, la seconde ignorée (compare-and-set).
- [ ] Signature après expiration ou annulation : refus uniforme.
- [ ] `SEQUENTIAL` : le signataire N+1 reste `WAITING` (lien et « Mes signatures » inactifs, réponse
      uniforme) jusqu'à la décision de N ; puis il est notifié.
- [ ] `PARALLEL` : tous notifiés à l'émission, ordre de décision indifférent.

### Issue et reprise du process (US-6, US-7)

- [ ] Le process reprend avec `signatureOutcome` et `signatureRequestId` ; une passerelle route sur
      les cinq issues (`SIGNED`, `DECLINED`, `EXPIRED`, `FAILED`, `CANCELLED`).
- [ ] Notification inbox des participants pour chaque issue.
- [ ] Reprise idempotente : issue appliquée deux fois ⇒ une seule reprise.
- [ ] Callback du provider : HMAC horodaté invalide, rejeu, corps trop grand, rate-limit dépassé ⇒
      rejeté sans effet (rejeu des cas F5–F8).
- [ ] Callback après expiration ou annulation : ignoré et journalisé.

### Progression multi-signataires (US-2, US-10, D-SIGNATURE-ORDER, D-SIGNATURE-REQUIRED, D-SIGNATURE-SEQ-OPTIONAL)

- [ ] Table de vérité de `SignatureProgression` : mode × (requis / facultatif) × (signe / refuse /
      expire) ⇒ issue et prochain signataire.
- [ ] Refus d'un requis (parallèle et séquentiel) : demande `DECLINED`, liens restants révoqués, aucun
      document signé.
- [ ] Expiration d'un requis : `EXPIRED`, mêmes effets.
- [ ] Refus ou expiration d'un facultatif : marqué, la demande continue ; en séquentiel, on passe au suivant.
- [ ] Tous les signataires facultatifs : refusé au déploiement.
- [ ] Même personne deux fois (adresse ou utilisateur) : refusé au déploiement (A5).
- [ ] Le dernier requis signe alors qu'un facultatif reste en attente : `SIGNED`, le facultatif est révoqué.

### Signataire interne (US-10, D-SIGNATURE-INTERNAL-UX, D-SIGNATURE-SIGNER-SOURCE)

- [ ] `GET /api/signatures/mine` ne renvoie que les demandes adressées à l'utilisateur courant.
- [ ] `T-IDOR-3` : un utilisateur ne peut ni lire le document ni agir sur la demande d'un autre.
- [ ] Le droit de lecture du dossier reste exigé ; sans lui, la demande n'est pas émise pour ce
      signataire (notification interne, process non bloqué).
- [ ] Utilisateur désigné inconnu, désactivé ou identifiant invalide : traité comme adresse absente.
- [ ] En séquentiel, signature avant son tour : refus uniforme.
- [ ] Notification inbox à l'émission et à la décision.

### Gestion instructeur (US-4)

- [ ] Liste des demandes : VIEW suffit, adresse masquée sans EDIT.
- [ ] Annuler / renvoyer : EDIT requis ; l'ancien lien est révoqué au renvoi ; annulation propagée
      au port.
- [ ] Tâche complétée en interne, process annulé, dossier clôturé ⇒ demande `CANCELLED`, liens invalides.

### Documents (US-5, D-SIGNATURE-ELEMENT)

- [ ] Document signé et preuve = `PRODUCED_DOC` avec marqueur ; aucune valeur d'`ElementType` ajoutée.
- [ ] L'API distingue document source, document signé, preuve (filtre sur le marqueur). [P3]
- [ ] L'export CSV et l'affichage existants restent inchangés (non-régression).

### Répartiteur d'issue (D-SIGNATURE-DISPATCH)

- [ ] Issue produite avant l'abonnement du process (échec à l'émission) : `correlateMessage` lève
      `CallbackNoMatchingInstanceException`, la demande reste non répartie, le job reprend et réussit
      une fois le process abonné.
- [ ] Reprise idempotente : `outcome_dispatched_at` posé une seule fois, aucune double reprise.
- [ ] Plafond `dispatch-max-attempts` atteint : alerte (métrique + journal), la demande reste consultable.
- [ ] Plusieurs demandes dans un même process : la corrélation par `signatureRequestId` ne mélange pas les issues.

### MCP (D-SIGNATURE-MCP)

- [ ] `list_signature_requests` / `get_signature_request` : adresses masquées, droits respectés.
- [ ] `cancel_signature_request` : `dryRun` sans effet ; annulation réelle propagée au port.
- [ ] Aucun tool d'émission n'est exposé (test de catalogue des tools).

### Expiration (H6)

- [ ] Job d'expiration : demandes et liens échus passent en `EXPIRED`, notification émise ; le job
      tolère un retard et reste idempotent.

### Purge (US-9)

- [ ] La purge d'un dossier supprime demandes, signataires (adresses incluses), liens, documents
      signés et preuves (extension du test de purge existant).

### Contrat (D-SIGNATURE-CONTRACT-CHECK)

- [ ] Suite de tests de contrat `SignaturePort` (émission, statut, annulation, artefacts, issues)
      exécutée contre le stub et réutilisable par un futur adaptateur.
- [ ] Tableau de correspondance avec 2 à 3 API publiques relu ; chaque écart est traité ou
      consigné comme limite connue.
- [ ] Golden OpenAPI mis à jour (portail, admin, callback).

### Connecteur (D-SIGNATURE-ABSTRACTION)

- [ ] L'entrée de catalogue expose son `configSchema` (secrets en `PASSWORD`, chiffrés au repos).
- [ ] Sélection de l'adaptateur par propriété ; adaptateur inconnu ⇒ échec de démarrage explicite.

## End-to-End & Frontend Tests

### Vitest

- [ ] Écran signataire : états chargement, valide, invalide (message neutre), signé, refusé.
- [ ] Panneau « Signatures » : statuts, actions selon droits, bandeau « SIMULATION ».
- [ ] Éditeur BPMN : marqueur du bloc signature (si retenu, aligné sur LC1). [P3]

### Playwright

- [ ] Signataire interne : « Mes signatures » affiche la demande à son tour, signature, statut mis à jour.
- [ ] Séquentiel : le second signataire ne peut agir qu'après le premier.
- [ ] Facultatif qui refuse : la demande aboutit quand même à `SIGNED`.
- [ ] Parcours nominal : le process demande la signature, le signataire ouvre le lien, signe,
      l'instructeur voit `SIGNED`, le document signé et la preuve « SIMULATION ».
- [ ] Refus : le signataire refuse, le process route sur `DECLINED`.
- [ ] Lien invalide : message neutre.
- [ ] Annulation et renvoi par l'instructeur.
- [ ] Fonctionnalité désactivée : routes absentes, bloc refusé au déploiement.

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
