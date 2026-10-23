# §71 — Exposition MCP de la couche low-code — cadrage transverse

> **Statut :** ouvert · **Section :** [Points ouverts › Security](section-points-security.md) · ancien `TODO.md` §71 · [registre](README.md)

**Énoncé :** conséquence directe de §70. Le sous-lot LC3 (assistant de conception IA) fait du
serveur MCP le moteur du no-code côté client, alors qu'il a été cadré comme outillage interne M2M
([15-serveur-mcp.md](../specs/15-serveur-mcp.md)). Sans cadrage préalable, chaque lot `LC*`
exposerait ses artefacts au MCP à sa façon, ou pas du tout (précédent : trois mécanismes livrés
sans tool, rattrapés par [24-extension-mcp-flux-metier.md](../specs/24-extension-mcp-flux-metier.md)).

**Constat vérifié (`main` @ `e3985fe`) :** 27 tools atomiques ; `dryRun` sur 9 tools d'écriture
(absent de `redeploy_decision_table`) ; déploiements BPMN/DMN **directs** ; brouillons
`BpmnDraft`/`DmnDraft` existants mais non utilisés par le MCP ; templates en lecture seule côté
MCP ; rôle constant `API_SERVICE` pour tout JWT M2M (risque A3-M2 accepté) ; capacités MCP
`resource`/`prompt` désactivées.

**Analyse :** [46-exposition-mcp-couche-low-code.md](../specs/46-exposition-mcp-couche-low-code.md)
— principes P1–P9 (parité par conception, atomicité, brouillon par défaut, auto-vérification,
formats du domaine comme contrat, aucun nouveau chemin de confiance, identité réelle de l'acteur,
provenance, aucun secret via l'assistant), exposition par sous-lot, risques propres à
l'assistant (injection de consignes par les données, données transmises au fournisseur du
modèle, DoS par les outils d'évaluation, élévation via A3-M2).

**Statut :** **ouvert** (reliquat de cadrage, lots Vague D déjà livrés). 4 décisions tranchées
par l'utilisateur (2026-09-28) : **D-MCP-LC-IDENTITY = A** (assistant in-process avec
l'`AccessContext` de l'utilisateur de la session BFF, catalogue partagé avec le serveur MCP M2M) ;
**D-MCP-LC-PUBLISH = C, défaut = A** (publication directe autorisable hors production par
propriété de démarrage, humaine dans l'UI sinon) ; **D-MCP-LC-MIGRATION = A**
(`deploy_workflow`, `deploy_decision_table`, `redeploy_decision_table` passent au brouillon dans
D6 — rupture de comportement à annoncer) ; **D-MCP-LC-MODES = A** (modes *conception* et
*opération* séparés). **D-MCP-LC-SCOPES = B** tranché en D6 (TODO §82). **D-MCP-LC-TS** caduc
tant que LC6 n'est pas relancé (D-SCRIPT-GO = non). Restent ouvertes : D-MCP-LC-PARITY,
D-MCP-LC-RESOURCES, D-MCP-LC-SECRETS.

**Numérotation :** l'analyse, créée en 31, a été renumérotée **46** le 2026-09-28 pour suivre les
analyses initiales des lots qu'elle cadre (31 à 45) ; le lot D6 a sa propre analyse
([37-socle-mcp-low-code.md](../specs/37-socle-mcp-low-code.md)).

**Impacte :** `ROADMAP.md` (D6 élargi en socle MCP low-code, ligne « Exposition MCP » dans chaque
lot `LC*`), `docs/specs/00-index.md`, `15-serveur-mcp.md` § Décisions de cadrage (amendé). Dans le lot D6 :
`.env.example`, `docs/guides/GETTING-STARTED.md`, `docs/guides/DEPLOIEMENT.md` (propriété de publication),
`CHANGELOG.md` (rupture des tools de déploiement).
