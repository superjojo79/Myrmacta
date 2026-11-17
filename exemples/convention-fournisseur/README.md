# Exemple — convention fournisseur (signature électronique)

Une convention avec un fournisseur est relue, générée en PDF, jointe au dossier, puis envoyée en signature au
représentant du fournisseur. Le processus attend le retour du fournisseur de signature et passe la convention à
« Signée » ou « Non signée ». C'est l'exemple de la signature électronique ([spec 30](../../docs/specs/30-signature-electronique.md))
et la scène de la capsule C13 du site ([§148](../../docs/decisions/148-teaser-video-du-site.md), D-VIDEO-SIGN).

## Ce que montre l'exemple

- un bloc « Demander une signature » (`signatureRequestTask`) avec un signataire **externe** : l'adresse vient du champ
  `signataireEmail` du dossier ; il reçoit par e-mail un lien vers le portail, qui le mène chez le fournisseur ;
- l'attente du message `signatureCompleted`, puis le routage sur `signatureOutcome` ;
- un modèle de document qui porte l'ancre de signature de Yousign, `{{s1|signature|85|37}}` (D-SIGNATURE-ANCHOR),
  en blanc : elle ne se voit pas à la lecture.

## Mise en service

L'instance doit avoir un fournisseur de signature (`APP_SIGNATURE_PROVIDER` : `yousign`, ou `stub` pour essayer, sans
valeur juridique) et le portail (`APP_PORTAL_ENABLED=true`, `APP_PORTAL_PUBLIC_BASE_URL`) : sans eux, le déploiement du
BPMN est refusé. Avec Yousign, créer d'abord le connecteur « Signature électronique » (clé d'API, secret du webhook),
puis déclarer le webhook chez Yousign vers `/api/callbacks/{id de l'instance}`.

Dans l'ordre :

1. type de dossier : `config/type-dossier.json` ;
2. configuration du workflow : `config/workflow-config.json` ;
3. modèle de document : `config/modele-document.json` (contenu : `template-convention.html`), à activer ;
4. processus : `convention-fournisseur.bpmn20.xml` (généré par `generer-bpmn.py`).

`config/exemple-dossier.json` donne un dossier d'essai. La variante anglaise est dans `en/` (textes des JSON, modèle de
document, table `names.json` des noms du BPMN).

## Scène de tournage

`scripts/video.sh start c13-signature` installe l'exemple (paquet `SignatureDemoPack`, profil `demo-video-signature`)
et ouvre le dossier d'essai, qui attend sa relecture. Bac à sable Yousign : `YOUSIGN_SANDBOX_API_KEY` et
`YOUSIGN_WEBHOOK_SECRET` dans `.env` ; `APP_SIGNATURE_PROVIDER=stub` pour répéter sans Yousign.
