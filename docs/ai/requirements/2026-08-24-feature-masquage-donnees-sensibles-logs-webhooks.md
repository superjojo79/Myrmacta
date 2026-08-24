---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: reviewed
created: 2026-08-24
updated: 2026-08-24
feature: masquage-donnees-sensibles-logs-webhooks
---

# Requirements & Problem Understanding — `masquage-donnees-sensibles-logs-webhooks`

## Problem Statement

**Énoncé du TODO §23 (reste ouvert) :** « exclusion des valeurs de champs
sensibles des logs applicatifs et des payloads de webhooks sortants
(notification) ».

**Vérifié par lecture de code (audit avant rédaction) :** contrairement à ce
que l'énoncé laisse penser, **aucune fuite active n'a été trouvée** — ni
dans les logs, ni dans les webhooks :

- **Logs** : les 8 fichiers du code de production qui journalisent
  (`grep log\.|LOGGER\.|logger\.` sous `src/main/java`) ne loggent jamais un
  `Dossier` entier ni sa map `fields`. `SecurityAuditLog.sensitiveFieldChanged()`
  (`src/main/java/com/flowforge/infrastructure/security/SecurityAuditLog.java:50-57`)
  loggue déjà uniquement le **nom** du champ modifié, jamais sa valeur — un
  précédent explicite dans le code existant.
- **Webhooks** : le payload sortant (`NotificationEvent.payload()`, une
  `Map<String, Object>` sans schéma) n'est aujourd'hui peuplé que par 2
  producteurs — `WorkflowStateEventListener` (payload fixe `{type, linkId}`,
  aucun champ dossier) et `NotificationDispatchTask` (lit la variable de
  process BPMN `notificationPayload`, absente de tous les `.bpmn20.xml` du
  dépôt — aucun process ne l'alimente depuis `dossier.fields()`
  aujourd'hui). Seul `NotificationSendController` (API admin) accepte un
  payload libre fourni par l'appelant — un `TENANT_ADMIN` pourrait y coller
  une valeur sensible lui-même, mais ce n'est pas une fuite automatique du
  système.
- **Ni `Dossier` ni `FieldDefinition`** (records) n'ont de `toString()`
  personnalisé : le `toString()` généré par défaut imprimerait toute la map
  `fields` si jamais un `Dossier` était un jour loggé directement — aucun
  garde-fou structurel ne l'empêche.

**Reformulation du problème :** ce n'est pas une fuite à corriger, mais un
**risque latent sans garde-fou structurel**. La feature construit la
protection préventive (défense en profondeur), sur le même principe que le
skeleton `HttpRestServiceConnector` du TODO §30 (« prêt à activer sans
redévelopper »), plutôt que de documenter un incident qui n'existe pas.

**Affectés :** développeurs futurs (risque de fuite accidentelle en ajoutant
un `log.info("dossier={}", dossier)` par erreur), tenants dont des champs de
dossier sont marqués `sensitive` (protection renforcée), auditeurs sécurité
(TODO §23 fermable proprement).

## Goals & Objectives

**Objectifs principaux (D-SCOPE = garde-fou complet, décision actée en
amont via `AskUserQuestion`) :**

1. Empêcher qu'un `Dossier`/`FieldDefinition` loggé directement (accident de
   développement futur) imprime des valeurs de champs sensibles.
2. Ajouter un mécanisme de masquage réutilisable, câblé en défense en
   profondeur au point d'envoi HTTP des webhooks
   (`WebhookRestAdapter`/chaîne `SendNotificationUseCase`), pour que si un
   futur producteur peuple un jour le payload avec des champs de dossier,
   les valeurs sensibles soient automatiquement retirées avant l'envoi —
   sans dépendre de la discipline de ce futur producteur.
3. Fermer TODO §23 avec un état honnête : documenter qu'aucune fuite active
   n'a été trouvée, et que la protection ajoutée est préventive.

**Objectifs secondaires :**

- Tests de non-régression façon `D-SECRETS-AUDIT` (précédent
  `NotificationWebhookResponseSecretMaskingTest` /
  `ConnectorResponseSecretMaskingTest`, TODO §30) prouvant que le mécanisme
  fonctionne sur un scénario simulé, même en l'absence de producteur réel
  aujourd'hui.

**Non-goals (hors périmètre explicite) :**

- Modifier des process BPMN existants pour leur faire peupler
  `notificationPayload` avec des champs de dossier — aucun process ne le
  fait aujourd'hui, ce n'est pas dans le périmètre de cette feature de leur
  en donner la capacité.
- Restreindre ou valider le payload libre de `NotificationSendController`
  (API admin) — un `TENANT_ADMIN` reste responsable du contenu qu'il
  soumet explicitement via cette API ; ce n'est pas un canal de fuite
  automatique.
