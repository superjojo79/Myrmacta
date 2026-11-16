# §141 — Visualisation après fin de processus : chemin parcouru et lien le plus récent

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] clos (issue [#103](https://github.com/superjojo79/Myrmacta/issues/103), mandat cloud agent
2026-11-15). Comportement : [spec 10](../specs/10-visualisation-workflow.md) § « Sélecteur de run »
et [spec 04](../specs/04-dossiers.md) § « Impact sur la visualisation ».

**Constat.** Une fois le processus terminé, la fiche dossier affichait « Diagramme indisponible »
et « Pas encore d'activité », alors que la liste des workflows montrait encore le lien
(`PROVISIONNE`, dates). Cause : `UpdateWorkflowLinkStatusUseCaseImpl` remet
`dossier.currentWorkflowLinkId` à `null` sur un `StatusOutcome` terminal (pour permettre un nouveau
démarrage) ; le SPA ne chargeait la visualisation que via ce champ, et le viewer traitait l'absence
de lien courant comme « indisponible ». L'adaptateur Flowable lisait déjà l'historique
(`requireProcess` runtime puis historic) — le trou était côté fiche, pas côté moteur.

**D-VIZ-ENDED** — pour une instance terminée :

1. le diagramme expose le XML BPMN, `activeIds` vide, `completedIds` = activités historiques
   terminées (chemin parcouru), `failedIds` selon les raisons d'erreur déjà en place ;
2. l'historique métier reste celui de **D-HISTORY-SIGNIFICANT** (§131) ;
3. le SPA affiche par défaut le workflow **actif** (`currentWorkflowLinkId` ou lien sans
   `endedAt`) ; à défaut, le lien **le plus récent** (plus grand `sequence` ayant un
   `processInstanceId`) — si la liste paginée (sequence ASC) a plusieurs pages, la dernière
   page est lue pour ce fallback sans changer la page affichée ;
4. la liste des workflows de la fiche est un **sélecteur** : choisir une entrée recharge
   diagramme, historique et tâches de ce `linkId` ;
5. l'authz diagramme/historique reste **participant ou admin** (inchangé) — un simple
   lecteur du dossier qui n'a pas participé voit la liste mais pas la visualisation.

Écarté : conserver `currentWorkflowLinkId` après clôture (bloquerait un second démarrage sans
changement métier) ; ne montrer la visualisation que pour un lien encore actif (perd l'audit
métier et les capsules #58) ; élargir l'authz aux non-participants (hors bug #103).

**Impacte :** `frontend/src/stores/dossier.ts`, `WorkflowLinksList`, `DossierDetailView`,
`FlowableWorkflowVisualizationAdapter` (test historic), `WorkflowApiTest`, Playwright pilotage,
spec 10, guide utilisateur § fiche dossier.
