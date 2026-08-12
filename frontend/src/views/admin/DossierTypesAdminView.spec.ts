import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import DossierTypesAdminView from './DossierTypesAdminView.vue'
import * as api from '@/api/dossierTypes'

vi.mock('@/api/dossierTypes')

describe('DossierTypesAdminView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listDossierTypes).mockResolvedValue([
      {
        id: '1',
        tenantId: 't',
        code: 'CLAIM',
        label: 'Sinistre',
        workflowKey: 'wf',
        active: true,
        allowUnknownFields: false,
        fieldDefinitions: [],
      },
    ])
  })

  it('lists types and can create', async () => {
    vi.mocked(api.createDossierType).mockResolvedValue({
      id: '2',
      tenantId: 't',
      code: 'NEW',
      label: 'New',
      workflowKey: 'wf2',
      active: true,
      allowUnknownFields: false,
      fieldDefinitions: [],
    })
    const wrapper = mount(DossierTypesAdminView)
    await flushPromises()
    expect(wrapper.text()).toContain('CLAIM')
    const inputs = wrapper.findAll('form.admin-form input')
    await inputs[0].setValue('NEW')
    await inputs[1].setValue('New')
    await inputs[2].setValue('wf2')
    await wrapper.findAll('form')[0].trigger('submit.prevent')
    await flushPromises()
    expect(api.createDossierType).toHaveBeenCalled()
  })
})
