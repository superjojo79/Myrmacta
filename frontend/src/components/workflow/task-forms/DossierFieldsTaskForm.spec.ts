import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import DossierFieldsTaskForm from './DossierFieldsTaskForm.vue'
import { getDossierById, updateDossierFields } from '@/api/dossiers'
import { evaluateFieldRules, getDossierType } from '@/api/dossierTypes'

vi.mock('@/api/dossiers', () => ({
  getDossierById: vi.fn(),
  updateDossierFields: vi.fn(),
}))
vi.mock('@/api/dossierTypes', () => ({
  getDossierType: vi.fn(),
  evaluateFieldRules: vi.fn(),
}))

beforeEach(() => {
  vi.clearAllMocks()
})

const DOSSIER = {
  id: 'd1',
  tenantId: 'tenant_dev',
  reference: 'DOS-1',
  dossierTypeCode: 'CLAIM',
  label: 'Claim',
  status: 'EN_COURS',
  fields: { priority: 'NORMAL', notes: 'unchanged' },
  currentWorkflowLinkId: null,
  version: 3,
  createdBy: 'u',
  createdAt: '2026-01-01T00:00:00Z',
  updatedBy: 'u',
  updatedAt: '2026-01-01T00:00:00Z',
  closedBy: null,
  closedAt: null,
  canEdit: true,
}

const DOSSIER_TYPE = {
  id: 't1',
  tenantId: 'tenant_dev',
  code: 'CLAIM',
  label: 'Claim',
  workflowKey: 'wf-claim',
  active: true,
  allowUnknownFields: false,
  fieldDefinitions: [
    { name: 'priority', label: 'Priority', type: 'STRING', required: true },
    { name: 'notes', label: 'Notes', type: 'STRING', required: false },
  ],
}

