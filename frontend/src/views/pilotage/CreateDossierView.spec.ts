import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import CreateDossierView from './CreateDossierView.vue'
import { routes } from '@/app/routes'
import * as typesApi from '@/api/dossierTypes'
import * as dossiersApi from '@/api/dossiers'

vi.mock('@/api/dossierTypes')
vi.mock('@/api/dossiers')
vi.mock('@/api/workflows')
vi.mock('@/api/tasks')
vi.mock('@/api/elements')

describe('CreateDossierView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.resetAllMocks()
    vi.mocked(typesApi.listAllDossierTypes).mockResolvedValue([
      {
        id: 't1',
        tenantId: 't',
        code: 'CLAIM',
        label: 'Sinistre',
        workflowKey: 'wf',
        active: true,
        allowUnknownFields: false,
        fieldDefinitions: [
          {
            name: 'priority',
            label: 'Priority',
            type: 'STRING',
            required: true,
            defaultValue: 'NORMAL',
          },
        ],
      },
    ])
  })

  it('creates dossier and navigates', async () => {
    vi.mocked(dossiersApi.createDossier).mockResolvedValue({
      id: 'd1',
      tenantId: 't',
      reference: 'DOS-1',
      dossierTypeCode: 'CLAIM',
      label: 'Claim',
      status: 'DRAFT',
      fields: {},
      currentWorkflowLinkId: null,
      version: 0,
      createdBy: 'u',
      createdAt: '',
      updatedBy: 'u',
      updatedAt: '',
      closedBy: null,
      closedAt: null,
      canEdit: true,
    })
    const router = createRouter({ history: createMemoryHistory(), routes })
    const push = vi.spyOn(router, 'push')
    await router.push('/dossiers/new')
    await router.isReady()
    const wrapper = mount(CreateDossierView, { global: { plugins: [router] } })
    await flushPromises()
    await wrapper.get('#dossier-label').setValue('Claim')
    await wrapper.get('form').trigger('submit.prevent')
    await flushPromises()
    expect(dossiersApi.createDossier).toHaveBeenCalled()
    expect(push).toHaveBeenCalledWith('/dossiers/d1')
  })

  it('shows a UNIQUE refusal under the field', async () => {
    vi.mocked(dossiersApi.createDossier).mockRejectedValue({
      response: {
        status: 400,
        data: {
          title: 'Dossier field validation failed',
          detail: 'plaque UNIQUE Value already used by dossier DOS-9',
          errors: [
            { field: 'priority', code: 'UNIQUE', message: 'Value already used by dossier DOS-9', reference: 'DOS-9' },
            { field: 'priority', code: 'REQUIRED', message: 'Field is required', reference: null },
          ],
        },
      },
    })
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/dossiers/new')
    await router.isReady()
    const wrapper = mount(CreateDossierView, { global: { plugins: [router] } })
    await flushPromises()
    await wrapper.get('#dossier-label').setValue('Claim')
    await wrapper.get('form').trigger('submit.prevent')
    await flushPromises()
    const alert = wrapper.get('[data-testid="field-error"]')
    expect(alert.text()).toContain('Déjà utilisé par le dossier DOS-9')
    expect(alert.text()).toContain('Field is required')
    expect(wrapper.text()).not.toContain('Value already used')
    expect(wrapper.find('.create-dossier__error').exists()).toBe(false)
  })

  it('keeps a detail-only error in the banner when the body has no errors[]', async () => {
    vi.mocked(dossiersApi.createDossier).mockRejectedValue({
      response: {
        status: 400,
        data: {
          title: 'Dossier type inactive',
          detail: 'Dossier type is inactive',
        },
      },
    })
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/dossiers/new')
    await router.isReady()
    const wrapper = mount(CreateDossierView, { global: { plugins: [router] } })
    await flushPromises()
    await wrapper.get('#dossier-label').setValue('Claim')
    await wrapper.get('form').trigger('submit.prevent')
    await flushPromises()
    const banner = wrapper.get('.create-dossier__error')
    expect(banner.attributes('role')).toBe('alert')
    expect(banner.text()).toContain('Dossier type is inactive')
    expect(wrapper.find('[data-testid="field-error"]').exists()).toBe(false)
  })

  it('keeps a UNIQUE error for a field hidden by a visibility rule in the banner (TODO §102)', async () => {
    vi.useFakeTimers()
    try {
      vi.mocked(typesApi.listAllDossierTypes).mockResolvedValue([
        {
          id: 't1',
          tenantId: 't',
          code: 'CLAIM',
          label: 'Sinistre',
          workflowKey: 'wf',
          active: true,
          allowUnknownFields: false,
          fieldDefinitions: [
            { name: 'priority', label: 'Priority', type: 'STRING', required: false },
            {
              name: 'siret',
              label: 'SIRET',
              type: 'STRING',
              required: false,
              visibleWhen: "dossier.fields.priority = 'FR'",
            },
          ],
        },
      ])
      vi.mocked(typesApi.evaluateFieldRules).mockResolvedValue({
        computed: {},
        hidden: ['siret'],
        requiredByRule: [],
        errors: [],
      })
      vi.mocked(dossiersApi.createDossier).mockRejectedValue({
        response: {
          status: 400,
          data: {
            detail: 'siret UNIQUE Value already used by dossier DOS-9',
            errors: [
              {
                field: 'siret',
                code: 'UNIQUE',
                message: 'Value already used by dossier DOS-9 SECRET-PLAQUE',
                reference: 'DOS-9',
              },
            ],
          },
        },
      })
      const router = createRouter({ history: createMemoryHistory(), routes })
      await router.push('/dossiers/new')
      await router.isReady()
      const wrapper = mount(CreateDossierView, { global: { plugins: [router] } })
      await flushPromises()
      await vi.advanceTimersByTimeAsync(350)
      await flushPromises()
      await wrapper.get('#dossier-label').setValue('Claim')
      await wrapper.get('form').trigger('submit.prevent')
      await flushPromises()

      expect(wrapper.find('[data-testid="field-error"]').exists()).toBe(false)
      const banner = wrapper.get('.create-dossier__error')
      expect(banner.text()).toContain('SIRET')
      expect(banner.text()).toContain('Déjà utilisé par le dossier DOS-9')
      expect(wrapper.text()).not.toContain('SECRET-PLAQUE')
      expect(wrapper.text()).not.toContain('Value already used')
    } finally {
      vi.useRealTimers()
    }
  })
})
