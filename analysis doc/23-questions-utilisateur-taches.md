# Questions/choix posés à l'utilisateur, indépendants du dossier (`userTask`)

> **Statut : première analyse fonctionnelle, aucune décision technique tranchée.** À affiner en
> phase `dev-lifecycle` (requirements → design) avant tout code — voir « Points ouverts » en fin de
> document. Origine : question utilisateur (2026-09-05) — les mécanismes de formulaires de tâche
> permettent-ils de poser un choix à l'utilisateur indépendamment des données du dossier ? Réponse
> vérifiée contre le code réel : le mécanisme `formKey` (TODO §52, livré) le permet déjà en
> principe (kind `document-generation` produit des variables de process pures, rien n'est écrit
> dans le dossier), mais aucun kind générique « question/choix arbitraire » n'existe — chaque kind
> aujourd'hui est un composant Vue métier écrit à la main pour un besoin précis. Analyse demandée
> pour cadrer un kind générique réutilisable. Aucune feature ouverte à ce stade — cette analyse ne
> préjuge pas de la priorité dans la file d'attente.

---

## 1. Constat — le mécanisme existe et est extensible par construction, mais aucun kind générique

Vérifié contre le code réel (TODO §52, livré le 2026-09-03) :

| Élément | Fichier | État |
|---|---|---|
| `flowable:formKey` | `MyWorkflowTask`, `WorkflowTaskView`, éditeur BPMN | Chaîne opaque de bout en bout côté backend — le moteur ne fait que la stocker/restituer |
| Registre de kinds | `taskFormRegistry.ts` | `Record<string, Component>` — **conçu extensible** (décision D-FORM-KIND-SCOPE, TODO §52) : ajouter un kind = une entrée + un composant, zéro changement backend |
| Kind `document-generation` | `DocumentGenerationTaskForm.vue` | Preuve que le mécanisme supporte déjà un choix **indépendant du dossier** : un `<select>` dont la réponse part uniquement comme variables de process (`documentType`/`documentVariant`/`documentLocale`), jamais écrites dans le dossier |
| Kind `dossier-fields` | `DossierFieldsTaskForm.vue` | À l'inverse, celui-là **est** lié au dossier (`PATCH /api/dossiers/{id}/fields`) |
| Validation serveur par kind | `CompleteTaskUseCaseImpl.assertDocumentTypeAllowed` | Chaque kind revalide côté serveur ce qu'il autorise à la complétion (ne fait pas confiance au frontend) — pattern à reproduire, pas de garde générique aujourd'hui |
| Garde-fou noms de variables | `WorkflowVariables.assertNoReservedKeys` | Empêche une collision avec les 4 variables plateforme (`dossierId`, `dossierWorkflowLinkId`, `tenantId`, `dossierFields`) — ne protège pas contre une collision entre deux variables **métier** |

Donc : oui, poser un choix indépendant du dossier est déjà possible aujourd'hui, mais seulement en
écrivant un nouveau composant Vue dédié à chaque besoin (comme `document-generation`). Ce qui
manque est un kind **générique** : un concepteur de workflow doit pouvoir définir lui-même une
petite question ou un mini-formulaire (libellé, champs, options) sans qu'un développeur écrive un
composant à chaque fois.

---

## 2. Décisions de cadrage déjà actées (2026-09-05, via `AskUserQuestion`)

1. **Cas d'usage** : générique — ne présume pas de ce qui est fait de la réponse en aval (peut
   aussi bien orienter une passerelle exclusive que rester une information annexe).
2. **Périmètre** : mini-formulaire multi-champs (texte/nombre/date/choix), pas seulement un choix
   unique — réutilise la forme déjà connue (`FieldDefinition`/`FieldType`, voir §3) plutôt qu'un
   nouveau concept.
3. **Origine des options** : statique — définie à l'avance par un concepteur, pas résolue
   dynamiquement via un endpoint métier au moment de l'exécution.
4. **Traçabilité** : dédiée — la réponse ne doit pas rester une simple variable de process
   éphémère, elle doit être historisée pour audit/reporting.
5. **Stockage du schéma** : dans une nouvelle entité **admin**, pas embarqué dans le XML BPMN — le
   BPMN garde une référence stable (via `formKey`), pas le schéma lui-même. Confirmé après
   vérification qu'aucun mécanisme d'extension XML structurée (`extensionElements`/moddle
   personnalisé) n'existe dans ce dépôt aujourd'hui — l'éditeur BPMN visuel ne pose que des
   attributs `flowable:*` plats (texte simple), jamais de structure imbriquée.

---

## 3. Mécanisme proposé

### Nouvelle entité admin — schéma de questions réutilisable

Un nouveau concept domaine, à nommer (voir « Points ouverts »), porteur d'une liste de champs —
même forme que `DossierType.fieldDefinitions` : `FieldDefinition(name, label, type, required,
defaultValue, options, group, sensitive)` avec `FieldType { STRING, NUMBER, DATE, BOOLEAN, SELECT,
FILE }` déjà existant (`domain.dossier.model`). Identifié par une clé stable (`schemaKey`),
configuré dans un nouvel écran d'admin (CRUD), complètement indépendant d'un `DossierType`.

### Nouveau kind `formKey`

