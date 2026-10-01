---
phase: implementation
title: Implementation — LC5 concepteur de parcours
status: done
created: 2026-10-01
feature: parcours-concepteur
lot: LC5
todo_ref: TODO.md §88
---

# Implementation — LC5

- **Domaine** `domain/parcours/**` : `ParcoursDefinition` (valide ids, valeurs de branche, mots réservés EL, variables du moteur, caractères XML 1.0), `ParcoursCompiler` (fonction pure : graphe, atteignabilité, sortie, BPMN `flow_<de>_<vers>`, `default=` de passerelle, BPMNDI, SHA-256, avertissements), cas d'usage `ManageParcoursUseCaseImpl`, `CompileParcoursUseCaseImpl` (compile → `ProposeDraftUseCase`, aperçu → parseur de déploiement sans écriture).
- **Persistance** migration `034-parcours.yaml`, `ParcoursJpaEntity`, verrou optimiste (`updateIfVersion`, `deleteIfVersion`).
- **Web** `ParcoursController` (`/api/parcours`), `ParcoursExceptionHandler` (400/404/409, message de l'étape remonté même depuis la désérialisation).
- **MCP** `ParcoursTools` ; `McpToolModes` (mode conception) ; provenance `PARCOURS` (brouillon automatisé, jamais d'écrasement d'un brouillon humain).
- **Front** `api/parcours.ts`, `views/admin/ParcoursView.vue` (éditeur par étapes, aperçu, avertissements, boutons bloqués si modifications non enregistrées), route et lien de navigation.
- **Revue adversariale** 2 passes : 1 Haut + 4 Moyens (passe 1), 2 Moyens (passe 2) — tous corrigés avec tests permanents.
