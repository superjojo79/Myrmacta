import { describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import { depositMail, listDepositPoints } from './mailDeposits'

vi.mock('axios')

describe('mail deposit api (TODO §113)', () => {
  it('lists the deposit points and posts the files with the optional sender and subject', async () => {
    vi.mocked(axios.get).mockResolvedValue({ data: [{ id: 'p1', name: 'Accueil', dossierTypeCode: 'COURRIER' }] })
    expect(await listDepositPoints()).toHaveLength(1)
    expect(axios.get).toHaveBeenCalledWith('/api/mail-deposit-points')

    vi.mocked(axios.post).mockResolvedValue({ data: { results: [{ filename: 'a.pdf', dossierId: 'd1', reference: 'R', refusal: null }] } })
    const results = await depositMail('p1', [new File(['%PDF'], 'a.pdf'), new File(['%PDF'], 'b.pdf')], { expediteur: ' x@y ', objet: ' ' })
    expect(results[0]!.reference).toBe('R')
    const [url, form] = vi.mocked(axios.post).mock.calls[0]!
    expect(url).toBe('/api/mail-deposit-points/p1/deposits')
    expect((form as FormData).getAll('files')).toHaveLength(2)
    expect((form as FormData).get('expediteur')).toBe('x@y')
    expect((form as FormData).has('objet')).toBe(false)
  })
})
