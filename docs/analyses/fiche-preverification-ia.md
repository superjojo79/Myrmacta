# FlowForge — Fiche technique : Pré-vérification IA des dossiers

*Annexe technique destinée aux intégrateurs et aux dossiers de marchés publics (pouvoirs locaux et régionaux en Belgique francophone). Version du 22/10/2026, établie à partir du code de la branche `main`. Les éléments **existants** sont référencés par leur chemin dans le dépôt ; tout le reste est une **proposition** et est signalé comme tel.*

---

## 1. Le besoin et la promesse

Les administrations reçoivent des demandes incomplètes ou incohérentes, et l'agent traitant passe une part importante de son temps à les contrôler avant même d'instruire. La pré-vérification IA de FlowForge vérifie automatiquement la **complétude** (champs et pièces), applique les **règles métier** et prépare un **résumé avec des signalements** pour l'agent. **La décision reste toujours humaine**, et chaque suggestion de l'IA est tracée.

## 2. Ce qui existe déjà dans FlowForge

| Capacité | Où (code) |
|---|---|
| Assistant IA avec une boucle d'agent côté serveur : les outils s'exécutent avec l'identité de l'utilisateur, et les résultats des outils sont traités « comme des données, jamais comme des consignes » | `domain/assistant/usecase/ChatWithAssistantUseCaseImpl.java` |
| Port de modèle neutre et adaptateur Spring AI (Spring AI 2.0.1) | `domain/assistant/port/out/LanguageModelPort.java`, `infrastructure/assistant/SpringAiLanguageModel.java` |
| Fournisseur Anthropic. L'assistant est **désactivé par défaut** et sa clé reste côté serveur. HTTPS est obligatoire. `baseUrl` est configurable (passerelle ou région) | `infrastructure/assistant/AnthropicChatModels.java`, `infrastructure/config/AssistantProperties.java` |
| Plafonds : tours d'outils, tours par conversation, jetons par conversation et par tenant et par jour | `domain/assistant/model/AssistantLimits.java` |
| Conversations et consommation persistées, purgées après 30 jours par défaut | `infrastructure/persistence/assistant/*`, `infrastructure/assistant/ConversationRetentionJob.java` |
| Séparation des modes **conception** et **opération**. L'assistant actuel est limité au mode conception, **sans aucune donnée de dossier** | `infrastructure/mcp/McpToolModes.java`, `infrastructure/assistant/McpDesignToolCatalog.java` |
| Règles déterministes : champs calculés, champs rendus obligatoires et champs sans objet (moteur JSONata borné) | `domain/dossier/DossierFieldRules.java`, `domain/dossier/port/in/EvaluateDossierRulesUseCase.java` |
| Validation des champs (champs obligatoires, types, dont `FILE`) | `domain/dossier/model/DossierFieldValidator.java` |
| Tables de décision DMN : brique « Décision DMN », outils `evaluate_decision` et `test_decision` | `domain/workflow/palette/BuildingBlockCatalog.java`, `infrastructure/mcp/tool/EvaluateDecisionTool.java`, `TestDecisionTool.java` |
| Palette métier BPMN (11 briques) et liste blanche des délégués côté serveur | `domain/workflow/palette/BuildingBlockCatalog.java`, `domain/workflow/AllowedBpmnDelegates.java` |
| Masquage des champs sensibles et lecture des champs bruts limitée à une liste de classes vérifiée par ArchUnit | `domain/dossier/DossierFieldRedactor.java`, `src/test/java/com/flowforge/ArchitectureRulesTest.java` |
| Pièces jointes : contrôle des magic bytes et antivirus ClamAV à activer explicitement | `infrastructure/dossier/ClamAvAttachmentScanAdapter.java` |
| Incidents visibles et rejouables sur la fiche dossier | TODO §94, `infrastructure/mcp/tool/GetDossierTool.java` |

