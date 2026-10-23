# §57 — Revue adversariale 2026-09-07 — durcissement High / Medium

> **Statut :** clos · **Section :** [Points ouverts › Security](section-points-security.md) · ancien `TODO.md` §57 · [registre](README.md)

**Énoncé :** une revue adversariale du code livré a trouvé deux High et treize Medium encore
exploitables, plus des Low / risques déjà assumés. Ce n'est pas une nouvelle surface produit :
ce sont des gardes manquantes sur du code déjà en `main`.

## High

| ID | Finding | Preuves | Fix retenu (Q1–Q12 = A) |
|---|---|---|---|
| **H1** | RCE BPMN : `scriptTask` / `flowable:expression` / `flowable:class` non filtrés au deploy. Un `WORKFLOW_ADMIN` (ou MCP `deploy_workflow`) exécute du JUEL sur la JVM — même blast radius que l'ancien SSTI Thymeleaf (point 40), désormais sandboxé. | `FlowableWorkflowDeploymentAdapter.java` L48–54 ; `FlowableBpmnParserAdapter` ne lit que l'id de process ; `docs/guides/GUIDE-CONCEPTION-BPMN.md` interdit `flowable:class` **sans garde runtime** ; l'éditeur expose `flowable:expression`. | **D-H1** — à l'extract/deploy/save draft : refuser `scriptTask`, `flowable:class`, `flowable:expression`, listeners script ; allowlist des `delegateExpression` du catalogue (`connectorServiceTask`, `documentGenerationTask`, `attachGeneratedDocumentTask`, `notificationDispatchTask`, `createChildDossierTask`, `updateDossierStatusTask`, `updateWorkflowLinkStatusTask`). Payload d'exploit → `InvalidBpmnException` / 400. |
| **H2** | `POST /api/workflow-tasks/{taskId}/complete` ne vérifie que l'ACL **EDIT** du type, pas l'assignee. `taskService.complete` est non borné. Un manager EDIT peut terminer la tâche d'un autre ; la réassignation, elle, exige TA/WA. | `WorkflowTaskController.java` L74–84 ; `CompleteTaskUseCaseImpl.java` L56–61 ; `FlowableWorkflowEngineAdapter.completeTask`. | **D-H2** — complete exige `assignee == actor` **ou** TA/WA ; tâche sans assignee : EDIT suffit. 403 sinon. |

## Medium

