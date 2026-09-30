import axios from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { loadBlockCatalog, resetBlockCatalogCache } from './catalogClient'
import { TEST_BLOCKS } from './testCatalog'

vi.mock('axios')

describe('loadBlockCatalog (S-FE-2)', () => {
  beforeEach(() => {
    resetBlockCatalogCache()
    vi.mocked(axios.get).mockReset()
  })

  it('charge une seule fois puis sert le cache', async () => {
    vi.mocked(axios.get).mockResolvedValue({ data: { version: 1, blocks: TEST_BLOCKS } })
    const first = await loadBlockCatalog()
    const second = await loadBlockCatalog()
    expect(first?.blocks).toHaveLength(TEST_BLOCKS.length)
    expect(second).toBe(first)
    expect(axios.get).toHaveBeenCalledTimes(1)
    expect(axios.get).toHaveBeenCalledWith('/api/bpmn-building-blocks')
  })

  it('renvoie null sans lever si le serveur échoue, et retente ensuite', async () => {
    vi.mocked(axios.get).mockRejectedValueOnce(new Error('boom'))
    expect(await loadBlockCatalog()).toBeNull()
    vi.mocked(axios.get).mockResolvedValueOnce({ data: { version: 1, blocks: [] } })
    expect(await loadBlockCatalog()).toEqual({ version: 1, blocks: [] })
  })
})