**Ce qui n'existe pas aujourd'hui :**
- aucune fonction d'IA ne lit les **données d'un dossier** (c'est un choix de sécurité) ;
- aucune extraction de texte ni OCR des pièces jointes : `pom.xml` ne contient ni PDFBox, ni Tika, ni Tesseract ;
- un seul fournisseur de modèle : `SpringAiLanguageModel.toPrompt` construit des `AnthropicChatOptions`.

## 3. Fonctionnement proposé *(proposition)*

**Point d'insertion.** Une nouvelle brique de palette « Pré-vérification IA » (délégué `aiPrecheckTask`, ajouté à `AllowedBpmnDelegates`). Elle est **asynchrone**, si bien qu'un échec du fournisseur devient un incident rejouable, déjà géré par le §94. On la place juste avant la tâche humaine d'instruction. Elle peut aussi être déclenchée à la demande depuis la fiche dossier.

On procède en trois passes, du plus fiable au moins fiable :

1. **Complétude (déterministe, sans IA).** Champs obligatoires, champs rendus obligatoires par une règle (`requiredByRule`) et pièces `FILE` attendues, d'après `DossierFieldValidator` et `EvaluateDossierRulesUseCase`. Le résultat est exact et explicable.
2. **Contrôle de règles.**
   - **Structuré :** les tables DMN et les expressions JSONata existantes, par exemple les conditions d'éligibilité, la cohérence entre code postal et commune ou la recherche d'un doublon sur `plaqueNormalisee`.
   - **Non structuré (LLM) :** lecture du texte extrait des pièces (à développer), avec des questions fermées du type « la preuve de domicile est-elle au nom du demandeur ? » ou « la date du document a-t-elle moins de 3 mois ? ».
3. **Résumé et signalements (LLM).** Une synthèse courte pour l'agent, accompagnée de constats typés : `MANQUANT`, `INCOHÉRENCE`, `À VÉRIFIER` ou `OK`. Chaque constat indique sa **source** (champ, pièce ou règle) et un niveau de confiance.

**Garde-fous de conception :**
- Un seul appel au modèle, **sans outils**, avec une **sortie JSON validée par un schéma**. Ce n'est donc pas la boucle d'agent : on ferme ainsi la porte à l'injection de consignes par le contenu des pièces.
- Les champs sensibles sont exclus en amont par `DossierFieldRedactor`.
- La sortie est rangée comme **élément du dossier** marqué « Généré par IA — indicatif ». Elle n'écrit **jamais** dans un champ de décision et ne fait jamais avancer le workflow.
- L'agent accepte ou écarte chaque constat, et son choix est journalisé.

## 4. Conformité

- **RGPD.**
  - Bases légales : mission d'intérêt public (art. 6.1.e) et minimisation, ce qui signifie n'envoyer au modèle que les champs et extraits utiles.
  - **Pas de décision exclusivement automatisée** (art. 22) : l'IA ne fait que préparer.
  - Une **AIPD** est recommandée.
  - Le fournisseur du modèle est un **sous-traitant** (art. 28) : il faut un contrat, la localisation des données et l'absence de réutilisation pour l'entraînement. L'analyse du lot 11 dit déjà que le fournisseur est un « sous-traitant à déclarer » (`docs/roadmap-goal/AVANCEMENT.md`).
- **AI Act (classification plausible, à confirmer au cas par cas).**
  - La complétude, le résumé et les signalements pour un agent relèvent a priori de la « tâche préparatoire » (art. 6.3), donc **hors haut risque**.
  - En revanche, un usage qui pèse sur l'**éligibilité à une prestation ou à une aide publique** (subsides) pourrait relever de l'annexe III, point 5 a). Il faut alors appliquer les exigences du haut risque.
  - Dans tous les cas : supervision humaine, transparence (marquage « IA »), formation des agents (art. 4) et **journalisation**. Pour chaque appel, on conserve le modèle et sa version, la version du prompt, l'empreinte des entrées, la sortie et la décision de l'agent.
  - Le calendrier d'application des obligations du haut risque est à vérifier au moment de l'offre.