`formKey = "user-question:<schemaKey>"` (nom de kind à confirmer) — même convention que
`document-generation`/`dossier-fields`. Un seul nouveau composant Vue générique, qui :
1. Résout `schemaKey` via un nouvel endpoint runtime (`GET /api/task-question-schemas/{key}`),
2. Rend les champs — réutilise autant que possible le moteur de rendu déjà écrit pour
   `DynamicFields.vue`, mais sans le binding dossier (pas de `dossierId`/`PATCH .../fields`),
3. À la soumission, `emit('ready', variables)` avec une variable de process par champ (nom =
   `FieldDefinition.name`), lues ensuite librement en aval (passerelle exclusive, `serviceTask`,
   template de document, etc.) — même contrat que les kinds existants.

### Traçabilité dédiée

Une nouvelle table (réponses horodatées : `dossierId`, `taskId`, `processInstanceId`, `schemaKey`,
réponses en JSON, auteur, date) écrite dans la **même transaction** que la complétion de tâche —
même emplacement que `CompleteTaskUseCaseImpl.assertDocumentTypeAllowed` aujourd'hui pour la
revalidation serveur du kind `document-generation` : ce kind devra, de la même façon, revalider
côté serveur que les valeurs soumises respectent le schéma (champs requis, valeur dans `options`
pour un `SELECT`) avant de persister et de compléter la tâche — pas seulement côté client.

---

## 4. Composants à ajouter (esquisse, à affiner en design)

### Backend

- Nouveau modèle domaine (schéma de questions) + port in/out + adaptateur JPA + migration
  Liquibase (nouvelle table admin) — package à déterminer (voir « Points ouverts »).
- Nouvelle table de traçabilité des réponses + port out + adaptateur JPA + migration Liquibable.
- Nouveau use case de revalidation serveur (pattern `assertDocumentTypeAllowed`), câblé dans
  `CompleteTaskUseCaseImpl` ou équivalent pour ce kind.
- Nouveaux endpoints : CRUD admin du schéma (RBAC à trancher, probablement
  `WORKFLOW_ADMIN`) + lecture runtime par le porteur de tâche.

### Frontend

- Nouvel écran admin (liste + éditeur de schéma), même famille que `DossierTypesAdminView.vue`/
  `FieldDefinitionDefsEditor.vue` (schéma déjà éprouvé pour `DossierType`).
- Nouveau composant `UserQuestionTaskForm.vue` (nom à confirmer) + entrée dans
  `taskFormRegistry.ts`.
- Nouveau champ dans `flowablePropertiesProvider.ts` si le concepteur BPMN doit choisir le
  `schemaKey` depuis une liste plutôt que de taper `formKey` à la main (ergonomie, pas structurant).

---

## 5. Points ouverts (à trancher en phase requirements/design)

1. **D-USERQ-FIELD-REUSE** — réutiliser tel quel `FieldDefinition`/`FieldType`
   (`domain.dossier.model`, recommandé — évite la duplication) vs. dupliquer un type propre au
   bounded context workflow pour ne pas faire dépendre un concept « question de tâche » du package
   `dossier` (couplage entre bounded contexts à trancher explicitement, cohérent avec la règle
   d'architecture hexagonale du dépôt).
2. **D-USERQ-VAR-COLLISION** — les noms de variables de process émis par ce kind viennent d'un
   schéma **configuré en admin**, donc potentiellement arbitraires : `WorkflowVariables
   .assertNoReservedKeys` protège contre une collision avec les 4 variables plateforme, mais rien
   ne protège contre une collision entre deux variables **métier** (ex. un champ `documentType`
   nommé par erreur, qui écraserait la variable lue par `${documentGenerationTask}`). Faut-il un
   garde-fou (préfixe imposé, validation à la sauvegarde du schéma) ?
3. **D-USERQ-TRACE-SCOPE** — quelles données exactement dans la table de traçabilité (réponses
   brutes seulement, ou aussi un lien vers un futur module reporting/SLA, cf.
   [16-reporting-sla.md](16-reporting-sla.md), qui calcule aujourd'hui tout « à la volée » sans
   table d'événements dédiée) ? Faut-il l'assujettir à une politique de rétention/purge, sur le
   modèle déjà appliqué au `Dossier` (TODO §21/§22) ?
4. **D-USERQ-SCHEMA-VERSIONING** — que se passe-t-il si un schéma admin est modifié (ou son
   `schemaKey` supprimé) pendant qu'un process en cours référence encore l'ancien `formKey` ? Faut-il
   un versionnage explicite, ou un comportement dégradé documenté (comme `D-FORM-BACKWARD-COMPAT`
   pour un `formKey` inconnu, TODO §52) ?
5. **D-USERQ-RBAC-ADMIN** — quel rôle gère l'écran admin de ces schémas (`WORKFLOW_ADMIN`
   recommandé, cohérent avec l'édition BPMN) ?
6. **D-USERQ-I18N** — les libellés (question, options) doivent-ils être traduits (le kind
   `document-generation` porte déjà une `locale` mais pour le document généré, pas pour le
   libellé du formulaire lui-même) ?
7. **Nom du kind et de l'entité** — `user-question` / `TaskFormSchema` sont des noms provisoires
   posés pour cette analyse, à confirmer ou remplacer en phase requirements.
