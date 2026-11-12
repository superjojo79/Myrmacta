# §129 — Libellé lisible d'un dossier créé par un déclencheur

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] clos (issue [#66](https://github.com/superjojo79/Myrmacta/issues/66), vague `vague-vitrine`). Mandat cloud agent
2026-11-12. Comportement : [spec 06](../specs/06-connecteurs.md) § « Implémentation — libellé à la création par
trigger » ; [spec 04](../specs/04-dossiers.md) § « Implémentation — libellé d'un dossier créé par trigger ».

**Constat.** Un dossier créé par `ConnectorTriggerRuntime` recevait le libellé `trigger:<uuid>` (même valeur que
`createdBy`). Ce texte technique apparaissait dans les listes, la supervision et les tâches (captures #55, S1/S6).

**D-TRIGGER-LABEL** — à la création, le libellé est dérivé des champs du dossier, pas de l'identifiant d'instance :

1. premier champ non vide parmi `objet`, puis `subject`, puis `expediteur`, puis `from` (valeur texte, espaces
   extrêmes retirés) ;
2. à défaut : `{dossierTypeCode} — {yyyy-MM-dd}` en UTC (horloge du runtime) ;
3. tronqué à 200 caractères (même borne que `RouteMailDelegate` pour un dossier métier issu du courrier).

`createdBy` reste `trigger:<instanceId>` (audit, autorisation §12). Aucune reprise des dossiers déjà persistés.
Gabarit configurable sur le type ou le connecteur : reporté (hors #66).

Écarté : laisser `trigger:<uuid>` comme libellé ; gabarit FreeMarker/JSONata dès ce lot (coût et surface sans besoin
démo immédiat — les connecteurs mail renseignent déjà `objet`).

**Impacte :** `ConnectorTriggerRuntimeImpl`, `TriggerDossierLabel`, tests unitaires associés, specs 04 et 06.
