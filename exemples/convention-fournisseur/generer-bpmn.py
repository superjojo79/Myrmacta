"""Génère le BPMN du flux « convention fournisseur » (sémantique + diagramme BPMNDI).

Exemple de la signature électronique (TODO §148, D-VIDEO-SIGN) : la convention est relue, générée en PDF, jointe au
dossier, puis envoyée en signature au représentant du fournisseur. Le processus attend le retour du fournisseur de
signature (message signatureCompleted) et route sur signatureOutcome.

Le signataire est externe : il reçoit par e-mail un lien vers le portail, qui le mène chez le fournisseur de signature.
Le déploiement exige donc un fournisseur de signature (APP_SIGNATURE_PROVIDER) et le portail (APP_PORTAL_ENABLED=true).

Usage : python generer-bpmn.py   (écrit convention-fournisseur.bpmn20.xml à côté du script)
"""

from pathlib import Path
from xml.sax.saxutils import quoteattr

PROCESS_ID = "conventionFournisseur"
PROCESS_NAME = "Convention fournisseur"
TEMPLATE_KEY = "convention-fournisseur"  # modèle de document (config/modele-document.json)

COL_W, X0, MAIN, LOW = 160, 60, 120, 270
SIZE = {"task": (120, 80), "gateway": (50, 50), "event": (36, 36)}


def status(target: str) -> str:
    return f'flowable:delegateExpression="${{updateWorkflowLinkStatusTask}}" flowable:targetWorkflowStatus="{target}"'


def formula(expression: str, result: str) -> str:
    return (f'flowable:delegateExpression="${{businessExpressionTask}}" flowable:formula={quoteattr(expression)} '
            f'flowable:formulaResult="{result}"')


# (id, kind, name, attributes, inner XML, column, row)
NODES = [
    ("debut", "startEvent", "Convention à établir", "", "", 0, MAIN),
    ("statutPreparation", "serviceTask", "Statut : en préparation", status("EN_PREPARATION"), "", 1, MAIN),
    ("relecture", "userTask", "Relire la convention", "",
     "<documentation>Vérifier le fournisseur, l'objet et le montant, puis valider : la convention est générée et "
     "part en signature chez le représentant du fournisseur.</documentation>", 2, MAIN),
    ("prepReference", "serviceTask", "Référence du dossier", formula("dossier.reference", "referenceDossier"), "", 3, MAIN),
    ("prepDate", "serviceTask", "Date de la convention", formula("$substring(now, 0, 10)", "dateConvention"), "", 4, MAIN),
    ("genererConvention", "serviceTask", "Générer la convention",
     f'flowable:delegateExpression="${{documentGenerationTask}}" flowable:documentType="{TEMPLATE_KEY}"', "", 5, MAIN),
    ("joindreConvention", "serviceTask", "Joindre la convention au dossier",
     'flowable:delegateExpression="${attachGeneratedDocumentTask}"', "", 6, MAIN),
    ("statutSignature", "serviceTask", "Statut : en signature", status("EN_SIGNATURE"), "", 7, MAIN),
    ("demanderSignature", "serviceTask", "Envoyer en signature",
     'flowable:delegateExpression="${signatureRequestTask}" flowable:signatureDocument="variable:generatedDocumentId" '
     'flowable:signatureMode="PARALLEL" flowable:signatureDeadlineDays="14"',
     '<extensionElements><flowable:signer source="dossierField:signataireEmail" kind="EXTERNAL" required="true" '
     'name="Fournisseur"/></extensionElements>', 8, MAIN),
    ("attenteSignature", "intermediateCatchEvent", "Signature reçue", "",
     '<messageEventDefinition messageRef="signatureCompletedMsg"/>', 9, MAIN),
    ("signee", "exclusiveGateway", "Signée ?", "", "", 10, MAIN),
    ("statutSignee", "serviceTask", "Statut : signée", status("SIGNEE"), "", 11, MAIN),
    ("finSignee", "endEvent", "Convention signée", "", "", 12, MAIN),
    ("statutNonSignee", "serviceTask", "Statut : non signée", status("NON_SIGNEE"), "", 11, LOW),
    ("finNonSignee", "endEvent", "Convention non signée", "", "", 12, LOW),
]

