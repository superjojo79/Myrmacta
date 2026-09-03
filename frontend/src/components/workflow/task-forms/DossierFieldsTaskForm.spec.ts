import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import DossierFieldsTaskForm from './DossierFieldsTaskForm.vue'
import { getDossierById, updateDossierFields } from '@/api/dossiers'
import { getDossierType } from '@/api/dossierTypes'

vi.mock('@/api/dossiers', () => ({
  getDossierById: vi.fn(),
  updateDossierFields: vi.fn(),
}))
vi.mock('@/api/dossierTypes', () => ({
  getDossierType: vi.fn(),
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
