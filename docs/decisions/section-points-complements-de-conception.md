# Points ouverts › Compléments de conception

> Section de l'ancien `TODO.md`, conservée pour son texte d'introduction · [registre](README.md)

Ces points ne bloquent pas le démarrage, mais ils doivent être planifiés explicitement : aucun n'est
couvert aujourd'hui, même partiellement.

## Points de cette section

- [§24 — Contrat d'API — surface incomplète et format d'erreur non normalisé](024-contrat-d-api-surface-incomplete-et-format.md) — clos
- [§25 — Modèle physique de données consolidé](025-modele-physique-de-donnees-consolide.md) — clos
- [§26 — Concurrence sur le dossier et unicité de la référence](026-concurrence-sur-le-dossier-et-unicite-de.md) — clos
- [§28 — Stratégie de test](028-strategie-de-test.md) — clos
- [§29 — Observabilité](029-observabilite.md) — clos
- [§30 — Durcissement des points d'entrée exposés](030-durcissement-des-points-d-entree-exposes.md) — clos
- [§35 — Modélisation et déploiement des diagrammes BPMN — écart vision / implémentation](035-modelisation-et-deploiement-des-diagrammes-bpmn-ecart.md) — clos
- [§36 — `canEditDossier` (frontend) ne reflète pas la vraie règle d'autorisation backend](036-caneditdossier-frontend-ne-reflete-pas-la-vraie.md) — clos
- [§37 — `findClosedForRetention` sans `tenantId` explicite — rupture du pattern « ceinture et bretelles »](037-findclosedforretention-sans-tenantid-explicite-rupture-du-pa.md) — clos
- [§38 — `PurgeOutboxPayloads.json` — échappement JSON incomplet + `DossierType.code` sans validation de format](038-purgeoutboxpayloads-json-echappement-json-incomplet-dossiert.md) — clos
- [§39 — Construction de diagrammes BPMN dans FlowForge — écart restant après §35](039-construction-de-diagrammes-bpmn-dans-flowforge-ecart.md) — clos
- [§47 — Édition du schéma des champs dynamiques — écran admin `DossierType`](047-edition-du-schema-des-champs-dynamiques-ecran.md) — clos
- [§48 — Activation du connecteur `http-rest` en rôle SERVICE](048-activation-du-connecteur-http-rest-en-role.md) — clos
- [§49 — Assainissement de la dette technique — reliquats `DEFERRED-FEATURES.md` §1](049-assainissement-de-la-dette-technique-reliquats-deferred.md) — clos
- [§50 — Nouveaux connecteurs hors V1 — `scheduler`, `smtp`, `document-storage`](050-nouveaux-connecteurs-hors-v1-scheduler-smtp-document.md) — clos
- [§51 — Corrélation par clé métier pour callbacks asynchrones — connecteur `flowable-callback`](051-correlation-par-cle-metier-pour-callbacks-asynchrones.md) — clos
- [§52 — Formulaires dynamiques rattachés aux tâches BPMN (`userTask`)](052-formulaires-dynamiques-rattaches-aux-taches-bpmn-usertask.md) — clos
- [§53 — `WebhookController` : un événement TRIGGER échoué peut bloquer les rejeux avec la même `Idempotency-Key`](053-webhookcontroller-un-evenement-trigger-echoue-peut-bloquer.md) — clos
- [§54 — 2ème revue de sécurité adversariale sur `connecteur-callback` — 4 nouveaux écarts (F5–F8)](054-2eme-revue-de-securite-adversariale-sur-connecteur.md) — clos
- [§55 — Nouveau kind de tâche générique — questions/choix utilisateur indépendants du dossier](055-nouveau-kind-de-tache-generique-questions-choix.md) — clos
- [§56 — Extension du serveur MCP au flux métier complet (connecteurs, association templates, questions de tâche)](056-extension-du-serveur-mcp-au-flux-metier.md) — clos
- [§62 — Nouveaux connecteurs `batch-import` (SFTP) et `kafka` — lot B1 de la file d'attente](062-nouveaux-connecteurs-batch-import-sftp-et-kafka.md) — clos
