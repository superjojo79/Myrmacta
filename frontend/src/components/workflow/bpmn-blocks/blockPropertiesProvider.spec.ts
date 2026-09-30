import { beforeEach, describe, expect, it, vi } from 'vitest'
import { setBlockCatalog } from './blockCatalogHolder'
import { TEST_BLOCKS } from './testCatalog'

// Même convention que flowablePropertiesProvider.spec.ts : les bibliothèques preact sont simulées à la frontière du
// module ; les entrées renvoient leurs props pour que le test appelle getValue/setValue directement.
const services: Record<string, unknown> = {}
vi.mock('bpmn-js-properties-panel', () => ({ useService: vi.fn((name: string) => services[name]) }))
vi.mock('@bpmn-io/properties-panel', () => ({
  TextFieldEntry: vi.fn((props: unknown) => props),
  SelectEntry: vi.fn((props: unknown) => props),
  CheckboxEntry: vi.fn((props: unknown) => props),
  ListGroup: 'ListGroup',
  isTextFieldEntryEdited: vi.fn(),
  isSelectEntryEdited: vi.fn(),
  isCheckboxEntryEdited: vi.fn(),
}))
vi.mock('@bpmn-io/properties-panel/preact/hooks', () => ({
  useState: (initial: unknown) => [initial, vi.fn()],
  useEffect: vi.fn(),
}))

const { default: BlockPropertiesProvider } = await import('./blockPropertiesProvider')
const { default: FlowablePropertiesProvider } = await import('../bpmn-properties/flowablePropertiesProvider')

type Group = { id: string; label?: string; entries?: Array<{ id: string; component: (p: unknown) => unknown }>; [k: string]: unknown }

function element(type: string, attrs: Record<string, unknown> = {}, signers: object[] = []) {
  const bo = {
    $type: type,
    $instanceOf: (t: string) => t === type,
    get: (key: string) =>
      key === 'extensionElements'
        ? { get: () => signers.map((s) => ({ $type: 'flowable:signer', ...s })) }
        : attrs[key],
  }
  return { id: 'Task_1', type, businessObject: bo }
}

function groupsFor(el: ReturnType<typeof element>): Group[] {
  const panel = { registerProvider: vi.fn() }
  const injector = { get: (name: string) => services[name] }
  new (BlockPropertiesProvider as unknown as new (p: unknown, i: unknown) => void)(panel, injector)
  const provider = panel.registerProvider.mock.calls[0][1] as { getGroups: (e: unknown) => (g: unknown[]) => Group[] }
  return provider.getGroups(el)([{ id: 'general' }, { id: 'documentation' }])
}

const statusTask = () =>
  element('bpmn:ServiceTask', { 'flowable:delegateExpression': '${updateDossierStatusTask}' })

