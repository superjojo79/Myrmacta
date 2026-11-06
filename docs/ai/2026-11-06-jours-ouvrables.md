# Jours ouvrables — document de travail

Issue : [#8](https://github.com/superjojo79/Myrmacta/issues/8) · Spec du domaine : [docs/specs/04-dossiers.md](../specs/04-dossiers.md) (échéance), [docs/specs/49-digital-mail-room.md](../specs/49-digital-mail-room.md) §7 · Points du registre : [§115](../decisions/115-echeances-de-dossier-rappel-escalade-et-minuteurs.md)

## Cadrage

- Problème et utilisateurs : un délai administratif (« 30 jours ouvrables ») ne peut pas s'exprimer. Les trois calculs de délai de dossier comptent des jours calendaires, week-ends et jours fériés compris. L'intégrateur qui pose une table de routage ou une brique, et l'administrateur qui règle le rappel d'un type, n'ont pas d'autre choix. Les agents qui traitent le dossier voient donc une échéance trop courte dès qu'un texte vise des jours ouvrables.
- Périmètre / hors-périmètre :
  - Dans le périmètre, si le cadrage est validé : une liste de jours fériés par tenant (Admin, `TENANT_ADMIN`), et le choix « calendaire » (défaut, comportement actuel) ou « ouvrable » là où un nombre de jours est déjà interprété — sortie DMN `routageDelaiJours`, paramètres `delaiJours` et `rappelJours` de la brique « Calculer une échéance », rappel J-n du réglage d'échéance du type.
  - Hors périmètre : SLA en heures, échéance de signature, validité d'un lien portail, rétention, jours fériés scolaires, demi-journées, calendriers différents selon le type de dossier, prorogation judiciaire (« si le terme tombe un samedi, un dimanche ou un férié, reporter »). Les dates déjà écrites dans les dossiers ne sont pas recalculées.
- Critères de succès (vérifiables) :
  - Un vendredi + 1 jour ouvrable, sans férié le lundi, donne le lundi ; le même calcul en calendaire donne le samedi.
  - Un jour présent dans la liste du tenant ne compte pas, même un lundi.
  - Sans option « ouvrable », `plusDays` UTC reste le résultat d'aujourd'hui (les process et tables déjà déployés ne bougent pas).
  - Le jour du terme reste dans les temps : l'escalade part le lendemain, comme [D-DEADLINE-ESCALATE](../decisions/115-echeances-de-dossier-rappel-escalade-et-minuteurs.md).
  - La liste des fériés et le mode du rappel voyagent avec la promotion de configuration, comme le réglage d'échéance.
- Contraintes (stack, tenant, sécurité, MCP, documentation à mettre à jour) :
  - Une instance = un tenant. La liste est une donnée du schéma du tenant, pas une résolution par requête.
  - Domaine pur : le calendrier est un type du domaine `deadline`, sans Spring. Les trois appelants (routage, brique, job) passent par lui.
  - Défaut calendaire : pas de migration des échéances déjà stockées.
  - Pas de nouvel outil MCP : la promotion et l'écran d'admin suffisent, comme pour le réglage d'échéance. À confirmer au cadrage.
  - Documentation : spec 04 et 49, guide utilisateur (écran fériés + libellé du rappel), guide de conception BPMN (brique et sortie DMN), identifiant au registre. Pas de nouvelle variable d'environnement.
- Validé par l'utilisateur le : en attente. Le mandat « roadmap complète » ne couvre pas cette issue.

## Faits déjà dans le code

- `RouteMailDelegate.deadline` : `LocalDate.ofInstant(clock, UTC).plusDays(routageDelaiJours)`. La valeur vient d'une sortie DMN, pas d'une colonne.
- `ComputeDeadlineDelegate` : même `plusDays` pour `delaiJours` ; `rappelJours` recule avec `minusDays` ; `echeanceDepassee` est le lendemain 00:00 UTC.
- `EvaluateDeadlinesUseCaseImpl` : « aujourd'hui » est la date UTC ; le rappel part quand `today` n'est plus avant `échéance - reminderDays` ; l'escalade part quand `today` est après l'échéance.
- Le réglage du type (`DeadlineSetting`, écran Admin → Types → Échéance) porte le champ, `reminderDays` et le groupe. Il ne porte pas de mode de décompte.
- [D-DEADLINE-CALENDAR](../decisions/115-echeances-de-dossier-rappel-escalade-et-minuteurs.md) a reporté ce sujet. Il ne choisit pas la règle de décompte.

## Questions ouvertes

| Question | Options | Recommandation | Statut |
|---|---|---|---|
| Que compte un jour ouvrable, et comment avance-t-on de N ? | 1. Samedi et dimanche exclus, plus les dates de la liste du tenant. `aujourd'hui + N` avance de N jours ouvrables (le jour de départ ne compte pas, comme `plusDays`). 2. Le samedi compte ; seuls le dimanche et les fériés sont exclus (sens de certains textes belges), avec prorogation du terme. 3. Week-end configurable par tenant (sam–dim, ven–sam ou dimanche seul). | 1. C'est le sens de l'issue (« week-ends et jours fériés »). L'option 2 est un autre outil juridique. L'option 3 dépasse le besoin tant qu'un tenant ne le demande pas. | ouverte — première question |
| Où se choisit « ouvrable » ? | A. À chaque délai (sortie DMN, deux paramètres de brique, rappel du type), défaut calendaire. B. Un interrupteur pour tout le tenant. C. Un mode sur le type de dossier, suivi par le routage et la brique. | A, comme le texte de l'issue. B empêcherait un délai calendaire et un délai ouvrable dans le même tenant. | en attente de la première |
| Quel jour est « aujourd'hui » ? | UTC (actuel). Fuseau du tenant, défaut UTC. Fuseau du tenant, défaut `Europe/Brussels`. | À poser ensuite. Un fuseau autre qu'UTC change le jour autour de minuit pour les délais calendaires déjà en production. | en attente de la première |
| Que préremplit-on pour la Belgique ? | Rien (liste vide, saisie manuelle). Jours fériés légaux fédéraux de l'année courante et de la suivante, que l'admin peut retirer. Jeux par Région ou Communauté. | À poser ensuite. Les fêtes régionales et communales ne tiennent pas dans une seule liste. | en attente de la première |
| Le rappel J-n et le « lendemain » de l'escalade suivent-ils le même mode ? | Rappel au choix, escalade toujours le lendemain calendaire de la date stockée. Les deux en ouvrables. | À poser ensuite. Hypothèse de travail : l'escalade ne change pas (D-DEADLINE-ESCALATE). Un terme calculé en ouvrables tombe déjà un jour ouvrable ; le lendemain calendaire peut être un samedi. | en attente de la première |

## Conception

- Faits vérifiés dans le code ou les sources (fichier, méthode) : voir « Faits déjà dans le code ». Pas de conception tant que la première question est ouverte.
- Composants touchés (domaine, ports, adaptateurs, frontend, MCP) : sans objet pour l'instant.
- Scénarios de test (unitaire, intégration, chaîne HTTP et sécurité, Playwright) : sans objet pour l'instant.

## Lots

| Lot | Contenu | Statut | PR |
|---|---|---|---|
| — | Découpé après la validation du cadrage. | à faire | — |

Prochaine action : réponse à la question « que compte un jour ouvrable, et comment avance-t-on de N ? ».

## Preuves

| Date | Commande | Résultat |
|---|---|---|
| — | — | pas encore de code |

## Revue de clôture

| Constat | Sévérité | Correctif ou issue |
|---|---|---|
| — | — | sans objet |
