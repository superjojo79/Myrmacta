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
    expect(events?.[0]?.[0]).toEqual({ file, label: 'Justificatif', pieceKey: null })
    expect(wrapper.find('[data-testid="add-element-piece-type"]').exists()).toBe(false)
  })

  /** TODO §106 — le type de pièce est choisi au dépôt quand le type de dossier en déclare. */
  it('lets the agent pick a piece type for an attachment', async () => {
    const wrapper = mount(AddElementDialog, {
      props: {
        open: true,
        pieceTypes: [
          { key: 'preuveDomicile', label: 'Preuve de domicile', sensitive: false },
          { key: 'identite', label: "Pièce d'identité", sensitive: true },
        ],
      },
    })
    await wrapper.get('input[type="text"]').setValue('Facture Sibelga')
    const file = new File(['%PDF'], 'facture.pdf', { type: 'application/pdf' })
    const fileInput = wrapper.get('input[type="file"]').element as HTMLInputElement
    Object.defineProperty(fileInput, 'files', { value: [file] })
    await wrapper.get('input[type="file"]').trigger('change')
    const select = wrapper.get('[data-testid="add-element-piece-type"]')
    expect(select.text()).toContain('Non classée')
    await select.setValue('preuveDomicile')
    await wrapper.get('button.primary').trigger('click')

    expect(wrapper.emitted('submitAttachment')?.[0]?.[0]).toEqual({
      file,
      label: 'Facture Sibelga',
      pieceKey: 'preuveDomicile',
    })
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
