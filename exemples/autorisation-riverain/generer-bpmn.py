"""Génère le BPMN du flux « autorisation riverain » (sémantique + diagramme BPMNDI).

Le BPMN est écrit à la main plutôt qu'avec un « parcours » Myrmacta : le parcours ne sait pas
poser un minuteur en bordure d'une tâche (délai de 30 jours qui court pendant que l'agent instruit).
Le script calcule aussi la mise en page, pour que le diagramme s'affiche lisiblement dans
l'éditeur BPMN de Myrmacta.

Trois variantes sont produites :
- autorisation-riverain.bpmn20.xml : conception cible, le demandeur complète sa demande sur le
  portail externe (exige APP_PORTAL_ENABLED=true sur l'instance) ;
- autorisation-riverain-sans-portail.bpmn20.xml : le demandeur est prévenu par e-mail et l'agent
  encode les compléments reçus (déployable sur une instance sans portail) ;
- autorisation-riverain-preverification.bpmn20.xml : la variante sans portail, précédée d'une
  pré-vérification IA du dossier (TODO §106 ; exige APP_PRECHECK_ENABLED=true sur l'instance).

Usage : python generer-bpmn.py   (écrit les deux fichiers à côté du script)
"""

from pathlib import Path
from xml.sax.saxutils import escape, quoteattr

PROCESS_ID = "autorisationRiverain"
PROCESS_NAME = "Autorisation riverain (véhicule)"
TEMPLATE_KEY = "decision-autorisation-riverain"
GABARIT_INFORMATIONS = "demande-informations-riverain"  # gabarit de notification (config/gabarit-demande-informations.json)
DELAI = "P30D"  # durée ISO 8601 — PT5M pour une recette accélérée

COL_W, X0 = 170, 80
ROW = {"tacite": 60, "autorisee": 170, "main": 290, "refusee": 410, "infos": 530, "sansReponse": 660}
SIZE = {"task": (120, 80), "gateway": (50, 50), "event": (36, 36)}

SIGNATURE = "\n\nCordialement,\nLe service des autorisations riverains"
MAIL_FIN = ("\n\nVous trouverez en pièce jointe le document de décision, qui reprend l'ensemble des "
            "informations de votre demande." + SIGNATURE)
PIECE_JOINTE = "decision-autorisation-riverain.pdf"

# TODO §106 — pré-vérification IA (variante « preverification »). Table de démonstration code postal -> commune : en
# production, une table de décision DMN ou un référentiel tient ce rôle ; un code absent de la table n'est pas jugé.
COMMUNES = {"1000": "bruxelles", "1020": "bruxelles", "1030": "schaerbeek", "1040": "etterbeek", "1050": "ixelles",
            "1060": "saint-gilles", "1070": "anderlecht", "1080": "molenbeek-saint-jean", "1180": "uccle",
            "4000": "liège", "5000": "namur", "6000": "charleroi", "7000": "mons", "9000": "gand"}
CONTROLE_CODE_POSTAL = (
    "($c := $lookup({" + ", ".join(f'"{k}": "{v}"' for k, v in COMMUNES.items()) + "}, "
    "$trim($string(dossier.fields.codePostal))); "
    "$exists($c) and $c != $lowercase($trim($string(dossier.fields.commune))))")
PRECHECK = {
    "precheckPieces": "preuveDomicile : obligatoire, lire\n"
                      "certificatImmatriculation : obligatoire, lire\n"
                      "pieceIdentite : obligatoire",
    "precheckFields": "nom, prenom, adresseRue, adresseNumero, codePostal, commune, paysImmatriculation, plaque",
    "precheckQuestions": "domicileAuNom = La preuve de domicile est-elle au nom du demandeur ?\n"
                         "adresseConforme = L'adresse de la preuve de domicile est-elle celle du formulaire ?\n"
                         "plaqueConforme = La plaque du certificat d'immatriculation est-elle celle du formulaire ?",
    "precheckRules": "variable:codePostalIncoherent est vrai => INCOHERENCE : "
                     "Code postal incohérent avec la commune déclarée.",
    "precheckResult": "preverification",
}


