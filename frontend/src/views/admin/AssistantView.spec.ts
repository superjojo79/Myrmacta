import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import AssistantView from './AssistantView.vue'
import * as api from '@/api/assistant'

vi.mock('@/api/assistant')

const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/admin/drafts', component: { template: '<i/>' } }] })

function mountView() {
  return mount(AssistantView, { global: { plugins: [router] } })
}

describe('AssistantView (LC3)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listConversations).mockResolvedValue([])
    vi.mocked(api.listActions).mockResolvedValue([])
  })

  it('says clearly when the assistant is not enabled, and offers no input', async () => {
    vi.mocked(api.getAssistantStatus).mockResolvedValue({ enabled: false })
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.find('[data-testid="assistant-disabled"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="assistant-input"]').exists()).toBe(false)
    expect(api.listConversations).not.toHaveBeenCalled()
  })

  it('sends a message, shows the reply as plain text, the tool activity and the link to the drafts', async () => {
    vi.mocked(api.getAssistantStatus).mockResolvedValue({ enabled: true, model: 'm' })
    vi.mocked(api.sendAssistantMessage).mockResolvedValue({
      conversationId: 'c1',
      reply: 'Brouillon créé <img src=x onerror=alert(1)>',
      activity: [{ tool: 'deploy_workflow', ok: true, summary: '{}' }, { tool: 'define_dossier_type', ok: false, summary: 'error: x' }],
      producedDrafts: true,
      proposals: [],
    })
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="assistant-input"]').setValue('Crée un sinistre')
    await wrapper.get('form').trigger('submit.prevent')
    await flushPromises()

    expect(api.sendAssistantMessage).toHaveBeenCalledWith('Crée un sinistre', null)
    const log = wrapper.get('[data-testid="assistant-log"]')
    expect(log.text()).toContain('Crée un sinistre')
    expect(log.text()).toContain('Brouillon créé <img src=x onerror=alert(1)>') // texte brut
    expect(log.find('img').exists()).toBe(false)
    expect(log.text()).toContain('deploy_workflow')
    expect(log.text()).toContain('refusé')
    expect(wrapper.find('[data-testid="assistant-drafts"]').exists()).toBe(true)
  })

  it('keeps the conversation id for the next message and shows the provider error without detail', async () => {
    vi.mocked(api.getAssistantStatus).mockResolvedValue({ enabled: true })
    vi.mocked(api.sendAssistantMessage)
      .mockResolvedValueOnce({ conversationId: 'c9', reply: 'ok', activity: [], producedDrafts: false, proposals: [] })
      .mockRejectedValueOnce({ response: { status: 502, data: { detail: 'the language model provider answered HTTP 500', status: 502 } } })
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="assistant-input"]').setValue('un')
    await wrapper.get('form').trigger('submit.prevent')
    await flushPromises()
    await wrapper.get('[data-testid="assistant-input"]').setValue('deux')
    await wrapper.get('form').trigger('submit.prevent')
    await flushPromises()
    expect(api.sendAssistantMessage).toHaveBeenLastCalledWith('deux', 'c9')
    expect(wrapper.get('[data-testid="assistant-error"]').text()).toContain('HTTP 500')
  })

  it('opens a past conversation, folding tool results into the assistant turn, and deletes it', async () => {
    vi.mocked(api.getAssistantStatus).mockResolvedValue({ enabled: true })
    vi.mocked(api.listConversations).mockResolvedValue([{ id: 'c1', title: 'Mon sinistre', createdAt: '', updatedAt: '', turns: 1 }])
    vi.mocked(api.getConversation).mockResolvedValue({
      conversation: { id: 'c1', title: 'Mon sinistre', createdAt: '', updatedAt: '', turns: 1 },
      messages: [
        { seq: 0, role: 'USER', content: 'bonjour', createdAt: '' },
        { seq: 1, role: 'TOOL', content: '{"ok":true}', toolName: 'define_dossier_type', createdAt: '' },
        { seq: 2, role: 'ASSISTANT', content: 'fait', createdAt: '' },
      ],
    })
    vi.mocked(api.deleteConversation).mockResolvedValue()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="assistant-open"]').trigger('click')
    await flushPromises()
    const log = wrapper.get('[data-testid="assistant-log"]')
    expect(log.text()).toContain('bonjour')
    expect(log.text()).toContain('define_dossier_type')
    expect(log.text()).toContain('fait')
    await wrapper.get('.assistant__remove').trigger('click')
    await flushPromises()
    expect(api.deleteConversation).toHaveBeenCalledWith('c1')
  })

  // ---- revue adversariale LC3 ----
  const pending: api.AssistantAction = {
    id: 'a1', tool: 'define_connector_instance', arguments: '{"name":"ERP <b>x</b>","connectorType":"http-rest"}',
    status: 'PENDING', createdAt: '', outcome: null, resolvedAt: null,
  }

  it('shows the writes the assistant proposes with their arguments, and nothing is applied before the user decides', async () => {
    vi.mocked(api.getAssistantStatus).mockResolvedValue({ enabled: true })
    vi.mocked(api.sendAssistantMessage).mockResolvedValue({
      conversationId: 'c1', reply: 'Je vous propose un connecteur', activity: [], producedDrafts: false, proposals: [pending],
    })
    vi.mocked(api.listActions).mockResolvedValue([pending])
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="assistant-input"]').setValue('crée un connecteur')
    await wrapper.get('form').trigger('submit.prevent')
    await flushPromises()

    const card = wrapper.get('[data-testid="assistant-action"]')
    expect(card.text()).toContain('Connecteur')
    expect(card.get('[data-testid="assistant-action-args"]').text()).toContain('"connectorType": "http-rest"')
    expect(card.find('b').exists()).toBe(false) // arguments en texte brut
    expect(api.applyAction).not.toHaveBeenCalled()
  })

  it('applies a proposal on the user click, then shows its outcome; a dismissed one is closed', async () => {
    vi.mocked(api.getAssistantStatus).mockResolvedValue({ enabled: true })
    vi.mocked(api.listConversations).mockResolvedValue([{ id: 'c1', title: 'T', createdAt: '', updatedAt: '', turns: 1 }])
    vi.mocked(api.getConversation).mockResolvedValue({ conversation: { id: 'c1', title: 'T', createdAt: '', updatedAt: '', turns: 1 }, messages: [] })
    vi.mocked(api.listActions).mockResolvedValue([pending, { ...pending, id: 'a2' }])
    vi.mocked(api.applyAction).mockResolvedValue({ ...pending, status: 'APPLIED', outcome: '{"written":true}' })
    vi.mocked(api.dismissAction).mockResolvedValue({ ...pending, id: 'a2', status: 'DISMISSED' })
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="assistant-open"]').trigger('click')
    await flushPromises()
    const [first, second] = wrapper.findAll('[data-testid="assistant-action"]')
    await first!.get('[data-testid="assistant-apply"]').trigger('click')
    await flushPromises()
    expect(api.applyAction).toHaveBeenCalledWith('c1', 'a1')
    expect(wrapper.findAll('[data-testid="assistant-action"]')[0]!.text()).toContain('appliquée')
    await second!.get('[data-testid="assistant-dismiss"]').trigger('click')
    await flushPromises()
    expect(api.dismissAction).toHaveBeenCalledWith('c1', 'a2')
    expect(wrapper.findAll('[data-testid="assistant-action"]')[1]!.text()).toContain('ignorée')
    expect(wrapper.findAll('[data-testid="assistant-apply"]')).toHaveLength(0)
  })

  it('blocks conversation switching while an answer is pending, and gives the text back after a failure', async () => {
    vi.mocked(api.getAssistantStatus).mockResolvedValue({ enabled: true })
    vi.mocked(api.listConversations).mockResolvedValue([{ id: 'c1', title: 'T', createdAt: '', updatedAt: '', turns: 1 }])
    let reject!: (e: unknown) => void
    vi.mocked(api.sendAssistantMessage).mockReturnValue(new Promise((_, r) => { reject = r }))
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="assistant-input"]').setValue('mon message important')
    await wrapper.get('form').trigger('submit.prevent')
    await flushPromises()
    expect(wrapper.get('[data-testid="assistant-open"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="assistant-new"]').attributes('disabled')).toBeDefined()
    reject({ response: { status: 409, data: { detail: 'already working', status: 409 } } })
    await flushPromises()
    expect((wrapper.get('[data-testid="assistant-input"]').element as HTMLTextAreaElement).value).toBe('mon message important')
    expect(wrapper.get('[data-testid="assistant-error"]').text()).toContain('already working')
  })
})
