import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as workflowsApi from '@/api/workflows'
import { useDmnDraft } from './useDmnDraft'

vi.mock('@/api/workflows')

describe('useDmnDraft', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('load(key) with an existing draft returns the draft XML', async () => {
    vi.mocked(workflowsApi.getDmnDraft).mockResolvedValue({
      decisionKey: 'grilleEligibilite',
      xml: '<draft/>',
      updatedAt: '2026-09-11T00:00:00Z',
      updatedBy: 'admin',
    })

    const draft = useDmnDraft()
    await draft.load('grilleEligibilite')

    expect(draft.xml.value).toBe('<draft/>')
    expect(workflowsApi.getDeployedDmnXml).not.toHaveBeenCalled()
  })

  it('load(key) without a draft falls back to the deployed XML', async () => {
    vi.mocked(workflowsApi.getDmnDraft).mockResolvedValue(null)
    vi.mocked(workflowsApi.getDeployedDmnXml).mockResolvedValue('<deployed/>')

    const draft = useDmnDraft()
    await draft.load('grilleEligibilite')

    expect(workflowsApi.getDeployedDmnXml).toHaveBeenCalledWith('grilleEligibilite')
    expect(draft.xml.value).toBe('<deployed/>')
  })

  it('load(key) without a draft nor a deployed table yields an empty state', async () => {
    vi.mocked(workflowsApi.getDmnDraft).mockResolvedValue(null)
    vi.mocked(workflowsApi.getDeployedDmnXml).mockResolvedValue(null)

    const draft = useDmnDraft()
    await draft.load('grilleEligibilite')

    expect(draft.xml.value).toBeNull()
  })

  it('load(key) network failure exposes loadError instead of throwing', async () => {
    vi.mocked(workflowsApi.getDmnDraft).mockRejectedValue(new Error('network down'))

    const draft = useDmnDraft()
    await expect(draft.load('grilleEligibilite')).resolves.toBeUndefined()

    expect(draft.loadError.value).toBe('network down')
    expect(draft.xml.value).toBeNull()
    expect(draft.loading.value).toBe(false)
  })

  it('load(key) clears a previous loadError on a subsequent successful call', async () => {
    vi.mocked(workflowsApi.getDmnDraft).mockRejectedValueOnce(new Error('network down'))
    const draft = useDmnDraft()
    await draft.load('grilleEligibilite')
    expect(draft.loadError.value).toBe('network down')

    vi.mocked(workflowsApi.getDmnDraft).mockResolvedValue({
      decisionKey: 'grilleEligibilite',
      xml: '<draft/>',
      updatedAt: '2026-09-11T00:00:00Z',
      updatedBy: 'admin',
    })
    await draft.load('grilleEligibilite')

    expect(draft.loadError.value).toBeNull()
    expect(draft.xml.value).toBe('<draft/>')
  })

  it('save(key, xml) calls PUT and resets the unsaved-changes state', async () => {
    vi.mocked(workflowsApi.getDmnDraft).mockResolvedValue(null)
    vi.mocked(workflowsApi.getDeployedDmnXml).mockResolvedValue(null)
    vi.mocked(workflowsApi.saveDmnDraft).mockResolvedValue({
      decisionKey: 'grilleEligibilite',
      xml: '<edited/>',
      updatedAt: '2026-09-11T00:00:00Z',
      updatedBy: 'admin',
    })

    const draft = useDmnDraft()
    draft.markDirty()
    expect(draft.hasUnsavedChanges.value).toBe(true)

    await draft.save('grilleEligibilite', '<edited/>')

    expect(workflowsApi.saveDmnDraft).toHaveBeenCalledWith('grilleEligibilite', '<edited/>')
    expect(draft.hasUnsavedChanges.value).toBe(false)
  })

  it('the beforeunload listener only prevents unload when there are unsaved changes', () => {
    const draft = useDmnDraft()

    const cleanEvent = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(cleanEvent)
    expect(cleanEvent.defaultPrevented).toBe(false)

    draft.markDirty()

    const dirtyEvent = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(dirtyEvent)
    expect(dirtyEvent.defaultPrevented).toBe(true)
  })
})
