import { describe, expect, it, vi } from 'vitest'
import { asPage, listAllPages, type Page } from './page'

describe('listAllPages', () => {
  it('concatenates two pages until totalElements', async () => {
    const fetchPage = vi.fn(async (page: number, size: number): Promise<Page<string>> => {
      if (page === 0) {
        return asPage(Array.from({ length: size }, (_, i) => `a${i}`), {
          page,
          size,
          totalElements: 150,
        })
      }
      return asPage(Array.from({ length: 50 }, (_, i) => `b${i}`), {
        page,
        size,
        totalElements: 150,
      })
    })

    const all = await listAllPages(fetchPage, 100)
    expect(all).toHaveLength(150)
    expect(fetchPage).toHaveBeenCalledTimes(2)
    expect(fetchPage).toHaveBeenNthCalledWith(1, 0, 100)
    expect(fetchPage).toHaveBeenNthCalledWith(2, 1, 100)
  })

  it('stops when a page is empty before totalElements', async () => {
    const fetchPage = vi.fn(async (page: number): Promise<Page<string>> => {
      if (page === 0) {
        return asPage(['only'], { totalElements: 50, size: 100 })
      }
      return asPage([], { page, size: 100, totalElements: 50, totalPages: 1 })
    })

    const all = await listAllPages(fetchPage, 100)
    expect(all).toEqual(['only'])
    expect(fetchPage).toHaveBeenCalledTimes(2)
  })
})
