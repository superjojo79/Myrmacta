---
phase: requirements
status: reviewed
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
---

# Requirements & Problem Understanding — `signature-electronique`

> Origine : `ROADMAP.md` item **2.4**, analyse
> [analysis doc/30-signature-electronique.md](../../../analysis%20doc/30-signature-electronique.md)
> (recalibrée le 2026-09-29). TODO §72. Prérequis livré : `portail-externe`
> ([PR #138](https://github.com/superjojo79/FlowForge/pull/138), TODO §68).
> **Phase 1 close le 2026-09-29** : neuf décisions tranchées par l'utilisateur via
> `AskUserQuestion` (voir « Décisions »). **Aucun fournisseur désigné** : le lot livre un socle
> générique et un adaptateur *stub*, pas une intégration.
> **Phase 2 (revue) close le 2026-09-29** : hypothèses H1–H7 validées ou remplacées, **11 décisions
> supplémentaires** tranchées (voir « Décisions Phase 2 »). Le périmètre a **grandi** : signataires
> internes et externes, modes séquentiel et parallèle, signataires facultatifs (taille **XL**,
> livrée en un lot à jalons internes).

## Problem Statement

**Constat vérifié dans le code (`main` @ `89b77e4`, 2026-09-29) :**

- Aucun symbole de signature électronique n'existe (grep `DocuSign|Yousign|eIDAS|signature` :
  seuls des faux positifs HMAC / magic bytes).
- Un tiers sans compte peut répondre à une `userTask` via le portail tokenisé (`portal_link`,
  `PortalController`, `flowable:portalRecipient`), mais uniquement pour un formulaire
  `user-question:` ; le portail ne sait ni présenter un document à signer, ni recueillir un
  consentement de signature.
- Le retour asynchrone d'un tiers vers un process en attente existe (`flowable-callback`,
  capacité `CALLBACK`, HMAC horodaté + idempotence) ; le document à signer existe
  (`GenerateDocumentUseCase` + `DocumentStoragePort`, éléments `PRODUCED_DOC`).
- Le patron « port + adaptateur no-op + adaptateur réel choisi par propriété » existe
  (`AttachmentScanPort` : `NoOp` par défaut, `ClamAv` opt-in, `DossierConfig`).
- `AllowedBpmnDelegates.NAMES` compte 7 delegates ; toute nouvelle logique BPMN passe par
  l'allowlist (TODO §57/§69).

**Qui est affecté :**

- **Le concepteur de process** (`WORKFLOW_ADMIN`) : il ne peut pas modéliser « faire signer ce
  document par un tiers » ; il le simule par une tâche interne ou par un échange hors outil.
- **L'instructeur** (`DOSSIER_MANAGER`) : il envoie le document par e-mail, attend, puis rattache à
  la main le document signé, sans statut ni trace structurée.
- **Le signataire externe** : il n'a ni compte ni parcours de signature dans FlowForge.
- **L'intégrateur** (positionnement D0 = C, TODO §70) : quand un client désignera un fournisseur, il
  n'existe aucun point d'extension stable pour le brancher.

**Contournement actuel :** génération du PDF, envoi manuel, signature hors FlowForge, dépôt manuel
du document signé en pièce jointe, aucun lien entre la demande, le signataire et le résultat.

## Goals & Objectives

**Objectif principal :** poser le **contrat, le modèle de données, le parcours et le point
d'extension** d'une signature électronique, avec un **adaptateur stub générique** qui simule le
cycle complet, pour que le branchement d'un provider réel se limite à **un adaptateur et une
configuration** — sans modifier le domaine, le BPMN, le portail ni le modèle de dossier.

**Objectifs secondaires :**

- Un bloc BPMN « Demander une signature » (delegate allowlisté) que la palette LC1 pourra exposer,
  déclarant les signataires (internes ou externes, requis ou facultatifs) et le mode
  (**parallèle** ou **séquentiel**).
