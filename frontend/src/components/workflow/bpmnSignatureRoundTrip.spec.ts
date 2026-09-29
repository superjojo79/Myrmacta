import { BpmnModdle } from 'bpmn-moddle'
import { describe, expect, it } from 'vitest'

// TODO §72 (S-VT-3) — l'éditeur n'a aucune UI dédiée au bloc signature (réservée à LC1) mais ne doit
// pas le détruire : bpmn-js repose sur bpmn-moddle, qui doit ré-émettre attributs et éléments
// `flowable:*` inconnus à l'identique.
const XML = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn"
  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" targetNamespace="urn:test">
  <process id="p" isExecutable="true">
    <startEvent id="s"/>
    <serviceTask id="sign" name="Signer" flowable:delegateExpression="\${signatureRequestTask}"
      flowable:signatureDocument="variable:contrat" flowable:signatureMode="SEQUENTIAL" flowable:signatureDeadlineDays="14">
      <extensionElements>
        <flowable:signer source="variable:clientEmail" kind="EXTERNAL" required="true" name="Client"/>
        <flowable:signer source="variable:approverId" kind="INTERNAL" required="false" name="Directeur"/>
      </extensionElements>
    </serviceTask>
    <intermediateCatchEvent id="wait"><messageEventDefinition messageRef="m"/></intermediateCatchEvent>
  </process>
  <message id="m" name="signatureCompleted"/>
</definitions>`

describe('BPMN signature block round trip (S-VT-3)', () => {
  it('re-emits the flowable:signature* attributes and flowable:signer elements untouched', async () => {
    const moddle = new BpmnModdle()
    const { rootElement } = await moddle.fromXML(XML)
    const { xml } = await moddle.toXML(rootElement, { format: true })

    expect(xml).toContain('flowable:signatureDocument="variable:contrat"')
    expect(xml).toContain('flowable:signatureMode="SEQUENTIAL"')
    expect(xml).toContain('flowable:signatureDeadlineDays="14"')
    expect(xml).toContain('flowable:delegateExpression="${signatureRequestTask}"')
    expect(xml.match(/<flowable:signer /g)).toHaveLength(2)
    expect(xml).toContain('source="variable:approverId"')
    expect(xml).toContain('kind="INTERNAL"')
    expect(xml).toContain('required="false"')
  })
})
