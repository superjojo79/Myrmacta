import { BpmnModdle } from 'bpmn-moddle'
import { describe, expect, it } from 'vitest'
import {
  ensureFlowableNamespace,
  readSigners,
  readValue,
  toggleMulti,
  writeSigners,
  writeValue,
  type BlockEditorServices,
  type Moddled,
} from './blockValues'
import { TEST_BLOCKS } from './testCatalog'

const HEAD = '<?xml version="1.0" encoding="UTF-8"?>\n<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"'
const NO_NS = `${HEAD} targetNamespace="urn:t"><process id="p" isExecutable="true">
  <serviceTask id="t"/><userTask id="u" flowable:formKey="x"/></process></definitions>`.replace(
  ' flowable:formKey="x"',
  '',
)
const WITH_NS = NO_NS.replace('targetNamespace', 'xmlns:flowable="http://flowable.org/bpmn" targetNamespace')

async function load(xml: string) {
  const moddle = new BpmnModdle()
  const { rootElement } = await moddle.fromXML(xml)
  const process = (rootElement as unknown as { rootElements: Moddled[] }).rootElements[0]
  const flowElements = process.get('flowElements') as Moddled[]
  const services: BlockEditorServices = {
    // Équivalent minimal de modeling.updateModdleProperties : même sémantique de set() sur le modèle.
    modeling: {
      updateModdleProperties: (_el, mo, props) => {
        for (const [k, v] of Object.entries(props)) (mo as { set(k: string, v: unknown): void }).set(k, v)
      },
    },
    moddle: moddle as unknown as BlockEditorServices['moddle'],
    bpmnFactory: {
      create: (type, attrs) => moddle.create(type, attrs) as unknown as ReturnType<BlockEditorServices['bpmnFactory']['create']>,
    },
  }
  const toXml = async () => (await moddle.toXML(rootElement, { format: true })).xml
  return { task: flowElements[0], user: flowElements[1], services, toXml }
}

const block = (id: string) => TEST_BLOCKS.find((b) => b.id === id)!
const param = (id: string, name: string) => block(id).parameters.find((p) => p.name === name)!

describe('blockValues', () => {
  it('écrit puis relit un attribut ; vider retire l’attribut (S-UI-2, S-UI-3)', async () => {
    const { task, services, toXml } = await load(WITH_NS)
    const b = block('set-dossier-status')
    const p = param('set-dossier-status', 'targetDossierStatus')
    writeValue(services, task, task, b, p, 'CLOTURE')
    expect(readValue(task, b, p)).toBe('CLOTURE')
    expect(await toXml()).toContain('flowable:targetDossierStatus="CLOTURE"')
    writeValue(services, task, task, b, p, '')
    expect(readValue(task, b, p)).toBe('')
    expect(await toXml()).not.toContain('targetDossierStatus')
  })

  it('déclare xmlns:flowable quand le diagramme importé ne l’a pas (S-UI-7)', async () => {
    const { task, services, toXml } = await load(NO_NS)
    const b = block('set-dossier-status')
    writeValue(services, task, task, b, param('set-dossier-status', 'targetDossierStatus'), 'OUVERT')
    const xml = await toXml()
    expect(xml).toContain('xmlns:flowable="http://flowable.org/bpmn"')
    expect(xml).toContain('flowable:targetDossierStatus="OUVERT"')
  })

  it('ensureFlowableNamespace est idempotent et sans effet sans definitions', async () => {
    const { task } = await load(WITH_NS)
    ensureFlowableNamespace(task)
    ensureFlowableNamespace(task)
    ensureFlowableNamespace({ $type: 'bpmn:Task', get: () => undefined })
    let defs: Moddled = task
    while (defs.$type !== 'bpmn:Definitions') defs = defs.$parent!
    expect(defs.$attrs!['xmlns:flowable']).toBe('http://flowable.org/bpmn')
  })

  it('FORM_KEY_SUFFIX : le préfixe reste le marqueur', async () => {
    const { user, services, toXml } = await load(WITH_NS)
    const b = block('user-question')
    const p = param('user-question', 'schemaKey')
    writeValue(services, user, user, b, p, 'demande')
    expect(readValue(user, b, p)).toBe('demande')
    expect(await toXml()).toContain('flowable:formKey="user-question:demande"')
    writeValue(services, user, user, b, p, '')
    expect(await toXml()).toContain('flowable:formKey="user-question:"')
  })

  it('FIELD_ELEMENT : écrit et retire <flowable:field> de la table DMN', async () => {
    const { task, services, toXml } = await load(WITH_NS)
    const b = block('dmn-decision')
    const p = param('dmn-decision', 'decisionTableReferenceKey')
    writeValue(services, task, task, b, p, 'scoring')
    expect(readValue(task, b, p)).toBe('scoring')
    const xml = await toXml()
    expect(xml).toMatch(/<flowable:field name="decisionTableReferenceKey">\s*<flowable:string>scoring<\/flowable:string>/)
    writeValue(services, task, task, b, p, undefined)
    expect(await toXml()).not.toContain('decisionTableReferenceKey')
  })

  it('la tâche externe garde portalRecipient même vide (marqueur de reconnaissance)', async () => {
    const { user, services, toXml } = await load(WITH_NS)
    const b = block('external-task')
    writeValue(services, user, user, b, param('external-task', 'portalRecipient'), '')
    expect(await toXml()).toContain('flowable:portalRecipient=""')
  })

  it('signataires : écriture, relecture et remplacement sans toucher aux autres éléments', async () => {
    const { task, services, toXml } = await load(WITH_NS)
    writeSigners(services, task, task, [
      { source: 'dossierField:emailClient', kind: 'EXTERNAL', required: true, name: 'Client' },
      { source: 'variable:approverId', kind: 'INTERNAL', required: false, name: '' },
    ])
    expect(readSigners(task)).toEqual([
      { source: 'dossierField:emailClient', kind: 'EXTERNAL', required: true, name: 'Client' },
      { source: 'variable:approverId', kind: 'INTERNAL', required: false, name: '' },
    ])
    const xml = await toXml()
    expect(xml.match(/<flowable:signer /g)).toHaveLength(2)
    writeSigners(services, task, task, [{ source: 'variable:a', kind: 'EXTERNAL', required: true, name: '' }])
    expect(readSigners(task)).toHaveLength(1)
  })

  it('toggleMulti conserve l’ordre du catalogue', () => {
    expect(toggleMulti('', 'WEBHOOK', true, ['EMAIL', 'WEBHOOK'])).toBe('WEBHOOK')
    expect(toggleMulti('WEBHOOK', 'EMAIL', true, ['EMAIL', 'WEBHOOK'])).toBe('EMAIL,WEBHOOK')
    expect(toggleMulti('EMAIL,WEBHOOK', 'EMAIL', false, ['EMAIL', 'WEBHOOK'])).toBe('WEBHOOK')
    expect(toggleMulti('EMAIL', 'EMAIL', false, ['EMAIL', 'WEBHOOK'])).toBe('')
  })

  it('n’écrit jamais d’espaces de bord (I2)', async () => {
    const { task, services } = await load(WITH_NS)
    const b = block('set-dossier-status')
    const p = param('set-dossier-status', 'targetDossierStatus')
    writeValue(services, task, task, b, p, '  CLOTURE ')
    expect(readValue(task, b, p)).toBe('CLOTURE')
  })
})
