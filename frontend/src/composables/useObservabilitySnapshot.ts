import { onMounted, shallowRef } from 'vue'
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
      error.value = toProblemDetail(err).detail ?? "Impossible de charger l'observabilité"
    } finally {
      loading.value = false
    }
  }

  onMounted(() => {
    void refresh()
  })

  return { snapshot, loading, error, refresh }
}
