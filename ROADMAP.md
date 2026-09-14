# FlowForge — Roadmap Fonctionnelle

> **Document de référence** — Dernière mise à jour : 12 septembre 2026  
> **Statut** : En cours de priorisation  
> **Contact** : @superjojo79

---

## Contexte

FlowForge est une plateforme de gestion de flux métier et de dossiers, positionnée comme un SaaS multi-tenant à architecture hexagonale. Le projet intègre :

- **Moteur BPMN Flowable** pour l'orchestration des workflows
- **Moteur DMN** pour les règles métier déclaratives
- **Serveur MCP** (Model Context Protocol) avec 25+ tools pour l'orchestration par agents IA
- **Connecteurs extensibles** (HTTP, SMTP, Scheduler, Message, Signal, Document Storage)
- **Gestion de documents versionnés** avec templates Thymeleaf
- **Schémas dynamiques** pour les dossiers (champs JSONB configurables par type)

Cette roadmap recense les fonctionnalités manquantes identifiées lors de l'analyse architecturale de septembre 2026, classées par priorité et par catégorie.

---

## Catégorie 1 : MCP & IA (Différenciation stratégique)

### 1.1 EvaluateDecisionTool
**Statut** : ⚠️ Gap critique — DMN est déployable mais non évaluable par les agents  
**Priorité** : Critique  
**Effort estimé** : 2-3 jours

**Principe de fonctionnement :**
- Tool MCP qui évalue une table de décision DMN déployée
- Inputs : `decisionKey` (clé de la décision), `inputs` (Map<String, Object> avec les données d'entrée), `businessKey` (optionnel, pour corrélation)
- Output : `decisionResult` (Map<String, Object> avec les sorties), `hitPolicy` (UNIQUE, FIRST, COLLECT, etc.), `hitRules` (liste des règles déclenchées avec scores si COLLECT), `executionTimeMs`
- Utilise le moteur DMN natif Flowable en backend

**Valeur ajoutée :**
- Débloque l'orchestration IA autonome : un agent peut interroger des règles métier pour prendre des décisions (routage KYC, calcul d'indemnisation, éligibilité subvention)
- Sans ce tool, DMN est un artefact déployable mais inutilisable par les agents — c'est le gap critique actuel
- Permet des scénarios du type : "Calcule le montant d'indemnisation pour ce sinistre" → l'agent appelle le tool, récupère le résultat, et agit en conséquence

---

### 1.2 TestDecisionTool
**Statut** : ❌ Non implémenté  
**Priorité** : Haute  
**Effort estimé** : 3-4 jours

**Principe de fonctionnement :**
- Tool MCP qui exécute un jeu de tests sur une décision DMN
- Inputs : `decisionKey`, `testDataset` (CSV ou JSON avec colonnes : inputs + outputs attendus)
- Output : `successRate` (%), `failedCases` (liste détaillée avec inputs, output attendu, output réel, écart), `executionTimeMs`
- Peut être invoqué en pré-déploiement pour valider une décision avant mise en production

**Valeur ajoutée :**
- Réduit le risque de régression lors des modifications de règles métier
- Permet aux métier de modifier des tables DMN en toute confiance avec validation automatique
- Indispensable pour la conformité (audit des règles avant déploiement)

---

### 1.3 GetDecisionHistoryTool + RollbackDecisionTool
**Statut** : ❌ Non implémenté  
**Priorité** : Haute  
**Effort estimé** : 4-5 jours

**Principe de fonctionnement :**
- `GetDecisionHistoryTool` : liste les versions déployées d'une décision (version, date de déploiement, déployé par, changelog)
- `RollbackDecisionTool` : revient à une version antérieure spécifiée (decisionKey + targetVersion)
- S'appuie sur un historique persisté des déploiements DMN dans une table `decision_deployment_history`

**Valeur ajoutée :**
- Sécurité opérationnelle : possibilité de revenir en arrière rapidement après un déploiement problématique
- Audit trail : traçabilité complète des évolutions de règles (qui a changé quoi, quand, et pourquoi)
- Critique pour les secteurs régulés (assurance, banque, secteur public)

---

### 1.4 CompositeScenarioTool (ou tools composites)
**Statut** : ❌ Non implémenté  
**Priorité** : Moyenne  
**Effort estimé** : 3-4 jours

