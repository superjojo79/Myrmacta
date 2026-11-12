# §131 — Historique d'exécution : étapes significatives seulement

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] clos (issue [#69](https://github.com/superjojo79/Myrmacta/issues/69), vague `vague-vitrine`). Mandat cloud agent
2026-11-12. Comportement : [spec 10](../specs/10-visualisation-workflow.md) § « Historique — étapes significatives ».

**Constat.** `GET /api/workflow-links/{linkId}/history` renvoyait toutes les activités historiques Flowable,
y compris les `sequenceFlow` (`flow_…`) et les passerelles. Le SPA affichait alors l'`activityId` technique
quand le nom était absent.

**D-HISTORY-SIGNIFICANT** — l'historique métier ne contient que les activités **significatives** :

1. exclus toujours : `sequenceFlow` et les types de passerelle (`exclusiveGateway`, `inclusiveGateway`,
   `parallelGateway`, `eventBasedGateway`, `complexGateway`) ;
2. exclus aussi : toute activité dont le nom Flowable est absent ou blanc ;
3. le reste (tâches, événements nommés, service tasks de statut nommés, etc.) est conservé dans l'ordre
   chronologique ; la pagination porte sur ce sous-ensemble.

Filtrage dans l'adaptateur Flowable via une règle domaine pure (`SignificantActivityFilter`), pas dans le SPA :
même contrat pour tout consommateur. Pas d'UI de « repli » des techniques. Le diagramme BPMN continue d'utiliser
l'ensemble des IDs (hors périmètre).

**Trade-off pagination / mémoire (revue de clôture, §24).** Une pagination Flowable native (`listPage`/`count`
sur le brut) laisserait des pages à trous une fois les techniques retirés. Le filtre exige donc un slice mémoire
sur le sous-ensemble. Pour ne pas régresser le garde-fou §24 (pas de `.list()` non borné), la lecture Flowable
est plafonnée à **`RAW_SCAN_LIMIT = 2_000`** activités brutes chronologiques (`listPage(0, limit+1)`), puis filtre
et pagination. Une exécution pathologique au-delà de cette borne peut omettre des étapes plus récentes ; pas de
champ `truncated` sur l'enveloppe history (contrairement à `D-INCIDENT-SCAN`) pour éviter une rupture OpenAPI dans
ce lot — la borne est documentée ici et dans la spec 10.

Écarté : filtre uniquement front (pagination et API resteraient polluées) ; exposer `activityType` + repli UI
(complexité sans besoin pour #69) ; `.list()` non borné ; champ `truncated` sur `/history` dès ce lot.

**Impacte :** `SignificantActivityFilter`, `FlowableWorkflowVisualizationAdapter.getHistory`, spec 10, guide
utilisateur.
