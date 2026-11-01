# Promotion de configuration, tranche 2 — document de travail

Issue : #279 · Spec du domaine : docs/specs/35-promotion-configuration.md · Points du registre : §83 (tranche 1), §117

## Cadrage

- **Problème et utilisateurs :**
  - L'intégrateur ou l'administrateur du tenant promeut la recette vers la production avec *Admin → Promotion*.
  - Plusieurs réglages ne voyagent pas : workflow « réponse » et échéance d'un type, groupes, templates de documents,
    définitions de parcours.
  - La cible se dégrade en silence (courrier en file de tri, tâche libre, pas d'escalade, génération de document en
    échec), et le plan d'import ne le signale pas.
- **Périmètre :** cinq nouveaux types d'artefacts :
  - groupe d'utilisateurs, sans ses membres ;
  - template de document : la version active de chaque combinaison clé, variante et langue ;
  - parcours : la définition seule ;
  - workflow « réponse » d'un type ;
  - échéance d'un type.

  S'y ajoute le contrôle des références orphelines dans le plan.
- **Hors périmètre :**
  - ACL par type de dossier : utilisateurs propres à chaque environnement ;
  - réglages du tenant (rétention par défaut, alertes SLA, webhooks) : URLs d'environnement ;
  - membres des groupes ;
  - historique des versions de templates ;
  - signature du bundle ;
  - suppression sur la cible d'un artefact absent du bundle.
- **Critères de succès (vérifiables) :**
  1. Un bundle exporté contient les cinq nouveaux types d'artefacts, sans membre de groupe ni secret.
  2. Importer ce bundle sur un tenant vierge, puis publier et activer ce qui doit l'être, reproduit le comportement de
     la source. Scénario de référence : la mail room de l'exemple. Une réponse démarre le workflow « réponse », le rappel
     et l'escalade ont lieu, le courrier est routé vers le groupe et le document est généré.
  3. Un template importé devient une version **DRAFT**, à activer dans l'écran Templates. Il n'est jamais activé par
     l'import.
  4. Un parcours importé retrouve sa définition modifiable dans le concepteur, sans être recompilé. Le plan avertit si
     la définition source a changé depuis sa dernière compilation.
  5. `idpGroup` accepte `{{paramètre}}`, et un paramètre absent bloque le groupe. En mise à jour, l'`idpGroup` de la
     cible n'est jamais remplacé.
  6. Une référence orpheline, c'est-à-dire absente de la cible et du bundle, est traitée selon son effet :
     - **Bloqué** quand l'écriture serait refusée : groupe d'escalade, type enfant ou workflow d'un workflow « réponse » ;
     - **avertissement** dans la raison quand l'exécution se dégrade : groupe en sortie DMN, `groupeTri`,
       `candidateGroups` littéraux, clé de template d'un type.
  7. Par le MCP, seuls les brouillons BPMN et DMN et les **brouillons de templates** s'appliquent. Les autres nouveaux
     artefacts sont « ignorés » dans le rapport MCP.
  8. Un bundle de format 1 se lit toujours ; rejouer l'import converge (tout *Inchangé*).
- **Contraintes :**
  - **Stack figée, aucune dépendance nouvelle.**
  - Écritures par les cas d'usage existants seulement (D-PROMO-APPLY) ; un schéma par tenant.
  - Export et import réservés à `TENANT_ADMIN` ou `API_SERVICE`, comme aujourd'hui.
  - Documentation à mettre à jour : spec 35, spec 49 (limites), spec 04, GUIDE-UTILISATEUR § Promotion,
    `docs/decisions/identifiants.md`.
  - Frontend : libellés des nouveaux types dans l'écran Promotion.
- **Validé par l'utilisateur le :** 2026-11-01.

## Questions ouvertes

| Question | Options | Recommandation | Statut |
|---|---|---|---|
| Templates : quelle version exporter, que fait l'import ? | actif → brouillon / actif → activé / toutes les versions | actif → brouillon | tranchée (utilisateur) → §117 |
| Parcours : que faire de la définition ? | définition seule / définition + compilation / rien | définition seule | tranchée (utilisateur) → §117 |
| Groupes : `idpGroup` ? | paramétrable, non remplacé en mise à jour / jamais / tel quel | paramétrable | tranchée (utilisateur) → §117 |
| Références orphelines ? | selon l'effet / toujours bloqué / toujours avertir | selon l'effet | tranchée (utilisateur) → §117 |
| Canal MCP ? | + brouillons de templates / statu quo / tout sauf groupes | + brouillons de templates | tranchée (utilisateur) → §117 |
| Workflow « réponse » ou échéance absent du bundle mais présent sur la cible ? | effacer / laisser | laisser : l'import ne supprime jamais | tranchée → §117 |
| ACL et réglages du tenant ? | inclure / exclure | exclure (D-PROMO-SCOPE inchangé) | tranchée → §117 |
| Réglages de type : dans le document du type ou artefacts à part ? | dans le type / à part | à part, appliqués après les configurations de workflow et les groupes, dont ils dépendent ; le document du type et sa comparaison restent inchangés | tranchée → §117 |
| Version du format ? | rester en 1 / passer en 2 | passer en 2 : une instance ancienne refuse en clair (« formatVersion 2 ») ; la nouvelle lit 1 et 2 | tranchée → §117 |

