# §115 — Échéances de dossier : rappel, escalade et minuteurs à expression

> **Statut :** clos · point de premier niveau · [registre](README.md)

Livré : lot 4 de la Digital Mail Room ([§113](113-digital-mail-room-courrier-dossier-et-fils.md), issue
[#217](https://github.com/superjojo79/Myrmacta/issues/217)), absorbe l'issue
[#186](https://github.com/superjojo79/Myrmacta/issues/186). Le cadrage du 2026-10-31 a retenu deux mécanismes, au choix
du concepteur : l'échéance portée par la plateforme (lot 4a), et le minuteur BPMN à expression (lot 4b).

**D-DEADLINE-FIELD** (tranché par l'utilisateur, 2026-10-31) — l'échéance d'un dossier est **un champ `DATE` de son
type**, désigné par un réglage du type :

- **Réglage :**
  - il est rangé à part du type (table `dossier_type_deadline`, API `/api/dossier-types/{code}/deadline`), comme le
    workflow « réponse » (D-MAILROOM-REPLY-WORKFLOW) ;
  - il porte le champ, le nombre de jours du rappel avant le terme (0 = pas de rappel) et le groupe d'escalade
    (facultatif) ;
  - le champ doit exister dans le type et être de type `DATE`, et le groupe doit exister.
- **Source de la date :** une saisie, un mapping de connecteur, ou la brique « Router le courrier ». Cette dernière écrit
  l'échéance calculée (`routageDelaiJours`) dans le champ désigné du type du dossier métier qu'elle ouvre, en plus du
  champ `echeance` du courrier.
- **Jours calendaires**, jour du terme compris, en UTC : le terme est dépassé le lendemain. Le calendrier de jours
  ouvrables est reporté (D-DEADLINE-CALENDAR).
- Écarté : réutiliser le SLA du type (délai en heures depuis la création). Il ignore l'échéance propre à chaque dossier,
  celle que la table de routage calcule.

**D-DEADLINE-ESCALATE** (tranché par l'utilisateur, 2026-10-31) — un job planifié parcourt les dossiers non clôturés des
types qui ont un réglage :

- **Rappel :** à partir de J-n, une notification (boîte de réception, §27) part vers les participants du dossier
  (assignés et candidats de ses process, comme l'alerte SLA).
- **Escalade :** dès le lendemain du terme :
  - les membres du groupe d'escalade sont prévenus (membres locaux et membres venus de l'IdP, D-GROUP-SOURCE) ;
  - **les tâches ouvertes** du process actif du dossier sont réaffectées au groupe d'escalade, comme une réaffectation
    manuelle (D-GROUP-REASSIGN) ;
  - une note de trace est versée au dossier, au nom de `system`.
- **Une seule fois :**
  - chaque palier est « pris » en base avant d'agir (table `dossier_deadline_alert`, clé dossier + palier + date) ;
  - deux exécutants concurrents ne notifient donc pas deux fois (même motif que l'alerte SLA, §79) ;
  - une échéance reportée ouvre de nouveaux paliers.
- **Hors périmètre :** les dossiers au statut `DRAFT`, clôturés ou archivés ; une valeur qui n'est pas une date ISO est
  ignorée.
- Sans groupe d'escalade, ou si le groupe a été supprimé depuis, l'escalade se limite à la note.

**D-DEADLINE-TIMER** (tranché par l'utilisateur, 2026-10-31 ; précisé à la conception du lot 4b) — un minuteur BPMN peut
porter une échéance calculée :

- La brique **« Calculer une échéance »** (`computeDeadlineTask`) lit, en paramètres littéraux, un champ date non sensible
  du dossier (`champEcheance`) ou un délai (`delaiJours`, repli si le champ est vide), et un rappel (`rappelJours`).
- Elle écrit trois variables : `echeance` (date, pour l'affichage), `echeanceRappel` et `echeanceDepassee` (instants ISO
  8601, 00:00 UTC ; dépassée = lendemain de l'échéance, comme D-DEADLINE-ESCALATE). Les deux instants sont **réservés**
  (`WorkflowVariables`) : ni un démarrage ni une complétion de tâche ne peut les poser, donc repousser le minuteur.
  `echeance` ne l'est pas : la revue de clôture a relevé que des configurations existantes utilisent déjà ce nom, et
  aucun minuteur ne le lit.
- Le `timeDate` d'un minuteur peut valoir **exactement** `${echeanceRappel}` ou `${echeanceDepassee}` :
  - c'est la troisième exception à la règle « aucune expression » (§69) ;
  - un nom nu ne peut ni appeler une méthode ni désigner un bean (aucun bean ne porte ces noms) ;
  - `timeDuration`, `timeCycle`, toute autre variable et toute autre forme restent refusés.
  - Le préfixe libre `echeance…` envisagé au cadrage est écarté : deux noms fixes suffisent et se réservent.
- Sans date ni délai, la brique échoue : un incident vaut mieux qu'un minuteur qui ne se déclenche jamais.
- Le minuteur en bordure d'une tâche fait la course « traité ou échu » de l'issue #186.

**D-DEADLINE-CALENDAR** (tranché par l'utilisateur, 2026-10-31) — le calendrier de jours ouvrables (week-ends, jours
fériés par tenant) est reporté à l'issue
[#270](https://github.com/superjojo79/Myrmacta/issues/270). Les délais sont en jours calendaires.

**Limite connue :** la promotion de configuration ne transporte pas encore le réglage d'échéance (même limite que
D-MAILROOM-REPLY-WORKFLOW).
