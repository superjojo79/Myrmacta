import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import {
  clearReplyWorkflow,
  evaluateFieldRules,
  getReplyWorkflow,
  setReplyWorkflow,
  updateDossierType,
} from './dossierTypes'

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

describe('reply workflow api (TODO §113)', () => {
  it('reads, sets and clears the reply workflow of a type; 204 means none', async () => {
    vi.mocked(axios.get).mockResolvedValueOnce({ data: '', status: 204 })
    expect(await getReplyWorkflow('FACTURE')).toBeNull()
    vi.mocked(axios.get).mockResolvedValueOnce({
      data: { dossierTypeCode: 'FACTURE', mode: 'SUR_DOSSIER', workflowKey: 'w', childTypeCode: null },
      status: 200,
    })
    expect((await getReplyWorkflow('FACTURE'))?.mode).toBe('SUR_DOSSIER')
    vi.mocked(axios.put).mockResolvedValue({ data: {} })
    vi.mocked(axios.delete).mockResolvedValue({})
    await setReplyWorkflow('FACTURE', { mode: 'DOSSIER_ENFANT', workflowKey: 'w', childTypeCode: 'REPONSE' })
    await clearReplyWorkflow('FACTURE')
    expect(axios.get).toHaveBeenCalledWith('/api/dossier-types/FACTURE/reply-workflow')
    expect(axios.put).toHaveBeenCalledWith('/api/dossier-types/FACTURE/reply-workflow', {
      mode: 'DOSSIER_ENFANT',
      workflowKey: 'w',
      childTypeCode: 'REPONSE',
    })
    expect(axios.delete).toHaveBeenCalledWith('/api/dossier-types/FACTURE/reply-workflow')
  })
})
