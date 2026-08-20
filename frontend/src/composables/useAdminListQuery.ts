import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

function parsePage(raw: unknown): number {
  const n = typeof raw === 'string' ? Number(raw) : 0
  return Number.isFinite(n) && n >= 0 ? n : 0
}

function parseSize(raw: unknown): number {
  const n = typeof raw === 'string' ? Number(raw) : 20
  return Number.isFinite(n) && n >= 1 && n <= 100 ? n : 20
}

export function useAdminListQuery() {
  const route = useRoute()
  const router = useRouter()
  const page = ref(parsePage(route.query.page))
  const size = ref(parseSize(route.query.size))

  watch(
    () => [route.query.page, route.query.size],
    () => {
      page.value = parsePage(route.query.page)
      size.value = parseSize(route.query.size)
    },
  )

  watch([page, size], async ([nextPage, nextSize]) => {
    const next: Record<string, string> = {}
    if (nextPage > 0) next.page = String(nextPage)
    if (nextSize !== 20) next.size = String(nextSize)
    const currentPage = typeof route.query.page === 'string' ? route.query.page : undefined
    const currentSize = typeof route.query.size === 'string' ? route.query.size : undefined
    if (currentPage === next.page && currentSize === next.size) {
      return
    }
    await router.replace({ query: next })
  })

  return { page, size }
}
