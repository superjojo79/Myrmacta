import { describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import DocumentGenerationTaskForm from './DocumentGenerationTaskForm.vue'
import { getDossierById } from '@/api/dossiers'
import { listDossierTypeDocumentTemplates } from '@/api/dossierTypes'

vi.mock('@/api/dossiers', () => ({
  getDossierById: vi.fn(),
}))
vi.mock('@/api/dossierTypes', () => ({
  listDossierTypeDocumentTemplates: vi.fn(),
}))

const DOSSIER = { id: 'd1', dossierTypeCode: 'CLAIM' }

describe('DocumentGenerationTaskForm', () => {
  it('loads allowed templates for the dossier type and lets the user pick one', async () => {
    vi.mocked(getDossierById).mockResolvedValue(DOSSIER as never)
    vi.mocked(listDossierTypeDocumentTemplates).mockResolvedValue([
      { templateKey: 'invoice', variantKey: 'premium', locale: 'fr', label: 'Facture premium FR' },
    ])

    const wrapper = mount(DocumentGenerationTaskForm, { props: { dossierId: 'd1' } })
    await flushPromises()

    expect(listDossierTypeDocumentTemplates).toHaveBeenCalledWith('CLAIM')
    expect(wrapper.text()).toContain('Facture premium FR')
  })

  it('shows a message and disables submission when no template is available', async () => {
    vi.mocked(getDossierById).mockResolvedValue(DOSSIER as never)
    vi.mocked(listDossierTypeDocumentTemplates).mockResolvedValue([])

    const wrapper = mount(DocumentGenerationTaskForm, { props: { dossierId: 'd1' } })
    await flushPromises()

    expect(wrapper.text()).toContain('Aucun template disponible')
    expect(wrapper.find('button').exists()).toBe(false)
  })

  it('emits ready with documentType/documentVariant/documentLocale — the variable names read by the ${documentGenerationTask} serviceTask (DocumentGenerationTask.java), not templateKey/variantKey/locale', async () => {
    vi.mocked(getDossierById).mockResolvedValue(DOSSIER as never)
    vi.mocked(listDossierTypeDocumentTemplates).mockResolvedValue([
      { templateKey: 'invoice', variantKey: 'premium', locale: 'fr', label: 'Facture premium FR' },
    ])

    const wrapper = mount(DocumentGenerationTaskForm, { props: { dossierId: 'd1' } })
    await flushPromises()

    await wrapper.get('select').setValue('0')
    await wrapper.get('button').trigger('click')

    expect(wrapper.emitted('ready')?.[0]).toEqual([
      { documentType: 'invoice', documentVariant: 'premium', documentLocale: 'fr' },
    ])
  })
})
