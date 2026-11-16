# §147 — Libellé citoyen des tâches portail

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] clos (issue [#120](https://github.com/superjojo79/Myrmacta/issues/120)). Mandat cloud agent 2026-11-16.
Comportement : [spec 26](../specs/26-portail-externe.md) ; conception : [GUIDE-CONCEPTION-BPMN](../guides/GUIDE-CONCEPTION-BPMN.md) §3ter.

**Constat.** Le nom de la tâche BPMN est montré tel quel au demandeur (objet et corps de l'e-mail d'invitation,
page du portail). Pour `exemples/autorisation-riverain`, le citoyen lit « Compléments du demandeur (portail) »
— un libellé pensé pour l'agent. Relevé au tournage de la capsule C5 ([§143](143-scene-riverain-capsules-c5-c6.md)).

**Options.**
1. Renommer la tâche dans l'exemple seulement (correctif local).
2. Un libellé destiné au tiers, distinct du nom de la tâche (`flowable:portalTitle`), avec repli sur le nom ;
   documenté dans GUIDE-CONCEPTION-BPMN.

**D-PORTAL-TITLE** — option 2 :

1. `flowable:portalTitle` facultatif sur une tâche externe (`portalRecipient` requis) : texte littéral, une ligne,
   200 caractères au plus ;
2. e-mail d'invitation et réponse `taskName` du portail utilisent ce titre ; sinon le `name` BPMN (comportement
   historique) ;
3. le `name` BPMN reste le libellé interne (listes de tâches, supervision) ;
4. la démo traduit `flowable:portalTitle` comme `name`/`label` via `en/names.json` (D-DEMO-LOCALE-FILES) ;
5. écarté pour ce lot : option 1 seule (ne règle pas les autres processus) ; écarté : remplacer `name` par le
   titre citoyen (perdrait le libellé agent).

**Impacte :** `PortalTaskDefinition`, `FlowableBpmnParserAdapter`, `SendPortalInvitesUseCaseImpl`,
`GetPortalTaskUseCaseImpl`, `DemoExample`, GUIDE-CONCEPTION-BPMN §3ter,
`exemples/autorisation-riverain`. Capsules C5 / S13 : sortie non versionnée ([§127](127-demo-par-paquets-et-captures-du-site.md)) ;
régénération locale (`scripts/video.sh capsule c05-portail`) après déploiement.
