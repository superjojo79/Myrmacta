# §149 — Expéditeur SMTP configurable (MAIL_FROM)

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] clos (2026-11-17, issue [#134](https://github.com/superjojo79/Myrmacta/issues/134)).

**Constat.** `SmtpEmailAdapter` ne posait aucun en-tête `From`. Tous les e-mails sortants passent par lui (outbox de
notifications, connecteur `smtp`, invitations portail et signature). JavaMail retombait alors sur
`utilisateur@nom-du-poste`, en `From` comme en expéditeur d'enveloppe (`Return-Path`). Le nom du poste de tournage
apparaissait dans Mailpit sur les capsules C5, C13 et L1. En production : fuite du nom du serveur et rejet probable
par SPF ou DMARC.

**Décision (D-MAIL-FROM).**

- Un seul expéditeur pour toute l'application : `app.mail.from`, variable `MAIL_FROM`. Il est posé en `From` par
  `SmtpEmailAdapter` et en enveloppe par `spring.mail.properties.mail.smtp.from` (même valeur).
- Adresse nue (`noreply@example.org`), sans nom affiché ni liste : la même valeur sert à la commande SMTP
  `MAIL FROM`, qui n'accepte qu'une adresse. Valeur invalide : démarrage refusé, avec un message qui nomme `MAIL_FROM`.
- **Défaut : `noreply@myrmacta.invalid`** (choix de l'utilisateur, 2026-11-17). Le domaine réservé `.invalid`
  (RFC 2606) démarre partout sans configuration et ne fuit rien, mais un vrai relais SMTP le refusera : un
  déploiement qui envoie des e-mails doit renseigner `MAIL_FROM`. Écartés : `noreply@localhost` (passe pour une
  adresse valide) et l'obligation sans défaut (casse le développement et les déploiements existants).

**Hors périmètre.** Un expéditeur par tenant, un nom affiché (`Myrmacta <noreply@…>`) et un `Reply-To` : à rouvrir
si un client le demande.

**Tests.** `SmtpEmailAdapterTest` (From posé, avec ou sans pièce jointe ; adresses refusées), `NotifierTemplateEmailIT`
(From et `Return-Path` relus sur GreenMail).
