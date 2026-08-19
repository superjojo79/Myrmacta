import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import DossierTypesAdminView from './DossierTypesAdminView.vue'
import * as api from '@/api/dossierTypes'
import * as retentionApi from '@/api/retentionSettings'

vi.mock('@/api/dossierTypes')
vi.mock('@/api/retentionSettings')

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
        retentionDays: null,
        retentionNever: false,
      },
    ])
    vi.mocked(retentionApi.getRetentionSettings).mockRejectedValue({
      response: { status: 404, data: { detail: 'not found', status: 404 } },
    })
  })

  it('lists types and can create with retention inherit', async () => {
    vi.mocked(api.createDossierType).mockResolvedValue({
      id: '2',
      tenantId: 't',
      code: 'NEW',
      label: 'New',
      workflowKey: 'wf2',
      active: true,
      allowUnknownFields: false,
      fieldDefinitions: [],
      retentionDays: null,
      retentionNever: false,
    })
    const wrapper = mount(DossierTypesAdminView)
    await flushPromises()
    expect(wrapper.text()).toContain('CLAIM')
    expect(wrapper.text()).toContain('Rétention tenant')
    expect(wrapper.text()).toContain('héritage')
    const createForm = wrapper.findAll('form.admin-form')[1]
    const inputs = createForm.findAll('input')
    await inputs[0].setValue('NEW')
    await inputs[1].setValue('New')
    await inputs[2].setValue('wf2')
    await createForm.trigger('submit.prevent')
    await flushPromises()
    expect(api.createDossierType).toHaveBeenCalledWith(
      expect.objectContaining({
        code: 'NEW',
        retentionDays: null,
        retentionNever: false,
      }),
    )
  })

  it('saves tenant retention default', async () => {
    vi.mocked(retentionApi.upsertRetentionSettings).mockResolvedValue({
      tenantId: 't',
      defaultRetentionDays: 90,
      updatedAt: '2026-01-01T00:00:00Z',
      updatedBy: 'admin',
    })
    vi.mocked(retentionApi.getRetentionSettings)
      .mockRejectedValueOnce({
        response: { status: 404, data: { detail: 'not found', status: 404 } },
      })
      .mockResolvedValue({
        tenantId: 't',
        defaultRetentionDays: 90,
        updatedAt: '2026-01-01T00:00:00Z',
        updatedBy: 'admin',
      })
    const wrapper = mount(DossierTypesAdminView)
    await flushPromises()
    await wrapper.get('[data-testid="retention-default-days"]').setValue('90')
    await wrapper.find('form.admin-form').trigger('submit.prevent')
    await flushPromises()
    expect(retentionApi.upsertRetentionSettings).toHaveBeenCalledWith(90)
  })
})
