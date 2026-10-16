---
phase: requirements
title: Requirements — destinataire de « Notifier » depuis le dossier, références des brouillons, variables de démarrage
status: reviewed
created: 2026-10-16
feature: notifier-dossier-et-brouillons
todo_ref: TODO.md §92 · DEFERRED-FEATURES.md §7 · exemples/autorisation-riverain/DIFFICULTES.md §3 et §8
---

# Requirements — notifier-dossier-et-brouillons

## Problème

L'exemple `exemples/autorisation-riverain/` a été monté uniquement par configuration via MCP. Deux manques le bloquent.

**§8 — un brouillon ne peut pas référencer une ressource encore inactive.** Un modèle de document créé par MCP reste en
brouillon tant qu'un humain ne l'a pas activé. Or tout brouillon BPMN venu d'un canal automatisé passe par
`ProposeDraftUseCaseImpl.validateBpmn` → `DeployBpmnProcessUseCaseImpl.validate` → `BlockReferenceVerifier`. Ce dernier exige
un modèle **actif** (`BlockReferenceLookupAdapter`). Ces canaux sont MCP `deploy_workflow` (brouillon et `dryRun`), la
compilation de parcours et l'import de configuration. Un agent ne peut donc pas proposer « modèle + workflow » ensemble : il
faut qu'un humain active le modèle entre les deux étapes. L'enregistrement depuis l'éditeur (`SaveBpmnDraftUseCaseImpl`) ne
vérifie pas les références : le problème ne touche que les canaux automatisés.

**§3 — le destinataire de la brique « Notifier » ne peut pas venir du dossier.** `notificationTo` est un littéral ou la
variable de process du même nom. Cette variable ne peut pas être calculée dans le workflow : `BlockAttributeRules.MANAGED_ATTRIBUTES`
l'interdit. Elle doit donc être passée au démarrage. Or l'écran « Démarrer workflow » (`StartWorkflowDialog.vue`) n'envoie
**aucune variable**, alors que l'API `POST /api/dossiers/{id}/workflows` les accepte déjà. Résultat : une demande encodée par
un agent dans l'interface n'envoie aucun e-mail automatique, et l'adresse est saisie deux fois (champ du dossier et variable).

## Objectifs

- **G1 (§8).** Un brouillon BPMN proposé par un canal automatisé vérifie que chaque ressource référencée **existe**, sans
  exiger qu'elle soit **active**. La règle s'applique à toutes les sources :
  - modèle de document : brouillon accepté ;
  - type de dossier : inactif accepté ;
  - webhook de notification : désactivé accepté ;
  - instance de connecteur : désactivée acceptée.

  Une ressource **inconnue, supprimée ou du mauvais rôle** (par exemple un connecteur qui n'est pas de rôle service) reste
  refusée.
- **G2 (§8).** Le résultat d'une proposition et d'un `dryRun` liste les ressources encore inactives (brique, paramètre,
  valeur), parce que la publication échouera tant qu'elles ne sont pas activées. Ce n'est pas bloquant.
- **G3 (§8).** La publication d'un brouillon et le déploiement direct (écran d'upload, MCP en publication directe) restent
  **stricts** : ressource active exigée, comportement inchangé.
