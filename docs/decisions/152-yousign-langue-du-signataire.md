# §152 — Yousign : langue du signataire

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] clos (2026-11-20, issue [#132](https://github.com/superjojo79/Myrmacta/issues/132)). Prolonge
[§109](109-fournisseurs-de-signature-yousign-et-docusign.md) et [§90](090-d7-internationalisation-fr-en.md)
(D-I18N-LANGS).

**Constat.** `YousignHttpClient.addSigner` créait toujours le signataire avec `"locale": "fr"`. Un tenant en anglais
envoyait ses signataires sur une page de signature et des messages Yousign en français (capsule C13 anglaise : langue
changée à la main dans le menu de Yousign).

**Décision (D-SIGNATURE-SIGNER-LOCALE).**

- Le signataire reçoit la langue du tenant, `app.i18n.default-locale`, celle qui sert déjà aux invitations de
  signature envoyées par Myrmacta (`EmailSignatureInviteSender`).
- Seuls `fr` et `en` sont envoyés : ce sont les langues d'un tenant (D-I18N-LANGS), et les seules dont le code a été
  vu accepté par Yousign. Le centre d'aide Yousign annonce huit langues de destinataire (anglais, français, allemand,
  italien, espagnol, néerlandais, portugais, polonais) ; la référence publique de l'API v3, relue le 2026-11-20, ne
  détaille pas l'énumération de `info.locale`. Une langue de tenant de plus devra y être vérifiée.
- **Repli : `fr`**, pour une langue absente ou hors liste (langue par défaut d'un tenant, valeur déjà validée sur le
  bac à sable). Une variante régionale (`en-GB`) garde sa langue.
- **Pas de langue par demande.** Ni le BPMN ni la demande de signature ne portent de langue aujourd'hui ; en ajouter
  une est une évolution du modèle, à ouvrir en issue si un tenant bilingue le demande. DocuSign n'envoie pas de
  langue (celle du compte s'applique) : il sera examiné dans la même issue.

**Tests.** `YousignHttpClientTest` (`fr`, `en`, `en-GB`, repli pour `ja` et pour une langue absente),
`YousignSignatureAdapterTest` (la langue du tenant passe à chaque signataire).

**Impacte.** [spec 30](../specs/30-signature-electronique.md) (implémentation Yousign), `identifiants.md`.
