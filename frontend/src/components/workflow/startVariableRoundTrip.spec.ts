import { BpmnModdle } from 'bpmn-moddle'
import { describe, expect, it } from 'vitest'

// TODO §111 — l'éditeur n'a pas encore de champ pour créer une variable déclarée, mais une ouverture
// puis un enregistrement ne doit pas l'effacer du processus.
const XML = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn"
  targetNamespace="urn:test">
  <process id="p" isExecutable="true">
    <extensionElements>
      <flowable:startVariable name="motif" label="Motif de la demande" required="true"/>
      <flowable:startVariable name="note" label="Note" required="false"/>
    </extensionElements>
    <startEvent id="s"/>
    <endEvent id="e"/>
    <sequenceFlow id="f" sourceRef="s" targetRef="e"/>
  </process>
</definitions>`

describe('declared start variables round trip (TODO §111)', () => {
  it('re-emits flowable:startVariable on the process', async () => {
    const moddle = new BpmnModdle()
    const { rootElement } = await moddle.fromXML(XML)
    const { xml } = await moddle.toXML(rootElement, { format: true })

    expect(xml.match(/<flowable:startVariable /g)).toHaveLength(2)
    expect(xml).toContain('name="motif"')
    expect(xml).toContain('label="Motif de la demande"')
    expect(xml).toContain('required="true"')
    expect(xml).toContain('name="note"')
    expect(xml).toContain('required="false"')
  })
})
