import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ReplyWorkflowPanel from './ReplyWorkflowPanel.vue'
import * as typesApi from '@/api/dossierTypes'
import * as workflowsApi from '@/api/workflows'

vi.mock('@/api/dossierTypes')
vi.mock('@/api/workflows')

const configs = [{ workflowKey: 'traiterReponse' }, { workflowKey: 'relancerFacture' }] as never

async function mountPanel() {
  const wrapper = mount(ReplyWorkflowPanel, { props: { typeCode: 'FACTURE', childTypeCodes: ['REPONSE_FACTURE', 'COURRIER'] } })
  await flushPromises()
  return wrapper
}

describe('ReplyWorkflowPanel (TODO §113)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(workflowsApi.listAllWorkflowTypeConfigs).mockResolvedValue(configs)
  })

  it('shows the current child-dossier setting of the type', async () => {
    vi.mocked(typesApi.getReplyWorkflow).mockResolvedValue({
      dossierTypeCode: 'FACTURE',
      mode: 'DOSSIER_ENFANT',
      workflowKey: 'traiterReponse',
      childTypeCode: 'REPONSE_FACTURE',
    })
    const wrapper = await mountPanel()
    expect((wrapper.get('[data-testid="reply-workflow-mode"]').element as HTMLSelectElement).value).toBe('DOSSIER_ENFANT')
    expect((wrapper.get('[data-testid="reply-workflow-key"]').element as HTMLSelectElement).value).toBe('traiterReponse')
    expect((wrapper.get('[data-testid="reply-workflow-child-type"]').element as HTMLSelectElement).value).toBe('REPONSE_FACTURE')
  })

  it('saves an on-dossier reply workflow without a child type', async () => {
    vi.mocked(typesApi.getReplyWorkflow).mockResolvedValue(null)
    vi.mocked(typesApi.setReplyWorkflow).mockResolvedValue({
      dossierTypeCode: 'FACTURE',
      mode: 'SUR_DOSSIER',
      workflowKey: 'relancerFacture',
      childTypeCode: null,
    })
    const wrapper = await mountPanel()
    expect(wrapper.find('[data-testid="reply-workflow-key"]').exists()).toBe(false)
    await wrapper.get('[data-testid="reply-workflow-mode"]').setValue('SUR_DOSSIER')
    expect(wrapper.find('[data-testid="reply-workflow-child-type"]').exists()).toBe(false)
    await wrapper.get('[data-testid="reply-workflow-key"]').setValue('relancerFacture')
    await wrapper.get('[data-testid="reply-workflow-save"]').trigger('click')
    await flushPromises()
    expect(typesApi.setReplyWorkflow).toHaveBeenCalledWith('FACTURE', {
      mode: 'SUR_DOSSIER',
      workflowKey: 'relancerFacture',
      childTypeCode: null,
    })
    expect(wrapper.find('[data-testid="reply-workflow-saved"]').exists()).toBe(true)
  })

  it('clears the setting when no workflow is chosen, and shows a refusal', async () => {
    vi.mocked(typesApi.getReplyWorkflow).mockResolvedValue({
      dossierTypeCode: 'FACTURE',
      mode: 'SUR_DOSSIER',
      workflowKey: 'relancerFacture',
      childTypeCode: null,
    })
    vi.mocked(typesApi.clearReplyWorkflow).mockResolvedValue()
    const wrapper = await mountPanel()
    await wrapper.get('[data-testid="reply-workflow-mode"]').setValue('')
    await wrapper.get('[data-testid="reply-workflow-save"]').trigger('click')
    await flushPromises()
    expect(typesApi.clearReplyWorkflow).toHaveBeenCalledWith('FACTURE')

    vi.mocked(typesApi.setReplyWorkflow).mockRejectedValue({
      response: { status: 400, data: { detail: 'No workflow configuration for inconnu', status: 400 } },
    })
    await wrapper.get('[data-testid="reply-workflow-mode"]').setValue('SUR_DOSSIER')
    await wrapper.get('[data-testid="reply-workflow-save"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="reply-workflow-error"]').text()).toContain('No workflow configuration')
  })
})
