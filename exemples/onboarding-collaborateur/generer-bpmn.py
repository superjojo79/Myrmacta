"""Génère le BPMN du flux « arrivée d'un collaborateur » (orchestration technique, TODO §112).

Le processus coordonne trois applications REST (SIRH, IAM, ITSM) par le connecteur `http-rest`. Chaque requête est
construite dans le BPMN par l'attribut `flowable:connectorRequest`, une expression JSONata bornée qui lit le dossier et
le dernier résultat de connecteur. Les validations humaines (manager, IT, incident technique) orientent le processus.

Les identifiants des trois connecteurs sont ceux de l'instance où le processus sera déployé. Par défaut, le script écrit
des identifiants de remplacement (voir config/connecteurs.json) :

    python generer-bpmn.py
    python generer-bpmn.py --sirh <uuid> --iam <uuid> --itsm <uuid>
"""

import argparse
from pathlib import Path
from xml.sax.saxutils import escape, quoteattr

PROCESS_ID = "onboardingCollaborateur"
PROCESS_NAME = "Arrivée d'un collaborateur"
PLACEHOLDERS = {"sirh": "00000000-0000-4000-8000-000000000001",
                "iam": "00000000-0000-4000-8000-000000000002",
                "itsm": "00000000-0000-4000-8000-000000000003"}

COL_W, X0 = 160, 40
ROW = {"haut": 60, "main": 200, "bas": 340, "echec": 460, "incident": 600, "compensation": 760}
SIZE = {"task": (120, 80), "gateway": (50, 50), "event": (36, 36)}

JSON = "'headers': {'Content-Type': 'application/json'}"

# ---------------------------------------------------------------- Requêtes (JSONata borné, chaînes entre apostrophes)
REQ_SIRH = ("{'method': 'POST', 'path': '/employees', " + JSON + ",\n"
            " 'body': {'firstName': dossier.fields.prenom, 'lastName': dossier.fields.nom,\n"
            "          'personalEmail': dossier.fields.emailPersonnel, 'jobTitle': dossier.fields.fonction,\n"
            "          'department': dossier.fields.departement, 'startDate': dossier.fields.dateArrivee,\n"
            "          'managerLogin': dossier.fields.managerLogin, 'externalReference': dossier.reference}}")
REQ_IAM = ("{'method': 'POST', 'path': '/users', " + JSON + ",\n"
           " 'body': {'username': $replace($lowercase($substring(dossier.fields.prenom, 0, 1) & dossier.fields.nom), ' ', ''),\n"
           "          'firstName': dossier.fields.prenom, 'lastName': dossier.fields.nom,\n"
           "          'employeeNumber': variables.matricule, 'enabled': false,\n"
           "          'attributes': {'department': dossier.fields.departement, 'manager': dossier.fields.managerLogin}}}")
REQ_GROUPE = ("{'method': 'PUT', 'path': '/users/{id}/groups/{groupe}',\n"
              " 'pathParams': {'id': variables.compteIam,\n"
              "                'groupe': variables.niveauAcces = 'DIRECTION' ? 'direction' : 'developpeurs'}}")
REQ_TICKET = ("{'method': 'POST', 'path': '/tickets', " + JSON + ",\n"
              " 'body': {'category': 'HARDWARE', 'profile': dossier.fields.profilPoste,\n"
              "          'title': 'Poste de travail pour ' & dossier.fields.prenom & ' ' & dossier.fields.nom,\n"
              "          'dueDate': dossier.fields.dateArrivee, 'requestedFor': variables.login,\n"
              "          'requestedBy': dossier.fields.managerLogin, 'correlationId': dossier.reference}}")
REQ_SUPPR_IAM = "{'method': 'DELETE', 'path': '/users/{id}', 'pathParams': {'id': variables.compteIam}}"
REQ_SUPPR_SIRH = "{'method': 'DELETE', 'path': '/employees/{id}', 'pathParams': {'id': variables.matricule}}"

APPEL_OK = "connectorResult.status >= 200 and connectorResult.status < 300"
JOURNAL = "($exists(dossier.fields.journalCompensation) ? dossier.fields.journalCompensation & ' ; ' : '')"