- Une tâche externe de type « signature » dans le portail pour les signataires externes
  (redirection vers le fournisseur pour un provider réel, écran de confirmation pour le stub).
- Une vue « Mes signatures » dans la SPA pour les signataires **internes** (utilisateurs FlowForge).
- Un statut de chaque demande visible dans le détail du dossier, avec annulation et renvoi.
- Un **document signé** et une **preuve** rattachés au dossier (`PRODUCED_DOC` marqués), traçables
  jusqu'à la demande et au signataire.
- Une entrée de catalogue de connecteurs portant configuration, secrets et retour du provider actif.
- Un **contrat validé** contre 2 à 3 API publiques (revue documentaire, sans intégration).

**Non-goals (explicitement hors périmètre de ce lot) :**

- Aucun fournisseur réel, aucune valeur juridique, aucun niveau eIDAS **implémenté** (reportés au
  premier provider réel : D-SIGNATURE-PROVIDER, D-SIGNATURE-LEVEL).
- Signature manuscrite, certificat, horodatage qualifié gérés par FlowForge.
- Bascule à chaud d'un provider vers un autre pour des demandes en cours.
- Nouvelle valeur d'`ElementType` (écartée par D-SIGNATURE-ELEMENT).
- Iframe fournisseur et élargissement de la CSP (redirection uniquement).
- Relances automatiques et rappels planifiés aux signataires (renvoi manuel seulement).
- Signature par groupe ou par rôle : un signataire est toujours **une personne** nommée.
- Génération du document à signer par le bloc signature (le BPMN enchaîne génération puis signature).
- Enveloppe de plusieurs documents : **un seul** `PRODUCED_DOC` par demande.

## Décisions (Phase 1, tranchées par l'utilisateur le 2026-09-29)

| ID | Décision | Alternatives écartées |
|---|---|---|
| **D-SIGNATURE-ABSTRACTION** | **Port `SignaturePort` + adaptateur exposé comme connecteur** mince : le port porte le contrat, l'entrée de catalogue porte configuration, secrets et retour. | Port seul ; connecteur du catalogue seul (`SERVICE` + `CALLBACK`). |
| **D-SIGNATURE-STUB-BEHAVIOR** | **Simulation de bout en bout**, marquée « SIMULATION — sans valeur juridique » (filigrane, `simulated = true`, bandeau UI). | Contrat seul avec échec explicite ; stub activable uniquement en profil test. |
| **D-SIGNATURE-UX** | **Tâche externe du portail + statut dans le dossier.** | Backend + statut seulement ; contrat UX laissé ouvert. |
| **D-SIGNATURE-TRIGGER** | **Nouveau delegate allowlisté** (`signatureRequestTask`), avec attente du retour normalisé. | Via `connectorServiceTask` existant ; REST/MCP direct uniquement. |
| **D-SIGNATURE-TRIGGER-VS-CONNECTOR** | **Delegate → `SignaturePort` direct** ; le connecteur ne sert qu'à configuration, secrets et retour `CALLBACK`. | Delegate → connecteur → port. |
| **D-SIGNATURE-PORTAL-KIND** | **Nouveau type de tâche externe « signature »** dans le portail (rouvre le périmètre de D-PORTAL-FORM). | Réutiliser `user-question` avec une question de confirmation (recommandé, écarté). |
| **D-SIGNATURE-ELEMENT** | **`PRODUCED_DOC` marqué** pour le document signé et la preuve. | Nouvelle valeur d'`ElementType`. |
| **D-SIGNATURE-STUB-GUARD** | **Refus de démarrer en profil production, activable par un second drapeau explicite** (avertissement). | Refus strict (recommandé, écarté) ; simple avertissement. |
| **D-SIGNATURE-CONTRACT-CHECK** | **Revue documentaire de 2 à 3 API publiques**, tableau de correspondance en Phase 2-3, sans intégration. | Contrat minimal figé par le seul stub. |
| ~~D-SIGNATURE-PROVIDER~~, ~~D-SIGNATURE-LEVEL~~ | **Reportées** au premier provider réel. | — |