**Principe de fonctionnement :**
- Tools MCP qui enchaînent plusieurs opérations atomiques en une seule invocation
- Exemples :
  - `CreateDossierAndStartWorkflowTool` : crée un dossier + démarre le workflow associé en un appel
  - `EvaluateDecisionAndRouteDossierTool` : évalue une décision DMN + réassigne le dossier selon le résultat
- Inputs/outputs composites qui encapsulent la logique d'orchestration

**Valeur ajoutée :**
- Réduit la latence et les erreurs d'orchestration par les agents IA (moins d'appels à enchaîner)
- Simplifie les prompts des agents (une intention = un tool, pas 4-6 calls à coordonner)
- Meilleure observabilité (un tool = une unité de travail traçable)

---

## Catégorie 2 : Connecteurs & Intégrations (Couverture des cas enterprise)

### 2.1 BatchImportConnector (SFTP / CSV / Excel)
**Statut** : ❌ Non implémenté  
**Priorité** : Critique  
**Effort estimé** : 5-7 jours

**Principe de fonctionnement :**
- Connecteur de type TRIGGER avec planification cron (ex. "toutes les heures")
- Se connecte à un SFTP/FTP/SMB, télécharge un fichier CSV/Excel
- Parse le fichier, mappe les colonnes vers les champs dynamiques d'un DossierType
- Crée un dossier par ligne (ou un dossier parent avec éléments enfants)
- Gestion d'erreurs par ligne : ligne invalide → dossier en erreur avec motif, les autres lignes continuent

**Valeur ajoutée :**
- Débloque l'ingestion massive depuis des ERP, exports bancaires, fichiers Excel métier
- Cas d'usage #1 en secteur public et assurance (imports de listes, migrations de données, synchronisations nightly)
- Sans ce connecteur, FlowForge ne peut pas s'intégrer aux systèmes legacy par flux fichiers

---

### 2.2 KafkaConnector (événementiel)
**Statut** : ❌ Non implémenté  
**Priorité** : Haute  
**Effort estimé** : 5-7 jours

**Principe de fonctionnement :**
- Connecteur TRIGGER qui s'abonne à un topic Kafka
- Chaque message Kafka déclenche la création d'un dossier ou l'avancement d'une tâche
- Mapping JSON Kafka → champs dynamiques du dossier
- Support des consumer groups pour la scalabilité

**Valeur ajoutée :**
- Intégration avec les architectures événementielles modernes (microservices, event sourcing)
- Cas d'usage : notification de sinistre depuis un système core assurance, commande client depuis un ERP
- Alternative au polling HTTP ou batch pour les flux temps réel

---

### 2.3 AttachmentScanConnector (ClamAV ou équivalent)
**Statut** : ⚠️ Port présent mais adaptateur no-op  
**Priorité** : Haute  
**Effort estimé** : 2-3 jours

**Principe de fonctionnement :**
- Connecteur de type CALLBACK invoqué automatiquement avant tout upload de pièce jointe
- Envoie le fichier à un serveur ClamAV (via socket ou API REST)
- Si virus détecté → rejette l'upload avec motif, notifie l'utilisateur
- Si clean → laisse passer l'upload

**Valeur ajoutée :**
- Conformité sécurité : obligatoire dans beaucoup de secteurs (banque, assurance, santé, public)
- Protège contre les malware uploadés par des tiers externes
- Actuellement, le port `AttachmentScanPort` existe mais l'adaptateur est no-op — c'est un risque en production

---

### 2.4 ElectronicSignatureConnector (DocuSign / eIDAS / Yousign)
**Statut** : ❌ Non implémenté  
**Priorité** : Haute  
**Effort estimé** : 5-7 jours

**Principe de fonctionnement :**
- Connecteur de type SERVICE avec 3 opérations :
  1. `prepareSignature` : envoie un document à signer à un service de signature électronique
  2. `checkStatus` : vérifie si le document a été signé
  3. `retrieveSignedDocument` : récupère le document signé une fois complété
- Intégration avec DocuSign, Yousign, Universign, ou un HSM eIDAS local