FLOWS = [
    ("f1", "debut", "statutPreparation", None, None),
    ("f2", "statutPreparation", "relecture", None, None),
    ("f3", "relecture", "prepReference", None, None),
    ("f4", "prepReference", "prepDate", None, None),
    ("f5", "prepDate", "genererConvention", None, None),
    ("f6", "genererConvention", "joindreConvention", None, None),
    ("f7", "joindreConvention", "statutSignature", None, None),
    ("f8", "statutSignature", "demanderSignature", None, None),
    ("f9", "demanderSignature", "attenteSignature", None, None),
    ("f10", "attenteSignature", "signee", None, None),
    ("fOui", "signee", "statutSignee", "Oui", "${signatureOutcome == 'SIGNED'}"),
    ("fNon", "signee", "statutNonSignee", "Non", "${signatureOutcome != 'SIGNED'}"),
    ("f11", "statutSignee", "finSignee", None, None),
    ("f12", "statutNonSignee", "finNonSignee", None, None),
]


def kind_of(element: str) -> str:
    if element.endswith("Event"):
        return "event"
    return "gateway" if element.endswith("Gateway") else "task"


def bounds(node) -> tuple[int, int, int, int]:
    _, element, _, _, _, column, row = node
    w, h = SIZE[kind_of(element)]
    cx = X0 + column * COL_W + SIZE["task"][0] // 2
    return cx - w // 2, row - h // 2, w, h


def semantic() -> list[str]:
    lines = []
    for node in NODES:
        node_id, element, name, attributes, inner, _, _ = node
        head = f'<{element} id="{node_id}" name={quoteattr(name)}' + (f" {attributes}" if attributes else "")
        lines.append(f"    {head}>{inner}</{element}>" if inner else f"    {head}/>")
    for flow_id, source, target, name, condition in FLOWS:
        label = f" name={quoteattr(name)}" if name else ""
        if condition:
            lines.append(f'    <sequenceFlow id="{flow_id}"{label} sourceRef="{source}" targetRef="{target}">'
                         f'<conditionExpression xsi:type="tFormalExpression">{condition.replace("&", "&amp;")}'
                         f"</conditionExpression></sequenceFlow>")
        else:
            lines.append(f'    <sequenceFlow id="{flow_id}"{label} sourceRef="{source}" targetRef="{target}"/>')
    return lines


def diagram() -> list[str]:
    by_id = {node[0]: node for node in NODES}
    lines = []
    for node in NODES:
        x, y, w, h = bounds(node)
        lines.append(f'      <bpmndi:BPMNShape id="{node[0]}_di" bpmnElement="{node[0]}">'
                     f'<omgdc:Bounds x="{x}" y="{y}" width="{w}" height="{h}"/></bpmndi:BPMNShape>')
    for flow_id, source, target, _, _ in FLOWS:
        sx, sy, sw, sh = bounds(by_id[source])
        tx, ty, tw, th = bounds(by_id[target])
        if sy + sh // 2 == ty + th // 2:
            points = [(sx + sw, sy + sh // 2), (tx, ty + th // 2)]
        else:  # vers la rangée du bas : sortie par le bas de la passerelle, puis vers la droite
            points = [(sx + sw // 2, sy + sh), (sx + sw // 2, ty + th // 2), (tx, ty + th // 2)]
        waypoints = "".join(f'<omgdi:waypoint x="{x}" y="{y}"/>' for x, y in points)
        lines.append(f'      <bpmndi:BPMNEdge id="{flow_id}_di" bpmnElement="{flow_id}">{waypoints}</bpmndi:BPMNEdge>')
    return lines


def document() -> str:
    return "\n".join([
        '<?xml version="1.0" encoding="UTF-8"?>',
        '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"',
        '             xmlns:flowable="http://flowable.org/bpmn"',
        '             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"',
        '             xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"',
        '             xmlns:omgdc="http://www.omg.org/spec/DD/20100524/DC"',
        '             xmlns:omgdi="http://www.omg.org/spec/DD/20100524/DI"',
        '             targetNamespace="http://myrmacta/exemples">',
        "  <!-- Généré par generer-bpmn.py : modifier le script plutôt que ce fichier. -->",
        '  <message id="signatureCompletedMsg" name="signatureCompleted"/>',
        f'  <process id="{PROCESS_ID}" name={quoteattr(PROCESS_NAME)} isExecutable="true">',
        *semantic(),
        "  </process>",
        f'  <bpmndi:BPMNDiagram id="{PROCESS_ID}_diagram">',
        f'    <bpmndi:BPMNPlane id="{PROCESS_ID}_plane" bpmnElement="{PROCESS_ID}">',
        *diagram(),
        "    </bpmndi:BPMNPlane>",
        "  </bpmndi:BPMNDiagram>",
        "</definitions>",
        "",
    ])


if __name__ == "__main__":
    target = Path(__file__).with_name("convention-fournisseur.bpmn20.xml")
    target.write_text(document(), encoding="utf-8", newline="\n")
    print(f"écrit : {target}")
