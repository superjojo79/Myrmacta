# §120 — Calendrier de jours ouvrables des échéances

> **Statut :** clos · **Section :** [Décisions actées › Opérationnel](section-decisions-operationnel.md) · [registre](README.md)

> **Complété par [§121](121-calendrier-saisie-sources-ics.md)** : la liste plate de dates (D-CAL-HOLIDAYS) devient des saisies
> et des sources.

**Contexte (issue #8).** D-DEADLINE-CALENDAR (§115) laissait les délais en jours calendaires. Le cadrage du 2026-11-06
fixe le week-end, le décompte, l'endroit où le mode se choisit, le fuseau, les fériés belges et l'escalade.

**D-CAL-WEEKEND** — le tenant choisit `SATURDAY_SUNDAY` (défaut), `FRIDAY_SATURDAY` ou `SUNDAY_ONLY`.

**D-CAL-COUNT** — un délai ouvrable avance ou recule de N jours qui ne sont ni le week-end ni un jour férié. Le jour
de départ ne compte pas. Zéro jour reste sur la date de départ. Un nombre négatif est refusé. Au-delà de 8 000 pas,
le calcul échoue (plan de routage refusé, brique en incident).

**D-CAL-SITES** — le mode se choisit à chaque délai, calendaire par défaut :

- sortie DMN `routageDelaiOuvrable` (`OUVRABLE`, `BUSINESS`, `true` ; `CALENDAIRE`, `CALENDAR`, `false` ou absent ;
  toute autre valeur refuse le plan) ;
- attributs `delaiOuvrable` et `rappelOuvrable` de la brique « Calculer une échéance » (`CALENDAIRE` ou `OUVRABLE`) ;
- `reminderDayCount` du réglage d'échéance du type, et de son rappel J-n.

**D-CAL-ZONE** — « aujourd'hui » est la date civile du fuseau IANA du tenant, 64 caractères au plus, canonisé par
`ZoneId`. Sans ligne enregistrée : UTC. Les instants `echeanceRappel` et `echeanceDepassee` sont le minuit de ce fuseau.

**D-CAL-HOLIDAYS** — dates concrètes, uniques, 400 au plus. L'action d'administration ajoute les dix jours fériés
fédéraux belges de l'année civile courante (dans le fuseau) et de la suivante, par fusion idempotente. Pas de règle
perpétuelle, pas de jeu régional, pas de rafraîchissement automatique.

**D-CAL-ESCALATE** — si le rappel du type est ouvrable, l'escalade de la plateforme attend le prochain jour ouvrable
après le terme. Entre les deux, ni second rappel ni escalade. En mode calendaire, l'escalade reste le lendemain
(`today` strictement après le terme). Le terme lui-même reste dans les temps. Un rappel de zéro jour ne change pas
cette règle.

**D-CAL-TIMER** — `echeanceDepassee` reste le lendemain calendaire à minuit dans le fuseau du tenant. Un rappel
ouvrable peut donc escalader le lundi alors que le minuteur a déjà sonné le samedi.

**D-CAL-PROMO** — l'artefact `CALENDAR` (clé fixe `tenant`) est ajouté en fin de format 2. Un bundle ancien sans
cet artefact s'importe. Un export nouveau le contient toujours, y compris les défauts. L'absence de ligne et les
défauts explicites se comparent égaux et n'insèrent rien. `reminderDayCount` absent d'un ancien JSON d'échéance
vaut `CALENDAR`.

**D-CAL-MCP** — aucun outil MCP nouveau.

**Hors périmètre.** Heures d'engagement, délais de signature, durée de vie d'un lien de portail, rétention, vacances
scolaires, demi-journées, calendrier par type, prorogation judiciaire. Les dates déjà stockées ne sont pas recalculées.
Les process existants restent calendaires tant qu'on ne les marque pas ouvrables.

**Impacte.** `BusinessCalendar`, `TenantCalendar`, changeset 046, `DeadlineAlertJob`, `RouteMailDelegate`,
`ComputeDeadlineDelegate`, palette, API `/api/tenant/calendar`, promotion, specs 04 et 49, guides utilisateur et BPMN.
