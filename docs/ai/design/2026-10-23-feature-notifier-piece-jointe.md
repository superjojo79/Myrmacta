---
phase: design
title: Joindre un document à la brique « Notifier » — conception
description: Résolution de la source à l'émission, référence dans l'outbox, octets relus à l'envoi
---

# Conception — `notifier-piece-jointe`

## Flux

```mermaid
sequenceDiagram
  participant D as NotificationDispatchTask (transaction du process)
  participant R as ResolveEmailAttachmentUseCase
  participant U as SendNotificationUseCase
  participant O as notification_outbox
  participant Rel as NotificationOutboxRelay
  participant S as DocumentStoragePort
  participant M as SmtpEmailAdapter
  D->>D: lit flowable:notificationAttachment (littéral seulement)
  D->>D: résout la source (variable / champ du dossier) → storageRef
  D->>R: resolve(dossierId, storageRef, nom demandé)
  R->>R: élément PRODUCED_DOC du dossier avec ce storageRef ?
  R->>S: retrieve → non vide, ≤ max, %PDF-
  R-->>D: EmailAttachment(storageRef, fileName, application/pdf)
  D->>U: NotificationEvent(…, attachment)
  U->>O: snapshot EMAIL {to, subject, body, attachment:{storageRef, fileName, contentType}}
  Rel->>O: lockNextPending
  Rel->>S: retrieve(tenant, storageRef) ; taille ≤ max
  Rel->>M: send(to, subject, body, EmailAttachmentContent)
  M->>M: MIME multipart/mixed, Auto-Submitted
```

## Décisions

- **D-NOTIF-ATTACH-PRODUCED-DOC** ⚠ — seul un élément `PRODUCED_DOC` **du dossier courant** dont `storageRef` est la valeur
  résolue peut être joint, et seulement un PDF (signature `%PDF-`). Même règle que la signature électronique
  (`RequestSignatureUseCaseImpl.findProducedDoc`, `SOURCE_NOT_PDF`). Une valeur qui désigne un autre dossier, une pièce
  déposée ou un objet arbitraire du stockage est refusée : la variable `generatedDocumentId` peut avoir été posée par un
  utilisateur. Écarté : accepter `ATTACHMENT` (statut antivirus à vérifier, données déposées par un tiers renvoyées par
  e-mail) ; accepter tout `storageRef` du tenant (exfiltration).
- **D-NOTIF-ATTACH-LITERAL** — `notificationAttachment` et `notificationAttachmentName` ne sont lus que depuis l'attribut
  littéral déployé (`DeployedActivityParameters.getLiteral`), jamais depuis une variable de même nom. Cohérent avec
  D-NOTIF-SOURCE-LITERAL et TODO §75.
- **D-NOTIF-ATTACH-REF-IN-OUTBOX** — l'outbox stocke une **référence** (`storageRef`, nom, type), pas les octets : la ligne
  reste petite, le contenu est relu à l'envoi (tenant de la ligne). Contrôle complet à l'émission (échec tôt, incident
  rejouable) ; à l'envoi, seule la taille est revérifiée (le plafond a pu baisser). Document purgé entre-temps : échec de
  l'envoi, nouvelles tentatives puis *dead letter* (visible sur le dossier, TODO §94).
- **D-NOTIF-ATTACH-MAX** ⚠ — plafond `app.notification.email.attachment-max-bytes`, défaut **10 Mio** (10 485 760 octets) :
  sous la limite courante des relais (10 à 25 Mo, encodage base64 +33 %). Pas de variable d'environnement dédiée (liaison
  souple Spring : `APP_NOTIFICATION_EMAIL_ATTACHMENTMAXBYTES`).
- **D-NOTIF-ATTACH-NAME** — nom du fichier : `notificationAttachmentName` (validé au déploiement : lettres, chiffres, espace,
  `. _ - ( ) '`, 100 caractères au plus, ne commence pas par un point), sinon le libellé de l'élément, assaini à
  l'exécution (même alphabet ; autre caractère → `_`). `.pdf` ajouté si absent.
- **D-NOTIF-ATTACH-EMAIL-ONLY** — une pièce jointe sans le canal `EMAIL` est une erreur de validation (pas d'ignorance
  silencieuse) ; les canaux `WEBHOOK` et `BROWSER` ne la transportent pas.
- **D-NOTIF-ATTACH-SMTP-CONNECTOR** ⚠ — le connecteur `smtp` n'est **pas** étendu : ses entrées (`connectorInput`) sont des
  variables d'exécution, sans dossier de rattachement vérifiable ; la brique « Notifier » est le chemin supporté. À
  rouvrir si un besoin d'e-mail calculé avec pièce jointe apparaît.
- **D-NOTIF-ATTACH-SINGLE** — une seule pièce jointe par brique (non-objectif : plusieurs).

## Contrats

| Élément | Changement |
|---|---|
| `BuildingBlockCatalog` « notify » | + `notificationAttachment` (`SOURCE_REF`, 200) et `notificationAttachmentName` (`TEXT`, 100) ; `VERSION` 1 → 2 |
| `BlockAttributeRules` | contrôle de forme `SOURCE_REF` (grammaire `PortalRecipientSource`) et du nom de fichier |
| `NotificationEvent` | + composant `EmailAttachment attachment` (nullable) ; constructeurs existants conservés |
| `EmailPort` | + `send(to, subject, body, EmailAttachmentContent)` ; la forme à 3 arguments devient une méthode par défaut |
| `NotificationPayloadJson.email` | + objet `attachment` facultatif |
| `NotificationProperties` | + `email.attachmentMaxBytes` |
| Palette (SPA) | diagnostic de forme pour tout paramètre `SOURCE_REF` ; rendu générique inchangé |

Aucune migration Liquibase (le JSON de l'outbox est libre), aucune route HTTP, golden OpenAPI inchangé.

## Sécurité

- Lecture du stockage avec le tenant de l'instance ; `storageRef` contrôlé par l'adaptateur (préfixe tenant, traversée).
- Jamais de valeur (adresse, référence) dans un message d'erreur : la source est nommée.
- En-tête `Auto-Submitted` conservé ; nom de fichier sans séparateur de chemin, guillemet ni caractère de contrôle.