**Valeur ajoutée :**
- Légal : rend les documents produits par FlowForge opposables juridiquement
- Cas d'usage : contrats d'assurance, conventions de subvention, procès-verbaux, décisions formalisées
- Bloquant pour beaucoup de secteurs sans cette fonctionnalité

---

## Catégorie 3 : Portail Externe & Expérience Utilisateur

### 3.1 Portail Externe Tokenisé (Guest Portal)
**Statut** : ❌ Non implémenté  
**Priorité** : Critique  
**Effort estimé** : 7-10 jours

**Principe de fonctionnement :**
- Endpoint public `/portal/{token}` accessible sans authentification OIDC
- Le token est généré lors de la création d'un dossier (ou envoyé par email/SMS)
- Le portail permet :
  - Upload de pièces jointes (limité au token)
  - Réponse à un questionnaire dynamique (basé sur le TaskQuestionSchema du dossier)
  - Suivi de l'avancement (statut BPMN, timeline)
- Le token a une durée de vie limitée et un nombre d'usages maximum

**Valeur ajoutée :**
- Débloque tous les cas "guichet numérique" : dépôt de demande par un citoyen, upload de pièces par un assuré, réponse à une enquête par un fournisseur
- Sans ce portail, toute interaction avec FlowForge exige un compte OIDC — bloquant pour les tiers externes
- Conforme RGPD : le token est un lien sécurisé, pas une authentification permanente

---

### 3.2 Notification Inbox (Centre de notifications persisté)
**Statut** : ❌ Non implémenté  
**Priorité** : Moyenne  
**Effort estimé** : 3-4 jours

**Principe de fonctionnement :**
- Table `notification` persistée avec : userId, type, title, message, status (UNREAD/READ), createdAt, readAt
- Endpoint REST `/notifications` pour lister/marquer comme lu
- Intégration avec les SSE existants : chaque notification SSE est persistée en base
- UI : cloche de notifications dans le header avec badge non-lus, dropdown avec historique

**Valeur ajoutée :**
- Les utilisateurs ne ratent plus les notifications (contrairement aux SSE seuls qui sont éphémères)
- Historique consultable : "quelles notifications ai-je reçues cette semaine ?"
- Standard attendu dans toute application enterprise moderne

---

### 3.3 Operational Dashboard (Tableau de bord métier temps réel)
**Statut** : ❌ Non implémenté  
**Priorité** : Moyenne  
**Effort estimé** : 5-7 jours

**Principe de fonctionnement :**
- Écran avec widgets configurables :
  - Files d'attente de tâches humaines (par assignee, par groupe, par type)
  - Dossiers bloqués (sans activité depuis X jours)
  - Instances en erreur (connecteur échoué, timeout, etc.)
  - SLA en temps réel (dossiers dans les temps vs en retard)
- Données agrégées depuis les tables Flowable et FlowForge avec requêtes optimisées
- Filtres par type de dossier, période, assignee, etc.

**Valeur ajoutée :**
- Premier écran consulté par un responsable métier le matin
- Détection proactive des goulots d'étranglement et dossiers oubliés
- Le `QuerySlaSummaryTool` MCP donne des données aux agents, mais pas aux humains — ce dashboard comble ce gap

---

## Catégorie 4 : Édition & Visualisation DMN (Adoption par les métier)

### 4.1 DMN Table Editor (Éditeur de décisions dans l'UI)
**Statut** : ❌ Non implémenté  
**Priorité** : Haute  
**Effort estimé** : 7-10 jours

**Principe de fonctionnement :**
- Éditeur tableur intégré dans l'UI FlowForge (inspiré de Camunda Modeler DMN mais plus simple)
- Colonnes : inputs (avec type et label), outputs (avec type et label), règles (lignes)
- Validation FEEL en temps réel (syntaxe, types incompatibles)
- Preview : simulation avec des données de test pour voir le résultat avant déploiement
- Bouton "Deploy" qui appelle le `DeployDecisionTableTool` MCP en backend

**Valeur ajoutée :**
- Rend DMN accessible aux métier (analystes, gestionnaires) sans passer par les développeurs
- Sans éditeur, DMN reste un outil pour devs (fichiers XML à éditer dans un outil externe)
- Accélère les cycles de modification de règles (de jours à minutes)

---

### 4.2 DRD Visualizer (Decision Requirements Diagram)
**Statut** : ❌ Non implémenté  
**Priorité** : Moyenne  
**Effort estimé** : 5-7 jours

