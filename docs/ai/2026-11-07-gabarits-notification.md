# Gabarits de notification avec variables — document de travail

Issue : #19 · Spec du domaine : docs/specs/09-notifications.md (et 49-digital-mail-room.md) · Points du registre : §123

> **À l'implémenteur.** Ce document est écrit pour être exécuté tel quel, lot par lot. Les décisions sont au registre
> ([§123](../decisions/123-gabarits-de-notification.md)) : ne pas les rediscuter, ne pas en trancher d'autres en silence. Un
> point non couvert ici devient une question à l'utilisateur ou une hypothèse nommée, ajoutée au tableau « Questions
> ouvertes ». Lire d'abord `AGENTS.md`, le skill `myrmacta-conventions` (§ Précédents surtout) et le skill
> `myrmacta-feature`. Une PR par lot, tests verts, TDD (skill `tdd`), preuves fraîches (skill `verify`).

## Cadrage

- **Problème et utilisateurs :**
  - Les e-mails de la brique « Notifier » sont des littéraux du BPMN : pas de variables, pas de langue, un redéploiement à
    chaque changement de texte. La motivation saisie par l'agent ne peut pas parvenir au demandeur
    (`exemples/autorisation-riverain/DIFFICULTES.md` §4).
  - La mail room n'accuse pas réception du courrier reçu par e-mail.
  - Utilisateurs : l'administrateur du tenant (rédige et active les gabarits), le concepteur BPMN (référence un gabarit
    dans la brique), l'assistant MCP (rédige des brouillons), le citoyen ou l'expéditeur (reçoit l'e-mail).
- **Périmètre / hors-périmètre :** voir D-NOTIF-TPL-SCOPE (§123). Dans le périmètre : la brique « Notifier » (canal
  `EMAIL`), l'accusé de réception de l'exemple mail room, l'e-mail « informations complémentaires » de l'exemple
  autorisation riverain, qui porte désormais la motivation. Hors périmètre : rappels d'échéance, consignes du portail,
  canaux webhook et navigateur, connecteur `smtp`, éditeur WYSIWYG des gabarits, profil de langue du destinataire.
- **Critères de succès (vérifiables) :**
  1. **CS1.** Un `TENANT_ADMIN` crée, modifie, active, archive et prévisualise un gabarit par l'API REST et par l'écran
     `/admin/notification-templates`. Tout autre rôle reçoit 403 (test sur la vraie chaîne HTTP et sécurité).
  2. **CS2.** Une brique « Notifier » avec `flowable:notificationTemplate="<clé>"` envoie un e-mail dont l'objet et le corps
     sont rendus avec `dossier.*` et `variables.*`. Avec un corps HTML, l'e-mail est `multipart/alternative`. Preuve : un
     test d'intégration GreenMail qui lit le message reçu.
  3. **CS3.** Un champ `sensitive` du type de dossier n'est jamais rendu : `${dossier.fields.<sensible>}` fait échouer le
     rendu, comme une propriété absente.
  4. **CS4.** Une variable absente : l'étape échoue (incident si asynchrone, 422 sinon) et aucun e-mail n'est écrit dans
     l'outbox.
  5. **CS5.** Un BPMN qui combine `notificationTemplate` et `notificationSubject` ou `notificationBody`, qui référence un
     gabarit sans canal `EMAIL`, ou avec un autre canal en plus, est refusé au déploiement. Une variable de process
     `notificationTemplate` est ignorée.
  6. **CS6.** Un corps HTML hors politique (D-NOTIF-TPL-HTML-POLICY) est refusé à l'activation (400) avec un message qui
     nomme la règle violée.
  7. **CS7.** Les outils MCP `define_notification_template`, `list_notification_templates` et
     `preview_notification_template` fonctionnent pour un `TENANT_ADMIN` et sont refusés aux autres rôles (test sur la vraie
     chaîne MCP). L'activation reste un geste humain.
  8. **CS8.** L'exemple mail room envoie un accusé de réception à l'expéditeur d'un e-mail attesté, et rien pour un courrier
     papier ou un expéditeur non attesté.
  9. **CS9.** La cascade de résolution `(clé, variante, langue)` suit les quatre niveaux de la spec 08.
  10. **CS10.** Les gabarits sont exportés et importés par la promotion de configuration (hypothèse H1).
- **Contraintes :**
  - Stack figée : Java 25, Spring Boot 4, Flowable 8, Thymeleaf déjà présent, jsoup 1.23.2 déjà présent. **Aucune
    nouvelle dépendance d'exécution.** GreenMail est déjà une dépendance de test (`greenmail-junit5`).
  - Hexagonal : rien de Spring, JPA, Thymeleaf ni jsoup dans `be.myrmacta.domain`. JPA seulement (§122).
  - Un tenant par instance : `tenant_id` porté par la table, `TenantProperties.id()` côté appelant, jamais résolu par
    requête.
  - Liquibase : nouveau changeSet seulement (`049-…`), jamais modifier un changeSet existant.
  - Documentation à mettre à jour : voir « Impacte » du §123.
- **Validé par l'utilisateur le :** 2026-11-07 (réponses aux six questions de cadrage, registre §123).

## Questions ouvertes

| Question | Options | Recommandation | Statut |
|---|---|---|---|
| Périmètre | Notifier / + accusé / + portail / + rappels | Notifier + accusé | tranchée → §123 D-NOTIF-TPL-SCOPE |
| Stockage | table dédiée / `document_template` / inline | table dédiée | tranchée → §123 D-NOTIF-TPL-STORE |
| Format | texte / texte + HTML | texte | tranchée (texte + HTML facultatif) → §123 D-NOTIF-TPL-FORMAT |
| Variables exposées | restreint / toutes masquées / tout | restreint | tranchée (toutes, masquées) → §123 D-NOTIF-TPL-MODEL |
| Variable absente | échec strict / chaîne vide | strict | tranchée → §123 D-NOTIF-TPL-STRICT |
| Langue, variante | attributs BPMN et cascade / clé seule | attributs et cascade | tranchée → §123 D-NOTIF-TPL-LITERAL |
| Outillage | REST + MCP + écran / sans écran / REST | REST + MCP + écran | tranchée → §123 D-NOTIF-TPL-TOOLS |
| H1 : promotion des gabarits | inclure / reporter | inclure (parité avec les templates de document, `DocumentTemplateArtifacts`) | hypothèse ; lot 3, peut être reportée en issue si le lot déborde |
| H2 : rôle de l'aperçu REST | `TENANT_ADMIN` seul / + `WORKFLOW_ADMIN` | `TENANT_ADMIN` seul, comme `/api/document-templates` | hypothèse |
| H3 : plafonds | voir « Bornes » | valeurs ci-dessous | hypothèse, modifiable par propriété |

## Conception

### Faits vérifiés dans le code (au commit `f84997a`)

| Fait | Où |
|---|---|
| La brique lit objet et corps par `DeployedActivityParameters.get` (attribut BPMN, sinon variable de même nom) | `infrastructure/flowable/NotificationDispatchTask.java` `execute` |
| `DeployedActivityParameters.getLiteral` lit l'attribut seul, sans repli sur une variable | `infrastructure/flowable/DeployedActivityParameters.java` |
| La validation de l'événement ne fait aucune I/O et écrit l'outbox (DD-UC-NO-IO) ; le canal EMAIL exige `to`, `subject`, `body` | `domain/notification/usecase/SendNotificationUseCaseImpl.java` |
| La charge utile EMAIL de l'outbox est construite par `NotificationPayloadJson.email(to, subject, body, attachment, originDossierId)` | `domain/notification/model/NotificationPayloadJson.java` |
| Le relais lit `to`, `subject`, `body`, `attachment` et appelle `EmailPort.send` | `infrastructure/notification/NotificationOutboxRelay.java` `dispatchEmail` |
| `EmailPort` ne connaît que le texte ; `SmtpEmailAdapter` fait `helper.setText(body, false)` et pose `Auto-Submitted: auto-generated` | `domain/notification/port/out/EmailPort.java`, `infrastructure/notification/SmtpEmailAdapter.java` (seule implémentation de production) |
| Moteur Thymeleaf restreint : `SafeHtmlTemplateEngineFactory.htmlStringEngine()` (mode HTML, liste blanche de processeurs, `th:utext`, `th:replace`, `th:insert` et `th:include` désactivés, évaluateur `SafeThymeleafVariableExpressionEvaluator`) | `infrastructure/document/` |
| Le contrôle de syntaxe d'activation applique la grammaire D-40 sans exécuter | `infrastructure/document/ThymeleafTemplateSyntaxChecker.java`, port `domain/document/port/out/TemplateSyntaxChecker` |
| Rendu : `HtmlTemplateProcessor.process(String, Map)` ; une variable ou propriété absente ou nulle lève `TemplateProcessingException` | `infrastructure/document/ThymeleafHtmlTemplateProcessor.java`, spec 08 § « Grammaire » |
| Contexte des expressions métier : `dossier` (champs déclarés non sensibles, via `DossierFieldRedactor.declaredNonSensitiveFields`), `connectorResult`, `variables` (scalaires) ; `forExecution(...)` lit le dossier en identité système et échoue fermé si le type est introuvable | `infrastructure/flowable/BusinessExpressionContext.java` (package-private, même paquet que la brique) |
| Templates de document : modèle, cycle de vie, cascade, use cases (`Create`, `Update`, `Activate`, `Archive`, `NewVersion`, `Get`, `List`, `Resolve`, `Preview`), REST `/api/document-templates` en `TENANT_ADMIN` | `domain/document/**`, `infrastructure/web/document/DocumentTemplateController.java`, `infrastructure/persistence/document/**` |
| Outils MCP des templates (brouillon seulement, activation humaine D-TPLED-NO-ACTIVATE, `TenantAdminGuard`) ; tout outil doit être classé dans `McpToolModes` (test) | `infrastructure/mcp/tool/DefineDocumentTemplateTool.java`, `ListDocumentTemplatesTool.java`, `PreviewDocumentTool.java`, `infrastructure/mcp/McpToolModes.java`, `infrastructure/assistant/McpDesignToolCatalog.java` |
| Catalogue de la palette : bloc `notify`, `VERSION = 6`, sources de valeurs `VALUE_SOURCES` (dont `documentTemplates`) | `domain/workflow/palette/BuildingBlockCatalog.java`, `infrastructure/palette/BlockReferenceLookupAdapter.java` |
| Contrôles de déploiement des attributs de la brique (pièce jointe, destinataire) | `domain/workflow/palette/BlockAttributeRules.java` (lignes ~118 et ~157-190) |
| Concepteur de parcours : clés autorisées du pas NOTIFY | `domain/parcours/ParcoursCompiler.java` `NOTIFY_KEYS` |
| Promotion des templates de document | `domain/promotion/artifact/DocumentTemplateArtifacts.java`, `infrastructure/config/PromotionConfig.java` |
| E-mail entrant : `mailFrom`, `mailSubject`, `mailSenderAuthenticated` (booléen) posés par le routage e-mail ; les messages automatiques ne deviennent jamais un dossier | `infrastructure/connector/builtin/InboundMailRouting.java`, `AngusImapMailbox.java`, `HttpGraphMailbox.java` |
| Exemple mail room : `tri-courrier.bpmn20.xml`, champs `canal` (`EMAIL`, `PAPIER`, `EBOX`) et `expediteur` ; aucune brique « Notifier » | `exemples/digital-mail-room/` |
| Dernier changeSet : `048-tenant-calendar-source-masked-url.yaml` ; dernier point du registre : §122 | `src/main/resources/db/changelog/`, `docs/decisions/` |
| Écran d'admin des templates de document : `TemplatesAdminView.vue`, route `/admin/templates` | `frontend/src/views/admin/` |

**Résultats des vérifications (lot 1, 2026-11-07)** — font foi sur la liste « À vérifier » ci-dessous :
- moteur TEXT : 14 processeurs chargés (Insert, Replace, Utext désactivés), épinglés par `SafeHtmlTemplateEngineFactoryTest` ;
- **`[[${x}]]` échappe en entités HTML même en mode TEXT** : le renderer décode en une passe `&amp; &lt; &gt; &quot; &#39;` sur
  l'objet et le corps texte ; `[(…)]` reste refusé ;
- **multipart** : `new MimeMessageHelper(msg, true, "UTF-8")` + `setText(text, html)` produit `multipart/mixed` >
  `multipart/related` > `multipart/alternative` > (text/plain, text/html), avec ou sans pièce jointe. Le lot 2 l'accepte
  tel quel (le test GreenMail vérifie la présence des deux parties `text/plain` et `text/html`, pas la racine) ;
- JSONata : `dossier.fields.canal = "EMAIL" and variables.mailSenderAuthenticated = true` donne `false` sans erreur quand
  la variable est absente.

**Noms livrés au lot 1, à utiliser par les lots suivants :** exceptions `NotificationTemplateNotFoundException`,
`NotificationTemplateStateException`, `NotificationTemplateValidationException(part, rule)` (sous-paquet
`domain.notification.template.model`) ; rendu en échec : `TemplateProcessingException` (paquet document). La brique (lot 2)
attrape `NotificationTemplateNotFoundException` et `TemplateProcessingException`. Condition dans un gabarit :
`th:if="${dossier.status} == 'OPEN'"` (comparaison hors accolades, grammaire D-40) — à mettre dans le guide (lot 5).

**Tests sur PostgreSQL externe (conteneur root) :** `MYRMACTA_TEST_PG_URL=jdbc:postgresql://localhost:5432/postgres`,
`MYRMACTA_TEST_PG_USER=postgres`, `MYRMACTA_TEST_PG_PASSWORD=postgres`, et `-Dspring.test.context.cache.maxSize=3` (sinon
`max_connections=100` est épuisé par les contextes en cache).

**À vérifier par l'implémenteur avant d'écrire du code** (Précédent « API supposée ») :
- les processeurs que Thymeleaf 3.1 charge en mode `TEXT` (`StandardTextTagProcessor`, `StandardIfTagProcessor`,
  `StandardEachTagProcessor`, `StandardInliningTextProcessor`, `StandardBlockTagProcessor`, …) : écrire un test qui
  liste les processeurs effectivement chargés par le moteur TEXT, sur le modèle de `SafeHtmlTemplateEngineFactoryTest` ;
- en mode `TEXT`, `[[${x}]]` n'échappe rien (c'est voulu pour du texte brut) ; `[(${x})]` reste refusé par le contrôle,
  par cohérence avec D-40 ;
