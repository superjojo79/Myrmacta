import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementsList from './ElementsList.vue'

describe('ElementsList', () => {
  it('disables add and lists downloadable elements', () => {
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
    expect(wrapper.get('button').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('Rapport')
    expect(wrapper.text()).toContain('Télécharger')
  })
})
