import { onMounted, shallowRef } from 'vue'
import { i18n } from '@/i18n'
import { getObservabilitySnapshot, type ObservabilitySnapshot } from '@/api/observability'
import { toProblemDetail } from '@/api/http'

export function useObservabilitySnapshot() {
  const snapshot = shallowRef<ObservabilitySnapshot | null>(null)
  const loading = shallowRef(true)
  const error = shallowRef<string | null>(null)

  async function refresh() {
    loading.value = true
    error.value = null
    try {
      snapshot.value = await getObservabilitySnapshot()
    } catch (err) {
      error.value = toProblemDetail(err).detail ?? i18n.global.t('common.errors2.observability')
    } finally {
      loading.value = false
    }
  }

  onMounted(() => {
    void refresh()
  })

  return { snapshot, loading, error, refresh }
}
