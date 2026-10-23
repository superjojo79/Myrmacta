# §58 — Contre-revue adversariale post-merge PR #113 — H1 incomplet + 2 Medium

> **Statut :** clos · **Section :** [Points ouverts › Security](section-points-security.md) · ancien `TODO.md` §58 · [registre](README.md)

**Énoncé :** relecture adversariale du code réellement mergé par la PR #113 (pas seulement la
revue qui l'a produite), avec vérification empirique systématique avant correctif — un premier
correctif de H1 s'est lui-même révélé insuffisant à l'exécution (voir plus bas), confirmant
l'intérêt de la démarche.

| ID | Finding | Preuve | Fix |
|---|---|---|---|
| **H1-bis** | L'allowlist BPMN de D-H1 ne couvrait que `scriptTask`/listeners/`delegateExpression`. `userTask assignee`/`candidateUsers`/`candidateGroups`, `sequenceFlow conditionExpression` et `flowable:field` restent des expressions UEL évaluées par le moteur, non inspectées. **Confirmé par exécution réelle** (pas seulement lecture de code) : un `userTask flowable:assignee="${connectorServiceTask}"` passe l'allowlist telle quelle, se déploie et démarre sur le vrai moteur Flowable, et `task.getAssignee()` vaut `connectorServiceTask.toString()` — preuve que l'expression résout et invoque un bean Spring vivant du contexte applicatif. Une première correction n'autorisant qu'un identifiant simple (`${nom}`) s'est révélée tout aussi contournable : `${connectorServiceTask}` **est** un identifiant simple, syntaxiquement indiscernable d'une variable de process légitime — UEL retombe sur la résolution de bean par nom dès qu'aucune variable de ce nom n'existe. | `FlowableBpmnAllowlistGapTest` (échoue avant correctif définitif, passe après) ; `FlowableBpmnParserAdapterTest` (22 cas dont les deux tentatives de contournement) ; `FlowableBpmnParserAdapterShippedProcessesTest` (non-régression sur les 7 BPMN de production). | `FlowableBpmnParserAdapter` : `assignee`/`candidateUsers`/`candidateGroups` **littéraux uniquement** (aucun `${…}`/`#{…}`, sans exception — aucun BPMN livré n'en avait besoin) ; `conditionExpression` restreinte à une grammaire de comparaison sûre (identifiants/nombres/littéraux/opérateurs `> < >= <= == != && \|\|`, ni point ni parenthèse ni crochet ni `@`/`#`, et au moins un opérateur présent — un identifiant seul est la même ambiguïté) ; `flowable:field` interdit partout (aucun delegate de l'allowlist n'en a besoin aujourd'hui) ; `businessRuleTask` refusé (pas de moteur DMN dans la stack). |
| M-preview | `POST /api/documents/preview` et `/preview/pdf` (mode non-`live`) n'appliquaient pas le contrôle ACL par `templateKey` ajouté à `/generate` par D-M12 — un `DOSSIER_MANAGER` pouvait prévisualiser (HTML/PDF) n'importe quel template du tenant sans permission VIEW sur un type de dossier qui le liste. | `DocumentPreviewApiTest.previewHtml_dossierManagerWithoutViewType_isForbidden` / `previewPdf_...` (200 avant correctif, 403 après). | `DocumentGenerationController.assertCanPreviewByKey` — même règle que `assertCanGenerate`, appliquée avant le rate-limit dans `previewHtml`/`previewPdf` (mode `live` déjà réservé TENANT_ADMIN, donc non concerné). |
| M-webhook-dos | D-M6 (rate-limit après authenticate, pour ne plus verrouiller un intégrateur légitime par flood non signé) a supprimé toute limite sur les requêtes non authentifiées — un flot de requêtes à signature invalide n'est plus throttlé du tout avant PR #113 (coût HMAC/parsing/bande passante illimité). | `CallbackWebhookControllerTest`/`WebhookControllerTest.receive_invalidSecretFloodTripsPreAuthLimitBeforeAuthentication`. | Plafond pré-auth séparé (`ConnectorProperties.Webhook.preAuthRateLimitPerMinute`, `WebhookPreAuthRateLimiter`), toujours ≥ 10× le plafond post-auth (auto-corrigé dans le compact constructor du record si configuré plus bas) pour ne jamais redevenir le goulot d'étranglement du trafic légitime que D-M6 a supprimé. |

**Faux positifs (vérifiés empiriquement, pas seulement écartés à la lecture) :**

- **SSRF IPv4-mapped IPv6** — hypothèse initiale : une adresse IPv6 IPv4-mapped (`::ffff:x.x.x.x`) résolue par DNS échapperait aux plages privées d'`OutboundUrlGuard` (le check octets ne s'appliquant qu'aux vraies `Inet4Address`). **Infirmé par test réel** : `InetAddress.getByAddress`/`getByName` du JDK convertit *toute* adresse IPv4-mapped en `Inet4Address` dès la construction, quelle que soit la source (littéral ou résolveur) — vérifié avec un tableau d'octets brut simulant une réponse DNS. Test de régression permanent ajouté (`OutboundUrlGuardTest.hostnameResolvingToIpv4MappedIpv6Address_isRejected`), aucun correctif de code nécessaire.
- **`allowRestrictedHeaders` global** — le `System.setProperty` JVM-wide dans `HttpRestConnector` semblait un effet de bord non maîtrisé ; en fait déjà posé au tout début de `FlowForgeApplication.main()` (décision documentée **D-44**), et `HttpRestConnector`/`WebhookRestAdapter` sont les 2 seules classes du dépôt à utiliser `HttpURLConnection` (grep vérifié) — aucun autre composant ne peut être affecté.

**Limitation connue, non corrigée (décision architecture à trancher, pas tranchée ici) :**
`WebhookRequestRateLimiter`, `WebhookPreAuthRateLimiter` et le cache anti-rejeu de
`SharedSecretWebhookAuthenticator` sont en mémoire locale (`ConcurrentHashMap`), un pattern
déjà présent avant la PR #113 (pas une régression). Sous plusieurs instances d'un même tenant
derrière un load-balancer (scale-out horizontal), ces garde-fous ne sont pas partagés — un
rejeu ou un flood peut atteindre une instance différente dans la fenêtre de validité sans être
détecté. Pas de Redis dans la stack figée ; migrer vers un store partagé si le scale-out
horizontal par tenant devient réel (décision n° 11 : scaling horizontal déjà notée comme
limitation Flowable Community, cohérent).

**Impacte :** `FlowableBpmnParserAdapter`, `DocumentGenerationController`,
`ConnectorProperties`, `WebhookController`, `CallbackWebhookController`,
`WebhookPreAuthRateLimiter` (nouveau), `OutboundUrlGuardTest` (régression uniquement).
