# Jours ouvrables — document de travail

Issue : [#8](https://github.com/superjojo79/Myrmacta/issues/8) · Spec du domaine : [docs/specs/04-dossiers.md](../specs/04-dossiers.md) (échéance), [docs/specs/49-digital-mail-room.md](../specs/49-digital-mail-room.md) §7 · Points du registre : [§115](../decisions/115-echeances-de-dossier-rappel-escalade-et-minuteurs.md)

## Cadrage

- Problème et utilisateurs : un délai administratif (« 30 jours ouvrables ») ne peut pas s'exprimer. Les trois calculs de délai de dossier comptent des jours calendaires, week-ends et jours fériés compris. L'intégrateur qui pose une table de routage ou une brique, et l'administrateur qui règle le rappel d'un type, n'ont pas d'autre choix. Les agents qui traitent le dossier voient donc une échéance trop courte dès qu'un texte vise des jours ouvrables.
- Périmètre / hors-périmètre :
  - Dans le périmètre : une liste de jours fériés par tenant (Admin, `TENANT_ADMIN`), le week-end du tenant et son fuseau. Le choix « calendaire » (défaut) ou « ouvrable » se fait à chaque délai : sortie DMN `routageDelaiJours`, paramètres `delaiJours` et `rappelJours` de la brique « Calculer une échéance », rappel J-n du réglage d'échéance du type. Si ce rappel est ouvrable, l'escalade de la plateforme attend le prochain jour ouvrable après le terme.
  - Hors périmètre : SLA en heures, échéance de signature, validité d'un lien portail, rétention, jours fériés scolaires, demi-journées, calendriers différents selon le type de dossier, prorogation (« reporter un terme tombé un jour fermé »). Les dates déjà écrites dans les dossiers ne sont pas recalculées.
- Critères de succès (vérifiables) :
  - Le tenant choisit son week-end parmi trois formes : samedi-dimanche, vendredi-samedi, dimanche seul. Hypothèse nommée : le défaut est samedi-dimanche.
  - En mode ouvrable, on avance de N jours qui ne tombent ni sur ce week-end ni sur un férié du tenant. Le jour de départ ne compte pas. Avec le défaut, un vendredi + 1, sans férié le lundi, donne le lundi. Le même calcul en calendaire donne le samedi.
  - Un lundi présent dans la liste du tenant ne compte pas. Reculer de N (rappel) saute les mêmes jours.
  - « Aujourd'hui » est la date civile du fuseau du tenant. Le défaut est UTC : un tenant qui ne le change pas garde le comportement actuel. Le même fuseau vaut pour le calendaire et l'ouvrable.
  - Une action d'admin ajoute les jours fériés légaux fédéraux belges de l'année courante et de la suivante, en dates concrètes. L'admin retire ou ajoute. Aucun job ne complète la liste l'année d'après.
  - Le jour du terme reste dans les temps. Si le rappel du type est ouvrable, l'escalade part le prochain jour ouvrable après le terme (un vendredi n'escalade que le lundi, week-end samedi-dimanche, lundi non férié). S'il est calendaire, l'escalade part le lendemain calendaire. Le nombre de jours du rappel peut être 0 : le mode suffit.
  - Le fuseau, le week-end, la liste des fériés et le mode du rappel voyagent avec la promotion de configuration, comme le réglage d'échéance.
