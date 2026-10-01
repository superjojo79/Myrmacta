import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import { evaluateFieldRules, updateDossierType } from './dossierTypes'

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

  it('posts the current values to the rules preview endpoint (code encoded)', async () => {
    const preview = { computed: { ttc: 120 }, hidden: ['siret'], requiredByRule: [], errors: [] }
    vi.mocked(axios.post).mockResolvedValue({ data: preview })

    await expect(evaluateFieldRules('A/B', { ht: 100 })).resolves.toEqual(preview)

    expect(axios.post).toHaveBeenCalledWith('/api/dossier-types/A%2FB/evaluate-rules', { fields: { ht: 100 } })
  })
})
