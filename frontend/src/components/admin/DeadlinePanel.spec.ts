import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import DeadlinePanel from './DeadlinePanel.vue'
import * as typesApi from '@/api/dossierTypes'
import * as groupsApi from '@/api/userGroups'

vi.mock('@/api/dossierTypes')
vi.mock('@/api/userGroups')

async function mountPanel(dateFields = ['echeance', 'dateReception']) {
  const wrapper = mount(DeadlinePanel, { props: { typeCode: 'FACTURE', dateFields } })
  await flushPromises()
  return wrapper
}

describe('DeadlinePanel (TODO §115)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(groupsApi.listUserGroupOptions).mockResolvedValue([
      { code: 'COMPTABILITE', label: 'Comptabilité' },
      { code: 'DIRECTION_FINANCIERE', label: 'Direction financière' },
    ])
  })

  it('shows the current deadline of the type', async () => {
    vi.mocked(typesApi.getDeadline).mockResolvedValue({
      dossierTypeCode: 'FACTURE',
      fieldName: 'echeance',
      reminderDays: 5,
      escalationGroup: 'DIRECTION_FINANCIERE',
    })
    const wrapper = await mountPanel()
    expect((wrapper.get('[data-testid="deadline-field"]').element as HTMLSelectElement).value).toBe('echeance')
    expect((wrapper.get('[data-testid="deadline-reminder-days"]').element as HTMLInputElement).value).toBe('5')
    expect((wrapper.get('[data-testid="deadline-escalation-group"]').element as HTMLSelectElement).value).toBe('DIRECTION_FINANCIERE')
  })

  it('saves a deadline with a reminder and no escalation group', async () => {
    vi.mocked(typesApi.getDeadline).mockResolvedValue(null)
    vi.mocked(typesApi.setDeadline).mockResolvedValue({
      dossierTypeCode: 'FACTURE',
      fieldName: 'echeance',
      reminderDays: 3,
      escalationGroup: null,
    })
    const wrapper = await mountPanel()
    expect(wrapper.find('[data-testid="deadline-reminder-days"]').exists()).toBe(false)
    await wrapper.get('[data-testid="deadline-field"]').setValue('echeance')
    await wrapper.get('[data-testid="deadline-reminder-days"]').setValue('3')
    await wrapper.get('[data-testid="deadline-save"]').trigger('click')
    await flushPromises()
    expect(typesApi.setDeadline).toHaveBeenCalledWith('FACTURE', { fieldName: 'echeance', reminderDays: 3, escalationGroup: null })
    expect(wrapper.find('[data-testid="deadline-saved"]').exists()).toBe(true)
  })

  it('clears the deadline when no field is chosen, shows a refusal, and says when the type has no date field', async () => {
    vi.mocked(typesApi.getDeadline).mockResolvedValue({
      dossierTypeCode: 'FACTURE',
      fieldName: 'echeance',
      reminderDays: 0,
      escalationGroup: null,
    })
    vi.mocked(typesApi.clearDeadline).mockResolvedValue()
    const wrapper = await mountPanel()
    await wrapper.get('[data-testid="deadline-field"]').setValue('')
    await wrapper.get('[data-testid="deadline-save"]').trigger('click')
    await flushPromises()
    expect(typesApi.clearDeadline).toHaveBeenCalledWith('FACTURE')

    vi.mocked(typesApi.setDeadline).mockRejectedValue({
      response: { status: 400, data: { detail: 'Unknown user group: X', status: 400 } },
    })
    await wrapper.get('[data-testid="deadline-field"]').setValue('echeance')
    await wrapper.get('[data-testid="deadline-save"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="deadline-error"]').text()).toContain('Unknown user group')

    const empty = await mountPanel([])
    expect(empty.find('[data-testid="deadline-no-date-field"]').exists()).toBe(true)
  })
})
