# File d’attente produit — FlowForge

> Ordre recommandé des **prochaines features** après les lots lifecycle déjà
> livrés. À relire **avant** de choisir la feature suivante (même critères
> qu’aux analyses 2026-08-17 / 2026-08-18).
>
> - Points ouverts à trancher : [TODO.md](TODO.md)
> - Inventaire follow-ups / lots : [DEFERRED-FEATURES.md](DEFERRED-FEATURES.md)
> - Séquencement « avant prod » : [REVUE-PRE-IMPLEMENTATION.md](REVUE-PRE-IMPLEMENTATION.md) §9
>   (rétention / purge / sensible — **livré**) ; revue post-implémentation 2026-08-25 :
>   [REVUE-ALIGNEMENT-SECURITE.md](REVUE-ALIGNEMENT-SECURITE.md) (TODO §40–46)
>
> **Dernière mise à jour :** 2026-09-21 — Lot **C** (`scan-antivirus-clamav`) : cycle
> `dev-lifecycle` **Phases 1-9 complet** + **revue adversariale (sous-agent)** demandée
> explicitement par l'utilisateur après la clôture, sur le modèle des revues adversariales déjà
> pratiquées dans ce dépôt (connecteur callback, `extension-mcp-flux-metier`). **2 findings réels
> trouvés et corrigés**, tous les deux reproduits indépendamment avant correction (pas une
> confiance aveugle dans le rapport du sous-agent) :
>
> - **Sévérité haute** — `socket.setSoTimeout(...)` ne borne que les `read()` Java, jamais les
>   `write()` : un `clamd` vivant mais lent à drainer (charge légitime, pas besoin de le
>   compromettre) bloquait indéfiniment le thread de requête HTTP à l'écriture — épuisement du
>   pool de threads Tomcat avec assez d'uploads concurrents, application entière injoignable.
>   Reproduit : un test encadré d'un `@Timeout` JUnit est resté bloqué et a dû être tué au niveau
>   du process (confirme au passage que `Thread.interrupt()` n'a aucun effet sur un
>   `java.io.Socket` bloqué en écriture). Corrigé par un thread watchdog qui ferme la socket au
>   -delà du budget.
> - **Sévérité moyenne** — `"stream: FOUND"` (chevauchement préfixe/suffixe sur l'espace
>   partagé, 8+6=14 > 13 caractères) faisait lever `StringIndexOutOfBoundsException`, une
>   `RuntimeException` non rattrapée par le `catch (IOException)`, cassant l'invariant fail-open
>   documenté par la classe elle-même. Corrigé par une garde de longueur avant le `substring`.
>
> + 2 remarques mineures corrigées en défense en profondeur (port `clamav` non authentifié
> documenté dans `docker-compose.yml`, sanitisation `\r`/`\n` des logs de fail-open contre
> l'injection de fausses lignes). 1 négatif confirmé (valeur `type` invalide = échec net du
> démarrage Spring, comportement voulu, rien à corriger). Ce que le sous-agent n'a pas pu
> trancher, faute de Docker dans ce bac à sable : existe-t-il un fichier qui ferait
> planter/geler un **vrai** moteur ClamAV de façon déterministe (donc un bypass fail-open à la
> demande) — à vérifier avec `ClamAvAttachmentScanIT` en environnement Docker.
>
> **Preuve finale** : `./mvnw verify -Dskip.frontend=true` → **2008 tests, 0 échec**,
> `ClamAvAttachmentScanAdapter` toujours à 100% couverture branche après les 4 correctifs. Diff
> nul reconfirmé sur les 3 fichiers gelés du port. **Prêt pour PR** — seule action restante avant
> « livré » : une entrée `CHANGELOG.md` au moment de l'ouverture de la PR (non demandée à ce
> stade). Détail :
> [docs/ai/implementation/2026-09-20-feature-scan-antivirus-clamav.md](docs/ai/implementation/2026-09-20-feature-scan-antivirus-clamav.md).
> Développement direct sur la branche de session `claude/funny-johnson-x0er1h`, pas de worktree
> séparé (précédent du dépôt, cf. `extension-mcp-flux-metier`).
>
> **2026-09-21 (plus tôt, Phase 9)** — Branche fusionnée avec `main` (lot A4
> `headers-http-globaux`, PR #128, mergé en parallèle du cycle — 15 commits de retard, zéro
> conflit réel vérifié via `git merge-tree` avant d'agir). Suite complète re-vérifiée après
> fusion : `./mvnw verify` → 2004 tests, 0 échec ; `npm run test:e2e` → 76/76.
>
> **2026-09-21 (plus tôt)** — Phase 8 (testing) close : suite complète `./mvnw verify` (frontend
> inclus) → 1982 tests, 0 échec ; `npm run test` → 352/352 Vitest ; `npm run test:e2e` → 72/72
> Playwright (contournement `PLAYWRIGHT_CHROMIUM_EXECUTABLE` pour un décalage de version du
> binaire pré-installé, mécanisme déjà prévu par `playwright.config.ts`), incluant
> explicitement `E2E-17`/`E2E-19` (upload de pièce jointe). Note d'environnement sans rapport
> avec ce lot : `mvn clean verify -Dskip.frontend=true` (options combinées) fait échouer
> `OpenApiDisabledApiTest`, artefact pré-existant non corrigé (hors périmètre).
>
> **2026-09-20 (plus tôt)** — Lot **C** : Phases 1-3 closes. Objectif explicite utilisateur :
> implémentation de référence ClamAV sans modifier le port existant (D-AV, PR #62), swappable
> vers un autre moteur AV plus tard. Produit/sécurité (Phases 1-2, `AskUserQuestion`) : `clamd`
> en TCP natif `INSTREAM`, **fail-open** si indisponible/timeout (log + compteur), `clamd`
> **mutualisé** entre tenants, activation **opt-in** par défaut désactivée, IT Testcontainers
> **isolée** du run standard, **signal de santé Micrometer actif exigé** sur le fail-open (un
> log seul jugé insuffisant), `clamd` en prod = **service externe déjà provisionné** (modèle
> PostgreSQL/Keycloak). Architecture (Phase 3, tranchées par alignement sur des patterns déjà
> établis dans le dépôt, sans nouvelle question produit) : timeouts 5s connexion+lecture (aligné
> `ConnectorProperties.Http`), image `clamav/clamav:1.4` épinglée (comme
> `postgres`/`keycloak`/`kafka`), mise à jour des signatures via le mécanisme natif de l'image
> (`FRESHCLAM_CHECKS`), `clamd.conf` `StreamMaxLength` aligné sur
> `APP_DOSSIER_ATTACHMENT_MAX_SIZE_MB`, activation via propriété
> `app.dossier.attachment.scan.type=noop|clamav` **calquée sur `app.storage.type`**
> (`StorageConfig`), métrique nommée `flowforge.attachment.scan.fail_open` (convention
> `flowforge.<bc>.<sujet>.<événement>` du dépôt). 2 clarifications réutilisables stockées en
> mémoire AI DevKit (`repo:FlowForge`).
>
> **2026-09-20 (plus tôt)** — Vague B (**B1**, `connecteurs-cible`) **livrée** : cycle
> `dev-lifecycle` Phases 1-9 complet, 2 nouveaux connecteurs `batch-import` (SFTP, CSV/Excel) et
> `kafka` (TRIGGER) dans un seul cycle comme prévu, TODO §62 clos. Revue adversariale post-cycle
> (2 vulnérabilités réelles corrigées : TOCTOU `maxFileBytes`, absence de plafond d'instances
> `kafka` par tenant) + 3 bugs de test préexistants sans rapport trouvés et corrigés en cours de
> route. `./mvnw verify` 2040 tests (0 échec, `Test*` + `*IT.java` sans exclusion), Vitest 352/352,
> Playwright 72/72. **Mergée sur `main`** ([PR #129](https://github.com/superjojo79/FlowForge/pull/129),
> CI verte, 2026-09-20, sur demande explicite utilisateur). Lot **A4**
> `headers-http-globaux` **livré**
> ([PR #128](https://github.com/superjojo79/FlowForge/pull/128), TODO §63 clos,
> CI `build` + `frontend` verte, merge `8415cee`, 2026-09-20).
> **File produit désormais vide** (Vagues A et B + A4 livrés), hors lot C
> (ClamAV, en cours sur une autre surface) et hors cycles
> `enrichissement-reporting` (TODO §65) et `outillage-dmn` démarré le
> 2026-09-22 (TODO §66, Phase 1) — surfaces disjointes du lot C.
>
> **2026-09-22** — exception explicite : `outillage-dmn` (document 28,
> roadmap 1.1 + 1.2 + 1.3/5.1) entre en Phase 1 `dev-lifecycle` (TODO §66).
> Cinq décisions produit tranchées ; docs
> `docs/ai/*/2026-09-22-feature-outillage-dmn.md`.
>
> **2026-09-14** — Revue de `ROADMAP.md` v1.1 : les 8 items marqués « Produit, pas
> cadré » (portail externe, signature électronique, inbox notifications, alerting SLA, dashboard
> widgets, export BI, `evaluate_decision` MCP, historique/rollback DMN) reçoivent chacun une
> **analyse initiale** écrite, regroupés par cohérence technique/thématique en **4 documents** :
> [26-portail-externe-signature.md](analysis%20doc/26-portail-externe-signature.md) (3.1 + 2.4),
> [27-notifications-inbox.md](analysis%20doc/27-notifications-inbox.md) (3.2),
> [28-outillage-dmn.md](analysis%20doc/28-outillage-dmn.md) (1.1 + 1.2 + 1.3/5.1),
> [29-enrichissement-reporting.md](analysis%20doc/29-enrichissement-reporting.md) (5.2 + 3.3 + 5.3).
> ClamAV (Vague C) et A4 headers HTTP globaux **ne sont pas concernés** — déjà scopés avec
> propriétaire/dépendances connus, pas besoin de cadrage produit. **Aucune décision de priorité
> n'est prise ici** : ces 4 items restent en *Gaps réels hors Vague B* (`ROADMAP.md`), à ne
> démarrer qu'après B1 (et C si concerné), chacun via son propre cycle Phase 1 → 9.
> **2026-09-21** — exception explicite : `enrichissement-reporting` (document 29)
> est lancé (TODO §65). Le lot C reste en cours sur une autre surface ; ce
> cycle ne la croise pas.
>
> 2026-09-14 — Vague A confirmée **intégralement livrée** :
> **A1** (`provisioning-tenant`, [PR #119](https://github.com/superjojo79/FlowForge/pull/119)),
> **A2** (`admin-connector-form-schema`, [PR #117](https://github.com/superjojo79/FlowForge/pull/117))
> et **A3** (`mcp-ecritures-metier`, [PR #118](https://github.com/superjojo79/FlowForge/pull/118))
> tous **mergés sur `main`** (vérifié via l'API GitHub : `merged: true` pour les 3 PR). TODO §60
> clos.
>
> 2026-09-11 — TODO **§61 livré** : introduction de DMN, cycle
> `dev-lifecycle` complet (analyse
> [25-introduction-dmn.md](analysis%20doc/25-introduction-dmn.md) → requirements → design →
> planning → implémentation, M1-M12). Le moteur DMN Flowable est activé, un
> `serviceTask flowable:type="dmn"` invoque une table de décision via
> `decisionTableReferenceKey` (pas le `businessRuleTask` standard, câblé sur Drools et toujours
> rejeté au parsing). Sécurité : Flowable évalue les cellules de table de décision en **JUEL**, pas
> FEEL — `FlowableDmnParserAdapter` applique une allowlist stricte (miroir du durcissement BPMN
> H1/H1-bis, TODO §57/§58), prouvée par exécution réelle sur le moteur
> (`DmnJuelSecuritySmokeIT`), pas seulement par lecture statique. Éditeur intégré `dmn-js`
> (`/admin/dmn-editor`), MCP (`deploy_decision_table`/`list_decision_tables`/
> `get_decision_table`), scénario illustratif livré (`grille-eligibilite-client.dmn`).
>
> 2026-09-08 — **A1 (`provisioning-tenant`) mergé**
> ([PR #119](https://github.com/superjojo79/FlowForge/pull/119)). **A2**
> `admin-connector-form-schema` mergé ([PR #117](https://github.com/superjojo79/FlowForge/pull/117)).
> **A3** `mcp-ecritures-metier` mergé le jour même, 21:17
> ([PR #118](https://github.com/superjojo79/FlowForge/pull/118)) — Vague A **intégralement
> livrée**.
>
> 2026-09-08 — TODO **§31 (volet outillage)/§41 (volet outillage) clos**, lot **A1**
> `provisioning-tenant` : `Dockerfile` (`eclipse-temurin:25-jdk`/`-jre`, non-root,
> `-XX:MaxRAMPercentage`) + `scripts/provision-tenant.sh` (schéma/rôle PostgreSQL par tenant,
> D-41) + activation `server.servlet.context-path` (décision n°14) hors profil `local`. 4
> décisions Dockerfile tranchées via `AskUserQuestion` en Phase 1 (DEPLOIEMENT.md avertissait
> qu'un Dockerfile sans passer par le processus de décision reviendrait à trancher
> silencieusement — donc posées explicitement plutôt que devinées). **2 bugs réels trouvés et
> corrigés** : clé YAML `server:` dupliquée (`application.yml`, cassait le parsing SnakeYAML,
> détecté par `./mvnw verify` lui-même) ; troncature silencieuse possible du rôle PostgreSQL
> généré au-delà de `NAMEDATALEN` (63 octets) pour un `tenant_id` proche de l'ancienne borne —
> corrigé en bornant `tenant_id` à 53 caractères, trouvé en Phase 7 (relecture ligne à ligne),
> ni `hadolint` ni `shellcheck` ne l'auraient détecté. **Limite d'environnement assumée et
> documentée** (pas de contournement) : Docker Hub inaccessible dans ce bac à sable (politique
> réseau de la session rejette `production.cloudfront.docker.com`, constaté systémique) —
> `docker build`/`docker run` réel non exécuté, `Dockerfile` validé par `hadolint` (propre) +
> revue statique seulement, à confirmer par un build réel avant premier déploiement. Le script
> de provisioning, lui, a été validé avec de vraies preuves (service PostgreSQL local du bac à
> sable) : isolation cross-tenant vérifiée avec un second tenant réel (`permission denied`
> confirmé), tous les scénarios d'erreur exécutés. `./mvnw verify` **1738 tests (0 échec)**.
> Détail complet : TODO §31/§41,
> [docs/ai/implementation/2026-09-08-feature-provisioning-tenant.md](docs/ai/implementation/2026-09-08-feature-provisioning-tenant.md).
>
> 2026-09-07 — TODO **§57 clos**, cycle
> `revue-adversariale-securite` **mergé** sur `main`
> ([PR #113](https://github.com/superjojo79/FlowForge/pull/113), Q1–Q12 = A).
> CI verte (`e96121a`). Contre-revue post-merge §58 livrée
> ([PR #115](https://github.com/superjojo79/FlowForge/pull/115)).
>
> 2026-09-06 — TODO **§56 clos**, cycle `dev-lifecycle` **complet
> (Phases 1 à 9) + revue adversariale post-Phase 9** sur `extension-mcp-flux-metier` — 16 tools MCP
> au total (9 → 16 : connecteurs + questions de tâche + `define_dossier_type` étendu). Écarts réels
> trouvés et corrigés au fil des phases : 3 en Phase 5, 1 en Phase 7, 3 en Phase 8 (Jacoco classe
> par classe). **Revue adversariale (sous-agent) post-Phase 9** : **1 faille bloquante réelle
> trouvée et corrigée** — `authHeaderValue` (credential HTTP réel du connecteur `http-rest`)
> n'était reconnu par aucune heuristique de détection de secret par nom, donc jamais chiffré en
> base ni masqué en lecture ; corrigé en basant la détection sur le schéma déclaré du connecteur
> (`ConnectorCatalog.isPasswordField`, source de vérité unique) plutôt que sur des heuristiques de
> nom dupliquées 3 fois. Piège trouvé en cours de correctif : une dépendance circulaire Spring
> réelle (non détectable par lecture statique), corrigée par `@Lazy`. + 2 findings mineurs corrigés
> (garde-fou non réaffirmé en dryRun sur `define_connector_instance` create ; casts non défensifs)
> + 1 risque documenté sans correctif (confiance « tout ou rien » du JWT M2M, décision assumée).
> `./mvnw verify` final : **1675 tests, 0 échec**, ArchUnit conforme, couverture Jacoco tenue ;
> Vitest 289/289 (0 régression, zéro fichier frontend touché) ; Playwright non exécutable dans ce
> bac à sable (binaire manquant, pré-existant, non bloquant). **Cycle mergé** :
> [PR #111](https://github.com/superjojo79/FlowForge/pull/111), sur `main`, sur demande explicite
> de l'utilisateur **sans attendre la CI GitHub Actions** (crédits épuisés) — décision basée sur la
> vérification locale fraîche ci-dessus. Détail complet dans TODO §56 et
> [docs/ai/implementation/2026-09-06-feature-extension-mcp-flux-metier.md](docs/ai/implementation/2026-09-06-feature-extension-mcp-flux-metier.md).
>
> 2026-09-06 (plus tôt) — Phase 3 (design) close. 6 décisions tranchées en Phase 1 via
> `AskUserQuestion` (périmètre un seul cycle, flux du secret connecteur, extension de
> `define_dossier_type`, `dryRun` partout, 7 noms de tools) + une trouvaille réelle : ni
> `ManageConnectorInstanceUseCaseImpl`/`TaskQuestionSchemaUseCaseImpl` ni
> `WorkflowTypeConfigUseCaseImpl` (déjà utilisé par `deploy_workflow`, PR #82) n'ont de garde de
> rôle au niveau domaine — décision d'ajouter le garde-fou partout, y compris ce correctif sur du
> code déjà en production. **Phase 2** a précisé l'asymétrie exacte du garde-fou par use case
> (D-MCP-EXT-RBAC-SCOPE : `WorkflowTypeConfigUseCaseImpl` garde uniquement `create`/`update`, ses
> lectures restent ouvertes comme le REST — pas de régression sur `list_workflow_types`). **Phase 3**
> a tranché le dernier point technique (D2) : `TenantAdminGuard` (`domain.dossier`) gagne une
> méthode `requireTenantOrWorkflowAdminOrApiService`, réutilisée par `domain.connector`/
> `domain.workflow` plutôt que dupliquée ou déplacée — aucune règle ArchUnit ne s'y oppose, et un
> précédent déjà accepté existe (`domain.workflow` → `domain.dossier.model.FieldDefinition`, TODO
> §55). Le compte d'interfaces `port.in` à modifier est passé de 4 à 5 en cours de revue
> (`GetTaskQuestionSchemaUseCase` en a aussi besoin). Documents :
> [docs/ai/requirements/2026-09-06-feature-extension-mcp-flux-metier.md](docs/ai/requirements/2026-09-06-feature-extension-mcp-flux-metier.md),
> [docs/ai/design/2026-09-06-feature-extension-mcp-flux-metier.md](docs/ai/design/2026-09-06-feature-extension-mcp-flux-metier.md),
> [docs/ai/testing/2026-09-06-feature-extension-mcp-flux-metier.md](docs/ai/testing/2026-09-06-feature-extension-mcp-flux-metier.md).
> Détail TODO §56, analyse d'origine
> [24-extension-mcp-flux-metier.md](analysis%20doc/24-extension-mcp-flux-metier.md). Suite : Phase 4
> (planning). Mode workspace : no-worktree, développement direct sur la branche de session
> `claude/mcp-changes-business-flow-ytc3j8` (confirmé par l'utilisateur, cohérent avec toutes les
> features précédentes de ce dépôt).
>
> 2026-09-05 — TODO **§55 clos** : `questions-utilisateur-taches`,
> cycle `dev-lifecycle` Phases 1–9 complet (M1–M9 enchaînés en TDD sur demande explicite), plus une
> **analyse adversariale post-Phase-9** (subagent dédié, demande explicite de l'utilisateur).
> Nouveau 3ᵉ kind de tâche
> `user-question:<schemaKey>` (`taskFormRegistry.ts`, TODO §52) : un concepteur de workflow
> définit un mini-formulaire admin (**Admin → Questions de tâche**) qu'un `userTask` affiche avant
> complétion, indépendamment du dossier — réponse en variables de process uniquement, tracée
> (`TaskQuestionAnswer`). **Compromis d'architecture tranché avec l'utilisateur** : la validation
> serveur résout le schéma en lisant `formKey` côté serveur (nouveau
> `WorkflowEnginePort.findFormKeyByTaskId`), jamais depuis une variable client — exception
> documentée et bornée au principe « `formKey` opaque » hérité de TODO §52. **2 gaps réels trouvés
> en revue requirements** (Phase 2) : `CompleteTaskUseCase.complete(...)` ne transmettait aucune
> identité d'appelant ; l'endpoint runtime n'avait aucun `DossierType`/`VIEW` à vérifier — corrigés
> (`submittedBy`, garde par `taskId`). Prouvé contre le **vrai** moteur Flowable
> (`UserQuestionFormSmokeIT`) : la passerelle exclusive lit effectivement la variable produite.
> **1 écart réel trouvé en Phase 7** (revue d'implémentation) : la validation ne couvrait que
> `required`/`SELECT`, acceptait une valeur non conforme sur un champ `NUMBER`/`DATE`/`BOOLEAN` —
> corrigé par réutilisation de `DossierFieldValidator`. **4 écarts réels trouvés en Phase 8**
> (balayage Jacoco classe par classe) : 2 branches non couvertes complétées (schéma supprimé après
> résolution du `formKey` ; 404 sur `schemaKey` inconnu en update), `TaskQuestionFormKeys.formKeyFor`
> était du code mort côté production — de vrais appelants lui ont été donnés côté tests plutôt que
> des littéraux dupliqués, `TaskQuestionAnswerJpaEntity.toDomain()` supprimée (0% de couverture,
> aucun appelant, cohérent avec le write-only de `TaskQuestionAnswerRepository`). **Bug réel trouvé
> et corrigé après Phase 9** (analyse adversariale) : le schéma ne bornait pas réellement les
> variables de process — `allowUnknownFields` codé à `true` et les `variables` brutes (pas la map
> `answers` filtrée) transmises au moteur, si bien qu'une clé absente du schéma (ex. `decision`)
> pouvait piloter une passerelle exclusive ailleurs dans le workflow sans jamais apparaître dans la
> trace `TaskQuestionAnswer` — corrigé (rejet 400 des clés hors schéma, transmission de la map
> filtrée au moteur). `./mvnw verify`
> 1592 tests (0 échec) ; Vitest 289/289 ; Playwright 65/65 (63 existants + 2 nouveaux depuis
> Phase 5, zéro régression). Analyse d'origine :
> [23-questions-utilisateur-taches.md](analysis%20doc/23-questions-utilisateur-taches.md).
> `CHANGELOG.md` différé (aucune PR ouverte à ce stade). File toujours vide, aucune feature en
> cours.
>
> 2026-09-03 — TODO **§52 clos** : `formulaires-dynamiques-taches`,
> cycle `dev-lifecycle` Phases 1–9 complet. Un `userTask` BPMN peut désormais déclarer
> `flowable:formKey` pour afficher un formulaire de tâche avant complétion — schéma complet du
> dossier (`dossier-fields`) ou choix d'un document parmi ceux associés au type de dossier
> (`document-generation`, nouvelle section Admin → Types). Mécanisme volontairement extensible côté
> frontend uniquement (`taskFormRegistry.ts`), `formKey` reste une chaîne opaque de bout en bout
> côté backend — décision utilisateur au lancement du cycle. **Écart de sécurité trouvé en Phase 3**
> (design) : `GET /api/document-templates` est `TENANT_ADMIN`-only, inutilisable par un porteur de
> tâche — corrigé par un nouvel endpoint dédié (jamais `htmlContent`, même contrôle `VIEW` que
> `GET /api/dossier-types/{code}`). **Bug réel latent trouvé en Phase 5** (test Playwright réel, pas
> mocké) : le tout premier attribut `flowable:*` posé sur un diagramme sans `xmlns:flowable` déjà
> déclaré — dont le gabarit BPMN vierge de l'éditeur — se perdait silencieusement à
> l'enregistrement (latent depuis `edition-bpmn`, TODO §39, jamais détecté faute de round-trip réel
> précédent) ; corrigé. Choisie comme feature après nouvel épuisement du backlog (file vide, seul
> candidat éligible — le provisioning tenant reste hors file produit). `./mvnw verify` 1572 tests
> (0 échec) ; Vitest 269/269 ; Playwright 63/63 (59 existants + 4 nouveaux, zéro régression).
>
> 2026-09-03 — TODO §51, **§53 et §54 clos** : nouveau connecteur
> générique `flowable-callback` (capability **CALLBACK**), `connecteur-callback`, mergé sur `main`
> sans attendre la CI hébergée (demande explicite utilisateur,
> [PR #102](https://github.com/superjojo79/FlowForge/pull/102)), puis **deux passes** de revue de
> sécurité adversariale demandées par l'utilisateur sur le code déjà mergé. 1ère passe : 5 écarts
> réels trouvés (F0–F4), dont un **critique** — `/api/callbacks/**` était absent de tout
> `securityMatcher` Spring Security (tombait dans le chaînage BFF authentifié, 403 avant même le
> contrôleur), **la feature livrée était totalement inatteignable en production**, non détecté par
> les 1533 tests alors verts. Corrigés le jour même
> ([PR #103](https://github.com/superjojo79/FlowForge/pull/103), 1539 tests) ; le défaut latent
> partagé laissé en dette (§53, `WebhookController`) corrigé le lendemain
> ([PR #104](https://github.com/superjojo79/FlowForge/pull/104), 1541 tests, merge conditionné à
> une CI locale verte). **2ème passe** (§54, même jour, « lance une analyse complète de type
> adversarial ») : 4 nouveaux écarts (F5–F8) — DoS mémoire non authentifié (corps de requête
> bufférisé en entier avant toute garde applicative), absence de protection anti-rejeu HMAC
> (rupture de contrat pour les appelants existants), aucune longueur minimale sur `webhookSecret`,
> échecs d'authentification jamais journalisés. Tous corrigés sur demande explicite
> (« corrige tout maintenant »), `./mvnw verify` 1554 tests, 0 échec. Détail complet :
> `docs/ai/implementation/2026-09-02-feature-connecteur-callback.md` § « Correctifs post-merge »,
> table ci-dessous, et TODO §51/§53/§54.
>
> 2026-09-01 — TODO §50 **clos** : nouveaux connecteurs hors V1
> (`scheduler`, `smtp`, `document-storage`) livrés, `connecteurs-hors-v1`, cycle `dev-lifecycle`
> Phases 1–9 complet. `s3-minio` généralisé en `document-storage` (réutilise
> `DocumentStoragePort`, pas de couplage S3 direct, demande explicite utilisateur). Choisie comme
> feature après nouvel épuisement du backlog (TODO.md 49/49, revue sécurité §40–46 close,
> `assainissement-dette-technique` TODO §49 livrée
> [PR #97](https://github.com/superjojo79/FlowForge/pull/97)) parmi deux pistes proposées à
> l'utilisateur (l'autre — outillage provisioning tenant — reste en réserve). **Mergée sur `main`**
> ([PR #99](https://github.com/superjojo79/FlowForge/pull/99), 2026-09-01).
>
> TODO §52 **ouvert** (2026-09-01) : formulaires dynamiques rattachés aux tâches BPMN (`userTask`),
> analyse écrite
> [22-formulaires-dynamiques-taches.md](analysis%20doc/22-formulaires-dynamiques-taches.md) —
> origine question utilisateur, pas encore priorisé ni tranché en `dev-lifecycle`. File toujours
> vide, aucune feature en cours.
>
> TODO §49 (`assainissement-dette-technique`) **clos**, cycle `dev-lifecycle` Phases 1–9 complet,
> **mergée sur `main`** ([PR #97](https://github.com/superjojo79/FlowForge/pull/97),
> [PR #98](https://github.com/superjojo79/FlowForge/pull/98) resync doc).
>
> TODO §46 (ArchUnit `application`) **clos** hors cycle `dev-lifecycle`
> formel : port `WorkflowStartConfirmationPort` extrait, règle ArchUnit
> `application_never_depends_on_infrastructure` ajoutée. `serveur-mcp`
> **livrée**
> ([PR #82](https://github.com/superjojo79/FlowForge/pull/82), mergée,
> Phases 5–9 complètes) et `schema-champs-dynamiques` **livrée**
> ([PR #87](https://github.com/superjojo79/FlowForge/pull/87), mergée),
> TODO §47 clos. **File d'attente vide** — aucune feature en cours,
> prochaine feature à identifier lors d'une prochaine analyse.
>
> `derive-documentaire` TODO §45 clos, [PR #75](https://github.com/superjojo79/FlowForge/pull/75) ;
> `edition-bpmn` TODO §39 clos, [PR #71](https://github.com/superjojo79/FlowForge/pull/71) **mergée**.
> `masquage-donnees-sensibles-logs-webhooks` PR #70 mergée. Hors cycle
> `dev-lifecycle` (PR #72) : DEV-PURGE, recovery `purge_outbox`
> `PROCESSING`, `CHECK` SQL `dossier_type_acl.permission`, chore
> `com.monapp` dans `01-architecture-hexagonale.md`, gestion d'erreur
> `useBpmnDraft.load()` — voir « Hors file produit ». Revue alignement ×
> sécurité actée (TODO §40–46,
> [REVUE-ALIGNEMENT-SECURITE.md](REVUE-ALIGNEMENT-SECURITE.md)).

## Comment s’en servir

1. Ne pas relancer une feature marquée **Livré** / **En revue**.
2. Prendre le premier item **Prochain** (ou l’alternative si le prochain est
   bloqué par un point TODO « à décider »).
3. Ne **pas** trancher silencieusement un point ouvert de `TODO.md`.
4. Après clôture d’une feature : déplacer la ligne vers *Récemment livré*,
   recalculer *Prochain*, dater ce fichier.

## Critères de sélection (stables)

Même grille que pour `preview-documents` puis `edition-champs-dossier` :

| Priorité du critère | Règle |
|---|---|
| 1 | Lot **nommé** déjà spécifié (analyse / DEFERRED « lot suivant ») |
| 2 | **Pas** de décision produit encore ouverte (`TODO.md` « À décider ») |
| 3 | Parcours **métier utilisateur** (API + UI) plutôt qu’un contrat transverse |
| 4 | REVUE §9 « avant prod » (rétention §21, purge §22, sensible §23) — seulement si les arbitrages sont tranchés ou acceptés en phase requirements |

## Récemment livré (ne pas replanifier)

| Feature | Réf. | Notes |
|---|---|---|
| `preview-documents` | PR #52, lot D-PREVIEW | Aperçu HTML/PDF admin templates |
| `edition-champs-dossier` | PR #53 | UI `PATCH /api/dossiers/{id}/fields` sur le détail (D-DIFF, D-DEFAULTS) |
| `retention-dossier-type` | PR #55, TODO §21 | Défaut tenant + override type ; job ARCHIVE + `purge_outbox` ; UI admin |
| `purge-stockage` | PR #56, TODO §22 | Job `purge_outbox` store+PG ; `DocumentStoragePort.delete` ; file admin lecture + enqueue |
| `openapi-pagination-admin` | PR #58, TODO §24 | Envelope 6 GET admin ; OpenAPI golden + flag off ; pagers SPA ; écran webhooks |
| `observabilite` | PR #59, TODO §29 | ECS + MDC ; Micrometer port management ; snapshot + SPA admin |
| `durcissement-donnees-sensibles` | PR #62, TODO §23 (reste réduit)/§30 (clos) | Magic-bytes + `AttachmentScanPort` no-op sur upload ; export CSV masqué (`GET /api/dossiers/export.csv`) ; skeleton `HttpRestServiceConnector` non enregistré ; garde-fous anti-fuite secrets ; injection de formule CSV trouvée et corrigée en revue |
| `contrat-api-client` | PR #65, TODO §24 (clos) | 6 listes non-admin migrées vers `PageResponse` (elements, dossiers/workflows, workflow-tasks/mine, connector-definitions, workflow-links history+tasks) ; 3 endpoints `@Hidden` documentés (SSE, multipart, webhook) ; pagination rendue visible en UI pour 5/6 listes (`WorkflowLinksList.vue` nouveau — liste jamais affichée avant) ; codegen client resté hors V1 ; 2 bugs réels trouvés et corrigés en Phase 7 (tri déclaré/réel) ; 2 régressions e2e réelles trouvées et corrigées en Phase 8 ; D-SPA-ADAPT rejouée avec l'utilisateur après un manquement Phase 3 (aucune question matérielle posée) |
| `fiabilisation-tests-plateforme` | PR #66, TODO §26 (clos), §28 (clos), §37 (clos) | Testcontainers ciblé (isolation `search_path`, séquence multi-pods — écrits/compilés, non exécutés dans ce bac à sable sans Docker, CI seulement) ; verrous Flowable + réservation workflow concurrente sous H2 + vrais threads (bug réel trouvé et corrigé : `DataIntegrityViolationException` brute traitée comme retry transitoire au lieu de dead-letter) ; migration Liquibase en échec partiel mono-schéma (portée corrigée — pas de job multi-schéma en réalité) ; règle ArchUnit `usecase.*Impl`→`port.in` ; cascade de résolution de templates (JPA réel) ; seuils Jacoco relevés (LINE 85%, nouvelle règle BRANCH 75%) — écart de couverture réel trouvé (73%) et comblé par 8 classes jamais testées (`StorageConfig`, adaptateurs idempotence webhook/workflow, delegate Flowable, etc.) |
| `deploiement-bpmn` | PR #67, TODO §35 (clos) | Écran d'upload BPMN admin (`/admin/workflow-types`, option (c) — pas de Flowable Modeler/Cockpit) avec prévisualisation obligatoire avant import (`BpmnCanvas.vue` extrait de `BpmnViewer.vue`) ; endpoint `WorkflowBpmnDeploymentController` (déploiement réel `RepositoryService`, clé de processus sans `WorkflowTypeConfig` → rejet strict 404) ; `BpmnParserPort` extrait en Phase 5 après violation ArchUnit détectée (domaine dépendait de `org.flowable..`) ; 2 vrais bugs trouvés et corrigés en Phase 7 (`NullPointerException` potentielle sur `originalFilename` null, regex d'extraction trop strict côté preview) ; `README.md`/`05-workflow-engine.md` corrigés (mentions non livrées retirées) |
| `modele-physique-consolide` | PR #68, TODO §25 (clos) | Nouveau [13-modele-physique.md](analysis%20doc/13-modele-physique.md) : ERD mermaid des 17 tables métier réelles (pas « une dizaine »), 4 index uniques partiels critiques avec invariant métier (pas « deux »), seulement 2 FK sur 17 tables contraintes en DB (le reste logique, explicité) ; feature 100% documentaire, écart trouvé en Phase 1 avant même d'écrire le problem statement (TODO §25 documentait un `TenantRegistry` et des contextes Liquibase `tenant-init`/`tenant-migration` explicitement obsolètes depuis la décision n°13 — jamais répercuté depuis la note de mise à jour de la décision n°1) |
| `masquage-donnees-sensibles-logs-webhooks` | PR #70, TODO §23 (clos) | Aucune fuite active trouvée (vérifié par audit exhaustif) — reformulé en risque latent sans garde-fou ; `SendNotificationUseCaseImpl` masque automatiquement les valeurs sensibles avant écriture outbox pour tous les canaux ; règle ArchUnit de détection écartée (ArchUnit ne fait pas d'analyse de flux de valeurs) au profit d'une liste blanche des classes autorisées à lire `Dossier.fields()`/`FieldDefinition.defaultValue()` ; preuve bout en bout réelle + 2 contrôles mutants manuels ; zéro fichier frontend touché |
| `edition-bpmn` | [PR #71](https://github.com/superjojo79/FlowForge/pull/71), TODO §39 (clos) | Construction/édition de diagrammes BPMN dans FlowForge (écart restant après `deploiement-bpmn`, §35, qui excluait explicitement l'édition). `bpmn-js` Modeler + `bpmn-js-properties-panel` avec provider custom pour les attributs `flowable:*` réellement utilisés, sans app tierce (Flowable Modeler écarté). Brouillon **persisté côté backend** (`BpmnDraftRepository`/`BpmnDraftJpaEntity`, clé composite `@IdClass`), supprimé après déploiement réussi (D-DESIGN-DRAFT-LIFECYCLE) ; nouvelle méthode `WorkflowDeploymentPort.findDeployedXml`. M1-M4 (backend) et M5-M7 (frontend) implémentés **en parallèle** par deux agents en worktrees isolés, fusionnés puis M8 (vérification globale) par l'orchestrateur. **4 vrais bugs trouvés et corrigés** au total : `eventBus`/`commandStack` (masqué par des mocks Vitest, trouvé en E2E vrai navigateur), race condition de montage `BpmnEditorCanvas`/`useBpmnDraft.load()` (TDD), `NullPointerException` sur `xml` `null`/manquant → 500 au lieu de 400 (Phase 7, revue dédiée fichier par fichier), écart de couverture réel comblé sur le panel de propriétés custom (Phase 8). `./mvnw verify` 1110 tests (0 échec) ; 196 Vitest + 47 Playwright (0 échec). `README.md`/`05-workflow-engine.md`/`GUIDE-UTILISATEUR.md` corrigés. **Mergée sur `main`** (2026-08-25). |
| `durcissement-securite-post-revue` | [PR #76](https://github.com/superjojo79/FlowForge/pull/76), TODO §40–44 (clos) | Regroupement des 5 findings de la revue alignement × sécurité (chaîne SSTI→GRANT PG partagé §40+41, config/secrets §42+43, SSRF TOCTOU §44). `TemplateEngine`+`StandardDialect` sans SpEL (SSTI) ; GRANT PG `LOGIN` par tenant ; actuator `127.0.0.1:8090` ; secrets fail-fast hors `local`/`test` ; pin IP webhook sortant. `./mvnw verify` 1186 tests (0 échec), Jacoco LINE 93,52 %/BRANCH 75,57 % ; Vitest 199 + Playwright 47. §46 (ArchUnit `application`) explicitement hors périmètre. **Mergée sur `main`** (2026-08-25, merge humain). |
| `reporting-sla` | [PR #80](https://github.com/superjojo79/FlowForge/pull/80), analyse [16-reporting-sla.md](analysis%20doc/16-reporting-sla.md) | Écran Rapports & SLA (`/rapports`), snapshot `GET /api/reports/sla`, summary `GET /api/reports/sla/summary` (`ON_TRACK`/`AT_RISK`/`BREACHED`), cible `DossierType.slaTargetHours`, settings `complianceTargetPercent`, inbox `slaRemainingHours`. Authz **D-AUTHZ = C**. `./mvnw verify` 1263 tests (0 échec) ; Vitest 213 ; Playwright 55. Hors V1 reporté : planification d'envoi, Chart.js, codegen client. **Mergée sur `main`** (2026-08-26). |
| `serveur-mcp` | [PR #82](https://github.com/superjojo79/FlowForge/pull/82), analyse [15-serveur-mcp.md](analysis%20doc/15-serveur-mcp.md) | Serveur MCP interne, 9 tools (`get_dossier`, `search_dossiers`, `query_sla_summary`, `list_document_templates`, `get_dossier_type`, `list_dossier_types`, `list_workflow_types`, `define_dossier_type`, `deploy_workflow`) sur `POST /api/m2m/mcp` (Streamable HTTP, Spring AI 2.0.0), authentifiés par le JWT M2M `API_SERVICE` déjà existant — aucune nouvelle chaîne de sécurité, zéro fichier `frontend/**` touché. 9 milestones TDD (M1 gardes domaine → M9 vérification globale) ; Phases 7 (check implementation) et 8 (comblement de trous de couverture Jacoco) rejouées avant la revue finale (Phase 9) dans la même PR. `deploy_workflow` fait délibérément plus que le flux REST admin existant (upsert + deploy en un seul appel MCP), conforme au design. `./mvnw verify` vert (Jacoco LINE ≥85 %/BRANCH ≥75 % tenus) ; Vitest 213/213 ; Playwright 55/55 (régression complète, aucun fichier frontend modifié). **Mergée sur `main`** (2026-08-28). |
| `schema-champs-dynamiques` | [PR #87](https://github.com/superjojo79/FlowForge/pull/87), analyse [17-schema-champs-dynamiques.md](analysis%20doc/17-schema-champs-dynamiques.md), TODO §47 (clos) | Écran Admin → Types édite désormais le schéma `fieldDefinitions` d'un `DossierType` (création + panneau d'édition inline, composant `FieldDefinitionDefsEditor.vue`) et la bascule `allowUnknownFields`. `UpdateDossierTypeUseCaseImpl` branche `CheckDossierTypeSchemaCompatibilityUseCase` (déjà écrit, jusqu'ici réservé au MCP) et bloque (409) tout schéma incompatible avec des dossiers existants — aligne REST sur le comportement déjà strict du MCP `define_dossier_type` (simplifié en conséquence). `DossierType` rejette un `name` dupliqué. **2 écarts réels trouvés et corrigés en TDD** : composant frontend lié sur la prop au lieu d'un état local réactif (Phase 5) ; ordre check DB/validation structurelle réordonné pour échec rapide (Phase 7). CI hébergé rouge de façon identique sur `main` (infra, pas ce diff) — mergée sur la base des vérifications locales. `./mvnw verify` 1383 tests (0 échec) ; Vitest 247 ; Playwright 59. **Mergée sur `main`** (2026-08-28). |
| `connecteur-http-rest-service` | [PR #95](https://github.com/superjojo79/FlowForge/pull/95), analyse [18-connecteur-http-rest-service.md](analysis%20doc/18-connecteur-http-rest-service.md), TODO §48 (clos) | Le connecteur `http-rest` devient utilisable en rôle **SERVICE** en plus de TRIGGER (capability `BOTH`) : `HttpRestTriggerConnector` renommé `HttpRestConnector`, fusionné avec le skeleton `HttpRestServiceConnector` (supprimé). `execute(...)` supporte GET/POST/PUT dynamiques, corps JSON ou brut, en-têtes arbitraires par appel, en-tête `Authorization` optionnel par instance (`authHeaderValue`, chiffré). Nouveau bean `HttpClient` (aucun n'existait), `ConnectorProperties.Http` (timeouts 5s/5s). **2 écarts réels trouvés en Phase 5** : `ExecuteConnectorRequest` (DTO REST BFF/M2M) câblé en dur sur `flowable-signal`, ne transmettait pas `url`/`method`/`body`/`headers` — `http-rest` SERVICE restait inatteignable via HTTP malgré la capacité activée côté connecteur (corrigé, golden OpenAPI régénéré). **1 durcissement en Phase 7** : `HttpClient.Redirect.NEVER` rendu explicite (défaut JDK déjà correct mais implicite — une redirection suivie contournerait `OutboundUrlGuard`). **3 lacunes de couverture réelles comblées en Phase 8** (`authHeaderValue` blanc, branche `connectTimeout==null`, `ExecuteConnectorRequest.toInputParams()` jamais testé unitairement). `./mvnw verify` 1420 tests (0 échec) ; Vitest 247 ; Playwright 59 (feature backend-only, zéro fichier frontend touché). Cycle `dev-lifecycle` Phases 1–9 complet dans la même session (2026-08-31). **Mergée sur `main`** (2026-08-31). |
| `assainissement-dette-technique` | [PR #97](https://github.com/superjojo79/FlowForge/pull/97), analyse [19-assainissement-dette-technique.md](analysis%20doc/19-assainissement-dette-technique.md), TODO §49 (clos) | Sweep de dette technique (reliquats `DEFERRED-FEATURES.md` §1) : validation blank `processKey`/`workflowKey` dans 4 records domaine (`WorkflowTypeConfig`, `TriggerOutboxPayload`, `WorkflowStartedEvent`, `DossierType`), défense en profondeur — tous les points d'entrée REST/MCP blank-checkaient déjà en amont. `dossierId` désormais en MDC par item du job de purge (`ProcessPurgeOutboxUseCase.processBatch` gagne un callback `Function<UUID, Runnable>`, `PurgeOutboxJob` ouvre/ferme `MdcScope.dossier(...)`) sans dépendance MDC dans le domaine. **Trouvaille en design** : le seul vrai `log.warn` par item existant (`FlowableWorkflowEngineAdapter.deleteProcessInstanceBestEffort`) avale son exception — le callback doit envelopper tout `purgeDossier(record)`, pas seulement le `catch`, prouvé par un test d'intégration multi-items avec le vrai use case (pas mocké). `Persistable`/`isNew()` confirmé redondant avec `@Version` par preuve documentée, sans code. `./mvnw verify` 1430 tests (0 échec) ; Vitest 247 ; Playwright 59 (feature backend-only, zéro fichier frontend touché). Cycle `dev-lifecycle` Phases 1–9 complet (2026-09-01). |
| `connecteurs-hors-v1` | [PR #99](https://github.com/superjojo79/FlowForge/pull/99), analyse [20-connecteurs-hors-v1.md](analysis%20doc/20-connecteurs-hors-v1.md), TODO §50 (clos) | 3 derniers connecteurs du catalogue cible livrés, chacun réutilisant un mécanisme domaine déjà existant : `scheduler` (TRIGGER, cron via `SchedulerConnectorTriggerJob` + table `connector_schedule_state`, 3ᵉ appelant de `ConnectorTriggerRuntime.onIncomingEvent` après `WebhookController`/`FlowableSignalEventListener`) ; `smtp` (SERVICE, réutilise `EmailPort`/`SmtpEmailAdapter` déjà livré, texte brut) ; `document-storage` (SERVICE, réutilise `DocumentStoragePort`, **généralisé depuis le candidat `s3-minio`** du catalogue initial — demande explicite utilisateur, pas de couplage MinIO/S3). Zéro modification de `ConnectorTriggerRuntimeImpl`/`ConnectorCatalog`/`ConnectorRegistry`. **Écart de design corrigé en Phase 4** (avant code) : `processKey` manquant du config schema `SchedulerConnector`. **2 déviations trouvées en Phase 5, confirmées sans écart supplémentaire en Phase 7** : `ExecuteConnectorRequest` (DTO BFF/M2M) étendu de 6 champs (même point de couture que `http-rest`, TODO §48) ; `NoopEmailPortTestConfiguration` (patron `OidcClientTestConfiguration`) pour tester `smtp` sans serveur SMTP réel. `./mvnw verify` 1486 tests (0 échec, coverage Jacoco au vert) ; Vitest 247 ; Playwright 59 (feature backend-only confirmée en Phase 2, zéro fichier frontend touché). Cycle `dev-lifecycle` Phases 1–9 complet (2026-09-01). |
| `connecteur-callback` | [PR #102](https://github.com/superjojo79/FlowForge/pull/102), analyse [21-correlation-callback-async.md](analysis%20doc/21-correlation-callback-async.md), TODO §51 (clos) | Nouveau connecteur générique `flowable-callback` (capability **CALLBACK**, nouvelle valeur d'enum) : un process BPMN en attente sur un `intermediateCatchEvent`/`receiveTask` à message est réveillé par un callback HTTP asynchrone (`POST /api/callbacks/{instanceId}`, auth/idempotence réutilisées de `WebhookController`), corrélé par une valeur de clé métier — indépendant du connecteur SERVICE ayant émis l'appel sortant. **Correction factuelle majeure en Phase 3** : `RuntimeService.createMessageCorrelation(...)`, proposé par l'analyse d'origine, n'existe pas en Flowable 8.0.0 Community (vérifié en lisant les sources réelles) — remplacé par `ExecutionQuery.messageEventSubscriptionName(...).processVariableValueEquals(...)` + `messageEventReceived(...)`, prouvé contre le vrai moteur (`CallbackSmokeIT`, `CallbackEndToEndIT`). **3 écarts réels trouvés et corrigés** : NPE sur variable de mapping optionnelle non résolue (`Map.copyOf`, Phase 5) ; mésappariement de type `String`/`Integer` sur la corrélation Flowable — Flowable compare par type ET valeur, un identifiant numérique côté callback externe aurait échoué systématiquement contre une variable de process `String` (D-CALLBACK-TYPE, Phase 7, sévérité élevée) ; race `list()`/`messageEventReceived(...)` promise 404 par le design mais pas implémentée (Phase 7). `README.md`/`06-connecteurs.md` mis à jour, `21-correlation-callback-async.md`/`TODO.md` §51 corrigés pour ne pas garder l'erreur technique. `./mvnw verify` 1533 tests (0 échec, 100 % des branches propres à la feature couvertes) ; feature backend-only confirmée (Phase 2, reconfirmée via le contrat OpenAPI en Phase 8), zéro fichier `frontend/**` touché. Cycle `dev-lifecycle` Phases 1–9 complet (2026-09-02). **Correctifs post-merge (2026-09-02)** : revue de sécurité adversariale demandée par l'utilisateur **après** le merge a trouvé 5 écarts réels sur le code déjà en production, dont un **critique** — `/api/callbacks/**` était absent de tout `securityMatcher` Spring Security et tombait dans le chaînage BFF authentifié (session OIDC requise), renvoyant un 403 CSRF avant même d'atteindre `CallbackWebhookController` : **la feature livrée était totalement inatteignable par tout système externe réel**, non détecté par les 1533 tests alors verts car aucun ne faisait une vraie requête HTTP à travers la vraie chaîne de sécurité. 4 autres écarts corrigés dans le même correctif (garde taille de corps manquante, callback échoué bloquant les rejeux 24h, ambiguïté de corrélation totalement silencieuse, absence de garde-fou documenté sur l'entropie de la valeur de corrélation). `./mvnw verify` 1539 tests (0 échec, +6 tests de régression permanents). Détail complet : `docs/ai/implementation/2026-09-02-feature-connecteur-callback.md` § « Correctifs post-merge ». **Suivi TODO §53 (2026-09-03)** : le même défaut latent partagé (`idempotencyStore.complete(...)` jamais appelé sur échec) était aussi présent dans `WebhookController`, d'abord laissé en dette puis corrigé sur demande explicite (« Je pensais avoir demandé de corriger tous les findings »), même pattern exact que F2. [PR #104](https://github.com/superjojo79/FlowForge/pull/104), ouverte puis **mergée uniquement après vérification que la CI locale (`./mvnw verify`) passait** (demande explicite utilisateur, contrairement aux PR #102/#103). `./mvnw verify` 1541 tests (0 échec, +2 tests de régression permanents). **2ème revue de sécurité adversariale, indépendante (TODO §54, 2026-09-03)** : demande explicite utilisateur (« lance une analyse complète de type adversarial sur le code de la feature des callbacks »), reconfirme F0–F4 toujours clos et trouve 4 nouveaux écarts. **F5 (haute)** : Spring bufférise `@RequestBody byte[] rawBody` en mémoire *avant* l'exécution du contrôleur — donc avant la garde de taille, le rate limiter et l'authentification ; `WebhookBodySizeLimitFilter` (F1) ne vérifiait que le `Content-Length` déclaré, contournable par `Transfer-Encoding: chunked` (DoS mémoire non authentifié). Corrigé par un `ServletInputStream` enveloppé qui borne les octets réellement lus. **F6 (moyenne)** : la signature HMAC ne couvrait que le corps brut, pas d'horodatage/nonce, `Idempotency-Key` non signée — une paire (corps, signature) capturée une fois était rejouable indéfiniment. Corrigé par une signature étendue à `timestamp + "." + corps` (`X-Webhook-Timestamp` requis, fenêtre de fraîcheur 5 min) — **rupture de contrat pour les appelants HMAC existants**, assumée sur demande explicite (« corrige tout maintenant »). **F7 (moyenne)** : aucune longueur minimale sur `webhookSecret`, brute-forçable en pratique avec la limite de débit par défaut — corrigé (`WebhookSecretPolicy.MIN_LENGTH = 20`). **F8 (moyenne)** : échec d'authentification webhook jamais journalisé, une tentative de brute force contre F7 serait passée inaperçue — corrigé (`SecurityAuditLog.webhookAuthenticationFailed`). Tous corrigés en TDD, 13 tests de régression permanents ajoutés. `./mvnw verify` **1554 tests** (0 échec). Détail complet : `docs/ai/implementation/2026-09-02-feature-connecteur-callback.md` § « Correctifs post-merge (2ème passe adversariale) ». |

| `formulaires-dynamiques-taches` | analyse [22-formulaires-dynamiques-taches.md](analysis%20doc/22-formulaires-dynamiques-taches.md), TODO §52 (clos) | Un `userTask` BPMN peut déclarer `flowable:formKey` pour afficher un formulaire de tâche avant complétion : `dossier-fields` (schéma complet du `DossierType`, `DynamicFields.vue` réutilisé) ou `document-generation` (choix d'un document parmi ceux associés au type de dossier via le nouveau `DossierType.documentTemplateKeys`, liste vide = aucun template disponible). Mécanisme volontairement extensible **côté frontend uniquement** (`taskFormRegistry.ts`, kind → composant) — `formKey` reste une chaîne opaque de bout en bout côté backend, décision utilisateur au lancement du cycle. **Écart de sécurité trouvé et corrigé en Phase 3** : `GET /api/document-templates` (admin-only) inutilisable par un porteur de tâche — nouvel endpoint `GET /api/dossier-types/{code}/document-templates` (jamais `htmlContent`, même contrôle `VIEW` que la lecture du type). **Bug réel latent trouvé et corrigé en Phase 5**, via un test Playwright réel (pas mocké au niveau composant) : le tout premier attribut `flowable:*` posé sur un diagramme sans `xmlns:flowable` déjà déclaré — dont le gabarit BPMN vierge de l'éditeur — se perdait silencieusement à l'enregistrement (`saveXML()`), latent depuis `edition-bpmn` (TODO §39) car jamais détecté par un test round-trip réel jusqu'ici ; `EMPTY_BPMN_XML` déclare désormais l'espace de noms. Spike de risque technique fait en premier (M0) : `Task.getFormKey()` confirmé fonctionner sans le module `flowable-form` sur ce Flowable 8.0.0, par un test d'intégration réel (`FormKeySmokeIT`) avant d'investir dans la plomberie qui en dépend. `./mvnw verify` 1572 tests (0 échec, Jacoco LINE/BRANCH tenus) ; Vitest 269/269 ; Playwright 63/63 (59 existants + 4 nouveaux `E2E-FORM-*`, zéro régression). Cycle `dev-lifecycle` Phases 1–9 complet (2026-09-03). |
| `revue-adversariale-securite` | [PR #113](https://github.com/superjojo79/FlowForge/pull/113), TODO §57 (clos) | Durcissement High/Medium de la revue adversariale 2026-09-07 (Q1–Q12 = A) : allowlist BPMN parse-time (H1), complete/runtime schemas bornés à l’assignee (H2/M8), OIDC sans `API_SERVICE` (M1), S3 yaml (M2), SSRF Host/CGNAT (M3/M4), clés connecteur hors schéma (M5), auth puis rate-limit (M6), HMAC `timestamp.Idempotency-Key.body` **rupture de contrat** (M7), reserved keys callback (M9), SMTP (M10), caps HTTP/storage (M11), ACL generate dans le contrôleur (M12), `th:utext` + CSP preview (M13). L1–L7 hors cycle. `./mvnw verify` 1705 tests (0 échec) ; Vitest 289/289 ; Playwright 65/65. CI GitHub verte. **Mergée sur `main`** (2026-09-07). |
| `admin-connector-form-schema` (lot **A2**) | [PR #117](https://github.com/superjojo79/FlowForge/pull/117), TODO §59 | Formulaire admin connecteurs généré depuis `configSchema` (plus de `<textarea>` JSON). Types sans config d’instance (`smtp`, `document-storage`) : plus de zone de texte. Après création, URL webhook (`http-rest`) et URL callback (`flowable-callback`) avec copie presse-papiers (D-WEBHOOK-URL = C). Frontend seulement. Vitest 324 ; Playwright 68 (`E2E-CONN-1` webhook, `E2E-CONN-2` smtp, `E2E-CONN-3` callback). **Mergée sur `main`** (2026-09-08). |
| `mcp-ecritures-metier` (lot **A3**) | [PR #118](https://github.com/superjojo79/FlowForge/pull/118), TODO §60 (clos) | 4 tools MCP d'écriture métier (`create_dossier`, `add_dossier_element`, `start_workflow`, `generate_document`), 16 → 20 tools. Adaptateurs `infrastructure.mcp.tool` uniquement ; `CreateDossierUseCase*` inchangé (réutilise les use cases REST existants, pas de duplication de logique). Auth inchangée (JWT M2M `API_SERVICE`). **Phases 1–3 rejouées** après constat utilisateur que le premier passage avait acté les décisions sans poser de question (violation du contrat SDD) : 9 arbitrages posés un par un via `AskUserQuestion` (D-GENERATE-ATTACH, D-GENERATE-TEMPLATE, D-GENERATE-VARS, D-DRY-RUN, D-RATE-LIMIT, D-NO-OPENAPI, D-START-ORCH, D-DRY-RUN-START, D-FIELDS-READ), **tous confirmés et alignés sur le code déjà écrit** — aucun écart à corriger. `./mvnw verify -Dskip.frontend=true` 1775 tests (0 échec), Jacoco tenu, classes de la feature 100 % LINE+BRANCH. Playwright N/A (feature backend-only, zéro fichier `frontend/**` touché). **Mergée sur `main`** (2026-09-08, conditionnée à CI GitHub verte). |
| `provisioning-tenant` (lot **A1**) | [PR #119](https://github.com/superjojo79/FlowForge/pull/119), TODO §31/§41 | Outillage ops manquant : `Dockerfile` multi-stage (`eclipse-temurin:25-jdk`/`-jre`, utilisateur non-root UID 10001, heap `-XX:MaxRAMPercentage=75.0`) + `.dockerignore` ; `scripts/provision-tenant.sh` (schéma + rôle PostgreSQL par tenant, D-41, dry-run par défaut/`--apply`, garde anti-écrasement) ; `server.servlet.context-path` (décision n°14) activé par défaut hors profil `local` (neutralisé dans `application-local.yml`, pattern déjà en place pour `app.security.cookie.secure`). Cycle `dev-lifecycle` Phases 1–9 complet, 4 décisions tranchées via `AskUserQuestion` en Phase 1 (image, non-root, heap, activation `context-path`). **2 bugs réels trouvés et corrigés en cours de cycle** : clé YAML `server:` dupliquée dans `application.yml` lors de la première tentative d'activation `context-path` (SnakeYAML « found duplicate key server », détecté par `./mvnw verify` lui-même, Phase 5) ; troncature silencieuse possible du rôle PostgreSQL généré au-delà de `NAMEDATALEN` (63 octets) pour un `tenant_id` proche de l'ancienne borne de 63 caractères — le rôle préfixe `flowforge_` (10 caractères) — corrigé en bornant `tenant_id` à 53 caractères (Phase 7, relecture ligne à ligne, ni `hadolint` ni `shellcheck` ne l'auraient trouvé). **Limite d'environnement assumée** : `docker build`/`docker run` réel impossible dans le bac à sable de ce cycle — politique réseau de la session bloquant explicitement `production.cloudfront.docker.com` (CDN Docker Hub), confirmé systémique (`eclipse-temurin:25-jdk` et `postgres:17-alpine` tous deux rejetés), non contourné (conforme à `/root/.ccr/README.md`) ; `Dockerfile` validé par `hadolint` (propre) + revue statique, **à confirmer par un build réel avant premier déploiement**. Script de provisioning en revanche validé avec de vraies preuves : service PostgreSQL local du bac à sable (Docker Hub bloqué aussi pour le conteneur jetable prévu), tous les scénarios exécutés (nominal, rejeu rejeté, identifiant invalide, mot de passe absent, **isolation cross-tenant vérifiée avec un second tenant réel** — `permission denied` confirmé). `shellcheck` propre. `./mvnw verify` **1738 tests (0 échec)**, ArchUnit conforme, Jacoco respecté ; zéro fichier `frontend/**` touché. `DEPLOIEMENT.md`/`GETTING-STARTED.md`/`.env.example` alignés sur l'outillage livré. **Pas de PR ouverte** (non demandé) — branche poussée, prête pour revue. |
| `headers-http-globaux` (lot **A4**) | [PR #128](https://github.com/superjojo79/FlowForge/pull/128), TODO §63 (clos) | En-têtes HTTP applicatifs (CSP enforce SPA sans `unsafe-eval`, HSTS 1 an hors local/test si pair TCP loopback/RFC1918, `X-Frame-Options`, `nosniff`, `Referrer-Policy`) sur les 4 chaînes Spring Security. Kill switch `APP_SECURITY_HEADERS_ENABLED`. Remédiation A4-HSTS-001. E2E-HDR-1–4. **Mergée sur `main`** (2026-09-20, CI verte, merge `8415cee`). |
| `connecteurs-cible` (lot **B1**) | [PR #129](https://github.com/superjojo79/FlowForge/pull/129), TODO §62 (clos) | 2 nouveaux connecteurs du catalogue cible dans un seul cycle (Kafka + import batch, « ERP » = cas d'usage de `batch-import`, pas un 3ème connecteur — confirmé en Phase 1) : `batch-import` (TRIGGER, SFTP/SSHJ, parsing CSV+Excel, un dossier par ligne, isolation par ligne invalide, garde `maxFileBytes`/`maxRows`, archivage post-traitement) et `kafka` (TRIGGER, `KafkaConsumerReconciler` — nouveau pattern de cycle de vie de ressource externe par polling, un message = un dossier). Nouveau `ConfigFieldType.SECRET_TEXTAREA` (clé privée SFTP), masqué/chiffré via le même point d'extension unique que `PASSWORD` (`ConnectorCatalog.isPasswordField`, TODO §56). **3 décisions non-recommandées choisies délibérément par l'utilisateur** : CSV **et** Excel dès ce cycle, authentification SFTP mot de passe **et** clé privée, tests Kafka/SFTP mockés uniquement. **2 trouvailles de sécurité actées en cours d'implémentation** (hors planification initiale) : garde taille/lignes sur le parsing (D-BATCH-SIZE-LIMIT) et vérification obligatoire de l'empreinte de clé d'hôte SSH (D-SFTP-HOSTKEY — SSHJ n'accepte silencieusement aucun hôte par défaut). **2 bugs réels trouvés et corrigés en TDD** : second constructeur sur un record `@ConfigurationProperties` cassant le démarrage Spring (corrigé en amont, tous les call sites mis à jour) ; isolation de ligne invalide inatteignable (calquée à tort sur `HttpRestConnector`, le vrai critère est un champ **mappé** requis manquant, pas `processKey`). Golden OpenAPI et trou de preuve `ConnectorRegistry` (contexte réel, 8 types sans collision) trouvés et comblés en Phase 7. `./mvnw verify` 1948 tests (0 échec), Jacoco tenu ; Vitest 352/352 ; Playwright 72/72. **Revue adversariale post-cycle** (sous-agent dédié) : 2 vulnérabilités réelles corrigées (TOCTOU sur `maxFileBytes` pouvant provoquer un OOM malgré la garde documentée ; absence de plafond sur le nombre d'instances `kafka` activées par tenant, `app.connector.kafka.max-instances-per-tenant`) + 2 bugs de robustesse mineurs. 3 bugs de test préexistants et sans rapport trouvés au passage et corrigés sur demande explicite (`NotificationPersistenceIT`, `EvaluateDossierRetentionUseCaseImplTest`, `DossierTest`). Cycle `dev-lifecycle` Phases 1–9 complet (2026-09-14). `./mvnw verify` final : 2040 tests (0 échec, `Test*` + `*IT.java` sans exclusion). **Mergée sur `main`** (2026-09-20). |

Lots lifecycle antérieurs (`workflow-engine` … `frontend`, pagination, relations, PATCH fields API, éléments) : voir [DEFERRED-FEATURES.md](DEFERRED-FEATURES.md) §2.

## File d’attente

Le V1 nommé est **livré** (`reporting-sla` … `revue-adversariale-securite` / §58,
voir *Récemment livré*). TODO.md **entièrement clos**. Reliquat = lots V1.x / ops,
**sans analyse `dev-lifecycle` écrite** (sauf backlog MCP déjà cadré dans
[15-serveur-mcp.md](analysis%20doc/15-serveur-mcp.md) § *Backlog*). Chaque agent
démarre par requirements → design ; **ne pas trancher silencieusement** une
décision d’image Docker, de store partagé, ou d’activation `context-path`.

### Vague A — 3 agents simultanés (surfaces disjointes)

Lancer **A1, A2, A3** en parallèle (3 branches / 3 PRs). **A4** optionnel en
même temps **si** A1 ne touche pas `SecurityConfig`.

| Lot | Feature | Objectif | Surface **exclusive** (seul cet agent y écrit) | Ne **pas** toucher |
|---|---|---|---|---|
| **A1** | `provisioning-tenant` | Outillage ops : Dockerfile, runbook/CLI schéma PG + instance Boot + bloc proxy + `app.tenant.id` ; `server.servlet.context-path` seulement si tranché en Phase 1 | `Dockerfile` (nouveau), `scripts/` provisioning (nouveau), [DEPLOIEMENT.md](DEPLOIEMENT.md), [GETTING-STARTED.md](GETTING-STARTED.md), `docker-compose*.yml`, `.env.example` ; `application.yml` **uniquement** pour `context-path` / tenant si décidé | `frontend/**`, `infrastructure/mcp/**`, `ConnectorsAdminView.vue`, `SecurityConfig.java`, `ConnectorCatalog` |
| **A2** | `admin-connector-form-schema` | Formulaire admin généré depuis `configSchema` à la place du `<textarea>` JSON ([06-connecteurs.md](analysis%20doc/06-connecteurs.md) « Écran d'admin ») | `frontend/src/views/admin/ConnectorsAdminView.vue`, `frontend/src/api/admin.ts`, e2e connecteurs, [GUIDE-UTILISATEUR.md](GUIDE-UTILISATEUR.md) § connecteurs | Backend connecteurs, `ConnectorCatalog`, MCP, Compose, `SecurityConfig` — l’API `GET /api/connector-definitions` **expose déjà** `configSchema` |
| **A3** | `mcp-ecritures-metier` | 4 tools d’écriture métier : `create_dossier`, `add_dossier_element`, `start_workflow`, `generate_document` — **un seul cycle**, pas 4 PRs (même enregistrement tools / tests MCP) | `src/main/java/com/flowforge/infrastructure/mcp/**`, tests MCP associés, golden OpenAPI si le contrat MCP est documenté | `frontend/**`, admin connecteurs, Compose, `SecurityConfig`. Réutiliser les use cases REST existants ; **pas** d’ACL/purge/statut de link (exclusions de [15-serveur-mcp.md](analysis%20doc/15-serveur-mcp.md)) |
| **A4** (opt.) | `headers-http-globaux` | CSP / HSTS / `X-Frame-Options` applicatifs (reliquat S-9) | `SecurityConfig.java` (+ éventuellement en-têtes de la réponse SPA) | Compose, `context-path`, cookies CSRF (S-8 assumé, ne pas rouvrir) |

Contraintes de lancement :

- Une branche `cursor/<feature>-…` par lot ; worktrees isolés si plusieurs agents sur la même machine.
- A3 : les 4 tools **ne sont pas** 4 features concurrentes — milestones TDD dans **une** PR.
- A1 : `DEPLOIEMENT.md` dit déjà qu’un Dockerfile tranche des décisions ouvertes (image, user non-root, heap) — Phase 1 obligatoire avant code.
- Validation : A1 docs + smoke compose si possible ; A2 Vitest **et** Playwright ; A3 `./mvnw verify` (feature backend-only, régression FE seulement).

### Vague B — après A, **un** agent (coutures partagées)

| Lot | Feature | Pourquoi pas en parallèle de A ni entre eux |
|---|---|---|
| **B1** | `connecteurs-cible` — Kafka + import batch + ERP **dans un seul cycle** | Même plomberie que `connecteurs-hors-v1` (PR #99) : `ConnectorCatalog`, `ExecuteConnectorRequest`, golden OpenAPI, [GUIDE-CONCEPTION-BPMN.md](GUIDE-CONCEPTION-BPMN.md). Trois PRs = conflits. Attendre **A2** (admin connecteurs) et **A1** si Kafka/ClamAV veulent le Compose |

Enchaînements interdits même après A :

| Combinaison | Raison |
|---|---|
| Kafka ∥ import-batch ∥ ERP | Même fichiers catalogue / DTO d’exécution |
| Import batch ∥ A3 (`create_dossier`) | Les deux passent par `CreateDossierUseCase` / `CreateDossierBatchUseCase` |
| AV ClamAV ∥ A1 | Les deux s’approprient `docker-compose` / `GETTING-STARTED.md` — ClamAV **après** A1, ou A1 reste propriétaire Compose |
| Codegen OpenAPI ∥ tout FE | Régénère `frontend/src/api/*` (`D-CODEGEN` = hors V1 de toute façon) |

### Vague C — optionnel, après A1

| Lot | Notes |
|---|---|
| Adaptateur AV (ClamAV) | Port `AttachmentScanPort` + `NoOpAttachmentScanAdapter` déjà livrés (S-6). Adapter réel + Compose — **propriétaire Compose = A1 d’abord** |

### Backlog produit — analyses initiales disponibles (non cadré, pas de priorité tranchée)

Écarts réels du `ROADMAP.md` § *Gaps réels hors Vague B*, catégorie « Produit, pas cadré ».
Chaque lot reste soumis à Phase 1 → 9 complet — l'analyse initiale ne tranche aucune décision
technique.

**Encore non démarrés :**

| Lot | Items `ROADMAP.md` | Analyse initiale |
|---|---|---|
| Portail externe & signature | 3.1 (Portail externe tokenisé) + 2.4 (Signature électronique) | [26-portail-externe-signature.md](analysis%20doc/26-portail-externe-signature.md) |
| Inbox notifications | 3.2 (Notification Inbox persistée) | [27-notifications-inbox.md](analysis%20doc/27-notifications-inbox.md) |

**Cycles démarrés depuis ces analyses :**
- `enrichissement-reporting` (doc 29, items 5.2 + 3.3 + 5.3) — TODO §65, PR #132
- `outillage-dmn` (doc 28, items 1.1 + 1.2 + 1.3/5.1) — TODO §66, Phase 1 2026-09-22

### Ne pas lancer (décision V1 contraire ou stack absente)

Rate-limit distribué / SSE multi-pod / Spring Session (pas de Redis dans la stack figée) ;
codegen OpenAPI ; Chart.js SLA (`D-CHARTS = A`) ; Cockpit Flowable ; OTel / Grafana ;
pagination inbox > 100.

**Vague A — intégralement livrée** (2026-09-08) :
**A1** mergé ([PR #119](https://github.com/superjojo79/FlowForge/pull/119)),
**A2** mergé ([PR #117](https://github.com/superjojo79/FlowForge/pull/117)),
**A3** mergé ([PR #118](https://github.com/superjojo79/FlowForge/pull/118)).

**Vague B — intégralement livrée** : **B1** (`connecteurs-cible`, TODO §62 clos), mergée sur
`main` ([PR #129](https://github.com/superjojo79/FlowForge/pull/129), 2026-09-20).

**En revue :** aucune.

**Cycle en cours (2026-09-21) :** `enrichissement-reporting` (TODO §65) —
les trois volets de [29-enrichissement-reporting.md](analysis%20doc/29-enrichissement-reporting.md)
sont implémentés (alerting SLA, widgets dead-letter / tâches sans
assigné, export BI planifié), en revue dans la PR #132. Décisions Phase 1 dans
`docs/ai/requirements/2026-09-21-feature-enrichissement-reporting.md`.
Ne pas le relancer en parallèle. Le lot C (ClamAV) est livré
([PR #131](https://github.com/superjojo79/FlowForge/pull/131)) et ne partage
pas ces fichiers.

**Cycle en cours (2026-09-22) :** `outillage-dmn` (TODO §66) — Phase 1
requirements. Analyse
[28-outillage-dmn.md](analysis%20doc/28-outillage-dmn.md), docs
`docs/ai/*/2026-09-22-feature-outillage-dmn.md`. Surfaces disjointes de
§65 et du lot C.

Détail inventaire : [DEFERRED-FEATURES.md](DEFERRED-FEATURES.md) §4 / §6.

## En revue

Aucune PR produit en revue.

`revue-adversariale-securite` (TODO §57)
**mergée sur `main`** ([PR #113](https://github.com/superjojo79/FlowForge/pull/113), 2026-09-07).

`connecteur-http-rest-service`
**mergée sur `main`** ([PR #95](https://github.com/superjojo79/FlowForge/pull/95), 2026-08-31).

`durcissement-securite-post-revue`
([PR #76](https://github.com/superjojo79/FlowForge/pull/76)) et les
analyses `serveur-mcp`/`reporting-sla`
([PR #78](https://github.com/superjojo79/FlowForge/pull/78)) sont
**mergées** sur `main`.

## Hors file produit (ne pas traiter comme « prochaine feature »)

- Smoke Keycloak réel (environnement manuel — distinct du smoke PostgreSQL/Testcontainers
  automatisé désormais dans `fiabilisation-tests-plateforme`, §26/§28)
- Reliquats sécu assumés S-6…S-10 / L1–L7 — sauf s’ils sont explicitement un lot
  de vague (A4 headers, C ClamAV) ci-dessus

Provisioning tenant **n’est plus hors file** : c’est le lot **A1**.

**Clos (2026-08-25, hors cycle `dev-lifecycle` formel — corrections/hygiène ponctuelles, PR #72) :**

- DEV-PURGE (`workflow-engine`) : `OutboxRepository.purgeProcessedOlderThanDays` et
  `IdempotencyStore.purgeExpired` existaient mais n'étaient appelés par aucun job —
  nouveau `WorkflowRetentionJob` planifié (`app.workflow.retention.poll-delay`, 1j par
  défaut) ; `purgeExpired()` implémenté pour de vrai (était un stub `return 0`),
  TTL `app.workflow.idempotency-retention` (7j par défaut, aligné sur
  `outbox.processed-retention`).
- Recovery `purge_outbox` bloqué en `PROCESSING` : colonne `updated_at` ajoutée
  (migration `015-purge-outbox-updated-at.yaml`), avancée sur `claim`/`markCompleted`/
  `markFailed` ; nouvelle méthode de port `requeueStaleProcessing` qui repasse en
  `FAILED` (donc re-tentable) tout item resté en `PROCESSING` au-delà de
  `app.purge.processing-timeout` (2h par défaut) ; appelée par `PurgeOutboxJob` avant
  chaque batch.
- Chore `com.monapp` résiduel dans `analysis doc/01-architecture-hexagonale.md` (3
  occurrences) → `com.flowforge`.
- Nice-to-have vérifiés lors de cette passe (`DEFERRED-FEATURES.md` §1) : index
  `(tenant_id, dossier_type_code)` déjà couvert par `idx_dossier_type_updated_at`
  (008) — **pas de migration ajoutée**, item corrigé en stale ; `CHECK`
  `dossier_type_acl.permission ∈ {VIEW,EDIT}` réellement manquant → migration
  `014-dossier-type-acl-permission-check.yaml`.
- `useBpmnDraft.load()` (point ouvert signalé en Phase 7 de `edition-bpmn`, PR #71) :
  gestion d'erreur réseau ajoutée (`loadError`), affichée dans `BpmnEditorView.vue` au
  lieu de bloquer l'éditeur sur une page vide sans message.

Vérifié : `./mvnw verify` 1136 tests (0 échec), Jacoco OK ; frontend `npm run test`
199/199, `typecheck`/`lint` propres, `npx playwright test` 47/47.

## Décisions §21 / §22 (rappel)

**§21 — Rétention par `DossierType` — acté 2026-08-19** (voir TODO §21 et
`docs/ai/requirements/2026-08-19-feature-retention-dossier-type.md`).

**§22 — Purge PostgreSQL / object store — livré 2026-08-19** (feature
`purge-stockage`, PR #56) :

- Job `ProcessPurgeOutboxUseCase` consomme `purge_outbox` (Q1=B).
- Port `DocumentStoragePort.delete` S3 + filesystem — jamais MinIO (Q2, DEFERRED §0).
- Ordre store puis PG ; graphe complet ; Flowable best-effort (Q3–Q5).
- UI lecture `/admin/purge-outbox` ; enqueue `POST` `CLOTURE`/`ARCHIVE` (Q6, Q7, Q11).
- Suppression de tenant = runbook ops (Q8, décision n° 13) : pas d’API destroy.

## Historique court des choix « prochaine feature »

| Date | Choix | Motif |
|---|---|---|
| 2026-08-17 | `preview-documents` | Dernier lot nommé D-PREVIEW ; pas de point TODO bloquant |
| 2026-08-18 | `edition-champs-dossier` | Plus de lot nommé ; trou API-sans-UI ; §21 toujours « à décider » |
| 2026-08-19 | file ci-dessus | `edition-champs-dossier` en revue (PR #53) ; prochain = §21 si on tranche, sinon §24 |
| 2026-08-19 | `retention-dossier-type` Phase 9 READY | PR #55 ; prochain = `purge-stockage` §22 |
| 2026-08-19 | `purge-stockage` Phase 9 READY | PR #56 ; prochain = `openapi-pagination-admin` §24 |
| 2026-08-19 | `openapi-pagination-admin` Phase 5 | PR #58 ; §24 partiel clos ; prochain = `observabilite` §29 |
| 2026-08-20 | `observabilite` Phase 1 | Stakeholder : UI admin (D-FE) ; Rapports/SLA hors V1 |
| 2026-08-20 | `observabilite` Phase 2 reviewed | Q1=B ; Q2=A ; Q3=B ; Q4=A ; Q5=A ; Q6=A |
| 2026-08-20 | `observabilite` Phase 3 reviewed | DD-JSON=ecs ; DD-PROM-NAMES ; DD-FLOWABLE-API ; DD-THRESHOLDS |
| 2026-08-20 | `observabilite` Phase 5 done | M1–M5 ; verify 906 + Vitest 135 + Playwright 39 |
| 2026-08-20 | `observabilite` Phase 9 READY | PR #59 ; file V1 sans lot nommé suivant |
| 2026-08-20 | §36 `canEditDossier` clos | PR #60 ; frontend aligné sur `DossierAccessPolicy` via `canEdit` |
| 2026-08-20 | Regroupement TODO restants en 5 features | `durcissement-donnees-sensibles`, `contrat-api-client`, `fiabilisation-tests-plateforme`, `deploiement-bpmn`, `modele-physique-consolide` ; §31 clos (process déjà en pratique) |
| 2026-08-21 | `durcissement-donnees-sensibles` Phase 1+2 reviewed | D-AV=B (magic-bytes + port no-op) ; D-CSV-SCOPE=A (export construit) ; D-CSV-ROLE=lecture liste ; D-SERVICE-HTTP=skeleton non enregistré ; D-SECRETS-AUDIT=exhaustif |
| 2026-08-21 | `durcissement-donnees-sensibles` Phase 3 reviewed | AttachmentContentSniffer+AttachmentScanPort ; DossierExportPort streamé + ExportDossiersUseCase ; HttpRestServiceConnector non-`ConnectorDefinition` (double garde registry) ; garde-fou secrets = test unitaire ciblé |
| 2026-08-21 | `durcissement-donnees-sensibles` Phase 4 reviewed | 5 milestones (M1 upload, M2 export CSV, M3 skeleton connecteur, M4 audit secrets, M5 qualité) ; M1–M4 sans dépendance croisée ; D-CSV-UI=pas d'écran SPA V1 (feature backend seul) |
| 2026-08-21 | `durcissement-donnees-sensibles` Phase 5 done | M1–M5 en TDD ; `./mvnw verify` 960 tests (0 échec) + Jacoco OK ; Vitest 136 + Playwright 39 (feature backend-only, régression complète) ; TODO §23 reste réduit, §30 clos |
| 2026-08-21 | `durcissement-donnees-sensibles` Phase 7 done | Revue fichier par fichier ; injection de formule CSV trouvée sur `CsvWriter` (labels/champs dynamiques exportés non protégés) et corrigée en TDD ; `./mvnw verify` 964 tests (0 échec) |
| 2026-08-21 | `durcissement-donnees-sensibles` Phase 9 READY | Revue finale : traçage appelants, cohérence modules similaires, pas de duplication, pas de migration à risque ; aucun blocage ; PR #62 (commit `284345c`) prête pour revue humaine |
| 2026-08-21 | `durcissement-donnees-sensibles` Phase 8 rejouée | Relecture Jacoco classe par classe (pas juste le total bundle) ; 2 lacunes réelles comblées (`HttpRestServiceConnector` chemin d'erreur, `AttachmentContentSniffer` texte vide) ; `./mvnw verify` 969 tests (0 échec) |
| 2026-08-21 | `durcissement-donnees-sensibles` Phase 9 re-vérifiée | Utilisateur : « la phase 9 a-t-elle été correctement exécutée ? » → non totalement ; balayage Jacoco corrigé (motif CSS `pc bpc` raté au 1er passage) sur les 15 fichiers modifiés, 4 lacunes réelles trouvées et comblées (`DossierExportController` closedAt, `AttachmentContentSniffer` null, `parseStatus` blanc, `CsvWriter` \r isolé) ; `./mvnw verify` 973 tests (0 échec) |
| 2026-08-21 | `durcissement-donnees-sensibles` livrée | PR #62 mergée sur `main` (commit `a4443e5`) ; TODO §23 reste réduit, §30 clos ; prochain = `contrat-api-client` §24 (reste) |
| 2026-08-21 | `contrat-api-client` Phase 1+2 reviewed | D-CODEGEN=exclu (contradiction FILE-ATTENTE/TODO résolue en faveur de TODO) ; D-RELATIONS=pas de pagination (décision existante respectée) ; D-PAGINATION-SCOPE=6 listes (elements, workflow-tasks/mine, connector-definitions, dossiers/{id}/workflows, workflow-links history+tasks) ; D-HIDDEN-SPEC=documentation complète SSE/multipart/webhook |
| 2026-08-21 | `contrat-api-client` Phase 3 reviewed | Mécanisme par source : JPA `Pageable` (elements, dossiers/{id}/workflows), Flowable `listPage`/`count` natif (workflow-tasks/mine, workflow-links history+tasks — remplace `DEFAULT_LIMIT=100`), slice mémoire `PageResult.slice` (connector-definitions) ; `AdminListSorts` renommé `ListSorts` (générique) ; SPA adapté via `listAllPages` existant, zéro changement de composant Vue ; 3 endpoints `@Hidden` documentés sans changement de comportement |
| 2026-08-21 | `contrat-api-client` Phase 4 reviewed | 7 milestones (M1 `ListSorts`, M2 pagination JPA, M3 pagination Flowable native, M4 pagination mémoire, M5 doc OpenAPI `@Hidden`, M6 adaptation SPA, M7 qualité), 9 tâches, testing doc scénarisé (aucun scénario orphelin) ; toutes les tâches `not started` — Phase 5 pas encore démarrée |
| 2026-08-21 | `contrat-api-client` Phase 5 exécutée (TDD) | 9 tâches (T1.1–T7.2) implémentées, rouge confirmé avant chaque nouvelle méthode de port/adaptateur ; `./mvnw verify` 986 tests (0 échec), Jacoco met ; golden OpenAPI régénéré (6 endpoints paginés + 3 `@Hidden` retirés) |
| 2026-08-21 | `contrat-api-client` Phase 7 (check implementation) | Relecture fichier par fichier : 2 bugs réels trouvés et corrigés — `ListSorts.MY_WORKFLOW_TASKS` déclarait `createdAt,asc` alors que l'adaptateur trie `.desc()` (constante corrigée, comportement préservé) ; `listActiveTasks` n'appliquait aucun `ORDER BY` avant `.listPage` (pagination non déterministe, vrai bug — `.orderByTaskCreateTime().asc()` ajouté) |
| 2026-08-21 | `contrat-api-client` Phase 8 (régression complète) | `npm run test` 136/136, `typecheck`/`lint` propres, `npm run test:e2e` 39/39 après correction de 2 régressions SPA réelles dans `frontend/e2e/pilotage.spec.ts` (mocks non enveloppés `PageResponse` + pattern glob Playwright ne matchant plus l'URL avec `?page=&size=`) ; non-déterminisme du golden OpenAPI trouvé et corrigé (`HttpHeaders` exposé par le webhook, `@Parameter(hidden = true)` ajouté) |
| 2026-08-21 | `contrat-api-client` Phase 9 revue | Revue holistique fichier par fichier : alignement design confirmé, breaking changes de ports tous traités atomiquement (appelants en repo mis à jour), aucun changement de sécurité/autorisation, rollback sûr (aucune migration DB). Aucun problème bloquant nouveau (les 2 trouvés en Phase 7 déjà corrigés). Implémentation complète, TODO §24 clos, en attente de PR |
| 2026-08-21 | `contrat-api-client` — D-SPA-ADAPT rejouée | Utilisateur : Phase 3 n'avait posé aucune question matérielle (violation du contrat `dev-design`). 3 décisions rejouées via `AskUserQuestion` : D-TASK-LIMIT et D-SORT-CONST confirmées, **D-SPA-ADAPT inversée** — pagination visible ajoutée pour 5/6 listes (`connector-definitions` reste `listAllPages`, menu déroulant pas une liste parcourue) ; `dossiers/{id}/workflows` gagne un affichage qui n'existait pas avant (constat fait en creusant, ajout validé explicitement). 9 tâches TDD supplémentaires, `npm run test` 153/153, `test:e2e` 40/40, aucun fichier backend touché |
| 2026-08-22 | `fiabilisation-tests-plateforme` Phase 1+2 reviewed | 6 décisions actées via `AskUserQuestion` (une à la fois, conformément au contrat `dev-requirements`) : D-SCOPE=une feature, D-TESTCONTAINERS=adoption ciblée (isolation `search_path` + séquence multi-pods uniquement), D-TESTCONTAINERS-EXEC=tests écrits mais étiquetés « non exécutés ici » (Docker inaccessible dans ce bac à sable, vérifié par `docker info`), D-COVERAGE=LINE 85%/BRANCH 75% BUNDLE, D-TENANT-ID-37=alignement `tenantId` explicite, D-ARCHUNIT-RULE=`usecase.*Impl`→`port.in`. 3 corrections factuelles trouvées avant de demander : `@Version` déjà en place sur `Dossier`, bug `Instant`/`%tY` cité au TODO §26 inexistant dans le code actuel, couverture outbox déjà substantielle — évite de refaire un travail déjà fait |
| 2026-08-22 | `fiabilisation-tests-plateforme` Phase 3 reviewed | 4 décisions techniques actées via `AskUserQuestion` (mécanisme, périmètre déjà figé en Phase 2) : D-FLOWABLE-LOCK-TEST=H2+threads réels, D-PARALLELISM-TEST=H2+threads réels, D-LIQUIBASE-TEST=échec partiel mono-schéma (la prémisse multi-schéma du TODO §28 ne correspond pas à l'architecture réelle), D-CONTAINER-REUSE=conteneur Testcontainers singleton statique |
| 2026-08-22 | `fiabilisation-tests-plateforme` Phase 4 (planning) | 15 tâches sur 5 milestones ; testing doc scénarisé (traçabilité requirements→tests complète) |
| 2026-08-22 | `fiabilisation-tests-plateforme` Phase 5 (implémentation) | 15 tâches en TDD (H2 exécuté et vert ; Testcontainers écrit/compilé, SKIPPED proprement ici via `Assumptions.assumeTrue`/`isDockerAvailable()`, CI seulement) ; 1 bug réel trouvé et corrigé (`DataIntegrityViolationException` brute au lieu de `WorkflowAlreadyRunningException`, traitée à tort comme retry transitoire par `OutboxRelayScheduler`) ; JDK 25 installé dans le bac à sable (requis par `pom.xml`, absent au démarrage du container) ; couverture BRANCH réelle 73% trouvée sous le seuil D-COVERAGE 75% décidé en Phase 1 — question posée à l'utilisateur (`AskUserQuestion`), réponse = combler par des tests ciblés ; 8 classes jamais testées comblées en 3 vagues (73%→74%→75.03%) ; `./mvnw verify` final : 1050 tests (0 échec), LINE 93.7%, BRANCH 75.0% ; TODO §26/§28/§37 clos |
| 2026-08-22 | `fiabilisation-tests-plateforme` livrée | PR #66 mergée sur `main` ; TODO §26/§28/§37 clos ; prochain = `deploiement-bpmn` §35 |
| 2026-08-22 | `deploiement-bpmn` Phase 1+2 reviewed | Décision produit débloquée via `AskUserQuestion` : D-BPMN-TARGET=option (c) écran d'upload minimal (pas Modeler ni Cockpit) ; précision demandée explicitement par l'utilisateur → D-BPMN-PREVIEW=prévisualisation obligatoire du diagramme avant import, réutilisant le rendu `bpmn-js` de `BpmnViewer.vue` ; D-BPMN-PLACEMENT=nouvelle section dans `/admin/workflow-types` existant (rôles `TENANT_ADMIN`/`WORKFLOW_ADMIN` déjà en place), pas de nouvel écran. Précédents de code identifiés avant de trancher (`DossierElementUploadController` comme patron d'upload multipart) |
| 2026-08-22 | `deploiement-bpmn` Phase 3 reviewed | 5 décisions design actées via `AskUserQuestion` : D-BPMN-PREVIEW-COMPONENT=extraction `BpmnCanvas.vue` partagé (utilisateur n'a **pas** suivi la recommandation, a choisi l'extraction plutôt que la réutilisation directe de `BpmnViewer.vue`) ; D-BPMN-KEY-VALIDATION=rejet strict clé inconnue ; D-BPMN-REDEPLOY-INFO=afficher version+instances actives avant confirmation ; D-BPMN-UPLOAD-SIZE=réutilise le seuil global (revirement après qu'une contradiction avec la convention « source de vérité unique » déjà établie pour les pièces jointes ait été trouvée et signalée avant de trancher) ; D-BPMN-UPLOAD-CONTENT-TYPE=nouvelle classe dédiée sans champ de taille |
| 2026-08-22 | `deploiement-bpmn` Phase 4 (planning) | 6 milestones, 20 tâches ; testing doc scénarisé (traçabilité requirements→tests) |
| 2026-08-22 | `deploiement-bpmn` Phase 5 (implémentation) | 20 tâches en TDD, M1→M5 (backend domaine/Flowable/endpoint, frontend `BpmnCanvas.vue`+`BpmnUploadPreview.vue`+intégration admin, doc). Écart réel trouvé en implémentation (pas en revue) : `BpmnXMLConverter` appelé directement dans le use case domaine violait `ArchitectureRulesTest.domain_has_no_framework_dependency` — extrait en `BpmnParserPort`/`FlowableBpmnParserAdapter`, pas prévu au design initial. Backend : `./mvnw verify` ciblé vert (ArchUnit, OpenAPI golden régénéré, tous les nouveaux tests) ; frontend : 162 tests Vitest (0 échec), typecheck/lint propres, coverage au-dessus des seuils. TODO §35 clos |
| 2026-08-22 | `deploiement-bpmn` M6 (vérification globale) | `./mvnw verify` 1075 tests (0 échec), seuils Jacoco tenus (écart BRANCH réel trouvé à 74.92% au 1er passage, comblé par 1 test ciblé) ; 163 Vitest, typecheck/lint propres ; 43/43 Playwright (3 nouveaux `E2E-BPMN-*`) |
| 2026-08-22 | `deploiement-bpmn` Phase 7 rejouée | Utilisateur : « toutes les phases ont-elles été exécutées ? pas d'autres questions en Phase 3 ? » → vérifié dans l'historique brut de session (pas la mémoire résumée) : Phase 3 complète (5 `AskUserQuestion` réels, tous documentés — le doc affirmait à tort « 7 », corrigé) ; en revanche Phase 7 (check implementation) n'avait pas été faite sérieusement — rejouée fichier par fichier, 2 vrais bugs trouvés et corrigés (`NullPointerException` potentielle sur `originalFilename` null dans `FlowableWorkflowDeploymentAdapter`, regex d'extraction trop strict côté preview) ; doc d'implémentation resté le squelette vide, rédigé ; plusieurs cases testing doc jamais cochées, corrigées |
| 2026-08-22 | `deploiement-bpmn` livrée | PR #67 mergée sur `main` (CI hébergé rouge depuis plusieurs jours pour une raison d'infrastructure sans rapport avec le diff — confirmé rouge aussi sur les merges précédents dont #66 — mergé sur la base des vérifications locales complètes) ; TODO §35 clos ; prochain = `modele-physique-consolide` §25 |
| 2026-08-24 | `edition-bpmn` — analyse initiale (hors cycle formel) | Question ouverte de l'utilisateur (« comment construire un BPMN dans l'app, sans dépendance à une app connexe ») comparée à 4 options ; option `bpmn-js` Modeler + `bpmn-js-properties-panel` (provider custom Flowable) retenue, Flowable Modeler explicitement écarté comme dernier recours par l'utilisateur. TODO §39 ouvert, [analysis doc/14-edition-bpmn.md](analysis%20doc/14-edition-bpmn.md) + doc requirements Phase 1 créés. **Cycle `dev-lifecycle` volontairement pas démarré** (demande explicite utilisateur) — Phase 2 (points ouverts D-EDIT-*) reste à faire avant tout passage en implémentation. |
| 2026-08-24 | `edition-bpmn` cycle `dev-lifecycle` lancé, Phase 2 reviewed | Setup vérifié (`skill list` 28/28, `lint`/`lint --feature` verts, `task` indisponible dans cette install — continué sans traçage). Mode **no-worktree** retenu (session CCR verrouillée sur `claude/awesome-dirac-h6zc7u`, une seule feature active — isolation worktree sans intérêt ici). Recherche mémoire vide (sujet neuf). Inventaire de code fait avant de poser les questions (attributs `flowable:*` réellement utilisés via `grep`, hauteur fixe 360px de `BpmnCanvas.vue`, `TaskService.setAssignee` au runtime). 7 décisions actées via `AskUserQuestion` (une à la fois) : D-EDIT-SCOPE, D-EDIT-PLACEMENT=nouvel écran dédié, D-EDIT-ENTRYPOINT=3 points d'entrée, D-EDIT-SAVE=brouillon persisté backend (**l'utilisateur n'a pas suivi la recommandation** d'export fichier local — élargit le scope à un nouveau composant backend), D-EDIT-DRAFT-MODEL=un brouillon par clé de process sans historique, D-EDIT-DEPLOY-FLOW=bouton déployer intégré avec logique partagée extraite de `BpmnUploadPreview.vue`, D-EDIT-UNSAVED=sauvegarde explicite + `beforeunload`. Décisions stockées dans la mémoire AI DevKit (`repo:flowforge`). Prochain : `dev-design` Phase 3. |
| 2026-08-24 | `edition-bpmn` Phase 3 reviewed | Exploration de code dédiée (agent Explore) avant de poser les questions : structure exacte de `DeployBpmnProcessUseCaseImpl`/`BpmnParserPort`/`WorkflowBpmnDeploymentController` (référence directe), pattern « un par clé, écrasé » déjà présent via `TenantRetentionSettings` (clé naturelle = `@Id`), précédent de clé composite `@IdClass` déjà utilisé 2x (`WorkflowIdempotencyJpaEntity`, `WebhookIdempotencyJpaEntity`), structure des migrations Liquibase, règles ArchUnit exactes. 3 décisions actées via `AskUserQuestion` (une à la fois) : D-DESIGN-DRAFT-KEY=clé composite `@IdClass` (pas UUID+contrainte unique), D-DESIGN-ROUTE=route unique `/admin/bpmn-editor` avec query param optionnel (pas de routes séparées), D-DESIGN-DRAFT-LIFECYCLE=brouillon supprimé après déploiement réussi. Gap réel trouvé en exploration (pas supposé) : aucun endpoint existant ne renvoie le XML brut d'un process déployé par clé — nouvelle méthode `WorkflowDeploymentPort.findDeployedXml`, documentée en Design Decisions plutôt que silencieusement ajoutée. Couverture requirements→design vérifiée exhaustive, aucun gap. Décisions stockées en mémoire AI DevKit. Prochain : `dev-planning` Phase 4. |
| 2026-08-24 | `edition-bpmn` Phase 4 (planning) créée | Testing doc créé (`docs/ai/testing/2026-08-24-feature-edition-bpmn.md`, status `planned`) — comble l'étape sautée lors de la Phase 1 initiale menée hors cycle formel. Scénarios dérivés des success criteria (requirements) et des composants (design) : `SaveBpmnDraftUseCaseImplTest`, `GetBpmnDraftUseCaseImplTest`, `DeployBpmnProcessUseCaseImplTest` modifié, `FlowableWorkflowDeploymentAdapterTest` modifié, `BpmnDraftApiTest`, `JpaBpmnDraftRepositoryAdapterTest` côté backend ; `flowablePropertiesProvider.ts`, `BpmnEditorCanvas.vue`, `useBpmnDraft.ts`, `useBpmnDeployment.ts`, `BpmnEditorView.vue` côté frontend ; 4 scénarios E2E (`E2E-EDIT-BPMN-1..4`). Plan en 8 milestones / ~20 tâches (`docs/ai/planning/2026-08-24-feature-edition-bpmn.md`), séquencement par dépendance (M1→M2→M3/M4 parallélisables→M5/M6→M7→M8), risques identifiés (compatibilité `bpmn-js-properties-panel`/`bpmn-js` 18.24, comportement du moddle sur un attribut `flowable:*` non couvert — vérification empirique prévue en T5.1 avant d'aller plus loin). Couverture testing→planning vérifiée exhaustive. Décisions stockées en mémoire AI DevKit. Prochain : `dev-implementation` Phase 5. |
| 2026-08-24 | `edition-bpmn` Phase 5 (implémentation) exécutée, TODO §39 clos | M1-M4 (backend) et M5-M7 (frontend, hors E2E) implémentés **en parallèle** par deux agents `general-purpose` dans des worktrees git isolés (`isolation: worktree`) — contrat API déjà figé par le design en Phase 3, aucune dépendance d'exécution croisée. Fusion séquentielle par l'orchestrateur : backend via `git merge --no-ff` (branche propre) ; frontend via `git cherry-pick` ciblé (3 commits) — sa branche partait d'une base `origin/main` ayant divergé (PR #70 non lié mergé entre-temps), seuls les commits strictement scopés `frontend/**` repris, sans importer l'historique non lié. M8 (vérification globale + E2E) exécuté ensuite par l'orchestrateur : **1 bug réel trouvé et corrigé** (`modeler.get('commandStack').on(...)` — `CommandStack` de diagram-js n'a pas de `.on()` propre, événements via l'`eventBus`, masqué par des mocks Vitest, trouvé en écrivant l'E2E dans un vrai navigateur Playwright) ; navigation E2E adaptée (clic plutôt que `page.goto` direct sur route imbriquée — le build en chemins relatifs casse le chargement direct d'assets sur une route profonde, limitation préexistante du dépôt) ; fixtures BPMN avec DI complète ajoutées pour le `Modeler` réel. L'agent frontend a lui-même trouvé et corrigé 1 bug en TDD (race condition de montage `BpmnEditorCanvas`/`useBpmnDraft.load()`). `./mvnw verify` final : **1106 tests, 0 échec**, LINE 93.86%/BRANCH 77.14% ; frontend **193 Vitest** (0 échec), typecheck/lint propres, **47/47 Playwright** (43 existants + 4 nouveaux `E2E-EDIT-BPMN-1..4`). `README.md`/`05-workflow-engine.md`/`GUIDE-UTILISATEUR.md` corrigés (mentions « pas de designer/éditeur BPMN » retirées). Docs planning/testing/implementation mis à jour avec preuves fraîches (une section « Phase 7 » avait été écrite à ce stade mais décrivait en réalité le travail de M8, pas une revue dédiée — corrigé à l'étape suivante). TODO §39 clos. Prochain, sur demande : `dev-review` Phase 9, puis PR. |
| 2026-08-24 | `edition-bpmn` Phase 7 (check implementation) exécutée, sur demande explicite | Revue fichier par fichier des 45 fichiers du diff (`git diff a56ceb7~1 7aa31d5 --stat`), au-delà de la revue déjà faite par les agents. Alignement design confirmé (7+3 décisions couvertes), précédent `WorkflowTaskController`/`WorkflowEnginePort` vérifié (pas supposé) pour justifier la Design Deviation du contrôleur dépendant d'un port. **1 bug réel trouvé et corrigé** : `SaveBpmnDraftUseCaseImpl.save()` levait une `NullPointerException` non gérée (→ `500`) sur un `xml` `null`/manquant dans le corps `PUT /api/workflow-bpmn-drafts/{key}` — même classe de défaut que la NPE déjà trouvée en Phase 7 de `deploiement-bpmn` (§35, `originalFilename` null), réapparue ici indépendamment malgré le précédent. Corrigé en TDD (rouge confirmé avant le correctif) : garde `xml == null \|\| xml.isBlank()` → `InvalidBpmnException` → `400`, 2 tests unitaires + 2 tests API (dont `PUT` sans rôle admin, jamais testé explicitement malgré une garde `@PreAuthorize` classe déjà correcte). **1 point ouvert signalé, non corrigé** (sévérité UX) : `useBpmnDraft.load()` sans `try/catch` — une erreur réseau bloque l'éditeur sur une page vide sans message, hors scope des success criteria actés, laissé à la décision de l'utilisateur. `./mvnw verify` final : **1110 tests, 0 échec**, LINE 93.84%/BRANCH 75.27% (≥ seuils, BRANCH proche du seuil). Docs testing/implementation/TODO/FILE-ATTENTE mis à jour avec les chiffres finaux. Prochain, sur demande : `dev-review` Phase 9. |
| 2026-08-24 | `edition-bpmn` Phase 8 (write tests) exécutée, sur demande explicite | Coverage vérifiée par classe/fichier (`jacoco.csv` backend via awk, `npm run test:coverage` frontend), pas seulement à l'agrégat `BUNDLE`. Backend : tous les fichiers `edition-bpmn` à 100% sauf 2 blocs `catch (IOException)` défensifs jugés acceptables (1 préexistant à `deploiement-bpmn`, 1 nouveau, non-branchant). **Écart de couverture réel trouvé et comblé côté frontend** (pas un bug) : `flowablePropertiesProvider.ts` à 33% branch coverage — les 5 tests initiaux ne couvraient que `getGroups()` (quels groupes pour quel type d'élément), jamais les fonctions `getValue`/`setValue` de chaque entrée, c'est-à-dire le pont lecture/écriture réel entre le panel de propriétés et les attributs `flowable:*` — la logique la plus spécifique de toute la feature (D-EDIT-SCOPE). 3 tests ajoutés en réécrivant les mocks `CheckboxEntry`/`TextFieldEntry` pour capturer leurs props plutôt que de les avaler, invoquant `getValue`/`setValue` directement : tous verts du premier coup, confirmant l'implémentation déjà correcte (suit l'API `modeling.updateModdleProperties` documentée par bpmn.io) → 100% sur les 4 métriques pour ce fichier. Validation finale : `./mvnw verify` 1110 tests (inchangé, aucun code backend touché) ; frontend `npm run test` **196/196** (193+3), `test:coverage` statements 86.84%/branches 77.59%/functions 86.05%/lines 88.71% (≥ seuils 80/75/70/60), typecheck/lint propres, `npx playwright test` **47/47** (0 régression). Prochain, sur demande : `dev-review` Phase 9, puis PR. |
| 2026-08-24 | `edition-bpmn` Phase 9 (revue finale) close, [PR #71](https://github.com/superjojo79/FlowForge/pull/71) ouverte, sur demande explicite | Revue holistique : diff complet vérifié (`git diff --stat origin/main...HEAD`, 65 fichiers strictement scopés à la feature + doc liée) ; contrat OpenAPI relu (2 nouveaux chemins, 2 schémas, imprécision cosmétique mineure relevée sur `deployedXml` — `format: byte` documenté au lieu de `text/xml` réel, non bloquant, comportement déjà testé) ; dépendance `bpmn-js-properties-panel` re-confirmée saine ; migration `013-bpmn-draft-tables.yaml` a un rollback explicite ; aucun breaking change ; scan diff pour secrets/debug/tests `.only`/`.skip` — rien trouvé. Preuve fraîche re-vérifiée juste avant la PR : `./mvnw verify` 1110 tests (0 échec), frontend `npm run test` 196/196, typecheck/lint propres, `npx playwright test` 47/47. **4 bugs réels trouvés et corrigés au total sur la feature** (Phases 5 et 7). TODO §39, FILE-ATTENTE.md et le doc implementation mis à jour avec la synthèse Phase 9. |
| 2026-08-22 | `modele-physique-consolide` implémentée (Phases 1-9) | Écart trouvé avant d'écrire le problem statement : TODO §25 supposait un `TenantRegistry` actif et des contextes Liquibase séparés, tous deux explicitement obsolètes depuis la décision n°13 (note de mise à jour de la décision n°1 jamais répercutée dans §25) — 3 décisions via `AskUserQuestion` (corriger le scope plutôt que documenter l'obsolète, doc seule sans créer `platform.tenant_migration_status`, nouveau doc `13-modele-physique.md` avec ERD mermaid plutôt qu'un dump SQL dupliquant les changelogs). Contenu extrait par parsing structuré des 12 changelogs (pas résumé de mémoire) : 17 tables réelles (pas « une dizaine »), 4 index uniques partiels (pas « deux »), seulement 2 FK sur 17 tables contraintes en DB (le reste logique, explicité pour ne pas induire en erreur). Feature 100% documentaire, vérifiée par `git diff --stat` (aucun `.java`/`.yaml` de changelog touché). TODO §25 clos, en attente de PR |
| 2026-08-22 | `modele-physique-consolide` livrée | PR #68 mergée sur `main` (CI hébergé rouge en ~2s, même signature d'infrastructure que sur les merges précédents #66/#67, sans rapport avec un diff 100% documentaire — mergé sur la base des vérifications factuelles locales) ; TODO §25 clos ; file d'attente vide — les 5 features du regroupement 2026-08-20 sont toutes livrées, prochaine feature à identifier lors d'une prochaine analyse |
| 2026-08-24 | `DEFERRED-FEATURES.md` resynchronisé | PR #69 mergée sur `main` — §3 corrigé (5 lignes stales : §23/24/25/26/28/30), §2 complété avec les 5 features du regroupement, §1.2 note obsolète corrigée (contextes Liquibase tenant-init/tenant-migration) |
| 2026-08-24 | `masquage-donnees-sensibles-logs-webhooks` Phase 1 reviewed | Audit avant rédaction : contrairement à l'énoncé TODO §23, **aucune fuite active trouvée** (ni logs, ni webhooks — vérifié par grep exhaustif sur le code et les `.bpmn20.xml`) ; reformulé en risque latent sans garde-fou. D-SCOPE=garde-fou complet (logs + webhook), décision actée via `AskUserQuestion` |
| 2026-08-24 | `masquage-donnees-sensibles-logs-webhooks` Phase 3 reviewed | 2 décisions techniques actées via `AskUserQuestion` : D-LOG-GUARD=toString() sûr + règle ArchUnit, D-WEBHOOK-MASK=contexte dossier optionnel + lookup `FieldDefinition` dans `SendNotificationUseCaseImpl` |
| 2026-08-24 | `masquage-donnees-sensibles-logs-webhooks` T1.3 corrigée en implémentation | La règle ArchUnit envisagée (détection de logging) s'est avérée irréalisable (ArchUnit ne fait pas d'analyse de flux de valeurs — signature bytecode identique quel que soit le type réel de l'argument d'un `Logger.info(String, Object)`) ; vérifié avant de coder, pas supposé. Remplacée par une liste blanche des classes autorisées à lire `Dossier.fields()`/`FieldDefinition.defaultValue()`, déterminée en faisant tourner la règle contre le code réel (pas devinée par `grep`, qui remontait des faux positifs `command.fields()`/`request.fields()`) ; décision validée via `AskUserQuestion` |
| 2026-08-24 | `masquage-donnees-sensibles-logs-webhooks` implémentée (Phases 1-9) | 10 tâches TDD sur 3 milestones (M1 garde-fou logs, M2 masquage webhook, M3 vérification+clôture). `SendNotificationUseCaseImpl` masque automatiquement les valeurs sensibles avant toute écriture outbox, pour tous les canaux, sans dépendre d'un futur producteur. Preuve bout en bout réelle (`SendNotificationUseCaseImpl` → `NotificationOutboxRelay` → `WebhookRestAdapter`, corps HTTP capturé) + 2 contrôles mutants manuels (masquage désactivé temporairement, tests confirmés en échec, code restauré) prouvant que les tests détectent vraiment une régression. `./mvnw verify` final : 1088 tests (0 échec), Jacoco tenu. Zéro fichier frontend touché. TODO §23 clos avec note honnête (pas de fuite active, protection préventive), en attente de PR |
| 2026-08-25 | Revue alignement × sécurité | [REVUE-ALIGNEMENT-SECURITE.md](REVUE-ALIGNEMENT-SECURITE.md) ; TODO §40–46 ouverts (SSTI, GRANT PG, actuator, secrets, SSRF TOCTOU, dérive docs, ArchUnit `application`). `edition-bpmn` PR #71 mergée ; follow-ups ops PR #72 (DEV-PURGE, recovery PROCESSING, `com.monapp` dans `01`). Prochain sans décision : chore §45. |
| 2026-08-25 | Points sécurité 40–44 regroupés en feature candidate | Demande utilisateur : rassembler les points pertinents de la revue en une feature de durcissement sécurité. Regroupement `durcissement-securite-post-revue` = TODO §40+41 (chaîne SSTI→PG partagé) + §42+43 (config/secrets) + §44 (SSRF TOCTOU), séquencés dans cet ordre. §45 (chore doc, pas d'arbitrage) et §46 (ArchUnit `application`, classé Architecture pas Sécurité par la revue §7) restent hors périmètre. Scoping seul — options A/B/C par point toujours à trancher en `dev-requirements` (`AskUserQuestion`, une décision à la fois), pas de cycle `dev-lifecycle` lancé sur demande explicite de l'utilisateur. |
| 2026-08-25 | `durcissement-securite-post-revue` cycle démarré | Demande utilisateur : démarrer le lifecycle. Phases 1–2 : D-* = recommandations revue (pas d'AskUserQuestion en agent cloud). §46 hors scope. |
| 2026-08-25 | `durcissement-securite-post-revue` Phase 5 done | M1–M7 TDD. `./mvnw verify` 1173 tests (0 échec), Jacoco BRANCH 75,03 % / LINE 93,3 % ; Vitest 199 + Playwright 47. `ObservabilityActuatorIT` vert ; `TenantRoleIsolationIT` SKIPPED sans Docker. SSTI 400 à l'activation (pas au POST draft). [PR #76](https://github.com/superjojo79/FlowForge/pull/76). |
| 2026-08-25 | `durcissement-securite-post-revue` Phase 7 checked | Revue fichier par fichier vs design. Aligné D-40 B2 / E, D-41 A/S, D-42 L, D-43 F, D-44 P. Écarts notés (Host JDK restricted headers ; « enregistrement » = activation). Pas de relock D-*, pas de retour Phase 5. 130 tests ciblés, 0 échec. |
| 2026-08-25 | `durcissement-securite-post-revue` Phase 8 verified | Jacoco classe par classe. Generate HTTP `T()` 400. `./mvnw verify` 1185 tests (0 échec), BRANCH 75,57 % ; Vitest 199 + Playwright 47. Suite : Phase 9. |
| 2026-08-25 | `durcissement-securite-post-revue` Phase 9 READY | Revue holistique : 42 fichiers, pas de pom/OpenAPI/Liquibase/SPA/domain ; ports inchangés ; sonde SSTI inlining/th:if/preprocess. Findings non bloquants (Host JDK restricted headers, IT hors surefire). `./mvnw verify` **1186** tests (0 échec), Jacoco LINE 93,52 % / BRANCH 75,57 % ; Vitest 199 + Playwright 47. [PR #76](https://github.com/superjojo79/FlowForge/pull/76). Merge humain. |
| 2026-08-25 | `durcissement-securite-post-revue` livrée | [PR #76](https://github.com/superjojo79/FlowForge/pull/76) mergée sur `main` (merge humain) ; TODO §40–44 clos ; §46 reste hors périmètre (architecture, pas sécurité) ; prochaine feature à identifier |
| 2026-08-26 | Analyse initiale `serveur-mcp` | Cadrage d'un serveur MCP interne : consommateur (outillage interne uniquement), 3 tools d'écriture (définition de type de dossier, insertion BPMN + `WorkflowTypeConfig`, lecture dossier) + 4 tools de lecture faible risque ajoutés après examen (types de dossier, types de workflow, recherche sans agrégats, templates documents) ; backlog de 4 tools à risque modéré (création dossier, ajout d'élément, démarrage workflow, génération document) ; exclusions explicites actées (ACL, purge, statut workflow link). Intégration = nouvel adaptateur `infrastructure.mcp` dans le monolithe, auth = rôle `API_SERVICE`. Point stats identifié comme dépendant d'une feature reporting/SLA à cadrer séparément. [15-serveur-mcp.md](analysis%20doc/15-serveur-mcp.md), aucun code. |
| 2026-08-26 | Analyse initiale `reporting-sla`, décision de séquencement | Prérequis du point stats de `serveur-mcp`. Périmètre proposé en deux lots (Lot 1 — comptages/durées à la volée sur `Dossier`/`DossierWorkflowLink`, sans nouvelle table de faits ; Lot 2 — seuils SLA par `DossierType` sur le patron déjà livré pour la rétention, décision n° 21) construit sans dupliquer l'observabilité technique déjà livrée (`observabilite`, PR #59). **Tranché à la demande de l'utilisateur : les deux lots sont requis** pour débloquer le MCP à coup sûr, pas de version dégradée avec le Lot 1 seul. 4 questions laissées pour la phase requirements formelle (seuil `AT_RISK`, RBAC exact, granularité `groupBy`, caractère obligatoire de `slaTargetHours`). [16-reporting-sla.md](analysis%20doc/16-reporting-sla.md), aucun code. |
| 2026-08-26 | Analyses `serveur-mcp`/`reporting-sla` mergées, positionnement file d'attente | [PR #78](https://github.com/superjojo79/FlowForge/pull/78) mergée sur `main` (commit `3947ced`). `reporting-sla` positionnée en **Prochain** dans la file d'attente ; `serveur-mcp` marquée bloquée sur sa livraison complète (les deux lots). |
| 2026-08-26 | `reporting-sla` livrée | [PR #80](https://github.com/superjojo79/FlowForge/pull/80) mergée sur `main` ; lifecycle phases 1–9 closes ; TODO n/a (feature issue de l'analyse, pas d'un point TODO numéroté). Prérequis stats MCP débloqué. |
| 2026-08-27 | `serveur-mcp` Phase 1 clarification **close** | **D-SPA = A** (pas de SPA). **D-ACTOR = A** (conséquence POLICY-AS). **D-SDK** reporté Phase 3. Prochain : Phase 2 review. |
| 2026-08-27 | `serveur-mcp` Phases 2–3 reviewed | **D-TYPE-FIELDS = A** (schéma seul). **A-WF-REST-PARITY**. **D-SDK = A** (Spring AI 2.0.0 WebMVC, `/api/m2m/mcp`). Design réécrit (Liquibase 017, check schéma domaine, parse→upsert→deploy). Prochain : Phase 4 planning. |
| 2026-08-27 | `serveur-mcp` Phase 4 planned | 9 milestones / 32 tâches TDD ; traçabilité complète testing → tasks ; M1 gardes domaine d'abord, M4 SDK, M9 verify+régression FE. Prochain : Phase 5 T1.1. |
| 2026-08-28 | Analyse initiale `schema-champs-dynamiques`, cycle `dev-lifecycle` démarré | Écart identifié : écran admin `DossierType` pour éditer `fieldDefinitions` manquant (backend déjà prêt, `CheckDossierTypeSchemaCompatibilityUseCase` existant mais jamais branché sur REST). TODO §47 ouvert (D-UI-SCHEMA-COMPAT et 3 autres points). Nom de lot choisi : `schema-champs-dynamiques` (aligné sur le nom du document d'analyse, distinct de `edition-champs-dossier` déjà pris). Feature indépendante de `serveur-mcp` (aucun chevauchement) — track lancé en parallèle. [17-schema-champs-dynamiques.md](analysis%20doc/17-schema-champs-dynamiques.md), aucun code. |
| 2026-08-28 | `schema-champs-dynamiques` Phases 1–2 (requirements) closes | Setup vérifié (`skill list` 28/28, `lint`/`lint --feature` OK, `task` indisponible — continué sans traçage). Mode **no-worktree** (session CCR verrouillée sur `claude/schema-champs-dynamiques-docs-zviunx`, une seule feature active côté cette session). Recherche mémoire vide (sujet neuf). 3 décisions actées via `AskUserQuestion` (une par question, options + recommandation) : **D-UI-SCHEMA-COMPAT=bloquer par défaut** (`UpdateDossierTypeUseCaseImpl` invoque `CheckDossierTypeSchemaCompatibilityUseCase` déjà existant, 409 via `DossierExceptionHandler`, aligne REST sur le comportement déjà strict du MCP `define_dossier_type`) ; **D-NAME-UNIQUE=garde-fou domaine** (unicité `name` portée par `DossierType`/`DossierFieldValidator`, protège aussi MCP/API directe) ; **D-ALLOW-UNKNOWN-SCOPE=inclus** (bascule `allowUnknownFields` dans le même écran). Références vérifiées contre le code réel avant rédaction (`grep` : `CheckDossierTypeSchemaCompatibilityUseCase`, `UpdateDossierTypeUseCaseImpl`, `DossierTypeSchemaIncompatibleException` existent bien tels que décrits par l'analyse). Décisions stockées en mémoire AI DevKit (`repo:flowforge`). Alignement maquette et branchement exact create vs update déférés à la phase design (pas bloquants). Prochain : `dev-design` Phase 3. |
| 2026-08-28 | `schema-champs-dynamiques` Phase 3 (design) close | Exploration de code dédiée avant de trancher : structure exacte de `UpdateDossierTypeUseCaseImpl`/`CreateDossierTypeUseCaseImpl`/`CheckDossierTypeSchemaCompatibilityUseCaseImpl`/`DossierTypeSchemaIncompatibleException`/`DossierExceptionHandler`/`DefineDossierTypeTool` lue directement (référence, pas supposition) ; confirmé que `CheckDossierTypeSchemaCompatibilityUseCaseImpl` est déjà dans l'allowlist ArchUnit `DOSSIER_FIELDS_READ_ALLOWLIST` (aucune règle à changer) ; confirmé qu'aucun DTO ni le golden OpenAPI ne change de forme. 1 décision actée via `AskUserQuestion` : **D-DESIGN-CHECK-PLACEMENT=centralisé dans `UpdateDossierTypeUseCaseImpl`** (option recommandée) — simplifie `DefineDossierTypeTool` (MCP) qui perd son appel dupliqué au check sur le chemin d'écriture réel, ne le garde que pour `dryRun`. Nouveau `@ExceptionHandler(DossierTypeSchemaIncompatibleException.class)` dédié (409, message + propriétés structurées). D-NAME-UNIQUE placé dans le compact constructor de `DossierType` (pas `DossierFieldValidator`, qui valide des valeurs pas la structure du schéma) — `IllegalArgumentException`, même pattern que `validateSlaTargetHours`, aucun nouveau handler nécessaire. Nouveau composant frontend `FieldDefinitionDefsEditor.vue` (HTML natif + scoped CSS, panneau inline sans modale, réutilisé création + édition). Couverture requirements→design vérifiée exhaustive (les 2 points ouverts restants du doc requirements — alignement maquette, forme du endpoint de check — sont fermés dans ce design). Décisions stockées en mémoire AI DevKit. Prochain : `dev-planning` Phase 4. |
| 2026-08-28 | `schema-champs-dynamiques` Phase 4 (planning) close | Testing doc scénarisé rédigé en premier (comble le manque de scénarios frontend repéré en relisant le premier jet — `FieldDefinitionDefsEditor.vue` et l'utilitaire de validation n'y figuraient pas, ajoutés avant de passer au planning) : ~40 scénarios (T-DOM/T-USE/T-EXC/T-MCP/T-CFG/T-FE/T-FE-VAL/T-FE-VIEW/T-API/E2E-SCHEMA). Plan en 8 milestones / ~15 tâches, séquencement par dépendance (M1 domaine, M2 blocage 409, M3 MCP dépend de M2, M4 API dépend M1-M3, M5 frontend composant indépendant, M6 intégration vue dépend M5, M7 E2E+doc, M8 clôture). Risque de régression signalé explicitement pour M3 : 2 tests `DefineDossierTypeToolTest` existants stubbent aujourd'hui `checkCompatibilityUseCase` directement et doivent être réécrits pour stubber `updateDossierTypeUseCase.update()` à la place (conséquence directe de D-DESIGN-CHECK-PLACEMENT, Phase 3). Couverture testing→planning vérifiée exhaustive dans les deux sens. Décisions stockées en mémoire AI DevKit. Prochain : `dev-implementation` Phase 5, tâche T1.1. |
| 2026-08-28 | `schema-champs-dynamiques` Phase 5 (implémentation) exécutée, TODO §47 clos | 8 milestones enchaînés en TDD sur demande explicite de l'utilisateur (rouge confirmé avant chaque correctif). M1 `DossierType.requireUniqueFieldNames`. M2 `UpdateDossierTypeUseCaseImpl` bloque via `CheckDossierTypeSchemaCompatibilityUseCase` (409, nouveau `@ExceptionHandler`). M3 `DefineDossierTypeTool` (MCP) simplifié — retire l'appel dupliqué au check sur le chemin réel, 2 tests existants réécrits comme anticipé en planning. M4 9 nouveaux cas `DossierApiTest` (200/409/400/403, round-trip `allowUnknownFields`). M5 `FieldDefinitionDefsEditor.vue` (nouveau composant, HTML natif + scoped CSS) + `validateFieldDefinitions.ts`. **1 bug réel trouvé et corrigé** : le composant liait son rendu directement sur la prop `modelValue` au lieu d'un état local réactif, n'affichait donc pas ses propres modifications avant un aller-retour par le parent (`type=SELECT` ne révélait pas l'éditeur d'options) — corrigé en TDD, 21/21 verts ensuite. M6 intégration `DossierTypesAdminView.vue` (formulaire création + panneau d'édition inline « Modifier les champs », pas de modale) — écart trouvé en implémentation : pré-remplissage direct depuis la liste déjà chargée plutôt qu'un `GET` dédié envisagé au design, la réponse de liste porte déjà le schéma complet. M7 4 nouveaux `E2E-SCHEMA-*` Playwright (59/59 au total, 0 régression) + `GUIDE-UTILISATEUR.md` §1 mis à jour. M8 vérification globale : couverture revérifiée par fichier (pas seulement l'agrégat) — 2 lacunes réelles trouvées et comblées côté frontend (6 cas de test ajoutés sur `FieldDefinitionDefsEditor.vue` → 100 % lignes/fonctions ; 1 cas ajouté sur le panneau d'édition de `DossierTypesAdminView.vue`, chemin de blocage local jamais exercé). `./mvnw verify` **1382 tests** (0 échec), Jacoco tenu ; frontend **247 Vitest** (0 échec), typecheck/lint propres, **59/59 Playwright**. Testing/planning/implementation docs et TODO §47 mis à jour avec les preuves fraîches. `CHANGELOG.md` différé (aucune PR ouverte à ce stade). Prochain, sur demande : `dev-implementation` Phase 7 (check implementation), puis Phase 8, Phase 9, PR. |
| 2026-08-28 | `schema-champs-dynamiques` Phases 7–9 closes, sur demande explicite (« enchaîne 7, 8, 9 puis crée une PR et merge ») | **Phase 7** : revue fichier par fichier des 27 fichiers du diff (`git diff origin/main...HEAD` après `git fetch` frais — le ref local était périmé, diff initial trompeur de 365 fichiers). Callers tracés (2 points d'instanciation `UpdateDossierTypeUseCaseImpl`, 2 consommateurs frontend `createDossierType`/`updateDossierType`), aucun manqué. **1 écart réel trouvé et corrigé** : le check de compatibilité (lecture DB) s'exécutait avant la validation structurelle en mémoire (unicité `name`) — réordonné pour échouer vite (400) sans requête DB inutile sur un schéma déjà malformé ; corrigé en TDD (rouge confirmé). **Phase 8** : preuve fraîche post-correctif — `./mvnw verify` 1383 tests (0 échec) ; frontend 247 Vitest + 59 Playwright (0 échec), typecheck/lint propres. **Phase 9** : revue holistique — aucune migration, aucun changement OpenAPI, aucune nouvelle dépendance, scan diff sans secret/debug/test désactivé, cohérence vérifiée avec les patterns existants (`DossierConflictException`, `validateSlaTargetHours`, `DynamicFields.vue`). Aucun blocage. |
| 2026-08-28 | `schema-champs-dynamiques` livrée | [PR #87](https://github.com/superjojo79/FlowForge/pull/87) mergée sur `main` (commit `fb921ca`, squash) — CI hébergé rouge sur les 2 checks (`build`, `frontend`), confirmé identique sur `main` lui-même (dernier commit `70e2d73` et 20+ exécutions précédentes, échec ~3s, HTTP 404 sur les logs — signature d'infrastructure sans rapport avec ce diff, déjà documenté à plusieurs reprises dans cet historique) ; commentaire posté sur la PR avant de merger sur la base des vérifications locales complètes. TODO §47 clos. |
| 2026-08-28 | `serveur-mcp` vérifiée livrée, statuts resynchronisés | Demande utilisateur : « la feature mcp est totalement finalisée et mergée sur main, vérifie et mets les statuts à jour ». Vérifié (pas supposé) : `git log origin/main` confirme « Merge pull request #82 from superjojo79/claude/pr-81-phase-5-bgd04q » (commit `be9aff7`, mergée 2026-08-28 06:14, **avant** `schema-champs-dynamiques` PR #87 le même jour) ; [PR #82](https://github.com/superjojo79/FlowForge/pull/82) confirmée `merged: true` via l'API GitHub, suite de la [PR #81](https://github.com/superjojo79/FlowForge/pull/81) (Phases 1–4). Contenu de la PR vérifié dans le corps et l'historique de commits : 9 milestones TDD (M1 gardes domaine `API_SERVICE` → M9 vérification globale), Phase 7 (check implementation) et Phase 8 (comblement de trous de couverture branches Jacoco) rejouées avant la Phase 9 (revue finale pre-push), toutes dans la même PR — cycle complet. 9 tools MCP sur `POST /api/m2m/mcp` (Streamable HTTP, Spring AI 2.0.0), JWT M2M `API_SERVICE` déjà existant réutilisé tel quel (aucune nouvelle chaîne de sécurité). `./mvnw verify` vert (Jacoco LINE ≥85 %/BRANCH ≥75 % tenus) ; frontend régression complète 213/213 Vitest + 55/55 Playwright, **zéro fichier `frontend/**` modifié** (feature 100 % backend). `FILE-ATTENTE.md` resynchronisé : ligne déplacée vers *Récemment livré* (insérée chronologiquement avant `schema-champs-dynamiques`, mergée plus tôt le même jour), retirée de la file d'attente (désormais vide), en-tête et `DEFERRED-FEATURES.md` §1/§2 mis à jour en cohérence. |
| 2026-09-01 | `assainissement-dette-technique` Phases 1–9 closes, TODO §49 clos | Cycle `dev-lifecycle` complet exécuté phase par phase sur demande explicite (« Oui, lance la phase N » à chaque étape), mode **no-worktree** (contrainte de session sur `claude/inspect-root-md-x43d24`). **Phases 1–2** : vérification exhaustive des 3 points d'entrée d'écriture connus (`WorkflowApiDtos.required()`, `McpArgs.requireString()`, `DefineDossierTypeTool`) confirme qu'aucun ne laisse passer un `processKey`/`workflowKey` blank aujourd'hui — le gap est uniquement une absence de défense en profondeur au niveau des 4 records domaine, pas un bug atteignable. 3 décisions actées via `AskUserQuestion` (options recommandées). **Phase 3** : investigation complémentaire déterminante — identifié le vrai `log.warn` par item existant (`FlowableWorkflowEngineAdapter.deleteProcessInstanceBestEffort`, exception avalée sans `throw`) qui justifie concrètement le mécanisme MDC et impose que le callback enveloppe tout `purgeDossier(record)`, pas seulement le `catch` (l'analyse initiale n'avait trouvé aucun log par item). Forme du callback affinée en `Function<UUID, Runnable>` (pas un simple `Consumer<UUID>`) pour une fermeture symétrique du `MdcScope` sans exception checked. `D-PERSISTABLE-CONFIRM` fermé par preuve documentée (`@Version long` primitif + `JpaRepository` standard = comportement `JpaMetamodelEntityInformation.isNew()` déjà correct), aucun test ajouté (comportement garanti par le framework). **Phase 5** : TDD sur M1 (4 records) et M2 (callback). Écart de discipline TDD documenté par honnêteté : le tout premier geste du changement de signature de port (`ProcessPurgeOutboxUseCase.processBatch` +1 paramètre) a implémenté le comportement complet avant le test — corrigé en revenant à une base compilable (callback no-op partout) puis en repilotant le vrai comportement par test rouge→vert. Preuve directe de la portée du callback via un test d'intégration multi-items utilisant le **vrai** `ProcessPurgeOutboxUseCaseImpl` (pas mocké) : `dossierId`/`tenantId` co-présents en MDC exactement au moment du `log.warn` réel, sans fuite entre 2 items du même batch. **Phase 7** : callers de production des 4 constructeurs gardés tracés un par un — tous protégés en amont, aucune régression. 1 trouvaille mineure hors périmètre documentée sans être corrigée (`WorkflowTriggerEvent.processKey`, même gap mais déjà couvert transitivement via `TriggerOutboxPayload`). **Phase 8** : balayage Jacoco classe par classe — 4 gardes + callback 100% couverts, tous les gaps résiduels pré-existants et hors périmètre. **Phase 9** : `CHANGELOG.md`, `TODO.md` §49, `analysis doc/00-index.md`/`19-...md`, `FILE-ATTENTE.md` mis à jour. `./mvnw verify` **1430 tests** (0 échec) ; frontend **247 Vitest** + **59 Playwright** (0 échec, zéro fichier `frontend/**` touché). PR pas encore créée à ce stade. |
| 2026-09-02 | `connecteur-callback` Phases 1–9 closes puis mergée, TODO §51 clos | Cycle `dev-lifecycle` complet exécuté phase par phase sur demande explicite (« Oui, lance la phase N » à chaque étape), mode **no-worktree** (session liée à `claude/callbacks-dev-lifecycle-0b8k2d`). **Phase 3** : correction factuelle majeure — `RuntimeService.createMessageCorrelation(...)`, proposé par l'analyse d'origine (TODO §51), n'existe pas en Flowable 8.0.0 Community, confirmé en extrayant et lisant les sources réelles (`flowable-engine-8.0.0-sources.jar`), pas supposé. Remplacé par `ExecutionQuery.messageEventSubscriptionName(...).processVariableValueEquals(...)` + `messageEventReceived(...)` ; `ConnectorCapability.CALLBACK` tranché via `AskUserQuestion`. **Phase 5** : M1–M7 enchaînés en TDD sur demande explicite (« enchaîne les milestones et les tâches »). Mécanisme moteur prouvé contre le **vrai** Flowable (`CallbackSmokeIT`, process `callback-smoke.bpmn20.xml`) — la fixture a fini dans `src/main/resources/processes/` (pas `src/test/resources/**` comme prévu en Phase 4) après découverte que 3 fixtures `*-smoke.bpmn20.xml` y vivent déjà, auto-déployées, démarrées directement par `RuntimeService` sans jamais passer par `WorkflowTypeConfig`. **1 bug réel trouvé et corrigé** : `Map.copyOf` plantait (NPE) sur une variable de `mapping.variables` optionnelle non résolue dans le payload. **Phase 7** (revue fichier par fichier) : **2 écarts réels supplémentaires trouvés et corrigés**, tous deux prouvés contre le vrai moteur — (1) D-CALLBACK-TYPE, sévérité élevée : Flowable compare les variables de process par type ET valeur, un `correlationValue` numérique dans le payload externe ne matchait jamais une variable de process `String` (mode de défaillance systématique, pas un cas limite) ; corrigé par normalisation en `String`. (2) le design (Phase 3) promettait un 404 sur la race résiduelle `list()`/`messageEventReceived(...)`, le code livré en Phase 5 laissait fuir un `FlowableException` brut ; corrigé. **Phase 8** : balayage Jacoco classe par classe, 3 tests ajoutés pour fermer les dernières branches propres à la feature — 100 % atteint, écarts résiduels vérifiés pré-existants ou justifiés par parité avec `WebhookController`/`HttpRestConnector`. Vérifié explicitement (pas supposé) qu'aucun contrat OpenAPI/FE n'est impacté. **Phase 9** : revue holistique — 2 trouvailles mineures (`README.md` catalogue jamais mis à jour, duplication interne triviale), corrigées ; `CHANGELOG.md` mis à jour après ouverture de la PR (numéro réel requis par la convention du projet). `./mvnw verify` final **1533 tests** (0 échec), zéro fichier `frontend/**` touché. [PR #102](https://github.com/superjojo79/FlowForge/pull/102) créée puis **mergée sur `main` sans attendre la CI hébergée**, sur demande explicite de l'utilisateur (« ouvre la PR et merge sur main sans attendre la CI ») — statut CI hébergée sur cette PR non vérifié avant le merge (demande explicite dispensait de cette vérification), décision basée sur les vérifications locales complètes (`./mvnw verify`) accumulées sur les 9 phases. |

| 2026-09-02 | `connecteur-callback` : revue de sécurité adversariale post-merge, 5 écarts trouvés et corrigés | Demande explicite utilisateur (« réalise une passe de contrôle type adversarial sur le code », puis « corrige F0 à F4 maintenant ») sur le code **déjà mergé** sur `main` (PR #102). **F0 (critique)** : `SecurityConfig` définit 4 chaînes de filtres par `@Order` (`/api/m2m/**`, `/api/webhooks/**` anonyme, catch-all BFF authentifié) — `/api/callbacks/**` n'avait jamais été ajouté au `securityMatcher` de la chaîne webhook, tombait dans le catch-all BFF (`anyRequest().authenticated()`, CSRF actif) et recevait un 403 avant même d'atteindre `CallbackWebhookController`. Prouvé empiriquement par un test jetable (`@SpringBootTest @AutoConfigureMockMvc`, pas `standaloneSetup`) : `POST /api/callbacks/{uuid}` anonyme → 403. **La feature livrée était totalement inatteignable par tout système externe réel** — non détecté par les 1533 tests alors verts car aucun ne passait par la vraie chaîne de sécurité (`CallbackWebhookControllerTest` contourne Spring Security via `standaloneSetup`, `CallbackEndToEndIT` contourne HTTP en appelant le use case directement). Corrigé : `securityMatcher("/api/webhooks/**", "/api/callbacks/**")`. **F1 (haute)** : même correctif ferme aussi l'absence de garde `Content-Length` (`WebhookBodySizeLimitFilter` n'était que sur `/api/webhooks/**`) — vecteur d'épuisement mémoire sur un endpoint anonyme exposé sur internet. **F2 (haute)** : un callback échoué (404/409) ne complétait jamais la ligne d'idempotence, restait `IN_FLIGHT` en base — tout rejeu avec la même `Idempotency-Key` (pratique standard de retry) recevait 409 « in progress » pendant 24 h au lieu d'être retraité, annulant la garantie de fiabilité de D-CALLBACK-RACE. Corrigé : `CallbackWebhookController` complète désormais l'idempotence avec le statut déterministe (404/409/400) sur les échecs dont l'issue est stable, laisse `IN_FLIGHT` pour l'imprévu. Même défaut latent partagé avec `WebhookController`, tracé en dette (TODO §53), pas corrigé (hors périmètre approuvé). **F3 (moyenne)** : ambiguïté de corrélation totalement silencieuse côté serveur malgré la promesse du design doc (« loguée en ERROR ») — corrigé via le canal `SECURITY_AUDIT` déjà établi dans le dépôt (`SecurityAuditLog.callbackAmbiguousCorrelation`), sans jamais logger la valeur de corrélation elle-même (donnée métier de l'appelant). **F4 (basse)** : documentation du champ de config recommandant désormais explicitement une valeur de corrélation à haute entropie (UUID), l'authentification étant par instance de connecteur et non par transaction. Tous corrigés en TDD, 6 tests de régression permanents ajoutés (dont `WebhookAndCallbackRoutesSecurityTest`, nouveau, qui couvre pour la première fois `/api/webhooks/**` **et** `/api/callbacks/**` contre la vraie chaîne Spring Security). `./mvnw verify` **1539 tests** (0 échec). Détail complet : `docs/ai/implementation/2026-09-02-feature-connecteur-callback.md` § « Correctifs post-merge ». |

| 2026-09-03 | `WebhookController` : TODO §53 corrigé (défaut latent partagé laissé en dette la veille) | Demande explicite utilisateur (« Corrige aussi le TODO §53 dans WebhookController. Je pensais avoir demander de corriger tous les findings ») — le F2 de la revue adversariale du 2026-09-02 avait été corrigé uniquement sur `CallbackWebhookController`, le même défaut sur `WebhookController` (composant `JpaWebhookIdempotencyStoreAdapter` partagé, pattern identique) avait été volontairement laissé hors périmètre et tracé en dette. Corrigé avec exactement le même pattern déjà écrit et testé : `WebhookController.receive()` capture désormais `RuntimeException` autour de `readPayload`/`onIncomingEvent`, appelle `idempotencyStore.complete(...)` avec le statut déterministe (400 pour `ConnectorValidationException`/`IllegalArgumentException`) sur les échecs dont l'issue est stable sur le même contenu de requête, laisse `IN_FLIGHT` pour l'imprévu (`UnknownConnectorException`/`DossierNotFoundException` compris — non mappés par `ConnectorExceptionHandler` pour ce contrôleur, propagation brute conservée à l'identique du comportement pré-existant). 2 tests de régression ajoutés à `WebhookControllerTest` (`receive_deterministicValidationFailureReturns400_andCompletesClaim`, `receive_unmappedRuntimeException_leavesClaimInFlightForFreshRetry`), mêmes noms/structure que leurs équivalents `CallbackWebhookControllerTest`. Branche redémarrée depuis `origin/main` (PR #102/#103 déjà mergées) avant ce correctif, conformément à la convention du dépôt. `./mvnw verify` **1541 tests** (0 échec) — build reproduit sous JDK 25 fraîchement installé (JDK 21 seul présent par défaut sur ce conteneur, `pom.xml` exige `java.version=25`). PR ouverte puis **mergée sur `main` uniquement après vérification explicite que la CI locale passait** (demande explicite utilisateur, contrairement au « sans attendre la CI » de la PR #102/#103). |

| 2026-09-03 | `connecteur-callback` : 2ème revue de sécurité adversariale, indépendante, 4 nouveaux écarts trouvés et corrigés (TODO §54) | Demande explicite utilisateur (« lance une analyse complète de type adversarial sur le code de la feature des callbacks »), volontairement conduite en ignorant les acquis de la 1ère passe (F0–F4, TODO §51) au-delà d'une simple reconfirmation qu'ils étaient toujours clos dans le code actuel. **F5 (haute)** : Spring matérialise `@RequestBody byte[] rawBody` en mémoire (`ByteArrayHttpMessageConverter`) *avant* l'exécution de la méthode du contrôleur — donc avant la garde `body.length > maxBodyBytes`, le rate limiter et l'authentification. `WebhookBodySizeLimitFilter` (F1, 1ère passe) ne vérifiait que le `Content-Length` déclaré ; son propre javadoc admettait déjà le contournement par `Transfer-Encoding: chunked`, mais le check applicatif présenté comme filet de sécurité était lui-même déjà trop tardif — DoS mémoire exploitable sans authentification ni UUID valide. Corrigé : le filtre enveloppe désormais la requête dans un `ServletInputStream` qui compte les octets réellement lus et rejette dès dépassement, indépendamment de ce que la requête déclare. **F6 (moyenne)** : la signature HMAC ne couvrait que le corps brut, sans horodatage ni nonce, et `Idempotency-Key` (fournie par l'appelant) n'était pas signée — une paire (corps, signature) capturée une fois (log, proxy compromis, outil de debug côté appelant) était rejouable indéfiniment avec une nouvelle clé à chaque fois, contournant totalement l'idempotence. Corrigé (`HmacWebhookAuthenticator`) : signature étendue à `timestamp + "." + corps` (en-tête `X-Webhook-Timestamp` requis, secondes epoch Unix), fenêtre de fraîcheur de 5 minutes, `Clock` injectable pour les tests — **rupture de contrat assumée pour les appelants HMAC déjà en production**, documentée dans les champs de config des 2 connecteurs concernés et dans `analysis doc/06-connecteurs.md`. `SHARED_SECRET_TOKEN` non concerné (compromis déjà documenté comme plus faible, pas une régression de cette revue). **F7 (moyenne)** : aucune longueur minimale imposée sur `webhookSecret` — avec la limite de débit par défaut (120 req/min) et l'absence de toute autre couche de défense (pas d'allowlist IP en V1), un secret court reste brute-forçable en temps réaliste. Corrigé : nouvelle classe `WebhookSecretPolicy` (`domain.connector.model`, seuil 20 caractères), appliquée dans `validate()` des 2 connecteurs à champ `webhookSecret` (`http-rest`, `flowable-callback`). **F8 (moyenne)** : un échec d'authentification webhook n'était jamais journalisé — une tentative de brute force soutenue contre F7 serait restée invisible, aucune alerte possible sur cet endpoint anonyme exposé sur internet. Corrigé : `SecurityAuditLog.webhookAuthenticationFailed(...)` (WARN, `instanceId` uniquement, jamais la signature/le secret soumis), câblé dans `ConnectorExceptionHandler` — même schéma que F3. Faux positifs vérifiés et écartés : DoS par imbrication JSON profonde (garde par défaut Jackson), RCE par désérialisation polymorphe (jamais activée dans le dépôt), IDOR cross-tenant, confusion de type de connecteur, fuite de secrets via l'API admin, injection SQL via `correlationValue`. Tous corrigés en TDD sur demande explicite (« corrige tout maintenant »), 13 tests de régression permanents ajoutés (`WebhookBodySizeLimitFilterTest`, `WebhookAuthenticatorTest` ×6, `FlowableMessageCallbackConnectorTest`, `WebhookControllerTest`/`CallbackWebhookControllerTest` ×2 chacun). `./mvnw verify` **1554 tests** (0 échec). Détail complet : `docs/ai/implementation/2026-09-02-feature-connecteur-callback.md` § « Correctifs post-merge (2ème passe adversariale) ». |
| 2026-09-08 | File d’attente relancée en vagues parallèles (V1.x / ops) | Demande utilisateur : conserver le cadrage « features restantes + concurrence » pour lancer des agents en parallèle. **Vague A** (3 agents, surfaces disjointes) : `provisioning-tenant` (A1, sorti de « hors file ») · `admin-connector-form-schema` (A2) · `mcp-ecritures-metier` (A3, 4 tools dans **un** cycle). A4 headers HTTP optionnel. **Vague B** : Kafka + import batch + ERP en **un** cycle, après A. Interdits documentés (catalogue connecteurs, Compose, `CreateDossierUseCase`). Aucune analyse lifecycle encore écrite sauf backlog MCP dans [15-serveur-mcp.md](analysis%20doc/15-serveur-mcp.md). |
| 2026-09-08 | `mcp-ecritures-metier` (lot A3) Phases 1–9 | 4 tools MCP d’écriture (`create_dossier`, `add_dossier_element`, `start_workflow`, `generate_document`), 16 → 20. Adaptateurs `infrastructure.mcp.tool` uniquement ; `CreateDossierUseCase*` inchangé. [PR #118](https://github.com/superjojo79/FlowForge/pull/118). Playwright N/A (backend-only). TODO §60 reste ouvert jusqu’au merge. |
| 2026-09-08 | `mcp-ecritures-metier` Phases 1–3 **rejouées** | Constat utilisateur : cycle livré sans questions (violation `dev-requirements` / `dev-design`). 9 arbitrages posés un par un, tous confirmés et **alignés sur le code de la PR #118** (aucun écart à corriger) : D-GENERATE-ATTACH=C, D-GENERATE-TEMPLATE=C, D-GENERATE-VARS=C, D-DRY-RUN=C, D-RATE-LIMIT=A, D-NO-OPENAPI=A, D-START-ORCH=C, D-DRY-RUN-START=B, D-FIELDS-READ=B. Cadrage FILE-ATTENTE non relancé (4 tools, ports REST, NOTE/LINK/INFORMATION). |
| 2026-09-08 | `mcp-ecritures-metier` (lot A3) **mergée sur `main`**, TODO §60 clos | [PR #118](https://github.com/superjojo79/FlowForge/pull/118) mergée à 21:17, conditionnée à CI GitHub verte (`build` + `frontend`) sur HEAD `6c1198d`. `./mvnw verify -Dskip.frontend=true` 1775 tests (0 échec). Vague A (A1/A2/A3) **intégralement livrée** le même jour. |
| 2026-09-14 | Statuts A1/A2/A3 vérifiés et resynchronisés dans `FILE-ATTENTE.md` | Demande utilisateur : confirmer que les lots A1, A2 et A3 sont livrés. Vérifié via l'API GitHub (`pull_request_read` méthode `get`) : PR #118 (A3) confirmée `merged: true` (merged_at 2026-09-08T21:17:57Z) — les statuts A1/PR #119 et A2/PR #117 étaient déjà marqués mergés dans ce fichier. Ligne A3 déplacée dans *Récemment livré*, retirée de « En revue », en-tête et section « File d'attente » mis à jour en cohérence. |
| 2026-09-20 | `headers-http-globaux` (lot A4) Phase 4 planning close | 7 milestones / T1.1–T7.2, toutes `not started`. Traçabilité testing→tasks complète (aucun scénario orphelin). **A-E2E-CSP-INJECT** : Playwright Vite `:4173` n’atteint pas Spring — injection CSP document dans E2E-HDR-1/2. Surface exclusive `SecurityConfig` + customizer ; B1 en parallèle (pas Compose, pas CSRF). [PR #128](https://github.com/superjojo79/FlowForge/pull/128). Prochain : Phase 5 TDD à **T1.1**. |
| 2026-09-20 | `headers-http-globaux` (lot A4) Phase 5 TDD T1.1–T7.2 | Customizer câblé sur les 4 chaînes ; MockMvc + actuator ; E2E-HDR-1/2 verts (73/73 Playwright) ; Vitest 351 ; `./mvnw verify` 1898 tests. Kill switch `APP_SECURITY_HEADERS_ENABLED`. Trouvaille hors lot : `EvaluateDossierRetentionUseCaseImplTest` `NOW` figé au 2026-08-19 vs `Instant.now()` — corrigé. [PR #128](https://github.com/superjojo79/FlowForge/pull/128). Prochain : Phase 7. |
| 2026-09-20 | `headers-http-globaux` (lot A4) Phases 7–9 | Phase 7 conforme design. Phase 8 : verify 1898 / Vitest 351 / Playwright 75/75 ; E2E-HDR-3/4 BPMN+DMN sans `unsafe-eval` ; flake BPMN save → `expect.poll`. Phase 9 : aucun blocking. **En revue** [PR #128](https://github.com/superjojo79/FlowForge/pull/128). |
| 2026-09-20 | `headers-http-globaux` (lot A4) remédiation A4-HSTS-001 | HSTS seulement si requête secure **et** pair TCP immédiat loopback/RFC1918 (unwrap `ForwardedHeaderFilter`). `DEPLOIEMENT.md` : bind `127.0.0.1:8081:8080`, unset `X-Forwarded-*` côté proxy. IT `SecurityHeadersHstsApiTest` hors profil `test` via customizer `@Primary`. `forward-headers-strategy: framework` inchangé. [PR #128](https://github.com/superjojo79/FlowForge/pull/128). |
| 2026-09-20 | `headers-http-globaux` (lot A4) rebase docs sur main (B1) | Conflits PR #128 × main après merge PR #129 : TODO A4 renommé **§63** (B1 a clos §62), FILE-ATTENTE / DEFERRED conservent A4 en revue + B1 livré, E2E HDR + batch-import conservés. |
| 2026-09-20 | `headers-http-globaux` (lot A4) **mergée sur `main`** | [PR #128](https://github.com/superjojo79/FlowForge/pull/128) mergée (merge `8415cee`) après CI verte (`build` + `frontend`). TODO §63 clos. File produit vide. |
| 2026-09-21 | `enrichissement-reporting` Phases 1–5 | Quatorze décisions produit, une par une. Trois volets implémentés (alerting, widgets, export BI). PR #132. Surfaces disjointes du lot C. |
| 2026-09-22 | `outillage-dmn` Phase 1 (requirements) | TODO §66. Cinq décisions produit (EVAL-SURFACE, EVAL-VERSION, TEST-FORMAT, ROLLBACK-UX, HISTORY-RETENTION). Docs `2026-09-22-feature-outillage-dmn`. Analyse 28 + ROADMAP 1.1/1.2/1.3 mis à jour « En cadrage ». |
