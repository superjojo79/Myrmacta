import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as workflowsApi from '@/api/workflows'
import { useBpmnDraft } from './useBpmnDraft'

vi.mock('@/api/workflows')

describe('useBpmnDraft', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('load(key) with an existing draft returns the draft XML', async () => {
    vi.mocked(workflowsApi.getBpmnDraft).mockResolvedValue({
      processDefinitionKey: 'wf',
      xml: '<draft/>',
      updatedAt: '2026-01-01T00:00:00Z',
      updatedBy: 'admin',
    })

    const draft = useBpmnDraft()
    await draft.load('wf')

    expect(draft.xml.value).toBe('<draft/>')
    expect(workflowsApi.getDeployedBpmnXml).not.toHaveBeenCalled()
  })

  it('load(key) without a draft falls back to the deployed XML', async () => {
    vi.mocked(workflowsApi.getBpmnDraft).mockResolvedValue(null)
    vi.mocked(workflowsApi.getDeployedBpmnXml).mockResolvedValue('<deployed/>')

    const draft = useBpmnDraft()
    await draft.load('wf')

    expect(workflowsApi.getDeployedBpmnXml).toHaveBeenCalledWith('wf')
    expect(draft.xml.value).toBe('<deployed/>')
  })

  it('load(key) without a draft nor a deployed diagram yields an empty state', async () => {
    vi.mocked(workflowsApi.getBpmnDraft).mockResolvedValue(null)
    vi.mocked(workflowsApi.getDeployedBpmnXml).mockResolvedValue(null)

    const draft = useBpmnDraft()
    await draft.load('wf')

    expect(draft.xml.value).toBeNull()
  })

  it('load(key) network failure exposes loadError instead of throwing', async () => {
    vi.mocked(workflowsApi.getBpmnDraft).mockRejectedValue(new Error('network down'))

    const draft = useBpmnDraft()
    await expect(draft.load('wf')).resolves.toBeUndefined()

    expect(draft.loadError.value).toBe('network down')
    expect(draft.xml.value).toBeNull()
    expect(draft.loading.value).toBe(false)
  })

  it('load(key) clears a previous loadError on a subsequent successful call', async () => {
    vi.mocked(workflowsApi.getBpmnDraft).mockRejectedValueOnce(new Error('network down'))
    const draft = useBpmnDraft()
    await draft.load('wf')
    expect(draft.loadError.value).toBe('network down')

    vi.mocked(workflowsApi.getBpmnDraft).mockResolvedValue({
      processDefinitionKey: 'wf',
      xml: '<draft/>',
      updatedAt: '2026-01-01T00:00:00Z',
      updatedBy: 'admin',
    })
    await draft.load('wf')

    expect(draft.loadError.value).toBeNull()
    expect(draft.xml.value).toBe('<draft/>')
  })

  it('save(key, xml) calls PUT and resets the unsaved-changes state', async () => {
    vi.mocked(workflowsApi.getBpmnDraft).mockResolvedValue(null)
    vi.mocked(workflowsApi.getDeployedBpmnXml).mockResolvedValue(null)
    vi.mocked(workflowsApi.saveBpmnDraft).mockResolvedValue({
      processDefinitionKey: 'wf',
      xml: '<edited/>',
      updatedAt: '2026-01-01T00:00:00Z',
      updatedBy: 'admin',
    })

    const draft = useBpmnDraft()
    draft.markDirty()
    expect(draft.hasUnsavedChanges.value).toBe(true)

    await draft.save('wf', '<edited/>')

    expect(workflowsApi.saveBpmnDraft).toHaveBeenCalledWith('wf', '<edited/>')
    expect(draft.hasUnsavedChanges.value).toBe(false)
  })

  it('the beforeunload listener only prevents unload when there are unsaved changes', () => {
    const draft = useBpmnDraft()

    const cleanEvent = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(cleanEvent)
    expect(cleanEvent.defaultPrevented).toBe(false)

    draft.markDirty()

    const dirtyEvent = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(dirtyEvent)
    expect(dirtyEvent.defaultPrevented).toBe(true)
  })
})
