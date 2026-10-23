# §52 — Formulaires dynamiques rattachés aux tâches BPMN (`userTask`)

> **Statut :** clos · **Section :** [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) · ancien `TODO.md` §52 · [registre](README.md)

**Statut (2026-09-03) : clos.** Cycle `dev-lifecycle` Phases 1-9 complet,
`formulaires-dynamiques-taches`. Décisions tranchées par l'utilisateur au lancement du cycle :
**D-FORM-KIND-SCOPE = mécanisme extensible** (pas seulement 2 kinds figés — `formKey` reste une
chaîne opaque de bout en bout côté backend, interprétée uniquement par un registre
`taskFormRegistry.ts` côté frontend) ; **D-FORM-DOSSIER-FIELDS-PARTIAL = schéma complet** du
`DossierType` (pas de sous-ensemble) ; **D-FORM-DOCGEN-TEMPLATE-SCOPE = filtré par type de
dossier**, nouveau champ `DossierType.documentTemplateKeys` (liste vide = aucun template
disponible, configuration explicite requise — pas de fallback « tous les templates »). Point
technique confirmé (D6, ex-point 6) : `Task.getFormKey()` fonctionne bien sans le module
`flowable-form` sur le moteur Flowable 8.0.0 réellement vendored par ce dépôt, prouvé par un test
d'intégration réel (`FormKeySmokeIT`) avant d'investir dans la plomberie qui en dépend.

**Écart de sécurité réel trouvé et corrigé en Phase 3 (design)** : `GET /api/document-templates`
est `TENANT_ADMIN`-only, inutilisable par un porteur de tâche pour peupler le picker
`document-generation` — corrigé par un nouvel endpoint dédié
`GET /api/dossier-types/{code}/document-templates` (jamais `htmlContent`, même vérification `VIEW`
que `GET /api/dossier-types/{code}`, pas un accès inconditionnel).

