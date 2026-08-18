import { defineComponent, shallowRef } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { previewHtml, previewPdf } from '@/api/documents'
import { useTemplatePreview } from './useTemplatePreview'

vi.mock('@/api/documents', () => ({
  previewHtml: vi.fn(),
  previewPdf: vi.fn(),
}))

describe('useTemplatePreview', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.mocked(previewHtml).mockReset().mockResolvedValue('<p>ok</p>')
    vi.mocked(previewPdf).mockReset().mockResolvedValue(new Blob(['%PDF'], { type: 'application/pdf' }))
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:preview'),
      revokeObjectURL: vi.fn(),
    })
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('debounces HTML preview by 400ms and aborts the previous fetch', async () => {
    const htmlContent = shallowRef('<p>one</p>')
    const { wrapper } = mountPreview(htmlContent)

    await vi.advanceTimersByTimeAsync(399)
    expect(previewHtml).not.toHaveBeenCalled()

    htmlContent.value = '<p>two</p>'
    await vi.advanceTimersByTimeAsync(400)
    await flushPromises()

    expect(previewHtml).toHaveBeenCalledTimes(1)
    expect(previewHtml).toHaveBeenCalledWith(
      { htmlContent: '<p>two</p>', variables: {} },
      expect.any(AbortSignal),
    )
    wrapper.unmount()
  })

  it('creates and revokes a PDF object URL', async () => {
    const htmlContent = shallowRef('<p>x</p>')
    const { wrapper, preview } = mountPreview(htmlContent)
    await vi.advanceTimersByTimeAsync(400)
    await flushPromises()

    await preview.previewPdf()
    expect(previewPdf).toHaveBeenCalledWith({ htmlContent: '<p>x</p>', variables: {} })
    expect(preview.pdfUrl.value).toBe('blob:preview')

    await preview.previewPdf()
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:preview')

    wrapper.unmount()
    expect(URL.revokeObjectURL).toHaveBeenCalled()
  })

  it('does not request a PDF when preview is disabled', async () => {
    const htmlContent = shallowRef('<p>x</p>')
    const { wrapper, preview } = mountPreview(htmlContent, { enabled: () => false })

    await preview.previewPdf()
    await flushPromises()

    expect(previewPdf).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})

function mountPreview(
  htmlContent: ReturnType<typeof shallowRef<string>>,
  extra: { enabled?: () => boolean } = {},
) {
  let preview!: ReturnType<typeof useTemplatePreview>
  const Comp = defineComponent({
    setup() {
      preview = useTemplatePreview({
        htmlContent,
        variables: () => ({}),
        enabled: extra.enabled,
      })
      return preview
    },
    template: '<div />',
  })
  return { wrapper: mount(Comp), preview }
}
