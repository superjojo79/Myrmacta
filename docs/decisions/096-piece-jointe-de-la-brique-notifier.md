# §96 — Pièce jointe de la brique « Notifier »

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] livré (issue [#183](https://github.com/superjojo79/FlowForge/issues/183), lot `notifier-piece-jointe`). Relevé par
l'exemple [`exemples/autorisation-riverain/`](../../exemples/autorisation-riverain/DIFFICULTES.md) (§2) : le PDF de décision
était généré et joint au dossier, mais un agent devait l'envoyer lui-même. Docs SDD :
[requirements](https://github.com/superjojo79/FlowForge/blob/555a692/docs/ai/requirements/2026-10-23-feature-notifier-piece-jointe.md),
[design](https://github.com/superjojo79/FlowForge/blob/555a692/docs/ai/design/2026-10-23-feature-notifier-piece-jointe.md),
[planning](https://github.com/superjojo79/FlowForge/blob/555a692/docs/ai/planning/2026-10-23-feature-notifier-piece-jointe.md).
Les choix marqués ⚠ ont été faits sans arbitrage de l'utilisateur : à relire.

**Contrat.** `flowable:notificationAttachment="variable:<nom>|dossierField:<champ>"` (paramètre `SOURCE_REF` du catalogue,
`VERSION` 2) et `flowable:notificationAttachmentName` (facultatif) sur la brique « Notifier ». Spec :
[`09-notifications.md`](../specs/09-notifications.md) (§ Implémentation — pièce jointe) ; guide :
[`GUIDE-CONCEPTION-BPMN.md`](../guides/GUIDE-CONCEPTION-BPMN.md) §6c.

**D-NOTIF-ATTACH-PRODUCED-DOC** ⚠ — seul un élément `PRODUCED_DOC` **du dossier courant** dont la référence de stockage est
la valeur résolue peut être joint, et seulement un PDF (signature `%PDF-`) ; même règle que la signature électronique
(§72). La variable `generatedDocumentId` peut être posée par un utilisateur : sans ce contrôle, elle désignerait le document
d'un autre dossier ou un objet quelconque du stockage. Écarté : accepter une pièce **déposée** (`ATTACHMENT` : statut
antivirus, données d'un tiers renvoyées par e-mail) ; accepter toute référence du tenant (exfiltration). Accepté : une
variable posée à la complétion peut désigner un **autre document produit du même dossier** (même périmètre d'accès).

**D-NOTIF-ATTACH-LITERAL** — les deux attributs ne sont lus que dans le BPMN déployé, jamais depuis une variable de même nom
(cohérent avec D-NOTIF-SOURCE-LITERAL et §75). Forme vérifiée au déploiement (400) et dans la palette.

**D-NOTIF-ATTACH-REF-IN-OUTBOX** — l'outbox porte la **référence** (`storageRef`, nom, type), jamais les octets. Contrôle
complet à l'émission (transaction du process : 422, ou incident rejouable si la brique est asynchrone) ; à l'envoi, le relais
relit le contenu dans le stockage du tenant de la ligne et revérifie seulement le plafond. Document supprimé entre-temps :
échec de la ligne, nouvelles tentatives puis `DEAD`. Accepté : un élément **retiré du dossier** entre l'émission et l'envoi
(fenêtre d'une seconde, plus longue en cas de nouvelles tentatives) part quand même, le relais ne connaissant pas le dossier.

**D-NOTIF-ATTACH-MAX** ⚠ — plafond `app.notification.email.attachment-max-bytes`, défaut **10 Mio**, sous la limite courante
des relais SMTP une fois encodé en base64. Pas de variable d'environnement dédiée (liaison souple Spring :
`APP_NOTIFICATION_EMAIL_ATTACHMENTMAXBYTES`).

**D-NOTIF-ATTACH-NAME** — nom du fichier : `notificationAttachmentName` (lettres, chiffres, espace, `. _ - ( ) '`,
100 caractères au plus, pas de point initial), sinon le libellé de l'élément, assaini (autre caractère → `_`) ; `.pdf`
ajouté s'il manque. Encodé par `MimeMessageHelper` (caractères non ASCII vérifiés en test).

**D-NOTIF-ATTACH-EMAIL-ONLY** — une pièce jointe sans le canal `EMAIL` est une erreur de validation, jamais ignorée en
silence ; `WEBHOOK` et `BROWSER` ne la transportent pas.

**D-NOTIF-ATTACH-SMTP-CONNECTOR** ⚠ — le connecteur `smtp` n'est **pas** étendu : ses entrées (`connectorInput`) sont des
variables d'exécution sans dossier de rattachement vérifiable. La brique « Notifier » est le chemin supporté ; à rouvrir si
un e-mail calculé avec pièce jointe devient nécessaire.

**D-NOTIF-ATTACH-SINGLE** — une seule pièce jointe par brique.

**Exemple riverain.** Les e-mails « accordée » et « refusée » joignent le PDF ; la tâche « Transmettre le document de
décision » et son questionnaire sont supprimés ; le BPMN régénéré est couvert par `FlowableBpmnParserAdapterShippedProcessesTest`,
étendu à `exemples/`. La recette R1–R5 est à rejouer sur une instance (non faite : pas d'instance ni de relais SMTP dans le
bac à sable).

**Revue (phase 9, sans sous-agent)** — aucun défaut bloquant ; les deux acceptations ci-dessus (autre document du même
dossier, élément retiré avant l'envoi) sont consignées.

**Impacte :** `docs/specs/09-notifications.md`, `docs/guides/GUIDE-CONCEPTION-BPMN.md` §6c, `docs/guides/GUIDE-UTILISATEUR.md`
(palette), `exemples/autorisation-riverain/` (BPMN, `questions.json`, README, DIFFICULTES §2), `application.yml`
(`app.notification.email.attachment-max-bytes`). Aucune migration, aucune route HTTP, golden OpenAPI inchangé.