class Model:
    def __init__(self):
        self.nodes, self.flows = [], []

    def node(self, node_id, tag, name, col, row, attrs=None, body=""):
        kind = ("gateway" if tag.endswith("Gateway") else
                "event" if tag.endswith("Event") else "task")
        self.nodes.append(dict(id=node_id, tag=tag, name=name, col=col, row=row,
                               attrs=attrs or {}, body=body, kind=kind))

    def service(self, node_id, name, col, row, delegate, asynchronous=False, **params):
        attrs = {"flowable:delegateExpression": "${" + delegate + "}"}
        if asynchronous:
            attrs["flowable:async"] = "true"
        attrs.update({f"flowable:{k}": v for k, v in params.items()})
        self.node(node_id, "serviceTask", name, col, row, attrs)

    def call(self, node_id, name, col, row, connector, request):
        # Asynchrone : l'appel ne tient pas dans la transaction de la validation humaine qui précède. Une panne réseau
        # devient un incident rejouable ; une réponse HTTP en erreur est routée par le contrôle qui suit.
        self.service(node_id, name, col, row, "connectorServiceTask", asynchronous=True,
                     connectorInstanceId=connector, connectorRequest=request)

    def expression(self, node_id, name, col, row, formula, result):
        self.service(node_id, name, col, row, "businessExpressionTask", formula=formula, formulaResult=result)

    def mapping(self, node_id, name, col, row, lines):
        self.service(node_id, name, col, row, "fieldMappingTask", fieldMapping=lines)

    def wf_status(self, node_id, name, col, row, code):
        self.service(node_id, name, col, row, "updateWorkflowLinkStatusTask", targetWorkflowStatus=code)

    def dossier_status(self, node_id, name, col, row, code):
        self.service(node_id, name, col, row, "updateDossierStatusTask", targetDossierStatus=code)

    def question(self, node_id, name, col, row, schema, doc):
        self.node(node_id, "userTask", name, col, row, {"flowable:formKey": "user-question:" + schema},
                  body=f"<documentation>{escape(doc)}</documentation>")

    def gateway(self, node_id, name, col, row, default=None):
        self.node(node_id, "exclusiveGateway", name, col, row, {"default": default} if default else {})

    def flow(self, source, target, condition=None, flow_id=None, low=None):
        self.flows.append(dict(id=flow_id or f"flow_{source}_{target}", source=source, target=target,
                               condition=condition, low=low))


def appel(m, col, cle, nom, connector, request, retenir, mapping_lines, etape):
    """Appel + contrôle du statut HTTP + identifiants retenus + recopie au dossier ; un échec part vers l'incident.

    Les identifiants rendus par une application sont retenus dans des **variables de process** écrites par le BPMN
    (``retenir`` : variable -> expression) : ce sont elles que les appels suivants, et la compensation, relisent. Les champs
    du dossier, modifiables par toute personne qui a le droit EDIT, ne servent qu'à l'affichage (revue §112, H1).
    """
    m.call(f"appel{cle}", nom, col, "main", connector, request)
    # Frontière asynchrone juste après l'appel (seconde revue §112) : un échec en aval (réponse sans identifiant, conflit
    # d'écriture du dossier) relance ce contrôle, pas l'appel ; un POST n'est jamais rejoué sans décision humaine.
    m.expression(f"controle{cle}", f"Contrôle : {nom.lower()}", col + 1, "main", APPEL_OK, "appelOk")
    m.nodes[-1]["attrs"]["flowable:async"] = "true"
    m.gateway(f"resultat{cle}", "Réussi ?", col + 2, "main", default=f"flow_resultat{cle}_echec{cle}")
    m.expression(f"echec{cle}", f"Échec : {etape}", col + 2, "echec", f"'{etape}'", "etapeEnEchec")
    m.flow(f"appel{cle}", f"controle{cle}")
    m.flow(f"controle{cle}", f"resultat{cle}")
    m.flow(f"resultat{cle}", f"echec{cle}")  # flux par défaut : statut HTTP hors 2xx
    m.flow(f"echec{cle}", "incident")
    precedent, condition = f"resultat{cle}", "${appelOk == true}"
    for i, (variable, expression) in enumerate(retenir):
        node = f"retenir{cle}{i}"
        m.expression(node, f"Retenir {variable}", col + 3, "bas" if i == 0 else "echec", expression, variable)
        m.nodes[-1]["sub"] = i
        m.flow(precedent, node, condition)
        precedent, condition = node, None
    m.mapping(f"enregistrer{cle}", "Afficher la réponse au dossier", col + 3, "main", mapping_lines)
    m.flow(precedent, f"enregistrer{cle}", condition)
    return f"enregistrer{cle}"


