import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PortalQuestionForm from './PortalQuestionForm.vue'

describe('PortalQuestionForm layout (LC4a)', () => {
  it('shows sections, half-width fields and help text as plain text', () => {
    const wrapper = mount(PortalQuestionForm, {
      props: {
        modelValue: {},
        fields: [
          { name: 'a', label: 'A', type: 'STRING', required: false, group: 'Contact', width: 'HALF', helpText: 'Votre <b>nom</b>' },
          { name: 'b', label: 'B', type: 'STRING', required: false, group: 'Contact', width: 'HALF' },
          { name: 'c', label: 'C', type: 'STRING', required: false, group: 'Suite' },
        ],
      },
    })
    expect(wrapper.findAll('h3').map((h) => h.text())).toEqual(['Contact', 'Suite'])
    expect(wrapper.findAll('.portal-form__field--half')).toHaveLength(2)
    const help = wrapper.get('.portal-form__help')
    expect(help.text()).toBe('Votre <b>nom</b>') // texte brut : aucune balise interprétée
    expect(help.find('b').exists()).toBe(false)
  })
})