- **G4 (§3).** `notificationTo` accepte, en plus d'un littéral, les sources `dossierField:<champ>` et `variable:<nom>`, comme
  `flowable:portalRecipient`. Le nom est un identifiant simple lu tel quel, jamais évalué (TODO §69). Le champ du dossier est
  lu **à jour** dans le dossier (pas dans l'instantané `dossierFields`), comme le fait le portail. La source n'est résolue
  que si elle est écrite dans l'**attribut littéral** du BPMN déployé. Une variable de process `notificationTo` posée au
  démarrage ou à la complétion d'une tâche reste un littéral : un utilisateur ne doit pas pouvoir rediriger l'envoi vers la
  valeur d'un autre champ.
- **G5 (§3).** Si la source résolue est absente, nulle ou vide, la brique **échoue** et crée un incident Flowable, comme
  aujourd'hui pour un destinataire e-mail absent. L'erreur nomme la source, jamais la valeur.
- **G6 (§3).** Une source mal formée (`dossierField:` suivi d'un nom invalide, ou d'un nom vide) est refusée au déploiement et à
  la proposition de brouillon (`InvalidBpmnException`, 400).
- **G7 (dialogue).** L'écran « Démarrer workflow » propose une section repliée **« Variables de démarrage »** : des lignes
  nom/valeur, avec des valeurs **texte**. Le nom doit être un identifiant simple. Les noms réservés (`dossierId`,
  `dossierWorkflowLinkId`, `tenantId`, `dossierFields`, `connectorResult`) et les doublons sont refusés côté client, avant
  l'envoi. Le serveur garde son propre contrôle. Une ligne vide est ignorée. Aucun changement backend pour ce point.

## Hors périmètre

- Variables de démarrage **déclarées** dans la configuration du workflow (formulaire typé) : noté dans le backlog.
- Valeurs non textuelles dans le dialogue (nombre, booléen, date, JSON).
- Pièce jointe dans « Notifier » (§2), unicité d'un champ (§1), gabarits d'e-mail (§4) : lots séparés (DEFERRED-FEATURES §7).
- Source `dossierField:` pour `notificationUserId` ou pour d'autres paramètres de brique.
- Changer l'enregistrement d'un brouillon depuis l'éditeur, qui ne vérifie toujours pas les références.
- Activer automatiquement une ressource à la publication d'un brouillon qui la référence.

## Récits utilisateur

- En tant qu'**agent IA (MCP)**, je propose un modèle de document puis le workflow qui l'utilise. Les deux brouillons sont
  acceptés, et je suis prévenu que la publication du workflow attend l'activation du modèle.
- En tant que **concepteur**, j'écris `notificationTo="dossierField:email"` : l'e-mail part à l'adresse saisie dans le
  dossier, sans variable à transmettre au démarrage.
- En tant que **gestionnaire**, je démarre un workflow depuis la fiche dossier en renseignant une variable attendue par le
  processus (par exemple `notificationTo` ou `priorite`).
- En tant qu'**administrateur**, je publie un brouillon qui référence un modèle encore inactif : la publication est refusée
  avec la référence en cause, comme aujourd'hui.

## Critères d'acceptation

- Un brouillon MCP qui référence un modèle **en brouillon** est enregistré. La réponse liste ce modèle comme inactif.
- Un brouillon MCP qui référence un modèle **inexistant** est refusé avec le même message qu'aujourd'hui.
- La publication de ce brouillon tant que le modèle est inactif est refusée (`UnknownBlockReferenceException`). Une fois le
  modèle activé, elle réussit.
- Un `dryRun` MCP applique la même règle que la proposition.
- `notificationTo="dossierField:email"` avec `email` renseigné : l'e-mail est mis en file à cette adresse. Avec `email` vide
  ou absent : la tâche échoue, et le message nomme `dossierField:email`.
- `notificationTo="variable:adresse"` : lit la variable `adresse`.
- `notificationTo="contact@example.org"` : comportement inchangé.
- `notificationTo="dossierField:"` ou `"dossierField:a.b"` : déploiement refusé (400).
- Dialogue : une variable `priorite=haute` est transmise dans `variables`. Un nom réservé, invalide ou en doublon bloque
  l'envoi avec un message. Sans variable, la requête est identique à aujourd'hui.
- `./mvnw verify` est vert, ainsi que `npm run test` et `npm run test:e2e`. Le golden OpenAPI ne change pas.
- Mises à jour de la documentation :
  - `GUIDE-CONCEPTION-BPMN.md` : sources de `notificationTo` et règle des brouillons ;
  - `GUIDE-UTILISATEUR.md` : variables de démarrage ;
  - `exemples/autorisation-riverain/` (README, DIFFICULTES §3 et §8) une fois l'exemple simplifié.

## Contraintes et hypothèses

- Architecture hexagonale : la règle « existence sans statut » vit dans le domaine (`BlockReferenceVerifier` et son port).
  L'adaptateur n'expose qu'une recherche de statut.
- On réutilise `PortalRecipientSource` (ou une généralisation) plutôt que de créer une seconde grammaire `dossierField:`.
- Lire `Dossier.fields()` depuis une nouvelle classe doit passer la liste blanche ArchUnit (`DOSSIER_FIELDS_READ_ALLOWLIST`).
- La valeur résolue n'apparaît dans aucun log ni message d'erreur : l'adresse est une donnée personnelle.
- **Hypothèse.** Aucun BPMN déployé n'utilise aujourd'hui un `notificationTo` littéral qui commencerait par `dossierField:`
  ou `variable:`, car une telle valeur ne serait pas une adresse valide.

## Questions ouvertes

Aucune. Décisions prises le 2026-10-16 :
- portée de §8 : statut seulement, toutes sources ;
- champ vide : incident ;
- dialogue inclus, en clé/valeur libre.