def build(ids):
    m = Model()

    # ------------------------------------------------------------ 1. Validation du manager
    m.node("debut", "startEvent", "Arrivée encodée par les RH", 0, "main")
    m.wf_status("statutValidation", "Statut : en validation", 1, "main", "EN_VALIDATION")
    m.question("validationManager", "Valider l'arrivée", 2, "main", "validation-manager-onboarding",
               "Vérifier la fiche du collaborateur. APPROUVER lance la création dans le SIRH, l'IAM et l'ITSM ; "
               "CORRIGER renvoie la fiche aux RH avec votre commentaire ; REFUSER clôture la demande sans rien créer.")
    m.gateway("decisionManager", "Décision ?", 3, "main", default="flow_decisionManager_statutProvisioning")
    m.node("correction", "userTask", "Corriger la fiche", 3, "haut", {"flowable:formKey": "dossier-fields"},
           body="<documentation>Corriger la fiche selon le commentaire du manager, puis enregistrer : la demande "
                "repart en validation.</documentation>")
    m.wf_status("statutRefuse", "Statut : refusé", 4, "bas", "REFUSE")
    m.dossier_status("clotureRefus", "Clôturer le dossier", 5, "bas", "CLOTURE")
    m.node("finRefus", "endEvent", "Arrivée refusée", 6, "bas")

    # ------------------------------------------------------------ 2. Provisioning : SIRH puis IAM
    m.wf_status("statutProvisioning", "Statut : provisioning", 4, "main", "EN_PROVISIONING")
    # Revue §112 (H1) : une variable posée au démarrage ou à la correction (formulaire libre) ne doit pas désigner ce que
    # la compensation supprimera. Après ce point, seules des tâches « user-question » (variables déclarées) restent.
    m.expression("razMatricule", "Réinitialiser : matricule", 4, "haut", "null", "matricule")
    m.expression("razCompteIam", "Réinitialiser : compte IAM", 5, "haut", "null", "compteIam")
    m.expression("razLogin", "Réinitialiser : login", 6, "haut", "null", "login")
    fin_sirh = appel(m, 5, "Sirh", "Créer la fiche SIRH", ids["sirh"], REQ_SIRH,
                     [("matricule", "connectorResult.json.id")],
                     "matriculeSirh = variables.matricule", "SIRH")
    fin_iam = appel(m, 9, "Iam", "Créer le compte IAM", ids["iam"], REQ_IAM,
                    [("compteIam", "connectorResult.json.id"), ("login", "connectorResult.json.username")],
                    "identifiantIam = variables.compteIam\nloginIam = variables.login", "IAM")

    # ------------------------------------------------------------ 3. Validation des accès par l'IT (profils sensibles)
    m.expression("besoinValidationIt", "Profil sensible ?", 13, "main",
                 "dossier.fields.profilPoste != 'STANDARD'", "validationItRequise")
    m.gateway("aiguillageIt", "Validation IT ?", 14, "main", default="flow_aiguillageIt_accesDecides")
    m.question("validationAcces", "Valider les accès", 15, "haut", "validation-acces-onboarding",
               "Le profil de poste demande des accès étendus. Choisir le groupe IAM à accorder (DEVELOPPEURS ou "
               "DIRECTION), ou STANDARD pour s'en tenir aux groupes par défaut. C'est ce choix, et non le profil de la "
               "fiche, qui désigne le groupe.")
    m.gateway("niveauAcces", "Niveau ?", 16, "haut", default="flow_niveauAcces_accesDecides")
    fin_groupe = appel(m, 17, "Groupe", "Ajouter au groupe IAM", ids["iam"], REQ_GROUPE, [],
                       "accesEtendus = true", "GROUPE")
    # Les noeuds de l'appel « groupe » sont sur la ligne du haut, comme la validation qui le déclenche.
    for n in m.nodes:
        if n["id"] in ("appelGroupe", "controleGroupe", "resultatGroupe", "enregistrerGroupe"):
            n["row"] = "haut"
    m.gateway("accesDecides", "", 21, "main")

    # ------------------------------------------------------------ 4. ITSM puis clôture
    fin_ticket = appel(m, 22, "Ticket", "Commander le matériel (ITSM)", ids["itsm"], REQ_TICKET, [],
                       "ticketMateriel = connectorResult.json.number", "ITSM")
    m.wf_status("statutProvisionne", "Statut : provisionné", 26, "main", "PROVISIONNE")
    m.dossier_status("clotureDossier", "Clôturer le dossier", 27, "main", "CLOTURE")
    m.node("fin", "endEvent", "Collaborateur provisionné", 28, "main")

    # ------------------------------------------------------------ 5. Incident technique : réessayer ou abandonner
    m.gateway("incident", "", 8, "incident")
    m.mapping("noterIncident", "Noter l'incident au dossier", 9, "incident",
              "dernierIncident = variables.etapeEnEchec & ' : HTTP ' & $string(connectorResult.status) & ' ' & "
              "$substring($string(connectorResult.body), 0, 200)")
    m.wf_status("statutIncident", "Statut : incident", 10, "incident", "EN_INCIDENT")
    m.question("traiterIncident", "Traiter l'incident technique", 11, "incident", "incident-technique-onboarding",
               "Un appel à une application a échoué (champ « Dernier incident technique » de la fiche). Corriger la "
               "cause côté application, puis REESSAYER ; ou ABANDONNER : ce qui a été créé dans l'IAM et le SIRH est "
               "supprimé et la demande est clôturée.")
    m.gateway("actionIncident", "Action ?", 12, "incident", default="flow_actionIncident_aCompteIam")
    m.wf_status("statutReprise", "Statut : provisioning", 13, "incident", "EN_PROVISIONING")
    m.gateway("reprise", "Reprendre où ?", 14, "incident", default="flow_reprise_appelTicket")

    # ------------------------------------------------------------ 6. Compensation : supprimer ce qui a été créé
    m.expression("aCompteIam", "Compte IAM créé ?", 13, "compensation",
                 "$exists(variables.compteIam)", "compteIamCree")
    m.gateway("gwCompteIam", "", 14, "compensation", default="flow_gwCompteIam_compteIamTraite")
    m.call("supprimerIam", "Supprimer le compte IAM", 15, "compensation", ids["iam"], REQ_SUPPR_IAM)
    m.mapping("journalIam", "Journaliser", 16, "compensation",
              f"journalCompensation = {JOURNAL} & 'IAM supprimé : HTTP ' & $string(connectorResult.status)")
    m.gateway("compteIamTraite", "", 17, "compensation")
    m.expression("aFicheSirh", "Fiche SIRH créée ?", 18, "compensation",
                 "$exists(variables.matricule)", "ficheSirhCreee")
    m.gateway("gwFicheSirh", "", 19, "compensation", default="flow_gwFicheSirh_ficheSirhTraitee")
    m.call("supprimerSirh", "Supprimer la fiche SIRH", 20, "compensation", ids["sirh"], REQ_SUPPR_SIRH)
    m.mapping("journalSirh", "Journaliser", 21, "compensation",
              f"journalCompensation = {JOURNAL} & 'SIRH supprimé : HTTP ' & $string(connectorResult.status)")
    m.gateway("ficheSirhTraitee", "", 22, "compensation")
    m.wf_status("statutAnnule", "Statut : annulé", 23, "compensation", "ANNULE")
    m.dossier_status("clotureAnnule", "Clôturer le dossier", 24, "compensation", "CLOTURE")
    m.node("finAnnule", "endEvent", "Arrivée abandonnée", 25, "compensation")

    # ------------------------------------------------------------ Flux
    for a, b in [("debut", "statutValidation"), ("statutValidation", "validationManager"),
                 ("validationManager", "decisionManager"), ("statutRefuse", "clotureRefus"),
                 ("clotureRefus", "finRefus"), ("statutProvisioning", "razMatricule"), ("razMatricule", "razCompteIam"), ("razCompteIam", "razLogin"), ("razLogin", "appelSirh"), (fin_sirh, "appelIam"),
                 (fin_iam, "besoinValidationIt"), ("besoinValidationIt", "aiguillageIt"),
                 ("validationAcces", "niveauAcces"), (fin_groupe, "accesDecides"), ("accesDecides", "appelTicket"),
                 (fin_ticket, "statutProvisionne"), ("statutProvisionne", "clotureDossier"),
                 ("clotureDossier", "fin")]:
        m.flow(a, b)
    m.flow("decisionManager", "correction", "${decisionManager == 'CORRIGER'}")
    m.flow("decisionManager", "statutRefuse", "${decisionManager == 'REFUSER'}")
    m.flow("decisionManager", "statutProvisioning")  # APPROUVER (flux par défaut)
    m.flow("correction", "validationManager", low=ROW["haut"] - 30)
    m.flow("aiguillageIt", "validationAcces", "${validationItRequise == true}")
    m.flow("aiguillageIt", "accesDecides")
    m.flow("niveauAcces", "appelGroupe", "${niveauAcces != 'STANDARD'}")
    m.flow("niveauAcces", "accesDecides")
    # Incident
    for a, b in [("incident", "noterIncident"), ("noterIncident", "statutIncident"),
                 ("statutIncident", "traiterIncident"), ("traiterIncident", "actionIncident"),
                 ("statutReprise", "reprise")]:
        m.flow(a, b)
    m.flow("actionIncident", "statutReprise", "${actionIncident == 'REESSAYER'}")
    m.flow("actionIncident", "aCompteIam")  # ABANDONNER (flux par défaut)
    m.flow("reprise", "appelSirh", "${etapeEnEchec == 'SIRH'}", low=ROW["compensation"] + 130)
    m.flow("reprise", "appelIam", "${etapeEnEchec == 'IAM'}", low=ROW["compensation"] + 150)
    m.flow("reprise", "appelGroupe", "${etapeEnEchec == 'GROUPE'}", low=ROW["compensation"] + 170)
    m.flow("reprise", "appelTicket", low=ROW["compensation"] + 190)  # ITSM (flux par défaut)
    # Compensation
    for a, b in [("aCompteIam", "gwCompteIam"), ("supprimerIam", "journalIam"), ("journalIam", "compteIamTraite"),
                 ("compteIamTraite", "aFicheSirh"), ("aFicheSirh", "gwFicheSirh"), ("supprimerSirh", "journalSirh"),
                 ("journalSirh", "ficheSirhTraitee"), ("ficheSirhTraitee", "statutAnnule"),
                 ("statutAnnule", "clotureAnnule"), ("clotureAnnule", "finAnnule")]:
        m.flow(a, b)
    m.flow("gwCompteIam", "supprimerIam", "${compteIamCree == true}")
    m.flow("gwCompteIam", "compteIamTraite")
    m.flow("gwFicheSirh", "supprimerSirh", "${ficheSirhCreee == true}")
    m.flow("gwFicheSirh", "ficheSirhTraitee")
    return m


