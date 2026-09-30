import axios from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { resetValueSourceCache, resolveValueSource, resolveValueSourceCached } from './valueSourceResolver'

vi.mock('axios')
vi.mock('@/api/dossierTypes', () => ({
  listAllDossierTypes: vi.fn().mockResolvedValue([{ code: 'CREDIT', label: 'Crédit' }]),
}))
vi.mock('@/api/workflows', () => ({
  listAllWorkflowTypeConfigs: vi.fn().mockResolvedValue([{ workflowKey: 'credit-flow' }]),
  getWorkflowTypeConfig: vi.fn().mockResolvedValue({
    statusDefinitions: [{ code: 'EN_ANALYSE', label: 'En analyse' }, { code: 'ACCEPTE', label: '' }],
  }),
}))
vi.mock('@/api/taskQuestionSchemas', () => ({
  listTaskQuestionSchemas: vi.fn().mockResolvedValue({
    content: [{ schemaKey: 'demande', label: 'Demande' }],
    page: 0,
    size: 100,
    totalElements: 1,
    totalPages: 1,
  }),
}))

describe('resolveValueSource (S-FE-3, S-FE-4)', () => {
  beforeEach(() => vi.mocked(axios.get).mockReset())

  it.each(['documentTemplates', 'connectorInstances', 'notificationWebhooks'])(
    '%s passe par l’endpoint de références',
    async (source) => {
      vi.mocked(axios.get).mockResolvedValue({ data: [{ value: 'a', label: 'A' }] })
      expect(await resolveValueSource(source)).toEqual([{ value: 'a', label: 'A' }])
      expect(axios.get).toHaveBeenCalledWith(`/api/bpmn-building-blocks/references/${source}`)
    },
  )

  it('utilise les API existantes pour les autres sources', async () => {
    expect(await resolveValueSource('dossierTypes')).toEqual([{ value: 'CREDIT', label: 'Crédit' }])
    expect(await resolveValueSource('workflowTypes')).toEqual([{ value: 'credit-flow', label: 'credit-flow' }])
    expect(await resolveValueSource('questionSchemas')).toEqual([{ value: 'demande', label: 'Demande' }])
  })

  it('les statuts viennent de la configuration du process ouvert', async () => {
    expect(await resolveValueSource('workflowStatuses', { processKey: 'credit-flow' })).toEqual([
      { value: 'EN_ANALYSE', label: 'En analyse' },
      { value: 'ACCEPTE', label: 'ACCEPTE' },
    ])
    expect(await resolveValueSource('workflowStatuses', {})).toBeNull()
  })

  it('renvoie null (saisie libre) pour dmnTables et en cas d’échec réseau', async () => {
    expect(await resolveValueSource('dmnTables')).toBeNull()
    vi.mocked(axios.get).mockResolvedValue(null as never) // réponse inexploitable → la déstructuration lève dans le try
    expect(await resolveValueSource('documentTemplates')).toBeNull()
  })
})

describe('resolveValueSourceCached', () => {
  beforeEach(() => {
    vi.mocked(axios.get).mockReset()
    resetValueSourceCache()
  })

  it('ne rappelle pas l’API pour une même source, mais réessaie après un échec', async () => {
    vi.mocked(axios.get).mockResolvedValueOnce(null as never)
    expect(await resolveValueSourceCached('documentTemplates')).toBeNull()
    vi.mocked(axios.get).mockResolvedValue({ data: [{ value: 'a', label: 'A' }] })
    expect(await resolveValueSourceCached('documentTemplates')).toHaveLength(1)
    expect(await resolveValueSourceCached('documentTemplates')).toHaveLength(1)
    expect(axios.get).toHaveBeenCalledTimes(2)
  })

  it('recharge une liste après expiration du cache (M7)', async () => {
    vi.useFakeTimers()
    try {
      vi.mocked(axios.get).mockResolvedValue({ data: [{ value: 'a', label: 'A' }] })
      await resolveValueSourceCached('documentTemplates')
      vi.advanceTimersByTime(30_000)
      await resolveValueSourceCached('documentTemplates')
      expect(axios.get).toHaveBeenCalledTimes(1)
      vi.advanceTimersByTime(60_000)
      await resolveValueSourceCached('documentTemplates')
      expect(axios.get).toHaveBeenCalledTimes(2)
    } finally {
      vi.useRealTimers()
    }
  })
})