**Principe de fonctionnement :**
- Vue graphique interactive des dépendances entre décisions DMN
- Noeuds : décisions (rectangles), inputs (ellipses), connaissances (rectangles arrondis)
- Flèches : dépendances (une décision utilise l'output d'une autre)
- Zoom, navigation, clic pour voir les détails d'une décision
- Généré automatiquement depuis le fichier `.dmn` (éléments `decision`, `inputData`, `knowledgeSource`)

**Valeur ajoutée :**
- Lisibilité des règles complexes : comprendre en un coup d'oeil comment les décisions s'articulent
- Indispensable pour les DRD avec 10+ décisions imbriquées
- Outil de communication entre métier et IT (le DRD est le "code" des règles métier)

---

## Catégorie 5 : Sécurité, Conformité & Observabilité

### 5.1 Decision Versioning & Rollback UI
**Statut** : ❌ Non implémenté  
**Priorité** : Haute  
**Effort estimé** : 4-5 jours

**Principe de fonctionnement :**
- Écran "Historique des décisions" listant toutes les versions déployées d'une décision
- Pour chaque version : date, auteur, changelog (diff avec la version précédente), statut (ACTIVE/ARCHIVED)
- Bouton "Rollback" qui revient à une version antérieure (appelle `RollbackDecisionTool` MCP)
- Audit trail : qui a déployé/rollbacké, quand, et pourquoi (champ commentaire obligatoire)

**Valeur ajoutée :**
- Sécurité opérationnelle : revenir en arrière en 1 clic après un déploiement problématique
- Conformité : traçabilité complète des évolutions de règles (audit NIS2, AI Act, etc.)
- Confiance des métier : ils savent qu'ils peuvent annuler une modification risquée

---

### 5.2 SLA Alerting (Alertes proactives de dépassement)
**Statut** : ❌ Non implémenté  
**Priorité** : Moyenne  
**Effort estimé** : 4-5 jours

**Principe de fonctionnement :**
- Configuration de seuils SLA par type de dossier (ex. : "90% des dossiers doivent être traités en < 5 jours")
- Job planifié qui scanne les dossiers en cours et détecte :
  - Dossiers sans activité depuis X jours
  - Dossiers proches du dépassement SLA (warning à J-1)
  - Dossiers en dépassement (alerte critique)
- Actions configurables : notification email/SMS, escalade automatique (réassignation à un groupe supérieur), création d'une tâche de supervision

**Valeur ajoutée :**
- Proactif : alerte avant le dépassement, pas seulement un reporting a posteriori
- Réduit les litiges clients liés aux délais non respectés
- Conforme aux exigences de supervision continue (NIS2, DORA, etc.)

---

### 5.3 Export BI Personnalisable (Power BI / Metabase / CSV avancé)
**Statut** : ❌ Non implémenté  
**Priorité** : Moyenne  
**Effort estimé** : 5-7 jours

**Principe de fonctionnement :**
- Générateur de rapports personnalisables :
  - Sélection des champs (dynamiques + système) à exporter
  - Filtres (par type, statut, période, assignee, etc.)
  - Agrégations (count, sum, avg, group by)
- Formats d'export : CSV, JSON, ou connexion directe à un outil BI (Power BI, Metabase, Tableau) via JDBC/ODBC
- Planification : export automatique quotidien/hebdomadaire vers un SFTP ou un bucket S3

**Valeur ajoutée :**
- Pilotage par la donnée : les décideurs peuvent créer leurs propres tableaux de bord sans développement
- Alternative à l'export CSV basique actuel (masqué sur les champs sensibles, sans agrégations)
- Intégration avec l'écosystème BI existant des entreprises

---

## Catégorie 6 : Performance & Scalabilité

### 6.1 Multi-Pod SSE (Scalabilité horizontale des notifications temps réel)
**Statut** : ⚠️ Hors V1 en multi-pod  
**Priorité** : Basse (seulement si gros volumes)  
**Effort estimé** : 5-7 jours

**Principe de fonctionnement :**
- Actuellement, les SSE sont hors V1 en multi-pod
- Solution : utiliser Redis Pub/Sub ou Kafka pour broadcaster les notifications SSE entre tous les pods
- Chaque pod publie les notifications qu'il génère dans un channel Redis/Kafka
- Tous les pods s'abonnent au channel et relayent les notifications aux clients connectés

