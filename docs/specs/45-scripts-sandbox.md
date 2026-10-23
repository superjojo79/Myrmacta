# Scripts JS/TS réduits en bac à sable (LC6)

> **Statut : spike réalisé le 2026-10-01 — D-SCRIPT-GO = NON (LC2 suffit ; lot non lancé), voir §9.** Sous-lot
> **LC6** `scripts-sandbox` de la couche low-code (D0 = C, [31](31-positionnement-produit.md),
> rang 6). **Conditionnel** : spike de qualification d'abord, lot seulement si LC2
> ([40](40-langage-expression-metier.md)) s'avère insuffisant.

---

## 1. Constat vérifié (`main` @ `e3985fe`)

| Élément | Constat | Preuve |
|---|---|---|
| `scriptTask` | Refusé au parsing | `FlowableBpmnParserAdapter` L113 |
| Conditions scriptées | Attributs `language` / `scriptFormat` refusés partout (défense en profondeur) | §69, `scriptConditionLanguage_isRejected` |
| Moteurs de script | Aucun sur le classpath (459 jars vérifiés : ni Groovy, ni GraalJS, ni Rhino, ni Jython ; Nashorn absent depuis Java 15) | §69 |
| Point d'extension | Uniquement du code Java compilé (delegates, connecteurs au classpath) | `AllowedBpmnDelegates`, SPI connecteur |

## 2. Objectif

Couvrir les cas que LC2 ne sait pas exprimer (algorithme de calcul, transformation complexe) avec
un script **court**, en JavaScript ou TypeScript, exécuté sans aucun accès au serveur.

## 3. Principe envisagé

- Nouvelle brique allowlistée « Script » ; le code est une **ressource versionnée** (comme une
  table DMN), **jamais inline** dans le BPMN.
- Exécution dans **QuickJS compilé en WebAssembly**, lancé par **Chicory** (runtime WASM pur
  Java, Apache 2.0) : ni système de fichiers, ni réseau, ni objets Java ; mémoire et nombre
  d'instructions bornés.
- API explicite et minimale : `ctx.dossier.fields` (lecture), `ctx.connectorResult`, valeur de
  retour validée comme une sortie de LC2.
- **TypeScript** transpilé à l'enregistrement (p. ex. `esbuild-wasm` dans l'éditeur Monaco) ; la
  frontière de sécurité est le bac à sable, pas la transpilation. `.d.ts` généré depuis le schéma
  du type pour l'autocomplétion.

## 4. Spike de qualification (avant tout lot)

- Maturité de `quickjs4j` / Chicory : stabilité, maintenance, licences transitives.
- Performances : démarrage d'une instance, coût par exécution, mode compilé de Chicory.
- Limites effectives : mémoire, instructions, interruption d'une boucle infinie.
- Vérification §69 : un moteur WASM n'enregistre pas de `ScriptEngine` JSR-223 ; le confirmer sur
  le classpath réel et rejouer `scriptConditionLanguage_isRejected`.

**Repli si le spike échoue :** GraalJS, avec les réserves de licence et d'isolation de
l'[analyse 31](31-positionnement-produit.md) §3.

## 5. Exposition MCP

`define_script` (brouillon), `test_script` (bac à sable, contexte d'exemple, cas bornés),
`get_script_typings` (`.d.ts`). Via MCP, pas de navigateur pour transpiler :
**D-MCP-LC-TS** (JavaScript seul via MCP, ou transpilation serveur dans le même bac à sable) —
à trancher après le spike. Voir [46](46-exposition-mcp-couche-low-code.md).

## 6. Risques

- Surface d'exécution de code, même isolée : revue adversariale obligatoire en fin de lot.
- Dépendance jeune.
- Tentation d'élargir l'API du script (réseau, écriture) : à refuser par principe.

## 7. Décisions à trancher

1. **D-SCRIPT-GO** — lancer le lot après le spike, ou s'en tenir à LC2.
2. **D-SCRIPT-ENGINE** — QuickJS / Chicory ou repli.
3. **D-SCRIPT-API** — contenu exact de `ctx` et type de retour.
4. **D-MCP-LC-TS** — TypeScript via MCP (analyse 46).

## 8. Documentation impactée

`GUIDE-CONCEPTION-BPMN.md` (brique Script), `GUIDE-UTILISATEUR.md` (éditeur de scripts),
`12-autorisation-conformite.md` (rôle autorisé à écrire des scripts).

---

## 9. Résultat du spike de qualification (2026-10-01)

**Question (charte §2, lot 14) :** le lot n'est lancé que si LC2 s'avère insuffisant, preuve à l'appui.

**Méthode.** Les cas que §2 range sous « algorithme de calcul, transformation complexe » ont été écrits en JSONata (LC2, moteur borné réel,
sans mock) dans [`ScriptQualificationSpikeTest`](../../src/test/java/com/flowforge/infrastructure/expression/ScriptQualificationSpikeTest.java) :

| Famille de cas | Résultat avec LC2 |
|---|---|
| Barème progressif par tranches (réduction d'état) | exprimable (`$reduce`) |
| Contrôle IBAN (modulo 97 itératif sur chiffres) | exprimable (`$map`, `$reduce`, `%`) |
| Regroupement / agrégation d'une liste de connecteur | exprimable (opérateur de regroupement `{clé: $sum(…)}`) |
| Fonction récursive (solde d'un prêt) | exprimable (fonction nommée récursive) |
| Arithmétique de dates, jours ouvrés | exprimable (`$toMillis`, `$fromMillis`, intervalles, prédicats) |
| Transformation d'objet, chaînes, conditionnelles | exprimable |

LC2 est un langage fonctionnel complet (fonctions d'ordre supérieur, récursion, agrégats) **déjà borné** : 2 000 caractères, 250 ms,
64 Ko de résultat, profondeur et allocation gardées, regex et `$eval`/`$random`/`$now` refusés, contexte JSON pur sans champs sensibles
(trois passes adversariales en LC2, §75). Aucun cas de la famille visée n'exige une boucle impérative ni un accès hors contexte.

**Décision D-SCRIPT-GO = NON.** Lancer LC6 ajouterait une surface d'exécution de code (WASM, nouvelle dépendance jeune, revue
d'isolation) pour une valeur que LC2 couvre déjà. **Conditions de réouverture :** un cas réel documenté (a) hors de portée de LC2
**et** (b) hors de portée d'un connecteur Java au classpath. Le premier endroit où le consigner est le fichier de test ci-dessus.
Si le lot était rouvert : QuickJS/Chicory (pas GraalJS : licence et isolation, analyse 31 §3), API en lecture seule
(`ctx.dossier.fields`, `ctx.connectorResult`), JavaScript seul via MCP (TypeScript transpilé côté éditeur uniquement).
**Non vérifié par ce spike** (sans objet puisque aucun moteur n'est ajouté) : maturité de `quickjs4j`/Chicory, performances,
interruption de boucle infinie ; la vérification §69 reste vraie (aucun `ScriptEngine` sur le classpath, `scriptTask` refusé).