- Contraintes (stack, tenant, sécurité, MCP, documentation à mettre à jour) :
  - Une instance = un tenant. La liste est une donnée du schéma du tenant, pas une résolution par requête.
  - Domaine pur : le calendrier est un type du domaine `deadline`, sans Spring. Les trois appelants (routage, brique, job) passent par lui.
  - Défaut calendaire : pas de migration des échéances déjà stockées.
  - Pas de nouvel outil MCP : la promotion et l'écran d'admin suffisent, comme pour le réglage d'échéance.
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
| Quels jours forment le week-end ? | 1. Toujours samedi et dimanche. 2. Le samedi compte, seul le dimanche est fermé. 3. Le tenant choisit : samedi-dimanche, vendredi-samedi ou dimanche seul. | 1, écartée. | tranchée le 2026-11-06 : option 3. Défaut samedi-dimanche (hypothèse nommée). |
| Comment avance-t-on de N une fois le week-end connu ? | 1. N jours qui ne tombent ni sur le week-end du tenant ni sur un férié de sa liste. Le jour de départ ne compte pas, comme `plusDays`. 2. N jours calendaires, puis report du terme au prochain jour ouvrable s'il tombe un jour fermé. 3. N jours hors week-end seulement. Les fériés ne retirent aucun jour. | 1. | tranchée le 2026-11-06 : option 1. Le rappel recule de la même façon. |
| Où se choisit « ouvrable » ? | A. À chaque délai (sortie DMN, deux paramètres de brique, rappel du type), défaut calendaire. B. Un interrupteur pour tout le tenant. C. Un mode sur le type de dossier, suivi par le routage et la brique. | A. | tranchée le 2026-11-06 : option 1 (A). Le week-end et les fériés restent un réglage du tenant. |
| Quel jour est « aujourd'hui » ? | 1. UTC, comme aujourd'hui, pour le calendaire et l'ouvrable. 2. Fuseau du tenant, défaut UTC : rien ne change tant que l'admin ne le règle pas. 3. Fuseau du tenant, défaut `Europe/Brussels`. | 2. | tranchée le 2026-11-06 : option 2. Le fuseau vaut pour les deux modes. |
| Que préremplit-on pour la Belgique ? | 1. Rien : liste vide, saisie manuelle. 2. Une action ajoute les jours fériés légaux fédéraux de l'année courante et de la suivante, en dates concrètes. L'admin retire ou ajoute. Pas de rafraîchissement automatique l'année d'après. 3. Plusieurs jeux : fédéral, Flandre, Wallonie, Communauté germanophone, Bruxelles. | 2. | tranchée le 2026-11-06 : option 2. Dates concrètes, pas une règle perpétuelle. |
| Le lendemain de l'escalade suit-il le mode ouvrable ? | 1. Non : l'escalade part le lendemain calendaire de la date stockée (D-DEADLINE-ESCALATE). Le rappel a son propre choix. 2. Oui : si le rappel du type est en ouvrables, l'escalade attend le prochain jour ouvrable après le terme. | 1, écartée. | tranchée le 2026-11-06 : option 2. Le mode du rappel commande l'escalade, même si le nombre de jours vaut 0. |

## Hypothèses nommées, couvertes par la validation

- Le week-end par défaut est samedi-dimanche.
- Le minuteur BPMN ne suit pas l'escalade. `echeanceDepassee` reste le lendemain calendaire de la date stockée, à 00:00 dans le fuseau du tenant (aujourd'hui 00:00 UTC). Un rappel de type ouvrable peut donc escalader le lundi alors que le minuteur a déjà tiré le samedi.

## Conception

- Faits vérifiés dans le code ou les sources (fichier, méthode) : voir « Faits déjà dans le code ». Pas de conception tant que ce cadrage n'est pas validé.
- Composants touchés (domaine, ports, adaptateurs, frontend, MCP) : sans objet pour l'instant.
- Scénarios de test (unitaire, intégration, chaîne HTTP et sécurité, Playwright) : sans objet pour l'instant.

## Lots

| Lot | Contenu | Statut | PR |
|---|---|---|---|
| — | Découpé après la validation du cadrage. | à faire | — |

Prochaine action : validation du cadrage par l'utilisateur. Ensuite seulement, la conception.

## Preuves

| Date | Commande | Résultat |
|---|---|---|
| — | — | pas encore de code |

## Revue de clôture

| Constat | Sévérité | Correctif ou issue |
|---|---|---|
| — | — | sans objet |
