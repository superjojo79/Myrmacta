# §93 — Brouillons avec ressource inactive, destinataire de « Notifier » depuis le dossier, variables de démarrage

> **Statut :** clos · point de premier niveau · ancien `TODO.md` §93 · [registre](README.md)

[x] livré (branche `feature-notifier-dossier-et-brouillons`). Trois manques relevés par l'exemple
[`exemples/autorisation-riverain/`](../../exemples/autorisation-riverain/DIFFICULTES.md) (§8 et §3), monté uniquement par
configuration via MCP. Docs SDD : [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-10-16-feature-notifier-dossier-et-brouillons.md),
[design](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/design/2026-10-16-feature-notifier-dossier-et-brouillons.md),
[planning](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/planning/2026-10-16-feature-notifier-dossier-et-brouillons.md),
[implementation](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/implementation/2026-10-16-feature-notifier-dossier-et-brouillons.md),
[testing](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/testing/2026-10-16-feature-notifier-dossier-et-brouillons.md).

**D-DRAFT-REF-STATUS** (tranché par l'utilisateur, 2026-10-16) — un brouillon BPMN proposé par un canal automatisé (MCP
`deploy_workflow` et son `dryRun`, compilation de parcours, import de configuration) accepte une ressource référencée qui
**existe sans être active** : template seulement en brouillon, type de dossier inactif, connecteur de rôle service ou webhook
désactivé. Elle est renvoyée (`inactiveReferences` MCP, `warnings` du parcours). Inconnue, supprimée, de mauvais rôle, ou template
seulement archivé : refus inchangé. Déploiement direct et publication d'un brouillon restent **stricts**. Écarté : ne rien
vérifier sur un brouillon (faute de frappe vue trop tard) ; n'assouplir que les templates.

**D-NOTIF-SOURCE-LITERAL** — `flowable:notificationTo` accepte `dossierField:<champ>` (relu à jour dans le dossier) et
`variable:<nom>`, grammaire de `portalRecipient`. La source n'est résolue que depuis l'**attribut littéral** déployé : une
variable `notificationTo` (démarrage, complétion de tâche) reste une adresse et ne peut pas désigner un autre champ. Valeur
absente, vide ou non textuelle : **incident** (tranché par l'utilisateur), message sans la valeur. Source mal formée : 400 au
déploiement et diagnostic dans la palette.

**D-NOTIF-REUSE-PORTAL-SOURCE** — réutilisation telle quelle de `PortalRecipientSource` et `PortalRecipientResolverAdapter`
(déjà dans la liste blanche ArchUnit de `Dossier.fields()`) ; renommage écarté (19 fichiers pour un gain nominal).

**Variables de démarrage** (tranché par l'utilisateur : inclus, clé/valeur libre) — section repliée du dialogue « Démarrer un
workflow », valeurs texte, noms réservés et doublons refusés côté client ; l'API existante est inchangée. Variables
**déclarées** dans la configuration du workflow : hors périmètre, notées au backlog.

**Revue adversariale (2026-10-16, sous-agent, aucun High introduit) — tout corrigé :**
- **M1** — « Notifier » synchrone : un champ mal rempli annulait la complétion ou le démarrage en 500 → `NotificationValidationException`
  traduite en **422** (source nommée, jamais la valeur) ; le guide recommande `flowable:async="true"` pour obtenir un incident.
- **L1** — MCP en publication directe : un brouillon à ressource inactive n'est plus publié (erreur « n'existe pas » trompeuse) ;
  il reste en attente avec `inactiveReferences`.
- **L2** — le plan d'import dit quelles ressources inactives bloqueront la publication.
- **L3** — la variable `notificationTo` (démarrage, complétion) passe le même contrôle « une seule adresse » que les sources ;
  l'adresse écrite en dur dans le BPMN reste libre. *Changement de comportement* : « Nom <adresse> » en variable est refusé.
- **I1/I2/I3/I4** — commentaire SMTP corrigé ; `dossierId` invalide → erreur de validation sans la valeur ; `exists` retiré du port
  (sans appelant) ; l'entrée `draft` des réponses MCP garde sa forme d'avant.
- **I6** — adresse lue dans un champ sensible : documentée (conservée en clair dans l'outbox, comme le portail).
- **I7 (préexistant)** — le connecteur `flowable-signal` refuse les variables plateforme (`dossierId`…) dans `connectorInput.variables`,
  qui pouvaient réécrire `dossierId` de toutes les instances du tenant en attente du signal.
- **I8 (préexistant)** — `documentType` passé au démarrage (dialogue, API, MCP, dossier enfant) est soumis à la liste blanche du
  type de dossier, comme à la complétion d'une tâche (`DocumentTypeAllowList`, D-TASK-FORM-DOCTYPE-SCOPE).
- **I5, I9** — sans action (pas de fuite nouvelle ; un tenant par instance).

**Impacte :** `docs/guides/GUIDE-CONCEPTION-BPMN.md` (§3ter références, §6c Notifier), `docs/guides/GUIDE-UTILISATEUR.md` (démarrer une instance),
`docs/specs/15-serveur-mcp.md`, `CHANGELOG.md`. Aucune migration, aucune variable d'environnement, golden OpenAPI inchangé.