**Valeur ajoutée :**
- Permet la scalabilité horizontale d'un tenant dès que sa charge justifie plusieurs répliques
- Sans cela, un tenant ne peut pas dépasser la capacité d'un seul pod — bloquant pour les gros volumes
- Critique pour les secteurs à forte saisonnalité (assurance en période de sinistres massifs, public en période de déclarations)

---

### 6.2 PostgreSQL Real Tests (Tests d'intégration sur vrai PG)
**Statut** : ⚠️ ITs sur H2 même en mode PostgreSQL  
**Priorité** : Basse (qualité interne)  
**Effort estimé** : 2-3 jours

**Principe de fonctionnement :**
- Actuellement, les ITs tournent sur H2 même en mode PostgreSQL
- Solution : ajouter un profil Maven/Gradle `pg-integration` qui lance un container Testcontainers PostgreSQL pour les tests
- Les ITs s'exécutent sur le vrai SGBD de production avec les mêmes types (JSONB, sequences, verrous)

**Valeur ajoutée :**
- Détecte les bugs spécifiques à PostgreSQL (subtilités JSONB, comportements de verrous, types de séquences)
- Évite les mauvaises surprises en production ("ça marche sur H2 mais pas sur PG")
- Standard de l'industrie pour les applications data-intensive

---

## Synthèse par priorité

### Priorité Critique (à livrer en premier)
1. **EvaluateDecisionTool** — sans lui, DMN est inutilisable par les agents IA
2. **Portail Externe Tokenisé** — bloque tous les cas "guichet numérique"
3. **BatchImportConnector** — bloque l'intégration avec les ERP et flux fichiers

### Priorité Haute (différenciation forte)
4. **TestDecisionTool** — sécurité des modifications de règles
5. **ElectronicSignatureConnector** — conformité légale des documents
6. **DMN Table Editor** — adoption par les métier
7. **AttachmentScanConnector** — conformité sécurité (antivirus)
8. **KafkaConnector** — intégration événementielle
9. **Decision Versioning & Rollback UI** — sécurité opérationnelle

### Priorité Moyenne (confort & supervision)
10. **Notification Inbox** — confort utilisateur
11. **Operational Dashboard** — supervision métier
12. **SLA Alerting** — proactivité
13. **GetDecisionHistoryTool + RollbackDecisionTool** — sécurité opérationnelle (backend)
14. **Export BI Personnalisable** — pilotage par la donnée
15. **CompositeScenarioTool** — orchestration IA simplifiée
16. **DRD Visualizer** — lisibilité des règles complexes

### Priorité Basse (optimisation)
17. **Multi-Pod SSE** — scalabilité (seulement si gros volumes)
18. **PostgreSQL Real Tests** — qualité interne (invisible pour les utilisateurs)

---

## Notes techniques

### Architecture cible
- **Backend** : Spring Boot 3.x, Java 17+, Flowable 6.x, PostgreSQL 15+
- **MCP** : SDK MCP Java, exposition des tools via SSE
- **Frontend** : React/TypeScript (à confirmer)
- **Infrastructure** : Docker, Kubernetes, multi-tenant par instance dédiée

### Contraintes connues
- Multi-tenancy par instance dédiée (1 instance Spring Boot = 1 tenant) → provisioning opérationnel à automatiser
- SSE multi-pod non supporté en V1 → nécessite Redis/Kafka pour la scalabilité horizontale
- Tests d'intégration sur H2 → risque de divergence avec PostgreSQL en production

### Prochaines étapes recommandées
1. **Valider la priorisation** avec les parties prenantes métier
2. **Estimer les efforts** avec l'équipe de développement
3. **Planifier les livraisons** par vagues de 2-3 semaines (sprints)
4. **Créer les issues GitHub** associées à chaque fonctionnalité
5. **Mettre à jour ce document** après chaque livraison majeure

---

## Historique des versions

| Version | Date | Auteur | Changements |
|---|---|---|---|
| 1.0 | 12/09/2026 | Assistant IA (analyse code) | Création initiale basée sur l'analyse du repo FlowForge |