## Décisions Phase 2 (revue, tranchées par l'utilisateur le 2026-09-29)

| ID | Décision | Alternatives écartées |
|---|---|---|
| **D-SIGNATURE-ORDER** (ex-H1/H7) | **Deux modes au choix du BPMN** : `PARALLEL` (tous notifiés ensemble) et `SEQUENTIAL` (le suivant n'est notifié qu'après la décision du précédent). Le contrat déclare « ordre imposé » comme capacité ; le stub implémente les deux. | Parallèle seul (recommandé, écarté) ; séquentiel seul. |
| **D-SIGNATURE-REQUIRED** | **Signataires requis ou facultatifs**, déclarés dans le bloc. `SIGNED` = **tous les requis ont signé** ; le refus ou l'expiration d'un **requis** ferme la demande (`DECLINED` / `EXPIRED`), liens restants révoqués, aucun document signé partiel. | Tout ou rien sans facultatifs (recommandé, écarté) ; signatures partielles conservées. |
| **D-SIGNATURE-SEQ-OPTIONAL** | **En séquentiel, le refus ou l'expiration d'un facultatif fait passer au suivant** (le signataire est marqué, la chaîne continue). | Arrêt de la chaîne à tout refus. |
| **D-SIGNATURE-OUTCOME** (ex-H3) | **Variable de process normalisée** : après l'attente (message corrélé, patron `flowable-callback`), le process reprend avec `signatureOutcome` (`SIGNED`, `DECLINED`, `EXPIRED`, `FAILED`, `CANCELLED`) et `signatureRequestId` ; le BPMN route par passerelle. | Un message par issue ; deux sorties succès/échec. |
| **D-SIGNATURE-DOCUMENT** (ex-H2) | **Un seul `PRODUCED_DOC` existant**, désigné par variable de process ; le lot ne génère rien. | Plusieurs documents ; génération intégrée au bloc. |
| **D-SIGNATURE-DEADLINE** (ex-H6) | **Une échéance de demande** (jours, plafonnée par le tenant) ; les liens ne la dépassent jamais ; à l'échéance la demande passe `EXPIRED`. **Renvoi manuel** par l'instructeur, **aucune relance automatique**. | TTL de lien indépendante ; relances automatiques. |
| **D-SIGNATURE-SIGNER-KIND** | **Signataires internes ET externes dès ce lot** ; le signataire porte un type (`EXTERNAL`, `INTERNAL`). | Externes seuls (recommandé, écarté). |
| **D-SIGNATURE-INTERNAL-UX** | **Vue « Mes signatures »** dans la SPA authentifiée (liste des demandes adressées à l'utilisateur, document, signer, refuser) + notification inbox. Pas de passage par le portail. | `userTask` Flowable classique ; lien e-mail + session OIDC. |
| **D-SIGNATURE-SIGNER-SOURCE** | Un signataire **interne** est désigné par un **identifiant utilisateur lu dans un champ du dossier ou une variable de process** (même mécanisme de source que l'adresse des externes) ; jamais par groupe. | Utilisateur littéral ou groupe candidat ; nom littéral seul. |
| **D-SIGNATURE-PROOF** | La preuve du stub est un **PDF de preuve filigrané** (demande, empreinte du document, signataires, horodatages, mention SIMULATION). | JSON seul ; PDF + JSON. |
| **D-SIGNATURE-DELIVERY** | **Un seul lot**, un cycle `dev-lifecycle`, une PR, **jalons internes ordonnés** : externes + parallèle + requis d'abord, puis séquentiel et facultatifs, puis internes. | Deux lots successifs ; livraison d'un bloc sans coupure. |

**Hypothèses acceptées telles quelles (nommées, sans nouvelle question) :**

- **A1** (ex-H4) — les signataires **externes** sont désignés comme les destinataires du portail :
  champ du dossier ou variable de process, même résolution d'adresse.
- **A2** (ex-H5) — annuler ou renvoyer exige **EDIT sur le type du dossier** (aligné sur le portail,
  écart H6 tranché en PR #138) ; la lecture de la liste exige VIEW, l'adresse est masquée sans EDIT.
- **A3** — un lien externe = **un signataire** ; les liens de signature et les liens `user-question`
  du portail coexistent sur un même dossier sans interférer (états, jetons et purge indépendants).
- **A4** — un signataire interne ne peut voir et signer **que les demandes qui lui sont adressées**
  (identité authentifiée), en plus du droit de lecture normal sur le dossier ; l'accès au document
  reste borné à celui de la demande.
- **A5** — la même personne (adresse ou utilisateur) ne peut figurer qu'**une fois** par demande.

## User Stories & Use Cases

- **US-1 (concepteur BPMN)** — En tant que `WORKFLOW_ADMIN`, j'ajoute à un process un bloc
  « Demander une signature » en désignant le document à signer (variable), les signataires
  (internes ou externes, requis ou facultatifs), le **mode** (`PARALLEL` ou `SEQUENTIAL`) et une
  échéance ; un déploiement incohérent (fonctionnalité désactivée, document non désigné, signataire
  sans source, doublon de personne, échéance au-delà du plafond) est refusé avec un message explicite.
- **US-2 (signataire externe)** — En tant que signataire sans compte, je reçois un e-mail avec un
  lien (immédiatement en parallèle, à mon tour en séquentiel) ; j'y consulte le document à signer,
  puis je signe ou je refuse.
- **US-3 (signataire externe)** — En tant que signataire, si mon lien est expiré, révoqué, déjà
  utilisé ou inexistant, je vois le même message neutre qu'au portail (pas d'oracle).
- **US-4 (instructeur)** — En tant que `DOSSIER_MANAGER` avec droit EDIT sur le type, je vois
  dans le détail du dossier le statut de chaque demande de signature (`PENDING`, `SIGNED`,
  `DECLINED`, `EXPIRED`, `FAILED`, `CANCELLED`) et de **chaque signataire** (rang, type, requis ou
  facultatif), je peux **annuler** la demande ou **renvoyer** à un signataire.
- **US-5 (instructeur)** — En tant qu'instructeur, je retrouve le **document signé** et la
  **preuve** rattachés au dossier, clairement distingués du document source, et étiquetés
  « SIMULATION » quand ils proviennent du stub.
- **US-6 (process)** — En tant que process BPMN, je reprends après la demande avec une **issue
  normalisée** (signée, refusée, expirée, en échec) sur laquelle je peux router.
- **US-7 (participants internes)** — En tant que participant au workflow, je suis notifié (inbox)
  quand une demande est signée, refusée, expirée ou en échec.
- **US-8 (administrateur / intégrateur)** — En tant qu'administrateur, je configure le provider
  actif (adaptateur et secrets) via l'entrée de catalogue, sans toucher au code ni au BPMN ;
  quand le stub est actif, je vois un avertissement explicite.
- **US-10 (signataire interne)** — En tant qu'utilisateur FlowForge désigné signataire, je vois dans
  « Mes signatures » les demandes qui m'attendent (à mon tour en séquentiel), je consulte le
  document, puis je signe ou je refuse ; je suis notifié dans l'inbox.
- **US-9 (audit)** — En tant qu'administrateur, je retrouve qui a signé quoi, quand, via quelle
  demande et quel provider, y compris pour une simulation.

**Cas limites à couvrir :**

- Tâche complétée en interne, process annulé, dossier clôturé ou purgé pendant une demande
  ouverte ⇒ demande annulée, lien invalide.
- Double soumission (double clic, rejeu du callback) ⇒ une seule issue, la seconde est ignorée
  (idempotence).
- Document source supprimé ou modifié après l'émission de la demande ⇒ la demande reste liée à
  l'empreinte du document émis.
- Adresse du signataire absente ou invalide à l'émission ⇒ aucune demande envoyée, notification
  interne, process non bloqué (même patron que D-PORTAL-NO-ADDRESS).
- Fonctionnalité désactivée après coup alors que des demandes existent ⇒ liens inutilisables,
  demandes annulables en interne.
- Retour du provider arrivant après expiration ou annulation ⇒ ignoré et journalisé.
- Stub actif en profil production sans le second drapeau ⇒ l'application refuse de démarrer.
- Refus ou expiration d'un **requis** ⇒ demande fermée, liens restants révoqués ; d'un
  **facultatif** ⇒ marqué, la demande continue (séquentiel : on passe au suivant).
- Tous les signataires sont facultatifs ⇒ refusé au déploiement (au moins un requis obligatoire).
- Séquentiel : un signataire n'est notifié ni ne peut agir avant son tour (lien ou entrée « Mes
  signatures » inactifs, réponse uniforme).
- Signataire interne désigné mais désactivé, supprimé ou sans droit de lecture sur le dossier ⇒ aucune
  demande envoyée à ce signataire, notification interne, process non bloqué.
- Même personne désignée deux fois ⇒ refusé (A5).

## Success Criteria

- Un process peut demander la signature d'un `PRODUCED_DOC` à un ou plusieurs signataires externes,
  attendre l'issue et reprendre, avec le stub, **sans aucun provider configuré**.
- Le document signé et la preuve sont des `PRODUCED_DOC` marqués (rôle `SIGNED_DOCUMENT` /
  `SIGNATURE_PROOF`, `simulated`), liés à la demande, sans nouvelle valeur d'`ElementType`, sans
  migration de l'export CSV ni de l'affichage existants.
- Tout artefact du stub porte « SIMULATION — sans valeur juridique » : filigrane PDF, champ
  `simulated`, bandeau UI ; vérifié par test.
- Les **deux modes** (`PARALLEL`, `SEQUENTIAL`) et la distinction **requis / facultatif** sont couverts
  par tests, dont les cas : facultatif qui refuse en séquentiel (la chaîne continue), requis qui
  refuse (demande fermée, liens révoqués), tous facultatifs (refusé au déploiement).
- Un signataire **interne** ne voit et ne signe que les demandes qui lui sont adressées, et seulement
  à son tour (test d'IDOR sur « Mes signatures »).
- La preuve du stub est un PDF filigrané contenant l'empreinte du document, les signataires et les
  horodatages (extraction de texte vérifiée).
- Le process reprend avec `signatureOutcome` et `signatureRequestId` ; le routage par passerelle
  fonctionne pour les cinq issues.
- Le stub est **désactivé par défaut** ; actif en profil production sans le second drapeau
  explicite, l'application **refuse de démarrer** (test) ; avec le drapeau, elle démarre avec un
  avertissement.
- Aucune route publique nouvelle hors `/portal/**` ; la CSP enforce reste inchangée ; le jeton n'est
  jamais journalisé ni stocké en clair (mêmes critères que le portail).
- Un lien expiré, révoqué, utilisé ou inexistant produit la **même** réponse (test d'absence
  d'oracle rejoué sur le type « signature »).
- Un lien de signature n'ouvre **que** son document et sa demande : tests d'IDOR (autre demande,
  autre document, autre dossier).
- `delegateExpression` `signatureRequestTask` accepté par l'allowlist ; toute autre forme reste
  refusée (tests `AllowedBpmnDelegates` et parser).
- La purge de dossier supprime demandes, signataires, liens, documents signés et preuves.
- Un tableau de correspondance entre `SignaturePort` et 2 à 3 API publiques est produit et relu
  (D-SIGNATURE-CONTRACT-CHECK), avec la liste des écarts et leur traitement.
- Le retour du provider (callback) reprend les garde-fous de `flowable-callback` : HMAC horodaté,
  idempotence, taille bornée, rate-limit pré-authentification.
- Tests : `./mvnw verify` vert (ArchUnit inclus, domaine pur), Vitest et Playwright verts avec
  scénarios dédiés (parcours nominal, refus, lien invalide, IDOR, stub en production).
- Documentation : `GUIDE-CONCEPTION-BPMN.md` (bloc signature), `GUIDE-UTILISATEUR.md`
  (instructeur), `GETTING-STARTED.md` + `.env.example` (aucun drapeau de simulation en production),
  `DEPLOIEMENT.md` (activation, garde du stub), `CHANGELOG.md`.

## Constraints & Assumptions

**Contraintes techniques (stack figée, conventions projet) :**

- Architecture hexagonale : domaine pur, ports `in`/`out`, ArchUnit en CI.
- Une instance = un tenant ; le lien porte le chemin de l'instance (décision n° 14).
- Le type de tâche « signature » réutilise la chaîne de sécurité et les composants du portail
  (jeton haché, limite par IP avant lecture du corps, `PortalPreAuthInterceptor`) ; aucune route
  hors `/portal/**`.
- Le delegate ajouté à `AllowedBpmnDelegates` ne doit ouvrir aucune expression libre.
- Secrets du provider : champs `PASSWORD` de connecteur (chiffrés au repos).
- Rate-limit JVM-local (mono-réplique, pas de Redis).
- PDF : le filigrane du stub s'appuie sur la pile existante (Flying Saucer / OpenPDF).

**Hypothèses de Phase 1, toutes levées en Phase 2 (2026-09-29) :** H1 et H7 → D-SIGNATURE-ORDER /
D-SIGNATURE-REQUIRED ; H2 → D-SIGNATURE-DOCUMENT ; H3 → D-SIGNATURE-OUTCOME ; H4 → A1 ; H5 → A2 ;
H6 → D-SIGNATURE-DEADLINE. Aucune hypothèse non nommée ne subsiste.

## Risques acceptés

- **R-PORTAIL (D-SIGNATURE-PORTAL-KIND)** — le type de tâche « signature » rouvre la sécurité du
  portail (routes publiques, CSRF, limitation, purge). Atténuation : réutilisation des composants
  du portail, revue adversariale rejouée sur le nouveau type.
- **R-SIM-EN-PROD (D-SIGNATURE-STUB-GUARD)** — un stub actif en production via le drapeau explicite
  peut produire un « document signé » simulé pris pour authentique. Atténuation : désactivé par
  défaut, drapeau absent des gabarits, avertissement au démarrage, marquage indélébile, refus sans
  drapeau.

## Questions & Open Items

**Toutes les décisions produit de Phase 1 et 2 sont tranchées ou acceptées comme hypothèses
nommées (A1–A5).** Aucune question produit ouverte. Le périmètre XL est assumé (D-SIGNATURE-DELIVERY).

**Questions de conception renvoyées à la Phase 3 :**

- Modèle de données : table dédiée ou extension de `portal_link` ; représentation du type de
  signataire (`EXTERNAL` / `INTERNAL`), du rang et de l'indicateur requis / facultatif.
- Marqueur BPMN du bloc signature (attributs `flowable:*` portant document, signataires, mode,
  échéance) et compatibilité avec l'allowlist (§57/§69) : les signataires sont des **sources**
  (champ / variable), jamais des expressions libres.
- Mécanisme d'attente du process (message corrélé, patron `flowable-callback`) et corrélation.
- Moteur de progression séquentielle : qui notifie le signataire suivant, transitions atomiques.
- Vue « Mes signatures » : route et API authentifiées, filtrage par identité, lien avec l'inbox.
- Nom de la propriété d'activation et du second drapeau, critère de « profil de production ».
- Forme du marqueur `PRODUCED_DOC` et du PDF de preuve ; filigrane.
- Forme de l'événement normalisé du contrat et des capacités (dont « ordre imposé », signataires
  facultatifs) ; tableau de correspondance avec 2 à 3 API publiques (M0).
- Chiffrement des secrets par instance de connecteur ; signature du callback du provider.
