import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import TemplatePreviewPanel from './TemplatePreviewPanel.vue'
import { previewHtml, previewPdf } from '@/api/documents'

vi.mock('@/api/documents', () => ({
  previewHtml: vi.fn().mockResolvedValue('<p>ok</p>'),
  previewPdf: vi.fn().mockResolvedValue(new Blob(['%PDF'], { type: 'application/pdf' })),
}))

describe('TemplatePreviewPanel', () => {
  beforeEach(() => {
    vi.mocked(previewHtml).mockClear()
    vi.mocked(previewPdf).mockClear()
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:pdf'),
      revokeObjectURL: vi.fn(),
    })
  })

  it('renders a sandboxed iframe and a PDF button', async () => {
    const wrapper = mount(TemplatePreviewPanel, { props: { htmlContent: '<p>Hi</p>' } })
    const iframe = wrapper.get('iframe')
    expect(iframe.attributes('sandbox')).toBe('')
    expect(iframe.attributes('srcdoc')).toBeDefined()
    expect(wrapper.get('button').text()).toBe('Aperçu PDF')
    wrapper.unmount()
  })

  it('does not call the API when variables JSON is invalid', async () => {
    vi.useFakeTimers()
    const wrapper = mount(TemplatePreviewPanel, { props: { htmlContent: '<p>Hi</p>' } })
    await wrapper.get('textarea').setValue('{not-json')
    await vi.advanceTimersByTimeAsync(400)
    await flushPromises()
    expect(wrapper.text()).toContain('JSON invalide')
    expect(previewHtml).not.toHaveBeenCalled()
    expect(wrapper.get('button').attributes('disabled')).toBeDefined()
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(previewPdf).not.toHaveBeenCalled()
    wrapper.unmount()
    vi.useRealTimers()
  })
})
