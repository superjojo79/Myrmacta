# §130 — Éditeur DMN : conversion 1.1 → 1.3 à l'ouverture

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] clos (issue [#68](https://github.com/superjojo79/Myrmacta/issues/68), vague `vague-vitrine`). Mandat cloud agent
2026-11-12 : option privilégiée = conversion à l'ouverture. Comportement :
[spec 25](../specs/25-introduction-dmn.md) § « Implémentation — version DMN ».

**Constat.** `dmn-js` n'ouvre que le DMN 1.3. Une table en namespace 1.1
(`http://www.omg.org/spec/DMN/20151101`) s'affiche vide, sans message — exemples du dépôt et grille
classpath, relevé en démo ([§127](127-demo-par-paquets-et-captures-du-site.md)).

**D-DMN-OPEN-CONVERT** — à l'ouverture dans l'éditeur intégré, le XML DMN 1.1 est converti en 1.3
(remplacement de l'attribut `xmlns` du modèle par `https://www.omg.org/spec/DMN/20191111/MODEL/`)
avant `importXML`. Même normalisation à l'écriture (brouillon UI, proposition MCP, déploiement)
pour que le stocké soit déjà lisible. Seul l'`xmlns` exact est réécrit (pas une URI citée dans une
cellule). Les commentaires XML placés avant la racine sont retirés : le validateur XSD de Flowable
refuse un document 1.3 qui a encore un commentaire avant `<definitions>` (`cvc-elt.1.a`) — y
compris un 1.3 déjà correctement namespacé. Le moteur acceptait déjà 1.1 et 1.3 ; on ne change pas
la sémantique des règles. Une construction propre au 1.1 qui ne validerait plus en 1.3 serait
refusée à l'écriture (accepté : les tables du dépôt et les imports clients ciblés sont des tables
simples).

**D-DMN-GENERATE-13** — toute génération (outil MCP `deploy_decision_table`, assistant de conception,
consignes skills / guide de conception) vise le DMN 1.3 et cite le namespace 1.3. Un XML 1.1 reçu
est accepté et normalisé, pas rejeté.

Écartés : migrer seulement les fichiers du dépôt (ne corrige pas un import client 1.1) ; message
d'erreur sans conversion (l'utilisateur ne peut pas ouvrir pour corriger) ; laisser le contournement
ponctuel de la démo ([§127](127-demo-par-paquets-et-captures-du-site.md)) comme seul remède.

**Impacte :** utilitaire domaine `DmnXmlVersions`, éditeur `DmnEditorCanvas`, chemins d'écriture
brouillon / MCP / déploiement, `DeployDecisionTableTool`, prompt assistant, skills
`myrmacta-conventions` / `security-review`, `GUIDE-CONCEPTION-BPMN`, spec 25, tests Vitest /
Playwright / unitaires domaine.
