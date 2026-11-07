# §123 — Gabarits de notification avec variables

> **Statut :** ouvert · point de premier niveau · [registre](README.md)

Issue [#19](https://github.com/superjojo79/Myrmacta/issues/19) (vague mail room, dernière sous-issue de
[#217](https://github.com/superjojo79/Myrmacta/issues/217)). Cadrage validé par l'utilisateur le 2026-11-07 ; livraison en cours
(document de travail `docs/ai/2026-11-07-gabarits-notification.md`). Ce point se clôt dans la PR qui livre le dernier lot.

**Constat.** Les e-mails de la brique « Notifier » (`notificationDispatchTask`) sont des littéraux du BPMN
(`flowable:notificationSubject`, `flowable:notificationBody`). Changer un texte demande de redéployer le process ; aucune
variable, aucune langue. La motivation saisie par un agent ne peut pas parvenir au demandeur
(`exemples/autorisation-riverain/DIFFICULTES.md` §4). La mail room n'envoie aucun accusé de réception à l'expéditeur.

**Décisions (utilisateur, 2026-11-07).**

- **D-NOTIF-TPL-SCOPE** — périmètre : les e-mails de la brique « Notifier » et un accusé de réception à l'expéditeur dans
  l'exemple mail room. Hors périmètre (issues séparées) : les rappels et escalades d'échéance (`EvaluateDeadlinesUseCaseImpl`,
  notifications navigateur aux agents), les consignes des tâches du portail (`PortalTaskResponse.instructions`), les canaux
  webhook et navigateur.
- **D-NOTIF-TPL-STORE** — une table dédiée `notification_template`, sur le modèle des templates de document
  ([spec 08](../specs/08-templates-documents.md)) : clé `(templateKey, variantKey, locale)`, cycle `DRAFT → ACTIVE → ARCHIVED`,
  une seule version active par clé, même cascade de résolution à quatre niveaux. Pas de réutilisation de `document_template`.
- **D-NOTIF-TPL-FORMAT** — un gabarit porte un objet (texte), un corps texte (obligatoire) et un corps HTML (facultatif).
  Avec un corps HTML, l'e-mail part en `multipart/alternative` (texte + HTML). L'objet et le corps texte sont rendus par
  Thymeleaf en mode `TEXT`, le corps HTML en mode `HTML`, avec la grammaire d'expression D-40 dans les deux cas.
- **D-NOTIF-TPL-HTML-POLICY** — le corps HTML est contrôlé à l'activation et refusé (jamais nettoyé en silence) s'il sort de la
  politique : balises de la liste blanche, pas d'image, pas de `<style>`, liens `http`, `https` et `mailto` seulement, attribut
  `style` sans `url(`, `expression(`, `@import` ni `javascript:`, attributs Thymeleaf limités à
  `th:text`, `th:if`, `th:unless`, `th:each`, `th:with`, `th:switch`, `th:case`, `th:remove` et l'élément `th:block`. Le HTML
  rendu repasse par le même nettoyeur jsoup avant l'envoi (défense en profondeur).
- **D-NOTIF-TPL-MODEL** — modèle exposé au gabarit : `dossier` (référence, type, statut, libellé, champs **déclarés et non
  sensibles**) et `variables` (toutes les variables de process **scalaires**), comme le contexte des expressions métier
  (`BusinessExpressionContext`, D-EXPR-CONTEXT), sans `connectorResult`. Un champ sensible n'est jamais exposé. Risque
  résiduel assumé : une variable de process posée par un formulaire, qui recopierait la valeur d'un champ sensible, est
  exposée ; à documenter dans le guide de conception.
- **D-NOTIF-TPL-STRICT** — une variable ou une propriété absente ou nulle fait échouer le rendu, comme pour les documents :
  l'étape échoue (incident si la brique est asynchrone, refus 422 de la complétion ou du démarrage sinon) et aucun e-mail ne
  part. Le message d'erreur nomme le gabarit et l'expression, jamais une valeur.
- **D-NOTIF-TPL-LITERAL** — `flowable:notificationTemplate` n'est lu que dans le BPMN déployé (jamais une variable de process
  du même nom). Il exclut `notificationSubject` et `notificationBody` : les deux à la fois sont refusés au déploiement.
  Il exige le canal `EMAIL` (et seulement lui). `flowable:notificationVariant` et `flowable:notificationLocale` sont un
  littéral ou `variable:<nom>`, lus eux aussi dans le BPMN déployé.
- **D-NOTIF-TPL-TOOLS** — livrés avec la V1 : API REST (`TENANT_ADMIN`), outils MCP `define_notification_template`,
  `list_notification_templates`, `preview_notification_template` (mode conception), écran d'administration, promotion vers
  un autre environnement (hypothèse H1 du document de travail : parité avec les templates de document).
- **D-NOTIF-TPL-ACK** — l'accusé de réception de l'exemple mail room part seulement si le courrier est arrivé par e-mail
  et que l'expéditeur est attesté (`mailSenderAuthenticated`, posé par `InboundMailRouting`) : pas d'accusé vers une adresse
  usurpée (backscatter). Les messages automatiques ne deviennent jamais un dossier (anti-boucle existant) et l'e-mail sortant
  porte `Auto-Submitted: auto-generated`.

**Impacte.** `docs/specs/09-notifications.md` (section « Implémentation » à la clôture), `docs/specs/49-digital-mail-room.md`,
`docs/guides/GUIDE-CONCEPTION-BPMN.md` (§6c, catalogue §7, mise en garde sur les données sensibles),
`docs/guides/GUIDE-UTILISATEUR.md` (nouvel écran `/admin/notification-templates`), `docs/decisions/identifiants.md`
(identifiants ci-dessus), `exemples/digital-mail-room/`, `exemples/autorisation-riverain/` (README, DIFFICULTES §4).