- `MimeMessageHelper(message, true, "UTF-8")` + `helper.setText(text, html)` produit bien `multipart/alternative` (avec une
  pièce jointe : `multipart/mixed` qui contient `multipart/alternative`) : le vérifier dans le test GreenMail ;
- l'évaluation JSONata de `variables.mailSenderAuthenticated = true` quand la variable est absente (courrier papier) : elle
  doit donner `false`, sans erreur. Le vérifier par un test de la brique « Évaluer une expression ».

### Modèle et rendu

**Domaine** (`be.myrmacta.domain.notification.template`, nouveau sous-paquet ; pur Java) :

```java
public record NotificationTemplate(
        UUID id,
        String templateKey,      // [a-z0-9][a-z0-9-]{0,99}, obligatoire
        String variantKey,       // nullable = défaut, même alphabet
        String locale,           // nullable = défaut, BCP 47 court (fr, nl, fr-BE), même règle que DocumentTemplate
        String label,            // ≤ 200
        int version,
        TemplateStatus status,   // réutiliser be.myrmacta.domain.document.model.TemplateStatus (DRAFT, ACTIVE, ARCHIVED)
        String subject,          // gabarit TEXT, obligatoire, ≤ 500 caractères
        String textBody,         // gabarit TEXT, obligatoire, ≤ 64 Kio
        String htmlBody,         // gabarit HTML, nullable, ≤ 256 Kio
        String tenantId,
        Instant createdAt,
        Instant updatedAt,
        String updatedBy) { /* requireNonNull comme DocumentTemplate */ }

public record RenderedNotification(String subject, String textBody, String htmlBody /* nullable */) {}
```

