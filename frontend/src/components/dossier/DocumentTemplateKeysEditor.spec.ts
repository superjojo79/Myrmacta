import { describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import DocumentTemplateKeysEditor from './DocumentTemplateKeysEditor.vue'
import { listDocumentTemplates } from '@/api/admin'

vi.mock('@/api/admin', () => ({
  listDocumentTemplates: vi.fn(),
}))

function page(content: unknown[]) {
  return { content, page: 0, size: 100, totalElements: content.length, totalPages: 1 }
}

describe('DocumentTemplateKeysEditor', () => {
  it('lists distinct ACTIVE template keys, ignoring DRAFT/ARCHIVED and duplicate versions', async () => {
    vi.mocked(listDocumentTemplates).mockResolvedValue(
      page([
        { templateKey: 'invoice', status: 'ACTIVE' },
        { templateKey: 'invoice', status: 'ARCHIVED' },
        { templateKey: 'contract', status: 'ACTIVE' },
        { templateKey: 'draft-only', status: 'DRAFT' },
      ]) as never,
    )

    const wrapper = mount(DocumentTemplateKeysEditor, { props: { modelValue: [] } })
    await flushPromises()

    expect(wrapper.findAll('[data-testid="document-template-key-row"]')).toHaveLength(2)
    expect(wrapper.text()).toContain('invoice')
    expect(wrapper.text()).toContain('contract')
    expect(wrapper.text()).not.toContain('draft-only')
  })

  it('checks boxes matching modelValue', async () => {
    vi.mocked(listDocumentTemplates).mockResolvedValue(
      page([{ templateKey: 'invoice', status: 'ACTIVE' }]) as never,
    )

    const wrapper = mount(DocumentTemplateKeysEditor, { props: { modelValue: ['invoice'] } })
    await flushPromises()

    expect(wrapper.get('[data-testid="document-template-key-invoice"]').element).toHaveProperty(
      'checked',
      true,
    )
  })

  it('emits update:modelValue with the key added when checked', async () => {
    vi.mocked(listDocumentTemplates).mockResolvedValue(
      page([{ templateKey: 'invoice', status: 'ACTIVE' }]) as never,
    )

    const wrapper = mount(DocumentTemplateKeysEditor, { props: { modelValue: [] } })
    await flushPromises()

    await wrapper.get('[data-testid="document-template-key-invoice"]').setValue(true)

    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([['invoice']])
  })

  it('emits update:modelValue with the key removed when unchecked', async () => {
    vi.mocked(listDocumentTemplates).mockResolvedValue(
      page([{ templateKey: 'invoice', status: 'ACTIVE' }]) as never,
    )

    const wrapper = mount(DocumentTemplateKeysEditor, { props: { modelValue: ['invoice'] } })
    await flushPromises()

    await wrapper.get('[data-testid="document-template-key-invoice"]').setValue(false)

    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([[]])
  })
})
