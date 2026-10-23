---
phase: requirements
title: Joindre un document à la brique « Notifier »
description: Issue #183 — un e-mail de la brique « Notifier » peut porter le PDF produit par le workflow
---

# Exigences — `notifier-piece-jointe` (issue #183)

## Problème

La brique « Notifier » (`NotificationDispatchTask`) n'envoie que du texte. Dans l'exemple « autorisation riverain »
(`exemples/autorisation-riverain/DIFFICULTES.md` §2), le PDF de décision est généré et joint au dossier, puis un agent
doit le télécharger et l'envoyer lui-même (tâche « Transmettre le document de décision »). Le demandeur reçoit un e-mail
informatif sans son autorisation.

Concernés : le concepteur de workflow (palette BPMN, MCP), l'agent (tâche manuelle évitable), le demandeur (destinataire).

## Objectifs

- **E1** — Un attribut `flowable:notificationAttachment="variable:<nom>|dossierField:<nom>"` sur la brique « Notifier »
  joint à l'e-mail le document désigné (cas nominal : `variable:generatedDocumentId`, le PDF généré puis rattaché).
- **E2** — Un attribut facultatif `flowable:notificationAttachmentName` fixe le nom du fichier vu par le destinataire
  (sinon : libellé de l'élément du dossier). L'extension `.pdf` est ajoutée si absente.
- **E3** — Le document joint est forcément un **document produit** (`PRODUCED_DOC`) **du dossier courant** et un **PDF** :
  aucun moyen d'envoyer un fichier d'un autre dossier, une pièce déposée par un tiers ou un fichier arbitraire du stockage.
- **E4** — Les deux attributs ne sont lus que depuis le BPMN **déployé** (littéral) : une variable de process de même nom,
  posée par un utilisateur au démarrage ou à la complétion d'une tâche, est ignorée.
- **E5** — Un document introuvable, vide, trop gros ou non PDF fait **échouer l'étape** (incident si la brique est
  asynchrone, refus 422 sinon), avec un message qui nomme la source, jamais une valeur.
- **E6** — Taille maximale configurable (`app.notification.email.attachment-max-bytes`, défaut 10 Mio), revérifiée à l'envoi.
- **E7** — La palette (catalogue, diagnostics de forme) et le déploiement (400 si source mal formée, si nom invalide)
  connaissent les deux attributs.
- **E8** — L'exemple « autorisation riverain » joint le PDF aux e-mails de décision et n'a plus de tâche de transmission manuelle.

## Non-objectifs

- Plusieurs pièces jointes par e-mail (une seule source ; extension possible plus tard).
- Joindre une pièce **déposée** (`ATTACHMENT`, statut antivirus) ou un document non PDF.
- Pièce jointe sur les canaux `WEBHOOK` et `BROWSER`.
- Pièce jointe dans le connecteur `smtp` (voir design, D-NOTIF-ATTACH-SMTP-CONNECTOR).
- Corps d'e-mail avec gabarit ou variables (issue `gabarits-notification`).

## Récits

- En tant que concepteur, je règle « Pièce jointe » = `variable:generatedDocumentId` sur la brique « Notifier » placée
  après « Joindre le document », pour que le demandeur reçoive sa décision en PDF.
- En tant qu'agent, je n'ai plus de tâche « Transmettre le document » à traiter à la main.
- En tant qu'administrateur, je vois un incident rejouable quand le document manque (brique asynchrone).

Cas limites : brique placée **avant** « Joindre le document » (le document n'est pas encore un élément du dossier → échec) ;
canal `EMAIL` absent alors qu'une pièce jointe est demandée (échec) ; document purgé entre l'émission et l'envoi
(l'outbox retente puis passe en *dead letter*) ; variable `notificationAttachment` posée à la complétion (ignorée).

## Critères d'acceptation

- Tests unitaires du delegate, du use case, du relais outbox, de l'adaptateur SMTP (MIME multipart avec la pièce).
- Test d'intégration Flowable : génération → rattachement → notification avec pièce jointe → e-mail capturé avec le PDF.
- Diagnostics palette (Vitest) ; `npm run test:all` vert.
- Le BPMN de l'exemple passe le parseur (ajouté au garde-fou des BPMN livrés).
- `./mvnw verify`, `scripts/check-docs.sh` verts.

## Questions ouvertes

Aucune bloquante ; les choix faits sans arbitrage de l'utilisateur sont marqués ⚠ dans le design et le registre (§96).