## Conception

Décisions : §117 (D-PROMO-FORMAT2, -GROUP, -TEMPLATE, -PARCOURS, -TYPE-SETTINGS, -ORPHAN, -MCP-TEMPLATE).

- Composants :
  - domaine `promotion` : `ArtifactType` (nouvelles constantes en fin, rang d'application explicite), `ConfigDocs`,
    export et import ; dépendances ajoutées : `UserGroupsUseCase`, `ManageReplyWorkflowUseCase`,
    `ManageDeadlineUseCase`, templates (dépôt et cas d'usage), parcours ;
  - `ZipConfigurationBundleCodec` (version contrôlée avant les types) ; `ImportConfigurationTool` (MCP) ;
  - frontend : `api/configuration.ts` et libellés i18n de l'écran Promotion.
- Scénarios de test :
  - unitaires : `ConfigurationPromotionUseCaseTest`, un par décision ;
  - chaîne HTTP : `ConfigurationPromotionApiTest` ;
  - aller-retour de la mail room de l'exemple (export, retrait des réglages, import, état reconstitué) ;
  - Playwright : plan affichant les nouveaux types.

Faits vérifiés :

- `ArtifactType` : l'ordre de déclaration est l'ordre d'application. `ZipConfigurationBundleCodec` refuse un type
  inconnu du manifeste, et `ConfigurationBundle` refuse tout format autre que 1.
- `BlockReferenceVerifier` : un template absent bloque un BPMN. En mode `DRAFT`, un template présent mais non actif est
  accepté, et sa publication refusée tant qu'il n'est pas activé. Un template importé en DRAFT s'insère donc dans ce
  chemin.
- `uq_template_active` : une seule version ACTIVE par (tenant, clé, variante, langue). Cas d'usage disponibles :
  `CreateDocumentTemplateUseCase`, `NewDocumentTemplateVersionUseCase` et `UpdateDocumentTemplateUseCase`.
- `ManageParcoursUseCase.save` : versionné (`expectedVersion`) ; `Parcours.lastCompiledSha256` permet de détecter une
  définition non recompilée.
- `UserGroupsUseCase.create` et `update` prennent (code, libellé, `idpGroup`).
- `ManageReplyWorkflowUseCase.set` contrôle le type, la configuration de workflow et le type enfant.
- `ManageDeadlineUseCase.set` contrôle le champ DATE et le groupe d'escalade.

## Lots

| Lot | Contenu | Statut | PR |
|---|---|---|---|
| 1 | format 2, rang d'application, artefact groupe (`idpGroup` paramétrable), avertissements de groupes orphelins (DMN `routageService`, `groupeTri`, `candidateGroups`), libellés frontend | livré | lot 1 |
| 2 | artefacts workflow « réponse » et échéance, blocages de dépendances | livré | lot 2 |
| 3 | artefact template (brouillon), clés de template orphelines, raison BPMN, MCP | en cours | |
| 4 | artefact parcours (définition seule, repère de synchronisation) | à faire | |
| 5 | aller-retour de la mail room, Playwright, documentation, revue de clôture | à faire | |

Prochaine action : lot 3.

## Preuves

| Date | Commande | Résultat |
|---|---|---|
| 2026-11-01 | lot 2 — `./mvnw verify -Dskip.frontend=true` | vert (4184 tests, 0 échec) |
| 2026-11-01 | lot 2 — `cd frontend && npm run lint && npm run test:all` | vert (849 Vitest, 158 Playwright) |
| 2026-11-01 | lot 1 — `./mvnw verify -Dskip.frontend=true` | vert (4176 tests, 0 échec) |
| 2026-11-01 | lot 1 — `scripts/check-docs.sh` | vert |
| 2026-11-01 | lot 1 — `cd frontend && npm run lint && npm run test:all` | vert (849 Vitest, 158 Playwright) |

## Revue de clôture

| Constat | Sévérité | Correctif ou issue |
|---|---|---|
