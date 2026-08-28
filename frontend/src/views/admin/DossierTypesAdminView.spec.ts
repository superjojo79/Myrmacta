import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import DossierTypesAdminView from './DossierTypesAdminView.vue'
import * as api from '@/api/dossierTypes'
import * as retentionApi from '@/api/retentionSettings'
import * as slaApi from '@/api/slaSettings'
import { routes } from '@/app/routes'
import { asPage } from '@/api/page'
import type { Component } from 'vue'

vi.mock('@/api/dossierTypes')
vi.mock('@/api/retentionSettings')
vi.mock('@/api/slaSettings')

const sampleType = {
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
  slaTargetHours: 48,
}

async function mountView(component: Component, path = '/admin/dossier-types') {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(component, { global: { plugins: [router] } })
  return { wrapper, router }
}

describe('DossierTypesAdminView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listDossierTypes).mockResolvedValue(asPage([sampleType]))
    vi.mocked(retentionApi.getRetentionSettings).mockRejectedValue({
      response: { status: 404, data: { detail: 'not found', status: 404 } },
    })
    vi.mocked(slaApi.getSlaSettings).mockRejectedValue({
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
    const { wrapper } = await mountView(DossierTypesAdminView)
    await flushPromises()
    expect(wrapper.text()).toContain('CLAIM')
    expect(wrapper.text()).toContain('Rétention tenant')
    expect(wrapper.text()).toContain('héritage')
    const createForm = wrapper.get('[data-testid="type-create-form"]')
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
        slaTargetHours: null,
      }),
    )
  })

  it('clears type SLA hours via clearSlaTargetHours', async () => {
    vi.mocked(api.updateDossierType).mockResolvedValue({
      ...sampleType,
      slaTargetHours: null,
    })
    const { wrapper } = await mountView(DossierTypesAdminView)
    await flushPromises()
    await wrapper.get('[data-testid="type-clear-sla"]').trigger('click')
    await flushPromises()
    expect(api.updateDossierType).toHaveBeenCalledWith('CLAIM', { clearSlaTargetHours: true })
  })

  it('saves tenant SLA compliance target', async () => {
    vi.mocked(slaApi.upsertSlaSettings).mockResolvedValue({
      tenantId: 't',
      complianceTargetPercent: 90,
      updatedAt: '2026-01-01T00:00:00Z',
      updatedBy: 'admin',
    })
    const { wrapper } = await mountView(DossierTypesAdminView)
    await flushPromises()
    expect(wrapper.text()).toContain('Objectif SLA tenant')
    await wrapper.get('[data-testid="sla-target-percent"]').setValue('90')
    await wrapper.get('[data-testid="sla-settings-form"]').trigger('submit.prevent')
    await flushPromises()
    expect(slaApi.upsertSlaSettings).toHaveBeenCalledWith(90)
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
    const { wrapper } = await mountView(DossierTypesAdminView)
    await flushPromises()
    await wrapper.get('[data-testid="retention-default-days"]').setValue('90')
    await wrapper.find('form.admin-form').trigger('submit.prevent')
    await flushPromises()
    expect(retentionApi.upsertRetentionSettings).toHaveBeenCalledWith(90)
  })

  it('includes allowUnknownFields and fieldDefinitions from the editor on create', async () => {
    vi.mocked(api.createDossierType).mockResolvedValue({
      id: '2',
      tenantId: 't',
      code: 'NEW',
      label: 'New',
      workflowKey: 'wf2',
      active: true,
      allowUnknownFields: true,
      fieldDefinitions: [],
      retentionDays: null,
      retentionNever: false,
    })
    const { wrapper } = await mountView(DossierTypesAdminView)
    await flushPromises()
    const createForm = wrapper.get('[data-testid="type-create-form"]')
    const inputs = createForm.findAll('input')
    await inputs[0].setValue('NEW')
    await inputs[1].setValue('New')
    await inputs[2].setValue('wf2')
    await createForm.get('[data-testid="type-allow-unknown-fields"]').setValue(true)
    await createForm.get('[data-testid="field-def-add"]').trigger('click')
    await createForm.get('[data-testid="field-def-name"]').setValue('priority')
    await createForm.get('[data-testid="field-def-label"]').setValue('Priority')
    await createForm.trigger('submit.prevent')
    await flushPromises()
    expect(api.createDossierType).toHaveBeenCalledWith(
      expect.objectContaining({
        code: 'NEW',
        allowUnknownFields: true,
        fieldDefinitions: [
          expect.objectContaining({ name: 'priority', label: 'Priority', type: 'STRING' }),
        ],
      }),
    )
  })

  it('blocks create submission when the field editor has validation errors', async () => {
    const { wrapper } = await mountView(DossierTypesAdminView)
    await flushPromises()
    const createForm = wrapper.get('[data-testid="type-create-form"]')
    const inputs = createForm.findAll('input')
    await inputs[0].setValue('NEW2')
    await inputs[1].setValue('New2')
    await inputs[2].setValue('wf3')
    await createForm.get('[data-testid="field-def-add"]').trigger('click')
    // name left blank on purpose
    await createForm.trigger('submit.prevent')
    await flushPromises()
    expect(api.createDossierType).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('obligatoire')
  })

  it('opens the edit-fields panel prefilled with the existing schema', async () => {
    const { wrapper } = await mountView(DossierTypesAdminView)
    await flushPromises()
    await wrapper.get('[data-testid="type-edit-fields"]').trigger('click')
    expect(wrapper.find('[data-testid="type-edit-fields-panel"]').exists()).toBe(true)
    expect(api.getDossierType).not.toHaveBeenCalled()
  })

  it('saves the edited schema and refreshes the list', async () => {
    vi.mocked(api.updateDossierType).mockResolvedValue({ ...sampleType, allowUnknownFields: true })
    const { wrapper } = await mountView(DossierTypesAdminView)
    await flushPromises()
    await wrapper.get('[data-testid="type-edit-fields"]').trigger('click')
    await wrapper.get('[data-testid="type-edit-fields-panel"] [data-testid="type-allow-unknown-fields"]').setValue(true)
    await wrapper.get('[data-testid="type-edit-fields-save"]').trigger('click')
    await flushPromises()
    expect(api.updateDossierType).toHaveBeenCalledWith(
      'CLAIM',
      expect.objectContaining({ allowUnknownFields: true, fieldDefinitions: [] }),
    )
    expect(wrapper.find('[data-testid="type-edit-fields-panel"]').exists()).toBe(false)
  })

  it('blocks the save when the edited schema has validation errors', async () => {
    const { wrapper } = await mountView(DossierTypesAdminView)
    await flushPromises()
    await wrapper.get('[data-testid="type-edit-fields"]').trigger('click')
    await wrapper.get('[data-testid="type-edit-fields-panel"] [data-testid="field-def-add"]').trigger('click')
    // name left blank on purpose
    await wrapper.get('[data-testid="type-edit-fields-save"]').trigger('click')
    await flushPromises()
    expect(api.updateDossierType).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('obligatoire')
    expect(wrapper.find('[data-testid="type-edit-fields-panel"]').exists()).toBe(true)
  })

  it('shows the 409 error and keeps the edit panel open on incompatible schema', async () => {
    vi.mocked(api.updateDossierType).mockRejectedValue({
      response: {
        status: 409,
        data: {
          status: 409,
          title: 'Dossier type schema incompatible',
          detail: 'Cette modification rend 1 dossier(s) existant(s) incompatible(s) avec le nouveau schéma (ex. DOS-1)',
        },
      },
    })
    const { wrapper } = await mountView(DossierTypesAdminView)
    await flushPromises()
    await wrapper.get('[data-testid="type-edit-fields"]').trigger('click')
    await wrapper.get('[data-testid="type-edit-fields-save"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('incompatible')
    expect(wrapper.find('[data-testid="type-edit-fields-panel"]').exists()).toBe(true)
  })

  it('cancels the edit panel without calling the API', async () => {
    const { wrapper } = await mountView(DossierTypesAdminView)
    await flushPromises()
    await wrapper.get('[data-testid="type-edit-fields"]').trigger('click')
    await wrapper.get('[data-testid="type-edit-fields-cancel"]').trigger('click')
    expect(wrapper.find('[data-testid="type-edit-fields-panel"]').exists()).toBe(false)
    expect(api.updateDossierType).not.toHaveBeenCalled()
  })

  it('updates page query when clicking next', async () => {
    vi.mocked(api.listDossierTypes).mockResolvedValue(
      asPage([sampleType], { page: 0, size: 20, totalElements: 40, totalPages: 2 }),
    )
    const { wrapper, router } = await mountView(DossierTypesAdminView)
    await flushPromises()
    await wrapper.get('[data-testid="admin-pager-next"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.page).toBe('1')
  })
})
