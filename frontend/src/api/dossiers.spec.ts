import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import { createDossier, startDossierWorkflow, updateDossierStatus } from './dossiers'

vi.mock('axios')

describe('dossiers api client', () => {
  beforeEach(() => {
    vi.mocked(axios.post).mockReset()
    vi.mocked(axios.patch).mockReset()
  })

  it('creates a dossier with typed payload', async () => {
    vi.mocked(axios.post).mockResolvedValue({
      data: { id: 'd1', label: 'Claim', status: 'DRAFT' },
    })
    const result = await createDossier({
      dossierTypeCode: 'CLAIM',
      label: 'Claim',
      fields: { a: 1 },
    })
    expect(result.id).toBe('d1')
    expect(axios.post).toHaveBeenCalledWith('/api/dossiers', {
      dossierTypeCode: 'CLAIM',
      label: 'Claim',
      fields: { a: 1 },
    })
  })

  it('patches status with expectedVersion', async () => {
    vi.mocked(axios.patch).mockResolvedValue({ data: { id: 'd1', status: 'OUVERT', version: 2 } })
    await updateDossierStatus('d1', 'OUVERT', 1)
    expect(axios.patch).toHaveBeenCalledWith('/api/dossiers/d1/status', {
      status: 'OUVERT',
      expectedVersion: 1,
    })
  })

  it('starts workflow with Idempotency-Key header', async () => {
    vi.mocked(axios.post).mockResolvedValue({ data: { id: 'link-1' } })
    await startDossierWorkflow('d1', 'wf', {}, 'idem-1')
    expect(axios.post).toHaveBeenCalledWith(
      '/api/dossiers/d1/workflows',
      { processDefinitionKey: 'wf', variables: {} },
      { headers: { 'Idempotency-Key': 'idem-1' } },
    )
  })
})