- Masquage des secrets de connecteurs/webhooks (`isSecretKey`,
  `withMaskedSecret`) — déjà couvert par TODO §30, hors périmètre ici (ce
  mécanisme existant n'est pas modifié).
- Chiffrement ou stockage différent des champs sensibles — hors périmètre
  (seule la fuite en sortie — logs, webhooks — est traitée, pas le
  stockage, déjà couvert par `tenant_connector_dek`/décision n° 4 pour un
  périmètre différent).

## User Stories & Use Cases

- En tant que **développeur backend**, si j'ajoute par erreur un
  `log.info("processing {}", dossier)` dans une future feature, je veux que
  le log produit ne contienne pas les valeurs des champs marqués
  `sensitive`, pour ne pas introduire une fuite par accident de code review
  manqué.
- En tant que **tenant admin**, si une future intégration webhook venait à
  transmettre des données de dossier dans son payload, je veux que les
  valeurs des champs marqués `sensitive` de mon `DossierType` soient
  automatiquement absentes du corps HTTP envoyé à mon URL de webhook, sans
  avoir à faire confiance à l'implémentation du producteur du payload.
- En tant qu'**auditeur sécurité**, je veux pouvoir vérifier par un test
  automatisé (pas seulement une revue manuelle) que le masquage fonctionne,
  de la même manière que les gardes-fous existants pour les secrets de
  connecteurs (TODO §30).

**Cas limites à couvrir :**

- Un `Dossier` avec zéro champ sensible (le garde-fou ne doit rien casser
  ni rien changer d'observable).
- Un payload de webhook qui ne contient aucune donnée de dossier (chemin
  actuel à 100 % du trafic réel) — le mécanisme ajouté ne doit avoir aucun
  effet de bord sur ce cas.
- Un champ `sensitive` dont la valeur est `null` (ne doit pas planter le
  masquage).

## Success Criteria

- [x] Un test prouve qu'un `Dossier` contenant un champ `sensitive` avec une
      valeur connue, journalisé via le chemin de log réel (pas un mock),
      ne fait **jamais** apparaître cette valeur dans la sortie.
- [x] Un test prouve que le mécanisme de masquage webhook, exercé sur un
      payload simulé contenant une valeur de champ sensible connue, la
      retire avant l'envoi HTTP réel (`WebhookRestAdapter`).
- [x] Le comportement actuel (aucun champ dossier dans les 2 producteurs
      réels de payload) est inchangé — non-régression complète
      (`./mvnw verify` : 1088 tests, 0 échec ; pas de Vitest/Playwright
      applicable, zéro fichier frontend touché).
- [x] TODO §23 passe à `[x]` avec une note de résolution reflétant l'état
      réel constaté (pas de fuite active trouvée, protection préventive
      ajoutée) plutôt que de prétendre corriger un incident inexistant.
- [x] `FILE-ATTENTE.md` et `DEFERRED-FEATURES.md` mis à jour en cohérence.

## Constraints & Assumptions

- **Contrainte architecturale** : `Dossier` (domaine) ne porte pas la liste
  des `FieldDefinition` de son type — un `toString()` "intelligent" qui
  connaîtrait quels champs sont sensibles nécessiterait soit de changer la
  forme du modèle, soit un appel externe depuis `toString()` (mauvaise
  pratique). Le mécanisme de protection des logs devra donc être
  structurel et non dépendant des métadonnées de sensibilité au moment du
  log (ex : ne jamais imprimer `fields` du tout plutôt que de filtrer
  sélectivement) — **arbitrage technique laissé à la Phase 3 (design)**.
- **Contrainte architecturale** : le payload webhook (`Map<String,Object>`)
  n'a pas de schéma et `WebhookRestAdapter` (infrastructure notification)
  n'a aujourd'hui aucune dépendance vers le port de lecture des
  `FieldDefinition` (domaine dossier) — le mécanisme de masquage webhook
  devra donc soit établir une convention de payload explicite, soit
  accepter une dépendance croisée nouvelle — **arbitrage laissé à la
  Phase 3 (design)**.
- **Hypothèse actée (audit)** : aucun process BPMN déployé ne peuple
  `notificationPayload` avec des données de dossier aujourd'hui — vérifié
  par `grep` exhaustif sur tous les `.bpmn20.xml` du dépôt.
- Cohérence avec le style de tests `D-SECRETS-AUDIT` déjà en place (TODO
  §30) — pas de nouveau framework de test à introduire.

## Questions & Open Items

Aucune question produit ouverte à ce stade — le périmètre (garde-fou
complet, logs + webhooks) a été tranché explicitement par l'utilisateur
avant la rédaction de ce document. Les deux points listés en « Contraintes »
ci-dessus (mécanisme exact de protection des logs, mécanisme exact de
masquage webhook) sont des décisions techniques, traitées en Phase 3
(`dev-design`), pas des questions produit.