class Model:
    def __init__(self):
        self.nodes, self.flows = [], []

    # ------------------------------------------------------------ éléments
    def node(self, node_id, tag, name, col, row, attrs=None, body=""):
        kind = ("gateway" if tag == "exclusiveGateway" else
                "event" if tag.endswith("Event") else "task")
        self.nodes.append(dict(id=node_id, tag=tag, name=name, col=col, row=row,
                               attrs=attrs or {}, body=body, kind=kind))

    def service(self, node_id, name, col, row, delegate, **params):
        attrs = {"flowable:delegateExpression": "${" + delegate + "}"}
        attrs.update({f"flowable:{k}": v for k, v in params.items()})
        self.node(node_id, "serviceTask", name, col, row, attrs)

    def wf_status(self, node_id, name, col, row, code):
        self.service(node_id, name, col, row, "updateWorkflowLinkStatusTask", targetWorkflowStatus=code)

    def dossier_status(self, node_id, name, col, row, code):
        self.service(node_id, name, col, row, "updateDossierStatusTask", targetDossierStatus=code)

    def expression(self, node_id, name, col, row, formula, result):
        self.service(node_id, name, col, row, "businessExpressionTask", formula=formula, formulaResult=result)

    def email(self, node_id, name, col, row, subject=None, body=None, decision=False, template=None):
        # Destinataire lu dans le dossier (champ obligatoire « email », relu au moment de l'envoi — TODO §93).
        # Asynchrone : une adresse mal saisie crée un incident rejouable au lieu de bloquer l'agent qui
        # complète la tâche précédente (docs/guides/GUIDE-CONCEPTION-BPMN.md §6c).
        # E-mails de décision : le PDF, généré puis joint au dossier en amont, part en pièce jointe (issue #183).
        # Avec `template`, l'objet et le corps viennent d'un gabarit de notification (issue #19, §123) : ils ne se
        # combinent pas avec notificationSubject / notificationBody.
        attachment = ({"notificationAttachment": "variable:generatedDocumentId",
                       "notificationAttachmentName": PIECE_JOINTE} if decision else {})
        content = ({"notificationTemplate": template} if template
                   else {"notificationSubject": subject, "notificationBody": body})
        self.service(node_id, name, col, row, "notificationDispatchTask", **{"async": "true"},
                     notificationChannels="EMAIL", notificationTo="dossierField:email", **content, **attachment)

    def timer(self, node_id, name, host, top):
        self.node(node_id, "boundaryEvent", name, None, None,
                  {"attachedToRef": host, "cancelActivity": "true"},
                  body=f"<timerEventDefinition><timeDuration>{DELAI}</timeDuration></timerEventDefinition>")
        self.nodes[-1]["top"] = top

    def flow(self, source, target, condition=None, flow_id=None, low=None):
        self.flows.append(dict(id=flow_id or f"flow_{source}_{target}", source=source, target=target,
                               condition=condition, low=low))