# ---------------------------------------------------------------- Mise en page
def layout(m):
    by_id = {n["id"]: n for n in m.nodes}

    def bounds(n):
        w, h = SIZE[n["kind"]]
        cx = X0 + n["col"] * COL_W + 60
        cy = ROW[n["row"]] + 40
        return cx - w / 2, cy - h / 2, w, h

    def anchor(n, side):
        x, y, w, h = bounds(n)
        return {"left": (x, y + h / 2), "right": (x + w, y + h / 2),
                "top": (x + w / 2, y), "bottom": (x + w / 2, y + h)}[side]

    def waypoints(f):
        s, t = by_id[f["source"]], by_id[f["target"]]
        if f["low"] is not None:  # contournement (au-dessus si la valeur est plus haute que la source)
            side = "top" if f["low"] < bounds(s)[1] else "bottom"
            sx, sy = anchor(s, side)
            tx, ty = anchor(t, side)
            return [(sx, sy), (sx, f["low"]), (tx, f["low"]), (tx, ty)]
        sx, sy = anchor(s, "right")
        tx, ty = anchor(t, "left")
        if abs(sy - ty) < 1 and tx > sx:
            return [(sx, sy), (tx, ty)]
        if s["kind"] == "gateway":
            sx, sy = anchor(s, "top" if ty < sy else "bottom")
            return [(sx, sy), (sx, ty), (tx, ty)]
        if t["kind"] == "gateway":
            tx, ty = anchor(t, "top" if sy < ty else "bottom")
            return [(sx, sy), (tx, sy), (tx, ty)]
        mid = (sx + tx) / 2
        return [(sx, sy), (mid, sy), (mid, ty), (tx, ty)]

    return bounds, waypoints


