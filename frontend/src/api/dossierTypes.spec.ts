import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import { updateDossierType } from './dossierTypes'

vi.mock('axios')

describe('dossierTypes api', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(axios.patch).mockResolvedValue({ data: { code: 'CLAIM', slaTargetHours: null } })
  })

  it('patches clearSlaTargetHours', async () => {
    await updateDossierType('CLAIM', { clearSlaTargetHours: true })
    expect(axios.patch).toHaveBeenCalledWith('/api/dossier-types/CLAIM', {
      clearSlaTargetHours: true,
    })
  })
})