- **Hébergement et fournisseur *(proposition)*.**
  - Rendre l'adaptateur multi-fournisseur, ce que permet l'abstraction `ChatModel` de Spring AI.
  - Options : Mistral AI (société européenne), Azure OpenAI ou Bedrock en région UE, ou **auto-hébergement** (Ollama, ou un serveur compatible OpenAI comme vLLM) sur l'infrastructure du client ou de l'intégrateur.
  - Choix par tenant, cohérent avec le modèle « une instance par client ».

## 5. Travaux à réaliser *(estimations indicatives, à affiner en phase de cadrage)*

| # | Lot | Effort | Jours/personne (estimation) |
|---|---|---|---|
| 1 | Adaptateur multi-fournisseur (options neutres, configuration par tenant, Mistral, Azure UE, Ollama) | faible à moyen | 3–5 |
| 2 | Complétude déterministe (champs, pièces, `requiredByRule`) exposée en API et MCP | faible | 2–3 |
| 3 | Cas d'usage `PrecheckDossier` : construction de l'entrée masquée, prompt versionné, sortie JSON validée, rangement dans le dossier | moyen | 8–12 |
| 4 | Brique de palette, délégué dans la liste blanche, parseur, outil MCP (mode opération) | moyen | 4–6 |
| 5 | Extraction de texte des pièces (PDFBox ou Tika, sous licence Apache 2.0), OCR facultatif, bornes de taille | moyen à élevé | 5–10 |
| 6 | Panneau « Pré-vérification » sur la fiche dossier, avec accepter ou écarter et le marquage IA, en français et en anglais | moyen | 5–8 |
| 7 | Journal de traçabilité pour l'AI Act et export pour audit | faible à moyen | 3–5 |
| 8 | Jeu d'évaluation, revue adversariale (injection par les pièces), modèles d'AIPD et notice | moyen | 5–8 |
| | **Total indicatif** | | **≈ 35–57 j/p** |

## 6. Scénario de démo (exemple `exemples/autorisation-riverain/`)

1. On crée une demande à partir de `config/exemple-dossier.json`, en omettant la preuve de domicile et en introduisant une incohérence de commune.
2. La brique « Pré-vérification IA » s'exécute avant « Instruire la demande ».
3. L'agent ouvre la fiche et voit :
   - « Pièce manquante : preuve de domicile » (déterministe) ;
   - « Code postal incohérent avec la commune » (règle) ;
   - « Une demande existe déjà pour `BE-1ABC123` » (recherche sur `plaqueNormalisee`) ;
   - un résumé en 5 lignes.
4. L'agent écarte un constat, en accepte deux, puis « Demander des informations » relance le demandeur.
5. On montre le journal : modèle, version du prompt, constats et choix de l'agent.

Pour que ce scénario fonctionne, il faut rendre les pièces obligatoires par une règle, car dans `config/type-dossier.json` les champs `FILE` sont `required: false`. Il faut aussi livrer le lot « unicité / recherche » (`DEFERRED-FEATURES.md` §7) ou une vérification équivalente.

## 7. Risques et limites

- **Erreurs du modèle :** faux positifs ou négatifs, voire constats inventés. On les atténue en gardant la priorité au déterministe, en citant la source de chaque constat et en laissant toujours la décision à l'agent.
- **Injection de consignes** via le contenu des pièces : appel sans outils, sortie bornée par un schéma et revue adversariale dédiée.
- **Qualité des pièces :** les scans et photos imposent un OCR ou un modèle multimodal, avec un coût et une précision variables.
- **Données personnelles chez un tiers :** c'est le choix du fournisseur et de sa région qui décide, et l'auto-hébergement a un coût d'infrastructure.
- **Coût et latence :** plafonds existants par tenant (`AssistantLimits`) et exécution asynchrone.
- **Dérive réglementaire :** la classification AI Act est à revoir pour chaque cas d'usage, en particulier les aides et subsides.
- **Rupture d'un choix actuel :** aujourd'hui, l'IA ne voit aucune donnée de dossier (`McpToolModes`). Ouvrir cette porte doit faire l'objet d'une décision explicite, consignée dans `docs/roadmap-goal/DECISIONS.md`, et d'une revue de sécurité.
