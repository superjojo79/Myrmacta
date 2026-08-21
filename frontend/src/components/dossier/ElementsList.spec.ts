import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementsList from './ElementsList.vue'

describe('ElementsList', () => {
  it('lists downloadable elements', () => {
    const wrapper = mount(ElementsList, {
      props: {
        elements: [
          {
            id: 'e1',
            type: 'PRODUCED_DOC',
            label: 'Rapport',
            addedBy: 'u',
            addedAt: '2026-08-12T00:00:00Z',
            workflowStepId: null,
            metadata: {},
            downloadable: true,
          },
        ],
      },
    })
    expect(wrapper.text()).toContain('Rapport')
    expect(wrapper.text()).toContain('Télécharger')
  })

  it('renders the actions slot', () => {
    const wrapper = mount(ElementsList, {
      props: { elements: [] },
      slots: { actions: '<button type="button">Ajouter</button>' },
    })
    expect(wrapper.get('button').text()).toBe('Ajouter')
  })

  it('shows a NOTE element inline and a LINK element as a link, neither downloadable', () => {
    const wrapper = mount(ElementsList, {
      props: {
        elements: [
          {
            id: 'e-note',
            type: 'NOTE',
            label: 'Rappel',
            addedBy: 'u',
            addedAt: '2026-08-16T00:00:00Z',
            workflowStepId: null,
            metadata: { text: 'A relancer' },
            downloadable: false,
          },
          {
            id: 'e-link',
            type: 'LINK',
            label: 'Ticket',
            addedBy: 'u',
            addedAt: '2026-08-16T00:00:00Z',
            workflowStepId: null,
            metadata: { url: 'https://example.com/1' },
            downloadable: false,
          },
        ],
      },
    })
    expect(wrapper.text()).toContain('A relancer')
    const link = wrapper.find('a.elements-list__link')
    expect(link.attributes('href')).toBe('https://example.com/1')
    expect(wrapper.findAll('a').every((a) => a.text() !== 'Télécharger')).toBe(true)
  })

  it('shows an INFORMATION element as key/value pairs, not downloadable', () => {
    const wrapper = mount(ElementsList, {
      props: {
        elements: [
          {
            id: 'e-info',
            type: 'INFORMATION',
            label: 'Vérification externe',
            addedBy: 'u',
            addedAt: '2026-08-16T00:00:00Z',
            workflowStepId: null,
            metadata: { statut: 'confirmé', score: 42 },
            downloadable: false,
          },
        ],
      },
    })
    const dl = wrapper.find('dl.elements-list__information')
    expect(dl.exists()).toBe(true)
    expect(dl.text()).toContain('statut')
    expect(dl.text()).toContain('confirmé')
    expect(dl.text()).toContain('score')
    expect(dl.text()).toContain('42')
    expect(wrapper.findAll('a').every((a) => a.text() !== 'Télécharger')).toBe(true)
  })

  it('shows the pager when there is more than one page and emits update:page', async () => {
    const wrapper = mount(ElementsList, {
      props: { elements: [], page: 0, totalPages: 3 },
    })
    expect(wrapper.find('[data-testid="admin-pager-next"]').exists()).toBe(true)
    await wrapper.get('[data-testid="admin-pager-next"]').trigger('click')
    expect(wrapper.emitted('update:page')?.[0]).toEqual([1])
  })

  it('hides the pager when there is a single page', () => {
    const wrapper = mount(ElementsList, {
      props: { elements: [], page: 0, totalPages: 1 },
    })
    expect(wrapper.find('[data-testid="admin-pager-next"]').exists()).toBe(false)
  })
})