Si réutiliser `TemplateStatus` crée une dépendance du sous-paquet notification vers `domain.document` refusée par
ArchUnit, créer `NotificationTemplateStatus` avec les mêmes valeurs ; ne pas affaiblir une règle.

**Ports** (`domain.notification.template.port.in` / `port.out`) — calqués sur `domain.document` :
- `in` : `CreateNotificationTemplateUseCase`, `UpdateNotificationTemplateUseCase` (brouillon seulement),
  `NewNotificationTemplateVersionUseCase`, `ActivateNotificationTemplateUseCase` (contrôle complet, archive l'active
  précédente), `ArchiveNotificationTemplateUseCase`, `GetNotificationTemplateUseCase`, `ListNotificationTemplateUseCase`
  (paginé), `ResolveNotificationTemplateUseCase` (cascade, ACTIVE seulement), `RenderNotificationUseCase`
  (`RenderedNotification render(TemplateResolutionKey key, Map<String,Object> model)` : résout puis rend),
  `PreviewNotificationTemplateUseCase` (rend un gabarit donné, brouillon compris, avec un modèle d'exemple).
- `out` : `NotificationTemplateRepository` (`findActive`, `findById`, `save`, `listByTenant(page)`,
  `findAllByKey`), `NotificationTemplateRenderer` (`RenderedNotification render(NotificationTemplate, Map model)`),
  `NotificationTemplateChecker` (`void check(NotificationTemplate)` : syntaxe des trois parties + politique HTML ; lève
  `InvalidTemplateSyntaxException` ou une nouvelle `NotificationTemplateValidationException` qui nomme la partie
  (`subject`, `textBody`, `htmlBody`) et la règle).
- Câblage par constructeur dans une classe de configuration d'infrastructure (`NotificationTemplateConfig`), comme
  `DocumentConfig`.

**Persistance** (`infrastructure.persistence.notification.template`) : `NotificationTemplateJpaEntity` (package-private),
`SpringDataNotificationTemplateRepository`, `JpaNotificationTemplateRepositoryAdapter`. ChangeSet
`049-notification-template.yaml` : table `notification_template` (`id uuid pk`, `tenant_id`, `template_key`, `variant_key`,
`locale`, `label`, `version int`, `status varchar(16)`, `subject text`, `text_body text`, `html_body text null`,
`created_at`, `updated_at`, `updated_by`), unicité `(tenant_id, template_key, variant_key, locale, version)` et index unique
partiel de l'active sur `(tenant_id, template_key, coalesce(variant_key,''), coalesce(locale,'')) where status = 'ACTIVE'`
(copier la forme exacte du changeSet `004-document-template-tables.yaml`). L'inclure dans `db.changelog-master.yaml`.

**Rendu** (`infrastructure.notification.template`, adaptateur de `NotificationTemplateRenderer`) :
1. Ajouter à `SafeHtmlTemplateEngineFactory` une méthode `textStringEngine()` : même dialecte filtré, même évaluateur,
   `TemplateMode.TEXT`. Elle peut rester dans `infrastructure.document` (la rendre accessible au paquet notification par
   une façade publique minimale), ou extraire le moteur dans un paquet commun `infrastructure.template` si c'est plus
   propre. Ne pas dupliquer la liste blanche.
2. `subject` → moteur TEXT → remplacer chaque `\r` ou `\n` par une espace, réduire les espaces multiples, `strip()` ; vide
   ou plus de 255 caractères après rendu : échec (`TemplateProcessingException` « subject … »). Pas d'injection d'en-tête
   possible.
3. `textBody` → moteur TEXT, tel quel.
4. `htmlBody` (si présent) → moteur HTML existant → `Jsoup.clean(html, policy)` avec la politique de
   D-NOTIF-TPL-HTML-POLICY (défense en profondeur ; le gabarit a déjà été refusé à l'activation s'il en sortait).
5. Taille rendue totale > 1 Mio : échec (borne contre un `th:each` sur une liste volumineuse).
6. Les messages d'erreur nomment la partie et l'expression, jamais une valeur de variable.

**Politique HTML** (classe `NotificationHtmlPolicy`, infrastructure, jsoup) :
- Balises permises : `a b blockquote br caption code div em h1 h2 h3 h4 h5 h6 hr i li ol p pre small span strong sub sup
  table tbody td tfoot th thead tr u ul`, plus `html head body title meta` pour un document complet.
- Attributs permis : `href` sur `a` (protocoles `http`, `https`, `mailto`) ; `style`, `class`, `align`, `colspan`,
  `rowspan`, `width` sur toutes ; `title` sur toutes. Valeur de `style` refusée si elle contient, sans tenir compte de la
  casse, `url(`, `expression(`, `@import`, `javascript:`.
- Interdits : `img`, `style` (élément), `script`, `iframe`, `object`, `embed`, `form`, `input`, `link`, `base`, `svg`,
  `math`, tout attribut `on*`.
- Attributs Thymeleaf permis : `th:text`, `th:if`, `th:unless`, `th:each`, `th:with`, `th:switch`, `th:case`, `th:remove`,
  et l'élément `th:block`. Tout autre `th:*` (`th:href`, `th:attr`, `th:style`, `th:src`, `th:utext`, `th:inline`, …) est
  refusé : une variable ne peut pas fabriquer une URL `javascript:` ni un attribut. Les liens restent littéraux dans le
  gabarit.
- **À l'activation : refuser**, avec la liste des violations (balise, attribut, ligne si jsoup la donne). Méthode : parser
  avec `Jsoup.parse`, parcourir les éléments et les attributs, comparer à la politique (ne pas se contenter de
  `Jsoup.isValid`, qui ne dit pas quoi est refusé). **Au rendu : nettoyer** avec un `Safelist` équivalent (sans les
  attributs `th:*`, déjà consommés par Thymeleaf).

**Modèle exposé au gabarit** (D-NOTIF-TPL-MODEL) :

```
dossier.reference | dossier.type | dossier.status | dossier.label | dossier.fields.<champ déclaré non sensible>
variables.<variable de process scalaire>
```

- Dans la brique : réutiliser `BusinessExpressionContext.forExecution(execution, getDossier, dossierTypes,
  tenantProperties, objectMapper).context()`, puis retirer la clé `connectorResult`. Même lecture, même liste blanche,
  même échec fermé.
- Aperçu (REST et MCP) : le modèle est fourni par l'appelant (`{"dossier": {...}, "variables": {...}}`), borné à 64 Kio
  de JSON, comme l'aperçu des documents.

### Brique « Notifier »

Nouveaux attributs, lus **dans le BPMN déployé seulement** (`DeployedActivityParameters.getLiteral`) :

| Attribut | Valeur | Règle |
|---|---|---|
| `flowable:notificationTemplate` | clé du gabarit, littérale | exclut `notificationSubject` et `notificationBody` ; exige `notificationChannels="EMAIL"` (seul canal) et `notificationTo` |
| `flowable:notificationVariant` | littéral ou `variable:<nom>` | facultatif ; exige `notificationTemplate` |
| `flowable:notificationLocale` | littéral ou `variable:<nom>` | facultatif ; exige `notificationTemplate` |

Dans `NotificationDispatchTask.execute`, si `getLiteral(execution, "notificationTemplate") != null` :
1. résoudre variante et langue (littéral ou `variable:<nom>` ; une variable absente vaut « pas de variante » ou « pas de
   langue », la cascade s'applique) ;
2. construire le modèle (ci-dessus) ;
3. `RenderNotificationUseCase.render(...)` : `TemplateNotFoundException` et `TemplateProcessingException` deviennent
   `NotificationValidationException` (même chemin d'erreur que les autres contrôles de la brique : incident en asynchrone,
   422 en synchrone ; vérifier la traduction existante de `NotificationValidationException`) ;
4. construire le `NotificationEvent` avec `subject` et `body` rendus et le nouveau `htmlBody`.

Sans `notificationTemplate`, le comportement actuel ne change pas (non-régression testée).

**Contrôles de déploiement** dans `BlockAttributeRules` (même style que la pièce jointe, `InvalidBpmnException` qui nomme
l'attribut et l'activité) : les règles du tableau ci-dessus. Le gabarit référencé n'a **pas** besoin d'exister au
déploiement (promotion entre environnements) ; son absence échoue à l'exécution.

**Événement et outbox :**
- `NotificationEvent` : ajouter `String htmlBody` (nullable) et une méthode `withHtmlBody(String)`, comme `withAttachment`.
  Garder les constructeurs existants (beaucoup d'appelants).
- `SendNotificationUseCaseImpl` : `htmlBody` sans canal `EMAIL` est une erreur de validation (même logique que
  D-NOTIF-ATTACH-EMAIL-ONLY).
- `NotificationPayloadJson.email(...)` : nouvelle surcharge avec `htmlBody`, clé `htmlBody` absente quand null (les
  enregistrements existants de l'outbox restent lisibles).
- `NotificationOutboxRelay.dispatchEmail` : lire `htmlBody` facultatif.
- `EmailPort` : nouvelle méthode `String send(String to, String subject, String textBody, String htmlBody,
  EmailAttachmentContent attachment)` ; l'actuelle à quatre arguments délègue avec `htmlBody = null` (méthode par défaut).
- `SmtpEmailAdapter` : `htmlBody != null` → `new MimeMessageHelper(message, true, UTF_8)` et
  `helper.setText(textBody, htmlBody)` ; sinon comportement actuel. `Auto-Submitted` toujours posé.

**Palette et parcours :**
- `BuildingBlockCatalog` : ajouter au bloc `notify` les paramètres `notificationTemplate` (référence, source de valeurs
  `notificationTemplates`, facultatif), `notificationVariant` et `notificationLocale` (texte court) ; passer
  `VERSION` à 7 ; ajouter `notificationTemplates` à `VALUE_SOURCES` ; mettre à jour la description de
  `notificationSubject`/`notificationBody` (« ou un gabarit »).
- `BlockReferenceLookupAdapter` : source `notificationTemplates` = clés ayant une version active, comme `documentTemplates`.
- `ParcoursCompiler.NOTIFY_KEYS` : ajouter les trois clés.
- Suivre les tests existants du catalogue (version et contrat MCP) : ils échoueront tant que les attentes ne sont pas mises
  à jour, c'est voulu.

### API REST (`infrastructure.web.notification.template`)

`@RequestMapping("/api/notification-templates")`, `@PreAuthorize("hasRole('TENANT_ADMIN')")`, calqué sur
`DocumentTemplateController` (DTO dédiés, gestionnaire d'exceptions dédié) :

| Méthode | Chemin | Effet | Codes |
|---|---|---|---|
| GET | `/` | liste paginée (sans les corps) | 200 |
| GET | `/{id}` | détail avec les corps | 200, 404 |
| GET | `/resolve?templateKey&variantKey&locale` | version active résolue | 200, 404 |
| POST | `/` | crée un brouillon (syntaxe non contrôlée, comme les documents) | 201, 400 (champs) |
| PUT | `/{id}` | met à jour un brouillon | 200, 404, 409 (pas un brouillon) |
| POST | `/{id}/activate` | contrôle complet (syntaxe + politique HTML) puis active | 200, 400, 404, 409 |
| POST | `/{id}/archive` | archive | 200, 404, 409 |
| POST | `/{id}/new-version` | nouveau brouillon depuis cette version | 201, 404 |
| POST | `/preview` | rend `{template: {subject,textBody,htmlBody}, model: {...}}` ou `{id, model}` | 200, 400 |

Plafond de taille du corps de requête pour `/preview` (réutiliser le mécanisme de
`DocumentGenerationBodySizeLimitFilter`). L'aperçu renvoie `{subject, textBody, htmlBody}` ; le SPA affiche le HTML dans
une `iframe` `sandbox` sans `allow-scripts` (`srcdoc`).

### MCP (`infrastructure.mcp.tool`)

- `define_notification_template` : même sémantique que `define_document_template` (brouillon seulement, nouvelle version
  si l'actuelle est active, `dryRun`, corps jamais renvoyés), avec le contrôle de syntaxe et de politique HTML **avant**
  écriture, pour donner un retour immédiat à l'assistant.
- `list_notification_templates` : clés, variantes, langues, versions, statuts.
- `preview_notification_template` : rend un gabarit (par clé, ou par contenu fourni) avec un modèle d'exemple.
- `TenantAdminGuard`, classement dans `McpToolModes` (conception), ajout à `McpDesignToolCatalog` si les outils de template
  y figurent. Descriptions en anglais, comme les outils existants.

### Frontend

- Vue `frontend/src/views/admin/NotificationTemplatesAdminView.vue`, route `/admin/notification-templates`, entrée de menu
  admin à côté de « Templates ». TypeScript, Pinia si un store existe pour les templates, composants OpenVue.
- Liste (clé, variante, langue, version, statut), formulaire (libellé, objet, corps texte, corps HTML facultatif ;
  zones de texte simples, pas d'éditeur WYSIWYG), actions activer, archiver, nouvelle version, aperçu avec un modèle JSON
  d'exemple et rendu HTML en `iframe sandbox`.
- Libellés en i18n (`frontend/src/i18n`), comme les autres vues.
- Vitest (vue et appels API) et Playwright (parcours créer → aperçu → activer, avec les mocks `frontend/e2e/**`).

### Exemples

- **Mail room** (`exemples/digital-mail-room/`) :
  - gabarit `accuse-reception-courrier` (fichier de config JSON, à importer comme les autres fichiers de `config/`) :
    objet `Accusé de réception : [[${dossier.fields.objet}]]`, corps texte qui cite la référence `[[${dossier.reference}]]`
    et la date de réception, corps HTML équivalent ;
  - dans `tri-courrier.bpmn20.xml`, juste après la création ou au début du tri : une brique « Évaluer une expression »
    (`flowable:formula='dossier.fields.canal = "EMAIL" and variables.mailSenderAuthenticated = true'`,
    `flowable:formulaResult="accuseReception"`), une passerelle exclusive `${accuseReception == true}`, une brique
    « Notifier » **asynchrone** (`flowable:async="true"`) avec `notificationChannels="EMAIL"`,
    `notificationTo="variable:mailFrom"`, `notificationTemplate="accuse-reception-courrier"`. Le flux reprend le tri dans
    les deux branches. Ne pas casser la course des fils (`reponse`) : placer l'accusé seulement sur un nouveau courrier.
  - README de l'exemple : une ligne sur l'accusé et la condition d'attestation.
- **Autorisation riverain** (`exemples/autorisation-riverain/`) : l'e-mail « informations complémentaires » des trois BPMN
  passe sur un gabarit `demande-informations-riverain` qui cite `[[${variables.motivation}]]` (la motivation saisie à
  l'instruction ; vérifier son nom exact dans le formulaire de la tâche d'instruction). Si `motivation` peut manquer, la
  calculer en amont par « Évaluer une expression » (comme `docMotivation`) : un rendu strict échoue sur une variable
  absente. Mettre à jour `DIFFICULTES.md` §4 (« Levé le … (PR #…, §123) ») et la consigne de la tâche `complement`.

### Bornes (H3, propriétés `app.notification.template.*`)

| Propriété | Défaut |
|---|---|
| `subject-max-chars` (gabarit) | 500 |
| `rendered-subject-max-chars` | 255 |
| `text-body-max-bytes` (gabarit) | 65536 |
| `html-body-max-bytes` (gabarit) | 262144 |
| `rendered-max-bytes` (somme des parties rendues) | 1048576 |
| `preview-model-max-bytes` | 65536 |

Ces propriétés ont des défauts : pas de variable d'environnement nouvelle, donc pas de mise à jour de `.env*.example`,
sauf si une propriété est exposée en `${…}` dans `application.yml`.

### Scénarios de test (dérivés des critères)

| Critère | Unitaire | Intégration | Chaîne HTTP et sécurité / MCP | Playwright |
|---|---|---|---|---|
| CS1 | use cases (transitions, archive automatique de l'active) | adaptateur JPA (index unique d'active, cascade) | `NotificationTemplateApiTest` : 403 pour `DOSSIER_MANAGER`, `WORKFLOW_ADMIN`, anonyme ; 201/200 pour `TENANT_ADMIN` | créer, aperçu, activer |
| CS2 | renderer (TEXT, HTML, sujet sans CR/LF) | process de test avec brique + GreenMail : objet, corps texte, partie HTML, `Auto-Submitted` | — | — |
| CS3 | renderer avec un champ sensible : échec | process avec type ayant un champ `sensitive` | — | — |
| CS4 | brique : variable absente → `NotificationValidationException` | asynchrone → incident, outbox vide ; synchrone → 422 | — | — |
| CS5 | `BlockAttributeRules` : quatre cas refusés ; variable `notificationTemplate` ignorée | déploiement refusé | — | — |
| CS6 | `NotificationHtmlPolicy` : `img`, `script`, `onclick`, `style` avec `url(`, `th:href`, `javascript:` refusés ; gabarit propre accepté | — | activation 400 avec le nom de la règle | — |
| CS7 | outils (brouillon, nouvelle version, dryRun) | — | appel MCP réel refusé hors `TENANT_ADMIN` | — |
| CS8 | expression `accuseReception` (variable absente → false) | process mail room : e-mail attesté → un e-mail ; papier → aucun ; non attesté → aucun | — | — |
| CS9 | résolution quatre niveaux | — | `/resolve` | — |
| CS10 | artefact de promotion | export puis import | — | — |
| Non-régression | brique sans gabarit inchangée ; outbox sans `htmlBody` lisible | tests existants verts | — | — |

## Lots

Chaque lot : une branche, une PR (titre Conventional Commits), `./mvnw verify` vert, `scripts/check-docs.sh` vert,
`cd frontend && npm run lint && npm run test:all` si le frontend change. Les lots 1 à 3 ne touchent pas le frontend.

| Lot | Contenu | Statut | PR |
|---|---|---|---|
| 1 | Domaine, persistance (changeSet 049), moteur TEXT, renderer, politique HTML, checker, API REST + gestionnaire d'exceptions. CS1 (sauf Playwright), CS3 et CS6 au niveau renderer et checker, CS9. Titre : `feat: gabarits de notification versionnés avec variables, objet et corps texte ou HTML` | livré (branche `feat/19-lot1-gabarits-domaine`) | voir PR lot 1 |
| 2 | Brique « Notifier » (`notificationTemplate`, `Variant`, `Locale`), `BlockAttributeRules`, catalogue v7 et source de valeurs, `ParcoursCompiler`, `NotificationEvent.htmlBody`, outbox, `EmailPort`, `SmtpEmailAdapter` multipart. CS2 à CS5. Titre : `feat: la brique Notifier envoie un e-mail rendu depuis un gabarit` | à faire | |
| 3 | Outils MCP (trois), `McpToolModes`, catalogue de l'assistant, promotion (H1). CS7, CS10. Titre : `feat: outils MCP et promotion des gabarits de notification` | à faire | |
| 4 | Écran d'administration, i18n, Vitest, Playwright, `GUIDE-UTILISATEUR.md`. CS1 (Playwright). Titre : `feat: écran d'administration des gabarits de notification` | à faire | |
| 5 | Exemples (accusé mail room, demande d'informations riverain), `GUIDE-CONCEPTION-BPMN.md` (§6c, §7, mise en garde données sensibles), spec 09 « Implémentation », spec 49, `identifiants.md`, §123 clos, revue indépendante de clôture, suppression de ce document. CS8. Titre : `feat: accusé de réception de la mail room et demande d'informations riverain par gabarit`. `Closes #19` | à faire | |

Le lot 2 dépend du lot 1 ; les lots 3 et 4 dépendent du lot 1 et peuvent être menés en parallèle (fichiers disjoints) ;
le lot 5 dépend de tous.

**Pause obligatoire avant décision irréversible :** le changeSet 049 crée une table (pas de transformation de données) :
pas de pause. L'ajout de champs à `EmailPort` et à la charge utile de l'outbox est rétrocompatible. Le catalogue de la
palette passe en version 7 : c'est un changement de contrat MCP additif, annoncé dans la PR.

Prochaine action : lots 2, 3 et 4 en parallèle, chacun depuis `feat/19-lot1-gabarits-domaine`.

## Preuves

| Date | Commande | Résultat |
|---|---|---|
| 2026-11-07 | `scripts/check-docs.sh` (cadrage) | ok |
| 2026-11-07 | lot 1 : `./mvnw verify -Dmaven.compiler.release=21 -Dspring.test.context.cache.maxSize=3` | Tests run: 4670, Failures: 0, Errors: 0, Skipped: 5 — BUILD SUCCESS |
| 2026-11-07 | lot 1 : `scripts/check-docs.sh` | ok |

## Revue de clôture

| Constat | Sévérité | Correctif ou issue |
|---|---|---|
| (lot 5) | | |