**Bug réel latent trouvé et corrigé en Phase 5 (implémentation, tests Playwright réels)** : le tout
premier attribut `flowable:*` posé sur un diagramme qui ne déclare pas encore `xmlns:flowable` —
dont le gabarit BPMN **vierge** de l'éditeur — round-trip correctement en mémoire mais est
silencieusement perdu par `saveXML()` (aucune extension moddle enregistrée pour l'espace de noms
`flowable`, latent depuis `edition-bpmn` TODO §39, jamais détecté faute de test round-trip réel
précédent — les tests Vitest existants mockaient `useService` et ne prouvaient que la logique
get/set, pas la sérialisation XML). Corrigé (`EMPTY_BPMN_XML` déclare désormais l'espace de noms) ;
risque résiduel documenté (`docs/guides/GUIDE-CONCEPTION-BPMN.md` D-FLOWABLE-NS) pour un diagramme importé d'un
outil externe n'ayant jamais utilisé d'attribut `flowable:*`.

`./mvnw verify` (Test-class only — ce dépôt n'a pas de plugin Failsafe, `*IT.java` en est exclu) :
1561 tests, 0 échec, Jacoco LINE/BRANCH tenus. `*IT.java` du périmètre de la feature exécutées
séparément (`FormKeySmokeIT`, `DocumentTemplatePersistenceIT`, `OpenApiContractIT`) : 9 tests,
0 échec. Vitest 269/269 ; Playwright 63/63 (59 existants + 4 nouveaux `E2E-FORM-*`, zéro
régression). `docs/guides/GUIDE-UTILISATEUR.md`/`docs/guides/GUIDE-CONCEPTION-BPMN.md`/`CHANGELOG.md` mis à jour.
Écart de revue trouvé et corrigé en Phase 9 : `openapi/openapi.yaml` (fichier de référence testé
par contrat) n'avait pas été régénéré après l'ajout du nouvel endpoint et des nouveaux champs —
régénéré et vérifié vert. Détail complet :
[`docs/ai/implementation/2026-09-03-feature-formulaires-dynamiques-taches.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/implementation/2026-09-03-feature-formulaires-dynamiques-taches.md).

**Revue adversariale approfondie (subagent dédié, post-Phase 9)** : 6 écarts réels trouvés et
corrigés — le kind `document-generation` était non fonctionnel de bout en bout (mismatch de noms
de variables entre le formulaire et le `serviceTask ${documentGenerationTask}`, jamais détecté car
chaque côté était testé isolément avec le même nom erroné) ; `DossierFieldsTaskForm` pouvait
effacer silencieusement un champ `sensitive` jamais lu par l'utilisateur et rendait la tâche
incomplétable dès qu'un champ `NUMBER` était présent (soumission de l'état complet au lieu d'un
diff) ; `documentTemplateKeys` n'était appliqué qu'à la découverte, jamais à la complétion de tâche
(n'importe quel `documentType` passait) ; le registre de formulaires de tâche était vulnérable aux
clés héritées d'`Object.prototype`. Chacun corrigé avec un test de non-régression dédié
(unitaire + IT/E2E réel selon le cas — dont un nouveau fixture BPMN + IT Flowable réel pour le
premier). Détail complet et écarts mineurs restants (non corrigés, hors périmètre) :
[`docs/ai/implementation/2026-09-03-feature-formulaires-dynamiques-taches.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/implementation/2026-09-03-feature-formulaires-dynamiques-taches.md) §M8. Vérification
finale après correctifs : 1566 tests `Test*` (0 échec), 13 tests `*IT.java` (0 échec), Vitest
278/278, Playwright 63/63.

<details>
<summary>Analyse d'origine (avant cycle dev-lifecycle)</summary>


**Constat :** le tuyau backend de complétion de tâche accepte déjà des variables
(`POST /api/workflow-tasks/{taskId}/complete` → `{ variables: {...} }`, `CompleteTaskUseCaseImpl`),
mais rien ne s'en sert : `MobileTasksView.vue` complète les tâches à l'aveugle, sans formulaire.
Aucun concept de `formKey` n'existe sur `MyWorkflowTask`/`WorkflowTaskView`, ni dans l'éditeur BPMN
visuel (`flowablePropertiesProvider.ts`). Origine : question utilisateur (2026-09-01), vérifiée
contre le code réel avant de répondre. Les 2 cas cités (compléter les champs du dossier, déclencher
une génération de documents) ne sont possibles aujourd'hui qu'en dehors du BPMN (fiche dossier pour
l'édition de champs ; génération entièrement automatique via le `serviceTask`
`${documentGenerationTask}`, jamais déclenchée par une saisie utilisateur en cours de process).

**Mécanisme proposé (pas encore tranché) :** réutiliser `flowable:formKey`, attribut BPMN standard
lu via `Task.getFormKey()` sur le moteur **cœur** (`flowable-engine`) — pas le module optionnel
`flowable-form`, absent du classpath (`pom.xml` vérifié). Deux briques déjà réutilisables :
`DynamicFields.vue` (composant générique `FieldDefinition[]` + `v-model`, déjà utilisé pour
l'édition des champs dossier) et `PATCH /api/dossiers/{id}/fields` (permission + concurrence
optimiste déjà gérées). Convention de 2 « kinds » fixes proposée, pas un schéma générique ouvert :
`dossier-fields` (rend `DynamicFields.vue` contre le schéma `DossierType`, soumission via
`PATCH .../fields` existant) et `document-generation` (nouveau petit composant de sélection
template/variante/locale, soumission = variables de complétion de tâche lues par le
`serviceTask` `${documentGenerationTask}` suivant — la génération reste où elle est, le formulaire
ne fait que fournir l'entrée).

**Points ouverts (à trancher en phase requirements/design, pas silencieusement) :**

1. **D-FORM-KIND-SCOPE** — s'en tenir à ces 2 kinds fixes vs. prévoir un mécanisme extensible.
2. **D-FORM-DOSSIER-FIELDS-PARTIAL** — schéma complet du `DossierType` à chaque tâche, ou
   sous-ensemble de champs pertinent à l'étape (non supporté par `DynamicFields.vue` aujourd'hui).
3. **D-FORM-DOCGEN-TEMPLATE-SCOPE** — tous les templates actifs du tenant, ou filtrés par type de
   dossier (aucun lien `DossierType`↔`templateKey` dans le modèle actuel).
4. **D-FORM-VALIDATION** — client seul (le serveur revalide déjà via `DossierFieldValidator` sur
   `PATCH .../fields`, pas de nouvelle garde nécessaire pour `dossier-fields`).
5. **D-FORM-BACKWARD-COMPAT** — tâche sans `formKey` = comportement actuel inchangé (à documenter
   explicitement, pas un vrai point ouvert).
6. **Vérification à faire en design, pas supposée** : confirmer `Task.getFormKey()` accessible sans
   le module `flowable-form` sur la version Flowable 8.0.x réellement utilisée par ce dépôt.

**À produire :** décisions ci-dessus, puis `formKey` sur `MyWorkflowTask`/`WorkflowTaskView` +
lecture dans `FlowableWorkflowTaskInboxAdapter`, champ `flowable:formKey` dans l'éditeur BPMN
(`flowablePropertiesProvider.ts`), rendu des 2 kinds côté frontend tâches.

**Impacte :** `MyWorkflowTask`, `WorkflowTaskView`, `WorkflowTaskController`,
`FlowableWorkflowTaskInboxAdapter`, `flowablePropertiesProvider.ts`, `MobileTasksView.vue`/
`TaskList.vue`, nouveau composant sélection template/variante/locale.

**Détail :** [22-formulaires-dynamiques-taches.md](../specs/22-formulaires-dynamiques-taches.md).

</details>
