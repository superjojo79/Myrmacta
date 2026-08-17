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
})