| ID | Finding | Preuves | Fix retenu (Q1–Q12 = A) |
|---|---|---|---|
| **M1** | `RealmAccessAuthoritiesMapper` mappe **tous** les rôles Keycloak, y compris `API_SERVICE`. Un humain avec ce rôle realm obtient VIEW **et** EDIT tenant-wide (`DossierAccessPolicy` L63). Spec : `API_SERVICE` = M2M. | `RealmAccessAuthoritiesMapper.java` ; `DossierAccessPolicy.java` L63–64 ; `docs/guides/DEPLOIEMENT.md`. | **D-M1** — allowlist OIDC des rôles humains uniquement (`TENANT_ADMIN`, `WORKFLOW_ADMIN`, `DOSSIER_MANAGER`, `DOSSIER_VIEWER`). MCP continue d'injecter `API_SERVICE` via `McpAccessContextFactory` (D6 assumé, L1). Policy inchangée pour le User MCP. |
| **M2** | `APP_STORAGE_S3_ACCESS_KEY` / `SECRET` interpolés sous `app.dossier.attachment`, alors que `StorageConfig` lit `app.storage.s3`. Clés mortes ; client S3 = `DefaultCredentialsProvider`. | `application.yml` L192–193 ; `StorageProperties` / `StorageConfig.java` L39–47 ; `AttachmentUploadProperties` n'a que `allowedContentTypes`. | **D-M2** — déplacer `access-key-id` / `secret-access-key` sous `app.storage.s3`. |
| **M3** | `HttpRestConnector` pinne l'IP mais ne pose pas `Host` / vérif TLS du hostname d'origine (contrairement à `WebhookRestAdapter`). `applyHeaders` accepte un `Host` arbitraire. | `HttpRestConnector.java` L208–216, L263–272. | **D-M3** — miroir pin webhook : `Host` = hostname d'origine, TLS name, écraser `Host` appelant. |
| **M4** | `OutboundUrlGuard` ne bloque pas `100.64.0.0/10` (CGNAT) ni `0.0.0.0/8` hors `0.0.0.0` exact. | `OutboundUrlGuard.java` L169–180, L308–343. | **D-M4** — bloquer CGNAT + tout `0.0.0.0/8`. |
| **M5** | Clés de config connecteur hors schéma : ni chiffrement ni masque (`backupToken`, etc.). `mapping` est utilisé mais **absent du schéma** — le fix l'y ajoute. | `HttpRestConnector.validate` ; `EnvMasterKeyCipher.isPasswordKey` ; `ConnectorInstanceMcpView`. | **D-M5** — rejeter toute clé hors schéma ; déclarer `mapping` (optionnel) sur `http-rest`, `flowable-signal`, `flowable-callback`. |
| **M6** | Rate-limit webhook/callback **avant** l'auth : flood anonyme sur UUID connu → 429 du partenaire légitime. | `WebhookController.java` L69–85 ; `CallbackWebhookController`. | **D-M6** — charger l'instance + authentifier, **puis** `tryConsume`. Échec auth : ne pas débiter le bucket partenaire. |
| **M7** | HMAC signe `timestamp.body` mais pas l'`Idempotency-Key` (replay 5 min avec clés neuves). `SHARED_SECRET_TOKEN` : token statique, pas de fraîcheur. | `HmacWebhookAuthenticator.java` ; `SharedSecretWebhookAuthenticator.java`. | **D-M7** — HMAC = `timestamp + "." + idempotencyKey + "." + body` (**rupture de contrat** HMAC, comme F6). Shared-secret : cache anti-rejeu `(instanceId, sha256(body))` sur la fenêtre 5 min (même corps + clé d'idempotence neuve rejeté). Token volé + **nouveau** corps = vol de credential, hors scope. |
| **M8** | `GET /api/task-question-schemas/{key}/runtime?taskId=` vérifie formKey↔schema, pas l'appelant. | `TaskQuestionSchemaController.java` L137–141 ; `GetTaskQuestionSchemaForTaskUseCaseImpl`. | **D-M8** — même règle d'assignation que H2 (assignee ou TA/WA). |
| **M9** | Callback `correlateMessage` sans `assertNoReservedKeys`. `ConnectorServiceTask` préfère la variable process `tenantId` à `TenantProperties`. | `CallbackConnectorRuntimeImpl.java` L32–37 ; `ConnectorServiceTask.java` L35–38. | **D-M9** — `assertNoReservedKeys` sur le callback ; service task **toujours** `tenantProperties.id()`. |
| **M10** | SMTP : `to.contains("@")` accepte `a@x.com, attacker@evil.com` / CRLF. | `SmtpConnector.java` L57–64 ; `SmtpEmailAdapter`. | **D-M10** — `InternetAddress(..., true)`, un seul destinataire, rejet `,` / `\r` / `\n` sur `to`/`subject`. |
| **M11** | `http-rest` SERVICE : `BodyHandlers.ofString()` non borné. `document-storage` : decode Base64 avant `maxContentSize` ; `RETRIEVE` sans cap. | `HttpRestConnector.java` L229 ; `DocumentStorageConnector.java` L90–102. | **D-M11** — `maxResponseBytes` (défaut = `maxContentSize` 5 MiB) ; taille encodée avant decode ; cap retrieve. |
| **M12** | `POST /api/documents/generate` autorise `DOSSIER_MANAGER` par rôle, sans ACL de type ni lien template↔type. | `DocumentGenerationController.java` L50–60 ; `GenerateDocumentUseCaseImpl`. | **D-M12** — TA/WA inchangés ; `DOSSIER_MANAGER` seulement si au moins un `DossierType` VIEW dont `documentTemplateKeys` contient la clé. |
| **M13** | Preview HTML : `th:utext` / `[(${...})]` encore permis ; pas de CSP sur la réponse API (iframe SPA `sandbox=""` seulement). | `ThymeleafTemplateSyntaxChecker` ; `DocumentGenerationController` preview. | **D-M13** — interdire `th:utext` / interpolation non échappée au checker ; en-tête `Content-Security-Policy: sandbox` (et `default-src 'none'`) sur preview HTML. |

