import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import TemplateVisualEditor from './TemplateVisualEditor.vue'

const SAMPLE = { client: { nom: 'Ada' }, lignes: [{ libelle: 'A', montant: 1 }] }

describe('TemplateVisualEditor', () => {
  it('opens a compatible template in visual mode', async () => {
    const wrapper = mount(TemplateVisualEditor, { props: { modelValue: '<p>Bonjour <span th:text="${client.nom}">x</span></p>', sampleVariables: SAMPLE } })
    await flushPromises()
    expect(wrapper.find('[data-testid="source-notice"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="tb-bold"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('keeps an incompatible template in source mode, untouched, and says why', async () => {
    const html = '<div th:with="x=${a}"><script>1</script></div>'
    const wrapper = mount(TemplateVisualEditor, { props: { modelValue: html, sampleVariables: SAMPLE } })
    await flushPromises()
    expect(wrapper.find('[data-testid="source-notice"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="tb-bold"]').exists()).toBe(false)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    // Le retour au visuel est refusé tant que le template est hors sous-ensemble.
    await wrapper.find('[data-testid="mode-visual"]').trigger('click')
    expect(wrapper.find('[data-testid="tb-bold"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('emits the edited source from the textarea', async () => {
    const wrapper = mount(TemplateVisualEditor, { props: { modelValue: '<p>a</p>', sampleVariables: SAMPLE } })
    await flushPromises()
    await wrapper.find('[data-testid="mode-source"]').trigger('click')
    const area = wrapper.find('textarea')
    await area.setValue('<p>b</p>')
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['<p>b</p>'])
    wrapper.unmount()
  })
})
