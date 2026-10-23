# §94 — Incidents visibles et rejouables sur le dossier

> **Statut :** clos · point de premier niveau · ancien `TODO.md` §94 · [registre](README.md)

[x] livré (branche `feature-incidents-visibles-dossier`). Relevé par la recette de l'exemple riverain (PR #175, cas R6 ;
`exemples/autorisation-riverain/DIFFICULTES.md` §11) : une brique **asynchrone** en échec partait en *dead letter* sans
que rien ne le montre — ni la fiche dossier, ni le diagramme (`failedIds` ne retenait que les erreurs BPMN) — et
sans moyen de la rejouer. Docs SDD : [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-10-18-feature-incidents-visibles-dossier.md),
[design](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/design/2026-10-18-feature-incidents-visibles-dossier.md),
[planning](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/planning/2026-10-18-feature-incidents-visibles-dossier.md),
[implementation](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/implementation/2026-10-18-feature-incidents-visibles-dossier.md),
[testing](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/testing/2026-10-18-feature-incidents-visibles-dossier.md).

Décisions (tranchées par l'utilisateur, 2026-10-18) : périmètre « visible + rejouable + supervision » ; deux états
(`RETRYING` : job ou minuteur en échec avec tentatives restantes ; `INCIDENT` : dead letter) ; MCP en lecture seule.

**D-INCIDENT-MESSAGE** — le message de l'exception (tronqué à 300 caractères) n'est renvoyé qu'à `TENANT_ADMIN` /
`WORKFLOW_ADMIN` ; il peut contenir des données métier selon la brique. Jamais par MCP, jamais dans l'audit.

**D-INCIDENT-DIAGRAM** — les deux états marquent l'étape en erreur (et active) dans le diagramme ; le bandeau les
distingue.

**D-INCIDENT-RETRY-SCOPE** — rejeu par `POST /api/workflow-links/{id}/incidents/{jobId}/retry` seulement : le job doit
appartenir à l'exécution du lien, dans le tenant (sinon 404) et être en dead letter (sinon 409). 3 tentatives rendues
(défaut du moteur). Journalisé (`SECURITY_AUDIT`, `event=incident_retried`, sans message). Pas de fenêtre de
confirmation native (bloquée par certains navigateurs, recette du 2026-10-18).

**D-INCIDENT-SCAN** — `GET /api/admin/incidents` lit au plus les 1 000 échecs les plus récents du tenant, rattachés à un
dossier, et paginés en mémoire ; `truncated` signale la borne. Les jobs restent la source de vérité (pas de table).

**Revue adversariale (2026-10-19, sous-agent, aucun High) — corrigée :**
- **M1** — un job sans instance de process (minuteur de démarrage) faisait tomber `/api/admin/incidents` en 500 : ignoré.
- **M2** — l'échec d'un sous-process appelé (`callActivity`) était invisible : rapporté à l'instance racine du lien et à
  l'étape d'appel du diagramme (« Appel › étape du sous-process »), rejouable depuis le dossier.
- **L1/L2** — rejeu sans effet (exécution disparue) ou concurrent : 409 au message neutre (traduit dans l'interface) au
  lieu de 204 trompeur ou de 500.
- **L4** — tri départagé par `jobId`, `truncated` calculé sur une lecture de 1 001, liens chargés en une requête `IN`.
- **L5** — l'audit `incident_retried` n'est écrit qu'après le commit.
- **L6** — bandeau en région nommée (plus d'alerte réannoncée), boutons « Rejouer » nommés d'après l'étape, retour à la page
  précédente quand un rejeu vide la dernière, réponse périmée d'un autre lien ignorée ; relectures à 5 s et 15 s après un
  rejeu (relevé en recette : l'état « nouvelle tentative prévue » restait affiché).
- **Info** — 204 documenté, `@PreAuthorize` sur `/api/admin/incidents`, port système séparé pour MCP (vue avec
  `workflowLinkId`), un simple lecteur du dossier reçoit une liste vide (plus de refus journalisé à chaque fiche),
  troncature sûre des caractères Unicode, pluriel des tentatives.
- **L3 (acté, non modifié)** — le message est montré à `WORKFLOW_ADMIN` comme à `TENANT_ADMIN` (décision du 2026-10-18),
  alors que les champs sensibles ne sont visibles que de `TENANT_ADMIN`. Risque accepté : les messages des briques
  FlowForge ne reprennent pas de valeur de champ (« Notifier » nomme la source) ; un message d'exception tierce
  (connecteur, JDBC) pourrait en contenir. À reconsidérer si un cas réel apparaît.

**Impacte :** `docs/guides/GUIDE-UTILISATEUR.md` (§0 rôles, §3 « Suivre l'exécution »), `docs/guides/GUIDE-CONCEPTION-BPMN.md` (§6c),
`docs/specs/15-serveur-mcp.md`, `openapi/openapi.yaml`, `CHANGELOG.md`. Aucune migration, aucune variable
d'environnement. Suivi : statut de `DEFERRED-FEATURES.md` §7 et de DIFFICULTES §11 une fois la PR #175 mergée.