def attrs_xml(attrs):
    return "".join(f" {k}={quoteattr(v, {chr(10): '&#10;'})}" for k, v in attrs.items())


def render(m):
    bounds, waypoints = layout(m)
    out = ['<?xml version="1.0" encoding="UTF-8"?>',
           '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"',
           '             xmlns:flowable="http://flowable.org/bpmn"',
           '             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"',
           '             xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"',
           '             xmlns:omgdc="http://www.omg.org/spec/DD/20100524/DC"',
           '             xmlns:omgdi="http://www.omg.org/spec/DD/20100524/DI"',
           '             targetNamespace="http://myrmacta/exemples">',
           '  <!-- Généré par generer-bpmn.py : modifier le script plutôt que ce fichier. -->',
           f'  <process id="{PROCESS_ID}" name={quoteattr(PROCESS_NAME)} isExecutable="true">']
    for n in m.nodes:
        name = f" name={quoteattr(n['name'])}" if n["name"] else ""
        head = f'    <{n["tag"]} id="{n["id"]}"{name}{attrs_xml(n["attrs"])}'
        out.append(f"{head}>{n['body']}</{n['tag']}>" if n["body"] else f"{head}/>")
    for f in m.flows:
        head = f'    <sequenceFlow id="{f["id"]}" sourceRef="{f["source"]}" targetRef="{f["target"]}"'
        if f["condition"]:
            out.append(f'{head}><conditionExpression xsi:type="tFormalExpression">'
                       f'{escape(f["condition"])}</conditionExpression></sequenceFlow>')
        else:
            out.append(f"{head}/>")
    out.append("  </process>")
    out.append(f'  <bpmndi:BPMNDiagram id="diagram_{PROCESS_ID}">')
    out.append(f'    <bpmndi:BPMNPlane id="plane_{PROCESS_ID}" bpmnElement="{PROCESS_ID}">')
    for n in m.nodes:
        x, y, w, h = bounds(n)
        out.append(f'      <bpmndi:BPMNShape id="shape_{n["id"]}" bpmnElement="{n["id"]}">'
                   f'<omgdc:Bounds x="{x:g}" y="{y:g}" width="{w:g}" height="{h:g}"/></bpmndi:BPMNShape>')
    for f in m.flows:
        pts = "".join(f'<omgdi:waypoint x="{x:g}" y="{y:g}"/>' for x, y in waypoints(f))
        out.append(f'      <bpmndi:BPMNEdge id="edge_{f["id"]}" bpmnElement="{f["id"]}">{pts}</bpmndi:BPMNEdge>')
    out += ["    </bpmndi:BPMNPlane>", "  </bpmndi:BPMNDiagram>", "</definitions>", ""]
    return "\n".join(out)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    for key in PLACEHOLDERS:
        parser.add_argument(f"--{key}", default=PLACEHOLDERS[key], help=f"identifiant du connecteur {key.upper()}")
    args = parser.parse_args()
    model = build({k: getattr(args, k) for k in PLACEHOLDERS})
    fichier = Path(__file__).parent / "onboarding-collaborateur.bpmn20.xml"
    fichier.write_text(render(model), encoding="utf-8", newline="\n")
    print(f"{fichier.name} : {len(model.nodes)} elements, {len(model.flows)} flux")
