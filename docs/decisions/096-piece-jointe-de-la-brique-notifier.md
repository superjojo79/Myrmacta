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
(cohérent avec D-NOTIF-SOURCE-LITERAL et §75). Forme vérifiée au déploiement (400) et dans la palette. **Avec une pièce
jointe, `notificationTo` et `notificationChannels` (qui doit contenir `EMAIL`) doivent aussi être écrits dans le BPMN** : au
déploiement (400), dans la palette, et à l'exécution (le repli sur les variables est coupé, le canal est vérifié avant toute
lecture du document) — revue M1 et L5. Objet et message gardent la règle commune (attribut, sinon variable) : ils partent
au destinataire fixé par le concepteur. Un `notificationAttachmentName` sans `notificationAttachment` est refusé.

**D-NOTIF-ATTACH-REF-IN-OUTBOX** — l'outbox porte la **référence** (`storageRef`, nom, type), jamais les octets. Contrôle
complet à l'émission (transaction du process : 422, ou incident rejouable si la brique est asynchrone) ; à l'envoi, le relais
relit le contenu dans le stockage du tenant de la ligne et revérifie seulement le plafond. Document supprimé entre-temps :
échec de la ligne, nouvelles tentatives puis `DEAD`. Accepté : un élément **retiré du dossier** entre l'émission et l'envoi
(fenêtre d'une seconde, plus longue en cas de nouvelles tentatives) part quand même, le relais ne connaissant pas le dossier.

**D-NOTIF-ATTACH-MAX** ⚠ — plafond `app.notification.email.attachment-max-bytes`, défaut **7 Mio** (7 340 032 octets) :
environ 9,8 Mo une fois encodé en base64, sous la limite par défaut de Postfix (`message_size_limit` = 10 240 000 octets ;
revue L1, le défaut initial de 10 Mio la dépassait). Valeur nulle ou négative : refus au démarrage (revue I4). Pas de
variable d'environnement dédiée (liaison souple Spring : `APP_NOTIFICATION_EMAIL_ATTACHMENTMAXBYTES`).

**D-NOTIF-ATTACH-NAME** — nom du fichier : `notificationAttachmentName` (lettres, chiffres, espace, `. _ - ( ) '`,
100 caractères au plus, pas de point initial), sinon le libellé de l'élément, assaini (autre caractère → `_`) ; `.pdf`
ajouté s'il manque. Forme NFC, troncature par caractère (revue I2). Encodé par `MimeMessageHelper` (caractères non ASCII
vérifiés en test).

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

**Revue adversariale (2026-10-23, sous-agent, aucun High)** — aucune exfiltration trouvée hors du contrôle d'appartenance.
- **M1 (corrigé)** — sans `flowable:notificationTo`, le destinataire retombait sur la variable `notificationTo`, posée par un
  utilisateur (complétion, démarrage, MCP, callback, signal) : le PDF du dossier partait à l'adresse de son choix, sans
  passer par le journal d'audit des téléchargements. Voir D-NOTIF-ATTACH-LITERAL.
- **L1 (corrigé)** — plafond ramené à 7 Mio (D-NOTIF-ATTACH-MAX).
- **L5 (corrigé)** — contrôles entre attributs (canal `EMAIL`, destinataire, nom sans pièce jointe) au déploiement, dans la
  palette et à l'exécution ; le canal est vérifié avant la lecture du document.
- **I1 (corrigé)** — `lastError` de l'outbox ne recopie plus le message du stockage, qui cite la référence.
- **I2, I4 (corrigés)** — nom en NFC tronqué par caractère ; plafond ≤ 0 refusé au démarrage.
- **L2 (accepté)** — pendant un déploiement progressif ou après un retour arrière, un relais d'avant #183 ignore la clé
  `attachment` et envoie l'e-mail sans la pièce. Une instance par tenant : arrêter l'ancienne instance avant de démarrer la
  nouvelle (cas nominal) ; en cas de retour arrière, vider d'abord l'outbox des lignes `PENDING` portant une pièce jointe.
- **L3 (accepté)** — le contenu est lu en entier avant le contrôle de taille (émission et envoi) ; borné par le contrôle
  d'appartenance, qui passe avant la lecture, et par le plafond.
- **L4 (accepté)** — `SendNotificationUseCaseImpl` ne revérifie pas `EmailAttachment` ; seul `NotificationDispatchTask` en
  construit un (vérifié sur tous les producteurs de `NotificationEvent`, REST `/send` compris).
- **I5 (hors périmètre)** — le concepteur de parcours (`ParcoursCompiler`) ne propose pas la pièce jointe.
- **P1 (préexistant, issue [#201](https://github.com/superjojo79/FlowForge/issues/201))** — `AttachGeneratedDocumentTask` rattache comme `PRODUCED_DOC` tout objet du tenant
  désigné par `generatedDocumentId`, variable modifiable par un utilisateur si une tâche s'intercale entre génération et
  rattachement. Une référence connue d'un autre dossier deviendrait alors un document produit légitime, joignable (et
  signable, §72). Atténué : références aléatoires jamais exposées (`DossierElementView` masque `storageRef`). D-NOTIF-ATTACH-PRODUCED-DOC
  repose sur cette hypothèse.
- **P2 (préexistant)** — avec `dossierField:email` et une brique asynchrone, l'adresse est relue à l'envoi (§93) : le PDF
  suit une adresse modifiée entre-temps par un éditeur du dossier.
- **Phase 9 (relecture propre)** — acceptés : un autre document produit du même dossier, un élément retiré avant l'envoi.

**Impacte :** `docs/specs/09-notifications.md`, `docs/guides/GUIDE-CONCEPTION-BPMN.md` §6c, `docs/guides/GUIDE-UTILISATEUR.md`
(palette), `exemples/autorisation-riverain/` (BPMN, `questions.json`, README, DIFFICULTES §2), `application.yml`
(`app.notification.email.attachment-max-bytes`). Aucune migration, aucune route HTTP, golden OpenAPI inchangé.
