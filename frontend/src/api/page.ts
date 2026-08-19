export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export const CATALOG_PAGE_SIZE = 100

export function asPage<T>(content: T[], extras: Partial<Page<T>> = {}): Page<T> {
  const size = extras.size ?? 20
  const totalElements = extras.totalElements ?? content.length
  return {
    content,
    page: extras.page ?? 0,
    size,
    totalElements,
    totalPages: extras.totalPages ?? (totalElements === 0 ? 0 : Math.ceil(totalElements / size)),
  }
}

export async function listAllPages<T>(
  fetchPage: (page: number, size: number) => Promise<Page<T>>,
  size = CATALOG_PAGE_SIZE,
): Promise<T[]> {
  const acc: T[] = []
  let page = 0
  for (;;) {
    const result = await fetchPage(page, size)
    const chunk = result.content ?? []
    if (chunk.length === 0) {
      break
    }
    acc.push(...chunk)
    if (acc.length >= (result.totalElements ?? acc.length)) {
      break
    }
    page += 1
  }
  return acc
}
