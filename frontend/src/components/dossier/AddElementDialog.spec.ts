import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AddElementDialog from './AddElementDialog.vue'

describe('AddElementDialog', () => {
  it('is hidden when closed', () => {
    const wrapper = mount(AddElementDialog, { props: { open: false } })
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })

  it('emits submitElement for a NOTE with label and text', async () => {
    const wrapper = mount(AddElementDialog, { props: { open: true } })
    await wrapper.get('select').setValue('NOTE')
    await wrapper.get('input[type="text"]').setValue('Rappel')
    await wrapper.get('textarea').setValue('A relancer le 20/08')
    await wrapper.get('button.primary').trigger('click')

    const events = wrapper.emitted('submitElement')
    expect(events).toHaveLength(1)
    expect(events?.[0]?.[0]).toEqual({
      type: 'NOTE',
      label: 'Rappel',
      metadata: { text: 'A relancer le 20/08' },
    })
  })

  it('rejects a LINK submission without a URL', async () => {
    const wrapper = mount(AddElementDialog, { props: { open: true } })
    await wrapper.get('select').setValue('LINK')
    await wrapper.get('input[type="text"]').setValue('Ticket')
    await wrapper.get('button.primary').trigger('click')

    expect(wrapper.emitted('submitElement')).toBeUndefined()
    expect(wrapper.text()).toContain('URL requise')
  })

  it('emits submitAttachment for ATTACHMENT with a selected file', async () => {
    const wrapper = mount(AddElementDialog, { props: { open: true } })
    await wrapper.get('input[type="text"]').setValue('Justificatif')
    const file = new File(['%PDF'], 'doc.pdf', { type: 'application/pdf' })
    const fileInput = wrapper.get('input[type="file"]').element as HTMLInputElement
    Object.defineProperty(fileInput, 'files', { value: [file] })
    await wrapper.get('input[type="file"]').trigger('change')
    await wrapper.get('button.primary').trigger('click')

    const events = wrapper.emitted('submitAttachment')
    expect(events).toHaveLength(1)
    expect(events?.[0]?.[0]).toEqual({ file, label: 'Justificatif' })
  })

  it('resets its fields each time it reopens', async () => {
    const wrapper = mount(AddElementDialog, { props: { open: false } })
    await wrapper.setProps({ open: true })
    await wrapper.get('input[type="text"]').setValue('Something')
    await wrapper.setProps({ open: false })
    await wrapper.setProps({ open: true })
    expect((wrapper.get('input[type="text"]').element as HTMLInputElement).value).toBe('')
  })
})
