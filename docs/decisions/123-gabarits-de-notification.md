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

**Revue de clôture (lot 1).**

- **Bornes de coût du rendu** — propres aux gabarits de notification (les templates de document sont inchangés ; leur
  durcissement fait l'objet d'une issue). Propriétés `app.notification.template.*` :
  - `rendered-max-bytes` (1 048 576) : le rendu écrit dans un `Writer` borné, la somme des octets de l'objet, du corps texte
    et du corps HTML ne le dépasse jamais, contrôlée pendant le rendu ;
  - `utility-max-size` (1 000) : éléments de `#numbers.sequence`, longueur produite par `#strings.repeat`, chiffres demandés
    à `#numbers.format*` ; `#strings.randomAlphanumeric` est refusé ;
  - `string-max-chars` (100 000) : longueur de toute chaîne produite par une évaluation (chemin, littéral ou utilitaire,
    appels imbriqués compris) ou passée à un utilitaire, taille de toute collection produite par un utilitaire, et résultat
    prévisible de `#strings.replace*` et `#strings.*join` (calculé avant l'appel). Une variable de process de plus de
    100 000 caractères ne peut donc pas être citée dans un gabarit ;
  - `render-budget` (100 000) : budget par rendu, porté par le fil de rendu (un gabarit ne peut ni le lire ni le masquer par
    `th:with` ou `th:each`). Une évaluation coûte 1, plus la longueur de l'expression divisée par 64, plus le poids des
    opérandes et du résultat (une chaîne pèse sa longueur divisée par 64, une collection sa taille) : les `th:each`
    imbriqués à sortie vide et les littéraux lourds échouent proprement ;
  - `render-timeout-millis` (2 000) : échéance du rendu, contrôlée à chaque évaluation ;
  - seuls les arguments qui servent de taille sont contrôlés dans la plage `int` (pas de troncature `long` vers `int`) :
    `#numbers.sequence` (rangs 0 à 2), `#strings.repeat` (rang 1) et le nombre de chiffres de `#numbers.format*` (rangs 1 et
    suivants) ; la valeur formatée reste libre (`Double`, `Long`, `BigDecimal`) ;
  - un `BigDecimal` de plus de `string-max-chars` chiffres (ou un `BigInteger` d'au moins autant de chiffres) est refusé et
    pèse dans le budget (chiffres divisés par 64) : les opérateurs arithmétiques de Thymeleaf ne sont pas bornés ;
  - une `Error` levée par un utilitaire (`OutOfMemoryError`) n'est plus avalée.

  Toute violation (y compris `StackOverflowError` et `OutOfMemoryError`) lève `TemplateProcessingException`, sans valeur dans
  le message.
- **Pré-traitement `__…__` refusé** — le contrôle d'activation décode les entités HTML (`HtmlEscape.unescapeHtml`, comme le
  moteur) puis refuse toute séquence `__` dans l'objet, le corps texte et le corps HTML, y compris écrite `&#95;`, `&#x5f;`,
  `&lowbar;` ou `&UnderBar;` ; il soumet aussi à la grammaire D-40 les expressions écrites avec des entités (`&#36;{…}`). Le
  moteur refuse de même au rendu, sur la source décodée (gabarits activés avant ce correctif, promotion). Le refus est
  global : le texte libre `________` (ligne de signature) est un faux positif accepté. Un refus dans l'évaluateur ou dans
  un `IStandardExpressionParser` n'est pas possible avec Thymeleaf 3.1.5 : `th:with` et `th:each` appellent le
  pré-processeur directement (`AssignationUtils`, `EachUtils`), et l'évaluation du pré-traitement n'est pas distinguable
  d'une évaluation ordinaire (contexte `RESTRICTED` partagé avec `[[…]]`).
- **Variante et langue validées à la résolution** — `resolve` applique les règles de format de l'écriture
  (`[a-z0-9][a-z0-9-]{0,99}` pour la variante, étiquette BCP 47 courte pour la langue) ; une valeur non conforme (texte
  avec saut de ligne, `Map` sérialisée) donne une erreur de validation sans la valeur. `NotificationTemplateNotFoundException`
  ne recopie plus la variante ni la langue, seulement la clé (littérale du BPMN).
