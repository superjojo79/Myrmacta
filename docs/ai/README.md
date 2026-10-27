# Document de travail d'une feature

`docs/ai/` ne contient que ce gabarit et, pendant qu'une feature est en cours, son document de travail
`AAAA-MM-JJ-<feature>.md`. Le document sert à reprendre la feature dans une autre session ou un autre outil
(Claude Code, Cursor). Il est supprimé dans la PR qui clôt la feature : ce qui reste vrai du système remonte dans
la spec du domaine (`docs/specs/`), les décisions vont au registre (`docs/decisions/`). Cycle : skill
`myrmacta-feature` ; décision : [registre §107](../decisions/107-allegement-des-skills-ai-devkit.md).

Copier le gabarit ci-dessous et le remplir au fil du cycle. Une section sans objet reste avec « sans objet ».

```markdown
# <Feature> — document de travail

Issue : #N · Spec du domaine : docs/specs/NN-*.md · Points du registre : §N

## Cadrage

- Problème et utilisateurs :
- Périmètre / hors-périmètre :
- Critères de succès (vérifiables) :
- Contraintes (stack, tenant, sécurité, MCP, documentation à mettre à jour) :
- Validé par l'utilisateur le :

## Questions ouvertes

| Question | Options | Recommandation | Statut (ouverte, reportée, hypothèse, tranchée → §N) |
|---|---|---|---|

## Conception

- Faits vérifiés dans le code ou les sources (fichier, méthode) :
- Composants touchés (domaine, ports, adaptateurs, frontend, MCP) :
- Scénarios de test (unitaire, intégration, chaîne HTTP et sécurité, Playwright) :

## Lots

| Lot | Contenu | Statut (à faire, en cours, livré) | PR |
|---|---|---|---|

Prochaine action :

## Preuves

| Date | Commande | Résultat |
|---|---|---|

## Revue de clôture

| Constat | Sévérité | Correctif ou issue |
|---|---|---|
```