## Low / hors correctif code (ce cycle)

| ID | Sujet | Traitement |
|---|---|---|
| L1 | Tout JWT M2M = admin MCP (`McpAccessContextFactory` D6) | **Accepté** — un seul client M2M de confiance. Documenté, pas de split d'autorités. |
| L2 | Actuator `permitAll` si `MANAGEMENT_ADDRESS` n'est plus loopback | Défaut `127.0.0.1:8090` conservé (D-42). Pas de fail-fast (casserait un scrape k8s documenté). |
| L3 | Pas de CSP/HSTS globaux | Traité lot A4 ([PR #128](https://github.com/superjojo79/FlowForge/pull/128), TODO §63) ; durci en TODO §64 (revue adversariale post-merge). |
| L4 | AV no-op | Reliquat S-6. |
| L5 | Rate-limit JVM-local | Reliquat S-7. |
| L6 | `define_dossier_type` create dryRun sans construire le type | Hors périmètre (pré-existant PR #82, noté TODO §56 Phase 9). |
| L7 | `DeployBpmnProcessUseCaseImpl` sans garde domaine | REST `@PreAuthorize` + MCP `TenantAdminGuard` déjà présents ; H1 ajoute la garde **contenu**. |
| L8 | CSP `style-src 'unsafe-inline'` (lot A4, TODO §64) | **Accepté** — requis par PrimeVue. Risque résiduel d'exfiltration CSS pur (sélecteurs d'attributs) si une injection HTML/attribut existe ailleurs dans l'app. Pas de nonce CSS à ce jour. |

## Chaînes d'attaque

1. **H1 → secrets instance** — RCE BPMN remplace la chaîne SSTI × PG (S-1+S-2). L'isolation SQL (point 41) ne protège pas la JVM de **cette** instance.
2. **H2 + ACL de type** — l'EDIT d'équipe (décision 20) devient un hijack de workflow.
3. **M1** — humain `API_SERVICE` court-circuite H2 et toutes les ACL.
4. **M5 + MCP `get_connector_instance`** — secret hors schéma relu en clair.
5. **M7** — HMAC/token fuité → starts / dossiers en boucle (fenêtre 5 min ou illimitée en shared-secret).

## Faux positifs (non rouverts)

JWT dans le SPA ; `/api/webhooks/**` `permitAll` + HMAC ; IDOR UUID dossier (ACL de type, décision 20) ; SSTI `${T(Runtime)}` (D-40) ; SSRF PDF ; SQLi ; path traversal storage ; XXE BPMN converter ; actuator loopback (D-42).

**Livré :** [PR #113](https://github.com/superjojo79/FlowForge/pull/113) mergée.
Prévenir les partenaires HMAC (`timestamp.Idempotency-Key.body`, Q5=A).

**Impacte :** parser BPMN, complete tâche, OIDC mapper, `application.yml` storage, `HttpRestConnector`, `OutboundUrlGuard`, CRUD connecteurs, webhooks HMAC, schemas runtime, callback + `ConnectorServiceTask`, SMTP, document-storage, generate PDF, syntax checker templates.

**Détail :** cette section. Artefacts lifecycle : `docs/ai/{requirements,design,planning,implementation,testing}/2026-09-07-feature-revue-adversariale-securite.md`.