def build(portail, preverification=False):
    m = Model()
    o = 2 if preverification else 0  # colonnes des deux étapes de pré-vérification, avant l'instruction

    # ------------------------------------------------------------ 1. Instruction
    m.node("debut", "startEvent", "Demande introduite", 1, "main")
    m.wf_status("statutEnInstruction", "Statut : en instruction", 2, "main", "EN_INSTRUCTION")
    if preverification:
        m.expression("controleCodePostal", "Contrôle : code postal et commune", 3, "main",
                     CONTROLE_CODE_POSTAL, "codePostalIncoherent")
        # Asynchrone (exigé au déploiement) : l'appel au modèle ne tient pas dans une transaction ; une panne du
        # fournisseur devient un incident rejouable.
        m.service("preverification", "Pré-vérification IA", 4, "main", "aiPrecheckTask",
                  **{"async": "true"}, **PRECHECK)
    m.node("instruction", "userTask", "Instruire la demande", 3 + o, "main",
           {"flowable:formKey": "user-question:instruction-autorisation-riverain"},
           body="<documentation>" + ("Commencer par le panneau « Pré-vérification » de la fiche : accepter "
                "ou écarter chaque constat (ceux marqués « IA » sont indicatifs). " if preverification else "")
                + "Vérifier la demande, puis autoriser, refuser ou demander des "
                "informations complémentaires. Une seconde demande pour le même véhicule est "
                "refusée à la création (clé d'unicité). Sans décision dans les 30 jours, l'autorisation est "
                "accordée automatiquement.</documentation>")
    m.timer("delaiInstruction", "30 jours écoulés", "instruction", top=True)
    m.node("aiguillageDecision", "exclusiveGateway", "Décision ?", 4 + o, "main",
           {"default": "flow_aiguillageDecision_statutEnAttente"})
    m.expression("issueTacite", "Issue : autorisée tacitement", 5 + o, "tacite",
                 '"AUTORISEE_TACITEMENT"', "issueDecision")
    m.expression("issueAutorisee", "Issue : autorisée", 5 + o, "autorisee", '"AUTORISEE"', "issueDecision")
    m.expression("issueRefusee", "Issue : refusée", 5 + o, "refusee", '"REFUSEE"', "issueDecision")

    # ------------------------------------------------------------ 2. Demande de compléments
    m.wf_status("statutEnAttente", "Statut : en attente d'informations", 5 + o, "infos", "EN_ATTENTE_INFOS")
    m.dossier_status("dossierSuspendu", "Dossier suspendu", 6 + o, "infos", "SUSPENDU")
    if portail:
        c = 7 + o
        m.node("complement", "userTask", "Compléments du demandeur (portail)", c, "infos",
               {"flowable:formKey": "user-question:complement-autorisation-riverain",
                "flowable:portalRecipient": "dossierField:email",
                "flowable:portalLinkTtlDays": "30"},
               body="<documentation>Le gestionnaire a besoin d'informations complémentaires pour instruire "
                    "votre demande d'autorisation riverain. Répondez ci-dessous et joignez les pièces utiles "
                    "(certificat d'immatriculation, preuve de domicile). Sans réponse dans les 30 jours, la "
                    "demande sera refusée.</documentation>")
        m.flow("dossierSuspendu", "complement")
    else:
        # Gabarit « demande-informations-riverain » (issue #19, §123) : il cite la motivation saisie à l'instruction
        # (variable `motivation`, obligatoire dans le questionnaire d'instruction, donc présente à cette étape).
        m.email("mailDemandeInfos", "E-mail : informations demandées", 7 + o, "infos",
                template=GABARIT_INFORMATIONS)
        c = 8 + o
        m.node("complement", "userTask", "Encoder les compléments reçus", c, "infos",
               {"flowable:formKey": "user-question:complement-recu-autorisation-riverain"},
               body="<documentation>Le demandeur a reçu par e-mail la motivation saisie à l'instruction (les "
                    "informations attendues). Encoder sa réponse et ajouter les pièces reçues au dossier. "
                    "Sans réponse dans les 30 jours, la demande est refusée automatiquement.</documentation>")
        m.flow("dossierSuspendu", "mailDemandeInfos")
        m.flow("mailDemandeInfos", "complement")
    m.timer("delaiComplement", "30 jours sans réponse", "complement", top=False)
    m.dossier_status("dossierRepris", "Dossier repris", c + 1, "infos", "EN_COURS")
    m.expression("issueSansReponse", "Issue : refusée sans réponse", c + 1, "sansReponse",
                 '"REFUSEE_SANS_REPONSE"', "issueDecision")

    # ------------------------------------------------------------ 3. Préparation du document
    k = c + 2
    m.node("decisionPrise", "exclusiveGateway", "", k, "main")
    m.expression("prepReference", "Référence du dossier", k + 1, "main", "dossier.reference", "referenceDossier")
    m.expression("prepDate", "Date de décision", k + 2, "main", "$substring(now, 0, 10)", "dateDecision")
    m.expression("prepTelephone", "Téléphone (défaut)", k + 3, "main",
                 '$exists(dossier.fields.telephone) and $length($trim($string(dossier.fields.telephone))) > 0'
                 ' ? dossier.fields.telephone : "Non communiqué"', "docTelephone")
    m.expression("prepPieces", "Pièces jointes à la demande", k + 4, "main",
                 '"Certificat d\'immatriculation : " & ($exists(dossier.fields.certificatImmatriculation) ? '
                 '"fourni" : "non fourni") & " — preuve de domicile : " & '
                 '($exists(dossier.fields.preuveDomicile) ? "fournie" : "non fournie")', "docPieces")
    m.expression("prepComplements", "Compléments (défaut)", k + 5, "main",
                 '$exists(variables.reponseComplement) ? variables.reponseComplement : "Aucun"',
                 "docComplements")
    m.expression("prepMotivation", "Motivation", k + 6, "main",
                 'variables.issueDecision = "AUTORISEE_TACITEMENT" ? "Absence de décision dans le délai de 30 '
                 'jours" : variables.issueDecision = "REFUSEE_SANS_REPONSE" ? "Informations complémentaires non '
                 'fournies dans le délai de 30 jours" : ($exists(variables.motivation) ? variables.motivation : "-")',
                 "docMotivation")
    m.service("enregistrerDecision", "Enregistrer la décision au dossier", k + 7, "main", "fieldMappingTask",
              fieldMapping="decision = variables.issueDecision\n"
                           "dateDecision = variables.dateDecision\n"
                           "motifDecision = variables.docMotivation")
    m.service("genererDecision", "Générer le document de décision", k + 8, "main", "documentGenerationTask",
              documentType=TEMPLATE_KEY)
    m.service("joindreDecision", "Joindre le document au dossier", k + 9, "main", "attachGeneratedDocumentTask")

    # ------------------------------------------------------------ 4. Information du demandeur
    m.node("aiguillageMail", "exclusiveGateway", "Accordée ?", k + 10, "main",
           {"default": "flow_aiguillageMail_mailRefus"})
    m.email("mailAutorisation", "E-mail : autorisation accordée", k + 11, "autorisee",
            "Votre demande d'autorisation riverain est accordée",
            "Bonjour,\n\nVotre demande d'autorisation riverain est accordée." + MAIL_FIN, decision=True)
    m.email("mailRefus", "E-mail : autorisation refusée", k + 11, "refusee",
            "Votre demande d'autorisation riverain est refusée",
            "Bonjour,\n\nVotre demande d'autorisation riverain est refusée. "
            "Les motifs figurent dans le document de décision." + MAIL_FIN, decision=True)
    m.node("mailEnvoye", "exclusiveGateway", "", k + 12, "main")

    # ------------------------------------------------------------ 5. Clôture
    m.node("aiguillageStatut", "exclusiveGateway", "Issue ?", k + 13, "main",
           {"default": "flow_aiguillageStatut_statutRefusee"})
    m.wf_status("statutAutorisee", "Statut : autorisée", k + 14, "autorisee", "AUTORISEE")
    m.wf_status("statutTacite", "Statut : autorisée tacitement", k + 14, "tacite", "AUTORISEE_TACITEMENT")
    m.wf_status("statutRefusee", "Statut : refusée", k + 14, "refusee", "REFUSEE")
    m.node("statutPose", "exclusiveGateway", "", k + 15, "main")
    m.dossier_status("clotureDossier", "Clôturer le dossier", k + 16, "main", "CLOTURE")
    m.node("fin", "endEvent", "Demande traitée", k + 17, "main")

    # ------------------------------------------------------------ Flux
    entree = ["statutEnInstruction", "controleCodePostal", "preverification", "instruction"] if preverification \
        else ["statutEnInstruction", "instruction"]
    for a, b in [("debut", "statutEnInstruction"), *zip(entree, entree[1:]), ("instruction", "aiguillageDecision"),
                 ("delaiInstruction", "issueTacite")]:
        m.flow(a, b)
    m.flow("aiguillageDecision", "issueAutorisee", "${decisionAgent == 'AUTORISER'}")
    m.flow("aiguillageDecision", "issueRefusee", "${decisionAgent == 'REFUSER'}")
    m.flow("aiguillageDecision", "statutEnAttente")  # DEMANDER_INFOS (flux par défaut)
    m.flow("statutEnAttente", "dossierSuspendu")
    m.flow("complement", "dossierRepris")
    m.flow("dossierRepris", "statutEnInstruction", low=ROW["sansReponse"] + 140)  # le délai repart
    m.flow("delaiComplement", "issueSansReponse")
    for issue in ("issueTacite", "issueAutorisee", "issueRefusee", "issueSansReponse"):
        m.flow(issue, "decisionPrise")
    chain = ["decisionPrise", "prepReference", "prepDate", "prepTelephone", "prepPieces", "prepComplements",
             "prepMotivation", "enregistrerDecision", "genererDecision", "joindreDecision", "aiguillageMail"]
    for a, b in zip(chain, chain[1:]):
        m.flow(a, b)
    m.flow("aiguillageMail", "mailAutorisation", "${issueDecision == 'AUTORISEE'}")
    m.flow("aiguillageMail", "mailAutorisation", "${issueDecision == 'AUTORISEE_TACITEMENT'}",
           flow_id="flow_aiguillageMail_mailAutorisation_tacite")
    m.flow("aiguillageMail", "mailRefus")
    for a, b in [("mailAutorisation", "mailEnvoye"), ("mailRefus", "mailEnvoye"),
                 ("mailEnvoye", "aiguillageStatut")]:
        m.flow(a, b)
    m.flow("aiguillageStatut", "statutAutorisee", "${issueDecision == 'AUTORISEE'}")
    m.flow("aiguillageStatut", "statutTacite", "${issueDecision == 'AUTORISEE_TACITEMENT'}")
    m.flow("aiguillageStatut", "statutRefusee")
    for s in ("statutAutorisee", "statutTacite", "statutRefusee"):
        m.flow(s, "statutPose")
    m.flow("statutPose", "clotureDossier")
    m.flow("clotureDossier", "fin")
    return m


