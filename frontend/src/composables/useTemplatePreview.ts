import { onUnmounted, shallowRef, toValue, watch, type MaybeRefOrGetter } from 'vue'
import { previewHtml, previewPdf } from '@/api/documents'
import { toProblemDetail } from '@/api/http'

const DEBOUNCE_MS = 400

export function useTemplatePreview(options: {
  htmlContent: MaybeRefOrGetter<string>
  variables: MaybeRefOrGetter<Record<string, unknown>>
  enabled?: MaybeRefOrGetter<boolean>
}) {
  const html = shallowRef('')
  const pdfUrl = shallowRef<string | null>(null)
  const loading = shallowRef(false)
  const error = shallowRef<string | null>(null)

  let timer: ReturnType<typeof setTimeout> | undefined
  let abort: AbortController | undefined

  watch(
    () =>
      [
        toValue(options.htmlContent),
        toValue(options.variables),
        options.enabled === undefined ? true : toValue(options.enabled),
      ] as const,
    ([, , enabled]) => {
      if (timer !== undefined) {
        clearTimeout(timer)
        timer = undefined
      }
      if (!enabled) {
        abort?.abort()
        return
      }
      timer = setTimeout(() => {
        void refreshHtml()
      }, DEBOUNCE_MS)
    },
    { immediate: true, deep: true },
  )

  async function refreshHtml(): Promise<void> {
    abort?.abort()
    const source = toValue(options.htmlContent)
    if (!source.trim()) {
      html.value = ''
      error.value = null
      return
    }
    abort = new AbortController()
    const signal = abort.signal
    loading.value = true
    error.value = null
    try {
      html.value = await previewHtml(
        { htmlContent: source, variables: toValue(options.variables) },
        signal,
      )
    } catch (err) {
      if (isAbortError(err)) {
        return
      }
      error.value = toProblemDetail(err).detail ?? 'Aperçu HTML impossible'
    } finally {
      if (!signal.aborted) {
        loading.value = false
      }
    }
  }

  async function requestPdf(): Promise<void> {
    const source = toValue(options.htmlContent)
    if (!source.trim()) {
      return
    }
    loading.value = true
    error.value = null
    try {
      const blob = await previewPdf({
        htmlContent: source,
        variables: toValue(options.variables),
      })
      if (pdfUrl.value) {
        URL.revokeObjectURL(pdfUrl.value)
      }
      pdfUrl.value = URL.createObjectURL(blob)
    } catch (err) {
      error.value = toProblemDetail(err).detail ?? 'Aperçu PDF impossible'
    } finally {
      loading.value = false
    }
  }

  onUnmounted(() => {
    if (timer !== undefined) {
      clearTimeout(timer)
    }
    abort?.abort()
    if (pdfUrl.value) {
      URL.revokeObjectURL(pdfUrl.value)
    }
  })

  return { html, pdfUrl, loading, error, previewPdf: requestPdf }
}

function isAbortError(err: unknown): boolean {
  return (
    (err instanceof DOMException && err.name === 'AbortError') ||
    (typeof err === 'object' &&
      err !== null &&
      'code' in err &&
      (err as { code?: string }).code === 'ERR_CANCELED')
  )
}