describe('DossierFieldsTaskForm', () => {
  it('loads the dossier and its type schema, then renders the fields', async () => {
    vi.mocked(getDossierById).mockResolvedValue(DOSSIER as never)
    vi.mocked(getDossierType).mockResolvedValue(DOSSIER_TYPE as never)

    const wrapper = mount(DossierFieldsTaskForm, { props: { dossierId: 'd1' } })
    await flushPromises()

    expect(getDossierById).toHaveBeenCalledWith('d1')
    expect(getDossierType).toHaveBeenCalledWith('CLAIM')
    expect(wrapper.findAll('input')[0].element).toHaveProperty('value', 'NORMAL')
  })

  it('submits only the changed fields (a diff patch), not the full field state, and emits ready on success', async () => {
    vi.mocked(getDossierById).mockResolvedValue(DOSSIER as never)
    vi.mocked(getDossierType).mockResolvedValue(DOSSIER_TYPE as never)
    vi.mocked(updateDossierFields).mockResolvedValue({
      ...DOSSIER,
      fields: { priority: 'HIGH', notes: 'unchanged' },
      version: 4,
    } as never)

    const wrapper = mount(DossierFieldsTaskForm, { props: { dossierId: 'd1' } })
    await flushPromises()

    await wrapper.findAll('input')[0].setValue('HIGH')
    await wrapper.get('button').trigger('click')
    await flushPromises()

    // `notes` never changed -- must not appear in the PATCH body even though the component
    // holds a full draft of every field internally (DynamicFields v-model).
    expect(updateDossierFields).toHaveBeenCalledWith('d1', { priority: 'HIGH' }, 3)
    expect(wrapper.emitted('ready')?.[0]).toEqual([{}])
  })

  it('skips the PATCH call entirely and completes immediately when nothing changed', async () => {
    vi.mocked(getDossierById).mockResolvedValue(DOSSIER as never)
    vi.mocked(getDossierType).mockResolvedValue(DOSSIER_TYPE as never)

    const wrapper = mount(DossierFieldsTaskForm, { props: { dossierId: 'd1' } })
    await flushPromises()

    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(updateDossierFields).not.toHaveBeenCalled()
    expect(wrapper.emitted('ready')?.[0]).toEqual([{}])
  })

  it('coerces a NUMBER field to an actual number in the PATCH body, not the raw input string', async () => {
    const dossierWithAmount = {
      ...DOSSIER,
      fields: { priority: 'NORMAL', amount: 100 },
    }
    const typeWithAmount = {
      ...DOSSIER_TYPE,
      fieldDefinitions: [
        { name: 'priority', label: 'Priority', type: 'STRING', required: true },
        { name: 'amount', label: 'Amount', type: 'NUMBER', required: false },
      ],
    }
    vi.mocked(getDossierById).mockResolvedValue(dossierWithAmount as never)
    vi.mocked(getDossierType).mockResolvedValue(typeWithAmount as never)
    vi.mocked(updateDossierFields).mockResolvedValue(dossierWithAmount as never)

    const wrapper = mount(DossierFieldsTaskForm, { props: { dossierId: 'd1' } })
    await flushPromises()

    const amountInput = wrapper.findAll('input').find((i) => i.element.type === 'number')
    await amountInput!.setValue('1500')
    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(updateDossierFields).toHaveBeenCalledWith('d1', { amount: 1500 }, 3)
  })

  it('never submits a redacted (absent) sensitive field that was left untouched, even after being typed into and cleared', async () => {
    // Simulates DossierFieldRedactor stripping a `sensitive` field's key entirely from the
    // response for a non-TENANT_ADMIN actor: `iban` is defined on the type but absent from
    // `fields`. Typing into the empty input and then clearing it must not resurrect the key as
    // an explicit "" in the PATCH -- that would silently wipe the real (unseen) value server-side.
    const dossierWithRedactedIban = {
      ...DOSSIER,
      fields: { priority: 'NORMAL' },
    }
    const typeWithIban = {
      ...DOSSIER_TYPE,
      fieldDefinitions: [
        { name: 'priority', label: 'Priority', type: 'STRING', required: true },
        { name: 'iban', label: 'IBAN', type: 'STRING', required: false, sensitive: true },
      ],
    }
    vi.mocked(getDossierById).mockResolvedValue(dossierWithRedactedIban as never)
    vi.mocked(getDossierType).mockResolvedValue(typeWithIban as never)
    vi.mocked(updateDossierFields).mockResolvedValue({
      ...dossierWithRedactedIban,
      fields: { priority: 'HIGH' },
      version: 4,
    } as never)

    const wrapper = mount(DossierFieldsTaskForm, { props: { dossierId: 'd1' } })
    await flushPromises()

    const ibanInput = wrapper.findAll('input')[1]
    await ibanInput.setValue('FR7600001')
    await ibanInput.setValue('')
    await wrapper.findAll('input')[0].setValue('HIGH')
    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(updateDossierFields).toHaveBeenCalledWith('d1', { priority: 'HIGH' }, 3)
  })

  it('refreshes its expected version from the PATCH response, so a second submit uses the new version', async () => {
    vi.mocked(getDossierById).mockResolvedValue(DOSSIER as never)
    vi.mocked(getDossierType).mockResolvedValue(DOSSIER_TYPE as never)
    vi.mocked(updateDossierFields).mockResolvedValue({
      ...DOSSIER,
      fields: { priority: 'HIGH', notes: 'unchanged' },
      version: 4,
    } as never)

    const wrapper = mount(DossierFieldsTaskForm, { props: { dossierId: 'd1' } })
    await flushPromises()

    await wrapper.findAll('input')[0].setValue('HIGH')
    await wrapper.get('button').trigger('click')
    await flushPromises()

    await wrapper.findAll('input')[0].setValue('URGENT')
    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(updateDossierFields).toHaveBeenLastCalledWith('d1', { priority: 'URGENT' }, 4)
  })

  it('shows UNIQUE under the field and does not emit the holder reference', async () => {
    vi.mocked(getDossierById).mockResolvedValue(DOSSIER as never)
    vi.mocked(getDossierType).mockResolvedValue(DOSSIER_TYPE as never)
    vi.mocked(updateDossierFields).mockRejectedValue({
      response: {
        data: {
          detail: 'priority UNIQUE Value already used by dossier DOS-9',
          errors: [{ field: 'priority', code: 'UNIQUE', message: 'Value already used by dossier DOS-9', reference: 'DOS-9' }],
        },
      },
    })
    const wrapper = mount(DossierFieldsTaskForm, { props: { dossierId: 'd1' } })
    await flushPromises()
    await wrapper.findAll('input')[0].setValue('HIGH')
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="field-error"]').text()).toContain('Déjà utilisé par le dossier DOS-9')
    expect(wrapper.text()).not.toContain('Value already used')
    expect(wrapper.emitted('ready')).toBeUndefined()
    const emitted = (wrapper.emitted('error') ?? []).flat().join(' ')
    expect(emitted).not.toContain('DOS-9')
  })

  it('emits a translated UNIQUE error when the field is hidden by a visibility rule (TODO §102)', async () => {
    vi.useFakeTimers()
    try {
      vi.mocked(getDossierById).mockResolvedValue(DOSSIER as never)
      vi.mocked(getDossierType).mockResolvedValue({
        ...DOSSIER_TYPE,
        fieldDefinitions: [
          ...DOSSIER_TYPE.fieldDefinitions,
          { name: 'siret', label: 'SIRET', type: 'STRING', required: false, visibleWhen: "dossier.fields.priority = 'FR'" },
        ],
      } as never)
      vi.mocked(evaluateFieldRules).mockResolvedValue({ computed: {}, hidden: ['siret'], requiredByRule: [], errors: [] })
      vi.mocked(updateDossierFields).mockRejectedValue({
        response: {
          data: {
            detail: 'siret UNIQUE Value already used by dossier DOS-9',
            errors: [{ field: 'siret', code: 'UNIQUE', message: 'Value already used by dossier DOS-9 SECRET-PLAQUE', reference: 'DOS-9' }],
          },
        },
      })
      const wrapper = mount(DossierFieldsTaskForm, { props: { dossierId: 'd1' } })
      await flushPromises()
      await vi.advanceTimersByTimeAsync(350)
      await flushPromises()
      await wrapper.findAll('input')[0].setValue('HIGH')
      await wrapper.get('button').trigger('click')
      await flushPromises()

      expect(wrapper.find('[data-testid="field-error"]').exists()).toBe(false)
      expect(wrapper.emitted('ready')).toBeUndefined()
    const emitted = (wrapper.emitted('error') ?? []).flat().join(' ')
    expect(emitted).toContain('SIRET')
    expect(emitted).toContain('Déjà utilisé par le dossier DOS-9')
    expect(emitted).not.toContain('SECRET-PLAQUE')
    expect(emitted).not.toContain('Value already used')
    } finally {
      vi.useRealTimers()
    }
  })

  it('retracts the previous holder when the next refusal is shown under a visible field (TODO §102)', async () => {
    vi.useFakeTimers()
    try {
      vi.mocked(getDossierById).mockResolvedValue(DOSSIER as never)
      vi.mocked(getDossierType).mockResolvedValue({
        ...DOSSIER_TYPE,
        fieldDefinitions: [
          ...DOSSIER_TYPE.fieldDefinitions,
          { name: 'siret', label: 'SIRET', type: 'STRING', required: false, visibleWhen: "dossier.fields.priority = 'FR'" },
        ],
      } as never)
      vi.mocked(evaluateFieldRules).mockResolvedValue({ computed: {}, hidden: ['siret'], requiredByRule: [], errors: [] })
      vi.mocked(updateDossierFields)
        .mockRejectedValueOnce({
          response: {
            data: {
              detail: 'siret UNIQUE Value already used by dossier DOS-9',
              errors: [{ field: 'siret', code: 'UNIQUE', message: 'Value already used by dossier DOS-9', reference: 'DOS-9' }],
            },
          },
        })
        .mockRejectedValueOnce({
          response: {
            data: {
              detail: 'priority UNIQUE Value already used by dossier DOS-2',
              errors: [{ field: 'priority', code: 'UNIQUE', message: 'Value already used by dossier DOS-2', reference: 'DOS-2' }],
            },
          },
        })
      const wrapper = mount(DossierFieldsTaskForm, { props: { dossierId: 'd1' } })
      await flushPromises()
      await vi.advanceTimersByTimeAsync(350)
      await flushPromises()
      await wrapper.findAll('input')[0].setValue('HIGH')
      await wrapper.get('button').trigger('click')
      await flushPromises()
      await wrapper.findAll('input')[0].setValue('LOW')
      await wrapper.get('button').trigger('click')
      await flushPromises()

      const events = wrapper.emitted('error') ?? []
      expect(events.at(-1)?.[0] ?? '').not.toContain('DOS-9')
      expect(events.at(-1)?.[0]).toBeNull()
      expect(wrapper.get('[data-testid="field-error"]').text()).toContain('Déjà utilisé par le dossier DOS-2')
      expect(wrapper.text()).not.toContain('DOS-9')
    } finally {
      vi.useRealTimers()
    }
  })

  it('retracts the banner when a hidden field becomes visible again (TODO §102)', async () => {
    vi.useFakeTimers()
    try {
      vi.mocked(getDossierById).mockResolvedValue(DOSSIER as never)
      vi.mocked(getDossierType).mockResolvedValue({
        ...DOSSIER_TYPE,
        fieldDefinitions: [
          ...DOSSIER_TYPE.fieldDefinitions,
          { name: 'siret', label: 'SIRET', type: 'STRING', required: false, visibleWhen: "dossier.fields.priority = 'FR'" },
        ],
      } as never)
      vi.mocked(evaluateFieldRules)
        .mockResolvedValueOnce({ computed: {}, hidden: ['siret'], requiredByRule: [], errors: [] })
        .mockResolvedValue({ computed: {}, hidden: [], requiredByRule: [], errors: [] })
      vi.mocked(updateDossierFields).mockRejectedValue({
        response: {
          data: {
            detail: 'siret UNIQUE Value already used by dossier DOS-9',
            errors: [{ field: 'siret', code: 'UNIQUE', message: 'Value already used by dossier DOS-9', reference: 'DOS-9' }],
          },
        },
      })
      const wrapper = mount(DossierFieldsTaskForm, { props: { dossierId: 'd1' } })
      await flushPromises()
      await vi.advanceTimersByTimeAsync(350)
      await flushPromises()
      await wrapper.findAll('input')[0].setValue('HIGH')
      await wrapper.get('button').trigger('click')
      await flushPromises()
      await wrapper.findAll('input')[0].setValue('LOW')
      await vi.advanceTimersByTimeAsync(350)
      await flushPromises()

      const events = wrapper.emitted('error') ?? []
      expect(events.at(-1)?.[0]).toBeNull()
      expect(wrapper.get('[data-testid="field-error"]').text()).toContain('Déjà utilisé par le dossier DOS-9')
    } finally {
      vi.useRealTimers()
    }
  })

  it('drops the off-screen banner once a successful patch clears the hidden field (TODO §102)', async () => {
    vi.useFakeTimers()
    try {
      vi.mocked(getDossierById).mockResolvedValue({
        ...DOSSIER,
        fields: { priority: 'NORMAL', notes: 'unchanged', siret: '12345678901234' },
      } as never)
      vi.mocked(getDossierType).mockResolvedValue({
        ...DOSSIER_TYPE,
        fieldDefinitions: [
          ...DOSSIER_TYPE.fieldDefinitions,
          { name: 'siret', label: 'SIRET', type: 'STRING', required: false, visibleWhen: "dossier.fields.priority = 'FR'" },
        ],
      } as never)
      vi.mocked(evaluateFieldRules).mockResolvedValue({ computed: {}, hidden: ['siret'], requiredByRule: [], errors: [] })
      vi.mocked(updateDossierFields)
        .mockRejectedValueOnce({
          response: {
            data: {
              detail: 'siret UNIQUE Value already used by dossier DOS-9',
              errors: [{ field: 'siret', code: 'UNIQUE', message: 'Value already used by dossier DOS-9', reference: 'DOS-9' }],
            },
          },
        })
        .mockResolvedValueOnce({
          ...DOSSIER,
          fields: { priority: 'LOW', notes: 'unchanged' },
          version: 4,
        } as never)
      const wrapper = mount(DossierFieldsTaskForm, { props: { dossierId: 'd1' } })
      await flushPromises()
      await vi.advanceTimersByTimeAsync(350)
      await flushPromises()
      await wrapper.findAll('input')[0].setValue('LOW')
      await wrapper.get('button').trigger('click')
      await flushPromises()

      expect(updateDossierFields).toHaveBeenCalledWith('d1', { priority: 'LOW', siret: null }, 3)
      expect(wrapper.get('[data-testid="task-form-banner"]').text()).toContain('DOS-9')

      await wrapper.get('button').trigger('click')
      await flushPromises()

      expect(updateDossierFields).toHaveBeenLastCalledWith('d1', { priority: 'LOW', siret: null }, 3)
      expect(wrapper.find('[data-testid="task-form-banner"]').exists()).toBe(false)
      expect(wrapper.text()).not.toContain('DOS-9')
    } finally {
      vi.useRealTimers()
    }
  })

  it('keeps only the off-screen error the successful patch did not touch (TODO §102)', async () => {
    vi.useFakeTimers()
    try {
      vi.mocked(getDossierById).mockResolvedValue({
        ...DOSSIER,
        fields: { priority: 'NORMAL', notes: 'unchanged', siret: '12345678901234' },
      } as never)
      vi.mocked(getDossierType).mockResolvedValue({
        ...DOSSIER_TYPE,
        fieldDefinitions: [
          ...DOSSIER_TYPE.fieldDefinitions,
          { name: 'siret', label: 'SIRET', type: 'STRING', required: false, visibleWhen: "dossier.fields.priority = 'FR'" },
        ],
      } as never)
      vi.mocked(evaluateFieldRules).mockResolvedValue({ computed: {}, hidden: ['siret'], requiredByRule: [], errors: [] })
      vi.mocked(updateDossierFields)
        .mockRejectedValueOnce({
          response: {
            data: {
              detail: 'siret UNIQUE',
              errors: [
                { field: 'siret', code: 'UNIQUE', message: 'Value already used by dossier DOS-9', reference: 'DOS-9' },
                { field: 'legacy', code: 'UNIQUE', message: 'Value already used by dossier DOS-8', reference: 'DOS-8' },
              ],
            },
          },
        })
        .mockResolvedValueOnce({
          ...DOSSIER,
          fields: { priority: 'LOW', notes: 'unchanged' },
          version: 4,
        } as never)
      const wrapper = mount(DossierFieldsTaskForm, { props: { dossierId: 'd1' } })
      await flushPromises()
      await vi.advanceTimersByTimeAsync(350)
      await flushPromises()
      await wrapper.findAll('input')[0].setValue('LOW')
      await wrapper.get('button').trigger('click')
      await flushPromises()
      expect(wrapper.get('[data-testid="task-form-banner"]').text()).toContain('DOS-9')
      expect(wrapper.get('[data-testid="task-form-banner"]').text()).toContain('DOS-8')

      await wrapper.get('button').trigger('click')
      await flushPromises()

      const banner = wrapper.get('[data-testid="task-form-banner"]').text()
      expect(banner).toContain('DOS-8')
      expect(banner).not.toContain('DOS-9')
    } finally {
      vi.useRealTimers()
    }
  })

  it('keeps an untouched out-of-schema banner when the successful patch does not mention that field (TODO §102)', async () => {
    vi.mocked(getDossierById).mockResolvedValue(DOSSIER as never)
    vi.mocked(getDossierType).mockResolvedValue(DOSSIER_TYPE as never)
    vi.mocked(updateDossierFields)
      .mockRejectedValueOnce({
        response: {
          data: {
            detail: 'legacy UNIQUE Value already used by dossier DOS-9',
            errors: [{ field: 'legacy', code: 'UNIQUE', message: 'Value already used by dossier DOS-9', reference: 'DOS-9' }],
          },
        },
      })
      .mockResolvedValueOnce({
        ...DOSSIER,
        fields: { priority: 'LOW', notes: 'unchanged' },
        version: 4,
      } as never)
    const wrapper = mount(DossierFieldsTaskForm, { props: { dossierId: 'd1' } })
    await flushPromises()
    await wrapper.findAll('input')[0].setValue('HIGH')
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="task-form-banner"]').text()).toContain('DOS-9')

    await wrapper.findAll('input')[0].setValue('LOW')
    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(updateDossierFields).toHaveBeenLastCalledWith('d1', { priority: 'LOW' }, 3)
    expect(wrapper.get('[data-testid="task-form-banner"]').text()).toContain('DOS-9')
  })

  it('keeps the off-screen banner when the next submit has an empty patch (TODO §102)', async () => {
    vi.mocked(getDossierById).mockResolvedValue(DOSSIER as never)
    vi.mocked(getDossierType).mockResolvedValue(DOSSIER_TYPE as never)
    vi.mocked(updateDossierFields).mockRejectedValue({
      response: {
        data: {
          detail: 'legacy UNIQUE Value already used by dossier DOS-9',
          errors: [{ field: 'legacy', code: 'UNIQUE', message: 'Value already used by dossier DOS-9', reference: 'DOS-9' }],
        },
      },
    })
    const wrapper = mount(DossierFieldsTaskForm, { props: { dossierId: 'd1' } })
    await flushPromises()
    await wrapper.findAll('input')[0].setValue('HIGH')
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="task-form-banner"]').text()).toContain('DOS-9')

    await wrapper.findAll('input')[0].setValue('NORMAL')
    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(updateDossierFields).toHaveBeenCalledTimes(1)
    expect(wrapper.emitted('ready')).toHaveLength(1)
    expect(wrapper.get('[data-testid="task-form-banner"]').text()).toContain('DOS-9')
  })

  it('emits error and does not emit ready when the PATCH fails', async () => {
    vi.mocked(getDossierById).mockResolvedValue(DOSSIER as never)
    vi.mocked(getDossierType).mockResolvedValue(DOSSIER_TYPE as never)
    vi.mocked(updateDossierFields).mockRejectedValue({
      response: { data: { detail: 'Conflit de version' } },
    })

    const wrapper = mount(DossierFieldsTaskForm, { props: { dossierId: 'd1' } })
    await flushPromises()

    await wrapper.findAll('input')[0].setValue('HIGH')
    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(wrapper.emitted('ready')).toBeUndefined()
    expect(wrapper.emitted('error')?.[0]).toEqual(['Conflit de version'])
  })
})