# ---------------------------------------------------------------- Mise en page
def layout(m):
    by_id = {n["id"]: n for n in m.nodes}

    def bounds(n):
        w, h = SIZE[n["kind"]]
        if n["tag"] == "boundaryEvent":
            hx, hy, hw, hh = bounds(by_id[n["attrs"]["attachedToRef"]])
            return hx + hw - w - 10, (hy - h / 2) if n["top"] else (hy + hh - h / 2), w, h
        cx = X0 + n["col"] * COL_W + 60
        cy = ROW[n["row"]] + 40
        return cx - w / 2, cy - h / 2, w, h

    def anchor(n, side):
        x, y, w, h = bounds(n)
        return {"left": (x, y + h / 2), "right": (x + w, y + h / 2),
                "top": (x + w / 2, y), "bottom": (x + w / 2, y + h)}[side]

    def waypoints(f):
        s, t = by_id[f["source"]], by_id[f["target"]]
        if f["low"] is not None:  # contournement par le bas
            sx, sy = anchor(s, "bottom")
            tx, ty = anchor(t, "bottom")
            return [(sx, sy), (sx, f["low"]), (tx, f["low"]), (tx, ty)]
        if s["tag"] == "boundaryEvent":
            sx, sy = anchor(s, "top" if s["top"] else "bottom")
            tx, ty = anchor(t, "left")
            return [(sx, sy), (sx, ty), (tx, ty)]
        sx, sy = anchor(s, "right")
        tx, ty = anchor(t, "left")
        if abs(sy - ty) < 1:
            return [(sx, sy), (tx, ty)]
        if s["kind"] == "gateway":  # sortie verticale d'une passerelle
            sx, sy = anchor(s, "top" if ty < sy else "bottom")
            return [(sx, sy), (sx, ty), (tx, ty)]
        if t["kind"] == "gateway":  # entrée verticale dans une passerelle
            tx, ty = anchor(t, "top" if sy < ty else "bottom")
            return [(sx, sy), (tx, sy), (tx, ty)]
        mid = (sx + tx) / 2
        return [(sx, sy), (mid, sy), (mid, ty), (tx, ty)]

    return bounds, waypoints


