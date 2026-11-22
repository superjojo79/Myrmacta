# Exemple — envoyer une décision dans l'eBox

Une commune publie la décision d'un dossier dans l'eBox d'une entreprise (numéro BCE) ; quand l'eBox ne suffit pas, un agent
envoie la copie papier. Issue [#13](https://github.com/superjojo79/Myrmacta/issues/13), décision
[§155](../../docs/decisions/155-envoi-ebox-citoyen-et-entreprise.md).

- `config/connecteur-ebox-envoi.exemple.json` : le compte expéditeur (type de connecteur `ebox-outbound`), à créer dans
  Administration → Connecteurs ; secrets chiffrés au repos.
- `envoi-decision-ebox.bpmn20.xml` : générer la décision, la joindre au dossier, « Envoyer en eBox » (asynchrone), puis une
  passerelle sur `${eboxSecoursRequis == true}` vers la tâche « Envoyer la décision par courrier » du groupe COURRIER. Remplacer
  `connectorInstanceId` par l'identifiant de l'instance et `documentType` par le gabarit de la décision.

Le type de dossier porte deux champs : `numeroBce` (destinataire) et `langue` (`fr`, `nl` ou `de`). Pour un citoyen :
`eboxEnvoiTypeDestinataire="CITOYEN"` et un champ NISS ; tant que l'API de l'eBox citoyen n'est pas branchée, aucun citoyen n'a
consenti et tout part par la voie de secours. Variables posées et règles : [guide de conception BPMN](../../docs/guides/GUIDE-CONCEPTION-BPMN.md).
