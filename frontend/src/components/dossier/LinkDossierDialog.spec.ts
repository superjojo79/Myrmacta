import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import LinkDossierDialog from './LinkDossierDialog.vue'
import * as dossiersApi from '@/api/dossiers'

vi.mock('@/api/dossiers')

describe('LinkDossierDialog', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('searches, submits selected target, and surfaces errors', async () => {
    vi.mocked(dossiersApi.listDossiers).mockResolvedValue({
      content: [
        {
          id: 'd2',
          reference: 'DOS-2',
          label: 'Cible',
          dossierTypeCode: 'FAC',
          dossierTypeLabel: 'Facture',
          status: 'OUVERT',
          createdAt: '',
          updatedAt: '',
        },
      ],
      page: 0,
      size: 10,
      totalElements: 1,
      totalPages: 1,
    })
    const wrapper = mount(LinkDossierDialog, {
      props: { open: true, currentDossierId: 'd1' },
    })
    await wrapper.get('input').setValue('DOS')
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(dossiersApi.listDossiers).toHaveBeenCalledWith({ reference: 'DOS', size: 10 })
    await wrapper.get('.results button').trigger('click')
    await wrapper.get('select').setValue('PARENT_CHILD')
    await wrapper.get('button.primary').trigger('click')
    expect(wrapper.emitted('submit')?.[0]).toEqual([
      { targetDossierId: 'd2', type: 'PARENT_CHILD' },
    ])
  })

  it('filters the current dossier and surfaces search errors', async () => {
    vi.mocked(dossiersApi.listDossiers).mockResolvedValueOnce({
      content: [
        {
          id: 'd1',
          reference: 'DOS-1',
          label: 'Courant',
          dossierTypeCode: 'FAC',
          dossierTypeLabel: 'Facture',
          status: 'OUVERT',
          createdAt: '',
          updatedAt: '',
        },
        {
          id: 'd2',
          reference: 'DOS-2',
          label: 'Cible',
          dossierTypeCode: 'FAC',
          dossierTypeLabel: 'Facture',
          status: 'OUVERT',
          createdAt: '',
          updatedAt: '',
        },
      ],
      page: 0,
      size: 10,
      totalElements: 2,
      totalPages: 1,
    })
    const wrapper = mount(LinkDossierDialog, {
      props: { open: true, currentDossierId: 'd1' },
    })
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Cible')
    expect(wrapper.text()).not.toContain('Courant')

    vi.mocked(dossiersApi.listDossiers).mockRejectedValueOnce({
      response: { status: 403, data: { detail: 'Recherche interdite', status: 403 } },
    })
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Recherche interdite')
  })

  it('shows API error on 409', async () => {
    vi.mocked(dossiersApi.listDossiers).mockResolvedValue({
      content: [],
      page: 0,
      size: 10,
      totalElements: 0,
      totalPages: 0,
    })
    const wrapper = mount(LinkDossierDialog, {
      props: { open: true, currentDossierId: 'd1' },
    })
    await wrapper.get('button.primary').trigger('click')
    expect(wrapper.text()).toContain('Sélectionnez un dossier cible')
  })
})