describe('blockPropertiesProvider', () => {
  beforeEach(() => {
    setBlockCatalog(TEST_BLOCKS)
    services.modeling = { updateModdleProperties: vi.fn() }
    services.moddle = {}
    services.bpmnFactory = {}
    services.debounceInput = (fn: unknown) => fn
    services.elementRegistry = { getAll: () => [] }
  })

  it('s’enregistre auprès du panneau', () => {
    const panel = { registerProvider: vi.fn() }
    new (BlockPropertiesProvider as unknown as new (p: unknown, i: unknown) => void)(panel, { get: vi.fn() })
    expect(panel.registerProvider).toHaveBeenCalledWith(500, expect.anything())
  })

  it('insère le groupe « Brique » juste après General (S-UI-8)', () => {
    const groups = groupsFor(statusTask())
    expect(groups.map((g) => g.id)).toEqual(['general', 'block', 'documentation'])
    expect(groups[1].label).toBe('set-dossier-status')
    expect(groups[1].entries!.map((e) => e.id)).toEqual(['block-targetDossierStatus'])
  })

  it('ne touche pas aux tâches non reconnues ni aux autres éléments (S-UI-5)', () => {
    expect(groupsFor(element('bpmn:ServiceTask')).map((g) => g.id)).toEqual(['general', 'documentation'])
    expect(groupsFor(element('bpmn:StartEvent')).map((g) => g.id)).toEqual(['general', 'documentation'])
  })

  it('sans catalogue, aucun groupe (repli éditeur brut)', () => {
    setBlockCatalog([])
    expect(groupsFor(statusTask()).map((g) => g.id)).toEqual(['general', 'documentation'])
  })

  it('l’entrée énumérée lit, écrit et retire l’attribut (S-UI-2, S-UI-3)', () => {
    const el = element('bpmn:ServiceTask', {
      'flowable:delegateExpression': '${updateDossierStatusTask}',
      'flowable:targetDossierStatus': 'OUVERT',
    })
    const entry = groupsFor(el)[1].entries![0]
    const props = entry.component({ element: el }) as {
      getValue: () => string
      setValue: (v: string) => void
      getOptions: () => Array<{ value: string }>
    }
    expect(props.getValue()).toBe('OUVERT')
    expect(props.getOptions().map((o) => o.value)).toEqual(['', 'DRAFT', 'OUVERT', 'EN_COURS', 'SUSPENDU', 'CLOTURE', 'ARCHIVE'])
    props.setValue('CLOTURE')
    props.setValue('')
    const update = (services.modeling as { updateModdleProperties: ReturnType<typeof vi.fn> }).updateModdleProperties
    expect(update).toHaveBeenNthCalledWith(1, el, el.businessObject, { 'flowable:targetDossierStatus': 'CLOTURE' })
    expect(update).toHaveBeenNthCalledWith(2, el, el.businessObject, { 'flowable:targetDossierStatus': undefined })
  })

  it('une valeur d’énumération importée hors liste reste visible et marquée invalide (I4)', () => {
    const el = element('bpmn:ServiceTask', {
      'flowable:delegateExpression': '${updateDossierStatusTask}',
      'flowable:targetDossierStatus': 'CLOSED',
    })
    const props = groupsFor(el)[1].entries![0].component({ element: el }) as {
      getValue: () => string
      getOptions: () => Array<{ value: string; label: string }>
    }
    expect(props.getValue()).toBe('CLOSED')
    expect(props.getOptions()).toContainEqual({ value: 'CLOSED', label: 'CLOSED (invalide)' })
  })

  it('multi-sélection : une case par canal, écrit la liste ordonnée', () => {
    const el = element('bpmn:ServiceTask', {
      'flowable:delegateExpression': '${notificationDispatchTask}',
      'flowable:notificationChannels': 'WEBHOOK',
    })
    const entries = groupsFor(el)[1].entries!
    expect(entries.map((e) => e.id).slice(0, 2)).toEqual([
      'block-notificationChannels-EMAIL',
      'block-notificationChannels-WEBHOOK',
    ])
    const email = entries[0].component({ element: el }) as { getValue: () => boolean; setValue: (v: boolean) => void }
    expect(email.getValue()).toBe(false)
    email.setValue(true)
    const update = (services.modeling as { updateModdleProperties: ReturnType<typeof vi.fn> }).updateModdleProperties
    expect(update).toHaveBeenCalledWith(el, el.businessObject, { 'flowable:notificationChannels': 'EMAIL,WEBHOOK' })
  })

  it('l’entrée entier signale une valeur hors bornes', () => {
    const el = element('bpmn:ServiceTask', { 'flowable:delegateExpression': '${signatureRequestTask}' })
    const group = groupsFor(el)[1]
    const deadline = group.entries!.find((e) => e.id === 'block-signatureDeadlineDays')!
    const props = deadline.component({ element: el }) as { validate: (v: string) => string | undefined }
    expect(props.validate('14')).toBeUndefined()
    expect(props.validate('0')).toContain('entre 1 et 365')
    expect(props.validate('abc')).toContain('entre 1 et 365')
  })

  it('la brique signature ajoute une liste de signataires (S-UI-2)', () => {
    const el = element(
      'bpmn:ServiceTask',
      { 'flowable:delegateExpression': '${signatureRequestTask}' },
      [{ source: 'variable:m', kind: 'EXTERNAL', required: 'true', name: 'Client' }],
    )
    const groups = groupsFor(el)
    expect(groups.map((g) => g.id)).toEqual(['general', 'block', 'block-signers', 'documentation'])
    const signers = groups[2] as unknown as { items: Array<{ label: string; entries: unknown[] }>; add: () => void }
    expect(signers.items).toHaveLength(1)
    expect(signers.items[0].label).toBe('Client')
    expect(signers.items[0].entries).toHaveLength(4)
  })

  it('« Avancé » : replié pour une brique, ouvert pour une tâche brute (S-UI-4, S-UI-5, S-UI-8)', () => {
    const panel = { registerProvider: vi.fn() }
    new (FlowablePropertiesProvider as unknown as new (p: unknown) => void)(panel)
    const provider = panel.registerProvider.mock.calls[0][1] as { getGroups: (e: unknown) => (g: unknown[]) => Group[] }
    const recognized = provider.getGroups(statusTask())([])
    expect(recognized).toEqual([expect.objectContaining({ id: 'flowable-service-task', label: 'Avancé', shouldOpen: false })])
    const raw = provider.getGroups(element('bpmn:ServiceTask'))([])
    expect(raw).toEqual([expect.objectContaining({ label: 'Avancé', shouldOpen: true })])
  })
})
