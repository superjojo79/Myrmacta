import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import DraftsAdminView from './DraftsAdminView.vue'
import * as draftsApi from '@/api/drafts'
import type { PendingDraft } from '@/api/drafts'

vi.mock('@/api/drafts')

const mcpDraft: PendingDraft = {
  type: 'BPMN',
  key: 'claim',
  provenance: 'MCP',
  author: 'agent-1',
  updatedAt: '2026-10-01T10:00:00.123456Z',
  hasProposedConfig: true,
  note: null,
  publishedVersion: 3,
  addedLines: 4,
  removedLines: 2,
  proposedConfig: {
    label: 'Sinistre',
    statusDefinitions: [
      { code: 'STARTED', label: 'Démarré', initial: true, outcome: 'NONE' },
      { code: 'DONE', label: 'Terminé', initial: false, outcome: 'COMPLETED' },
    ],
    allowedDossierStatuses: ['OUVERT', 'EN_COURS'],
    onStartDossierStatus: null,
    observerUserIds: ['spy-1'],
  },
  configProvenance: 'MCP',
  configAuthor: 'agent-1',
}
const uiDraft: PendingDraft = {
  type: 'DMN',
  key: 'eligibilite',
  provenance: 'UI',
  author: 'alice',
  updatedAt: '2026-10-01T09:00:00Z',
  hasProposedConfig: false,
  note: 'Remise en ligne de la version 1',
  publishedVersion: null,
  addedLines: 10,
  removedLines: 0,
  proposedConfig: null,
  configProvenance: null,
  configAuthor: null,
}

async function mountView() {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { template: '<div/>' } }] })
  await router.push('/')
  const wrapper = mount(DraftsAdminView, { global: { plugins: [router], stubs: { RouterLink: { template: '<a><slot/></a>' } } } })
  await flushPromises()
  return wrapper
}

describe('DraftsAdminView (TODO §82, D6)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(draftsApi.listPendingDrafts).mockResolvedValue([mcpDraft, uiDraft])
    vi.mocked(draftsApi.listPublications).mockResolvedValue([])
    vi.spyOn(window, 'confirm').mockReturnValue(true)
  })

  it('shows provenance, author, published version and the indicative diff', async () => {
    const wrapper = await mountView()
    const row = wrapper.get('[data-testid="draft-row-claim"]')
    expect(row.text()).toContain('Agent (MCP)')
    expect(row.text()).toContain('agent-1')
    expect(row.text()).toContain('v3 : +4 / −2 lignes')
    expect(row.text()).toContain('configuration de workflow jointe')
    const ui = wrapper.get('[data-testid="draft-row-eligibilite"]')
    expect(ui.text()).toContain('Éditeur')
    expect(ui.text()).toContain('jamais publié')
    expect(ui.text()).toContain('Remise en ligne de la version 1')
  })

  it('shows the workflow configuration that will be applied, with its origin, so it can be reviewed (revue D6 M1)', async () => {
    const wrapper = await mountView()
    const config = wrapper.get('[data-testid="draft-config-claim"]')
    expect(config.text()).toContain('Sinistre')
    expect(config.text()).toContain('STARTED (initial)')
    expect(config.text()).toContain('DONE → COMPLETED')
    expect(config.text()).toContain('OUVERT, EN_COURS')
    expect(config.text()).toContain('spy-1')
    expect(config.text()).toContain('agent-1')
    expect(wrapper.find('[data-testid="draft-config-eligibilite"]').exists()).toBe(false)
  })

  it('publishes exactly the draft it displays, with its timestamp, then reloads', async () => {
    vi.mocked(draftsApi.publishDraft).mockResolvedValue({ version: 4 } as never)
    const wrapper = await mountView()
    await wrapper.get('[data-testid="draft-row-claim"] [data-testid="draft-publish"]').trigger('click')
    await flushPromises()
    expect(draftsApi.publishDraft).toHaveBeenCalledWith('BPMN', 'claim', '2026-10-01T10:00:00.123456Z')
    expect(wrapper.get('[data-testid="drafts-notice"]').text()).toContain('version 4')
    expect(draftsApi.listPendingDrafts).toHaveBeenCalledTimes(2)
  })

  it('does nothing when the confirmation is declined', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    const wrapper = await mountView()
    await wrapper.get('[data-testid="draft-row-claim"] [data-testid="draft-publish"]').trigger('click')
    await wrapper.get('[data-testid="draft-row-claim"] [data-testid="draft-discard"]').trigger('click')
    expect(draftsApi.publishDraft).not.toHaveBeenCalled()
    expect(draftsApi.discardDraft).not.toHaveBeenCalled()
  })

  it('shows the server reason when the draft changed since it was reviewed (409) and reloads', async () => {
    vi.mocked(draftsApi.publishDraft).mockRejectedValue({
      isAxiosError: true,
      response: { status: 409, data: { detail: 'The draft changed since you reviewed it' } },
    })
    const wrapper = await mountView()
    await wrapper.get('[data-testid="draft-row-claim"] [data-testid="draft-publish"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="drafts-error"]').text()).toContain('changed since you reviewed')
    expect(draftsApi.listPendingDrafts).toHaveBeenCalledTimes(2)
  })

  it('discards a draft after confirmation', async () => {
    vi.mocked(draftsApi.discardDraft).mockResolvedValue()
    const wrapper = await mountView()
    await wrapper.get('[data-testid="draft-row-eligibilite"] [data-testid="draft-discard"]').trigger('click')
    await flushPromises()
    expect(draftsApi.discardDraft).toHaveBeenCalledWith('DMN', 'eligibilite', '2026-10-01T09:00:00Z')
  })

  it('shows an empty state and the publication log', async () => {
    vi.mocked(draftsApi.listPendingDrafts).mockResolvedValue([])
    vi.mocked(draftsApi.listPublications).mockResolvedValue([
      {
        id: 'p1',
        artifactType: 'DMN',
        artifactKey: 'eligibilite',
        version: 2,
        draftProvenance: 'MCP',
        draftAuthor: 'agent-1',
        publishedBy: 'alice',
        publishedAt: '2026-10-01T10:00:00Z',
        mode: 'DIRECT',
      },
    ])
    const wrapper = await mountView()
    expect(wrapper.find('[data-testid="drafts-empty"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="publications-log"]').text()).toContain('publication directe, sans relecture')
  })
})