# ---------------------------------------------------------------- Écriture
def attrs_xml(attrs):
    return "".join(f" {k}={quoteattr(v)}" for k, v in attrs.items())


def render(m, variante):
    bounds, waypoints = layout(m)
    out = ['<?xml version="1.0" encoding="UTF-8"?>',
           '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"',
           '             xmlns:flowable="http://flowable.org/bpmn"',
           '             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"',
           '             xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"',
           '             xmlns:omgdc="http://www.omg.org/spec/DD/20100524/DC"',
           '             xmlns:omgdi="http://www.omg.org/spec/DD/20100524/DI"',
           '             targetNamespace="http://myrmacta/exemples">',
           f'  <!-- Variante {variante}. Généré par generer-bpmn.py : modifier le script plutôt que ce fichier. -->',
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
    here = Path(__file__).parent
    for portail, preverification, fichier, variante in [
            (True, False, "autorisation-riverain.bpmn20.xml", "avec portail"),
            (False, False, "autorisation-riverain-sans-portail.bpmn20.xml", "sans portail"),
            (False, True, "autorisation-riverain-preverification.bpmn20.xml", "sans portail, avec pré-vérification IA")]:
        model = build(portail, preverification)
        (here / fichier).write_text(render(model, variante), encoding="utf-8", newline="\n")
        print(f"{fichier} : {len(model.nodes)} elements, {len(model.flows)} flux")
