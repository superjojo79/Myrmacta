# Positionnement produit — intégrateurs et couche low-code (D0)

> **Statut : décision actée (2026-09-28), TODO §70.** Lot **D0** de la Vague D (`ROADMAP.md`
> Catégorie 7). Ce document conserve l'analyse qui a conduit à la décision et le classement des
> options low-code / no-code, pour que les lots `LC1`–`LC6` (analyses
> [39](39-palette-metier-bpmn.md) à [45](45-scripts-sandbox.md)) partent d'un même raisonnement.
> Exposition MCP de l'ensemble : [46-exposition-mcp-couche-low-code.md](46-exposition-mcp-couche-low-code.md).

---

## 1. Constat vérifié (`main` @ `e3985fe`)

| Élément | Constat | Preuve |
|---|---|---|
| Promesse | « Modéliser un processus sans coder » | `README.md` § Proposition de valeur |
| Réalité de la conception | Expertise BPMN nécessaire : conventions, noms de delegates, champs d'extension | `docs/guides/GUIDE-CONCEPTION-BPMN.md` (33 Ko) |
| Extension | 7 delegates allowlistés ; toute logique hors de ce catalogue impose du code Java et un rebuild | `AllowedBpmnDelegates` |
| Panneau BPMN | Champs texte libres (`delegateExpression`, `formKey`, `type`, `decisionTableReferenceKey`) | `flowablePropertiesProvider.ts` |
| Templates | HTML Thymeleaf saisi dans un `<textarea>` | `TemplatesAdminView.vue` |
| Expressions | JUEL restreint par allowlist (§57, §58, §69) ; aucun langage « métier » | `FlowableBpmnParserAdapter`, `BpmnExpressionSurfaceScanner` |
| Déploiement | 1 instance Spring Boot = 1 tenant (décision n° 13) : isolation forte, densité faible | `02-multi-tenant.md` |

**Lecture :** le verrouillage est **serveur**, et il est voulu. Le manque de no-code est
**côté UI**. Les options les moins risquées sont donc celles qui simplifient l'écran sans ouvrir
de nouvelle surface d'expression côté serveur.

## 2. Options de positionnement

| Option | Description | Conséquences |
|---|---|---|
| **A** | Outil de delivery pour intégrateurs, hébergement dédié par client | Cohérent avec la décision n° 13 ; abandon du discours « sans développement » |
| **B** | SaaS mutualisé en libre-service | Rouvrir la décision n° 13 ; UX de modélisation pour non-techniciens ; densité multi-tenant à revoir |
| **C** | Produit pour intégrateurs **avec une couche low-code / no-code** pour les utilisateurs avancés côté client | Décision n° 13 conservée ; l'intégrateur conçoit les processus, l'utilisateur métier ajuste formulaires, documents et règles |

**Décision : D-POSITIONING = C** (utilisateur, 2026-09-28). L'option B n'est pas fermée mais
n'est plus un préalable.

## 3. Classement des options low-code / no-code

Critères : valeur pour l'utilisateur, coût, risque de sécurité, cohérence avec l'existant.

| Rang | Option | Type | Taille | Nouvelle surface serveur | Lot |
|---|---|---|---|---|---|
| 1 | Briques métier dans l'éditeur BPMN | low-code | S–M | aucune | [LC1](39-palette-metier-bpmn.md) |
| 2 | Langage d'expression borné (conditions, calculs, mappings) | low-code | M | faible | [LC2](40-langage-expression-metier.md) |
| 3 | Assistant de conception IA dans l'UI | no-code | M | faible (si D6) | [LC3](41-assistant-conception-ia.md) |
| 4 | Éditeurs visuels de formulaires et de templates | no-code | M chacun | aucune | [LC4a](42-editeur-formulaires.md), [LC4b](43-editeur-templates-wysiwyg.md) |
| 5 | Concepteur de parcours compilé en BPMN | no-code | L | aucune | [LC5](44-concepteur-parcours.md) |
| 6 | Scripts JS/TS réduits en bac à sable WASM | code réduit | M–L | oui, maîtrisée | [LC6](45-scripts-sandbox.md) |
| 7 | Règles « Quand / Si / Alors » hors BPMN | no-code | M | faible | non retenu |
| 8 | Scripts JS/TS via GraalJS | code réduit | M | oui, plus risquée | repli de LC6 |
| — | Modèles n8n / Make prêts à l'emploi | complément | S | aucune | documentation |
| ✗ | `scriptTask` natif Flowable (JSR-223) | — | — | exécution de code arbitraire | **écarté** (§57, §69) |

**Motifs des non-retenus :**
- *Règles hors BPMN* : recouvrement avec les événements BPMN et l'alerting SLA (§65) — deux
  endroits pour exprimer la logique métier.
- *GraalJS* : à ma connaissance, les politiques de bac à sable les plus strictes (`ISOLATED`,
  `UNTRUSTED`) sont réservées à Oracle GraalVM sous licence GFTC, ce qui heurte le principe
  « licences libres » du `README.md` — à reconfirmer si la piste revient. Moteur dans la même JVM.
- *`scriptTask` natif* : accès complet à la JVM ; exactement ce que §57 et §69 ont fermé.

**Séquence retenue :** LC1 → LC2 → D6 + LC3 → LC4a / LC4b → LC5, puis LC6 seulement si LC2
montre ses limites.

## 4. Conséquences sur les autres lots

- **D4** ([35](35-promotion-configuration.md)) remonte : indispensable à un intégrateur qui fait
  recette puis production.
- **D5** ([36](36-verrou-jobs-planifies.md)) reste à l'audit : l'hébergement dédié ne l'impose pas.
- **D6** ([37](37-socle-mcp-low-code.md)) devient prérequis de LC3.
- **D1** ([32](32-modele-champs-enrichi.md)) : champs calculés via LC2 ; **D3b**
  ([34](34-integrations-entreprise.md)) consomme le langage de LC2.
- **D7** ([38](38-internationalisation-ui.md)) dépend du marché visé.

## 5. Documentation impactée

`README.md` (vision « sans développement » à nuancer, § Statut obsolète) — à répercuter lors du
premier lot livré, pas avant.
