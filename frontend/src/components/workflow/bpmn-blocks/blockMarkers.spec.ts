import axios from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { BlockMarkers, WARNING_MARKER } from './blockMarkers'
import { setBlockCatalog, setOpenProcessKey } from './blockCatalogHolder'
import { TEST_BLOCKS } from './testCatalog'

vi.mock('axios')

function task(id: string, attrs: Record<string, unknown>) {
  return {
    id,
    businessObject: { $type: 'bpmn:ServiceTask', get: (k: string) => attrs[k] },
  }
}

function setup(elements: ReturnType<typeof task>[]) {
  const handlers: Record<string, () => void> = {}
  const eventBus = {
    on: (events: string | string[], cb: () => void) => [events].flat().forEach((e) => (handlers[e] = cb)),
  }
  const canvas = { addMarker: vi.fn(), removeMarker: vi.fn() }
  const registry = { getAll: () => elements }
  new BlockMarkers(eventBus, canvas, registry)
  return { handlers, canvas }
}

describe('BlockMarkers (S-DIAG-1, S-DIAG-4)', () => {
  beforeEach(() => {
    setBlockCatalog(TEST_BLOCKS)
    setOpenProcessKey(null)
    vi.mocked(axios.get).mockReset()
  })

  it('marque une brique incomplète et démarque une brique correcte', () => {
    const incomplete = task('a', { 'flowable:delegateExpression': '${updateDossierStatusTask}' })
    const ok = task('b', {
      'flowable:delegateExpression': '${updateDossierStatusTask}',
      'flowable:targetDossierStatus': 'OUVERT',
    })
    const { handlers, canvas } = setup([incomplete, ok])
    handlers['commandStack.changed']()
    expect(canvas.addMarker).toHaveBeenCalledWith('a', WARNING_MARKER)
    expect(canvas.removeMarker).toHaveBeenCalledWith('b', WARNING_MARKER)
  })

  it('ignore les tâches brutes', () => {
    const { handlers, canvas } = setup([task('raw', {})])
    handlers['commandStack.changed']()
    expect(canvas.addMarker).not.toHaveBeenCalled() // jamais d'avertissement sur une tâche brute
  })

  it('retire le marqueur d’un élément qui n’est plus une brique', () => {
    const { handlers, canvas } = setup([task('a', { 'flowable:delegateExpression': '${autre}' })])
    handlers['commandStack.changed']()
    expect(canvas.removeMarker).toHaveBeenCalledWith('a', WARNING_MARKER)
  })

  it('sans catalogue, ne fait rien', () => {
    setBlockCatalog([])
    const { handlers, canvas } = setup([task('a', { 'flowable:delegateExpression': '${updateDossierStatusTask}' })])
    handlers['commandStack.changed']()
    expect(canvas.addMarker).not.toHaveBeenCalled()
  })

  it('à l’import, charge les références puis marque une référence disparue (S-DIAG-2)', async () => {
    vi.mocked(axios.get).mockResolvedValue({ data: [{ value: 'autre', label: 'Autre' }] })
    const stale = task('doc', {
      'flowable:delegateExpression': '${documentGenerationTask}',
      'flowable:documentType': 'supprime',
    })
    const { handlers, canvas } = setup([stale])
    handlers['import.done']()
    expect(canvas.addMarker).not.toHaveBeenCalled() // liste pas encore chargée : aucun faux avertissement
    await vi.waitFor(() => expect(canvas.addMarker).toHaveBeenCalledWith('doc', WARNING_MARKER))
    expect(axios.get).toHaveBeenCalledWith('/api/bpmn-building-blocks/references/documentTemplates')
  })
})
