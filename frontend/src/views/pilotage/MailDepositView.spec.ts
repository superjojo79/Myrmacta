import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import MailDepositView from './MailDepositView.vue'
import * as api from '@/api/mailDeposits'
import { routes } from '@/app/routes'

vi.mock('@/api/mailDeposits', async (original) => ({
  ...(await original<typeof import('@/api/mailDeposits')>()),
  listDepositPoints: vi.fn(),
  depositMail: vi.fn(),
}))

const point = { id: 'p1', name: 'Courrier papier (accueil)', dossierTypeCode: 'COURRIER' }

async function mountView() {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push('/mail-deposit')
  await router.isReady()
  const wrapper = mount(MailDepositView, { global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

function pdf(name: string): File {
  return new File(['%PDF-1.4'], name, { type: 'application/pdf' })
}

async function choose(wrapper: Awaited<ReturnType<typeof mountView>>, files: File[]) {
  const input = wrapper.get('[data-testid="mail-deposit-files"]')
  Object.defineProperty(input.element, 'files', { value: files, configurable: true })
  await input.trigger('change')
}

describe('MailDepositView (TODO §113)', () => {
  beforeEach(() => {
    vi.mocked(api.listDepositPoints).mockReset()
    vi.mocked(api.depositMail).mockReset()
  })

  it('selects the only deposit point and shows the mail created for each file, or why it was refused', async () => {
    vi.mocked(api.listDepositPoints).mockResolvedValue([point])
    vi.mocked(api.depositMail).mockResolvedValue([
      { filename: 'lettre.pdf', dossierId: 'd1', reference: 'DOS-COURRIER-2026-000007', refusal: null },
      { filename: 'photo.pdf', dossierId: null, reference: null, refusal: 'pas un PDF' },
    ])
    const wrapper = await mountView()
    expect((wrapper.get('[data-testid="mail-deposit-point"]').element as HTMLSelectElement).value).toBe('p1')
    expect(wrapper.get('[data-testid="mail-deposit-send"]').attributes('disabled')).toBeDefined()

    await choose(wrapper, [pdf('lettre.pdf'), pdf('photo.pdf')])
    await wrapper.get('[data-testid="mail-deposit-sender"]').setValue('avocat@cabinet.example')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(api.depositMail).toHaveBeenCalledWith('p1', expect.any(Array), { expediteur: 'avocat@cabinet.example', objet: '' })
    const rows = wrapper.findAll('[data-testid="mail-deposit-result"]')
    expect(rows[0]!.text()).toContain('DOS-COURRIER-2026-000007')
    expect(rows[0]!.find('a').attributes('href')).toBe('/dossiers/d1')
    expect(rows[1]!.text()).toContain('Refusé : pas un PDF')
  })

  it('says when no deposit point is open, and refuses more files than the limit', async () => {
    vi.mocked(api.listDepositPoints).mockResolvedValue([])
    const empty = await mountView()
    expect(empty.find('[data-testid="mail-deposit-no-point"]').exists()).toBe(true)

    vi.mocked(api.listDepositPoints).mockResolvedValue([point])
    const wrapper = await mountView()
    await choose(wrapper, Array.from({ length: api.MAX_DEPOSIT_FILES + 1 }, (_, i) => pdf(`f${i}.pdf`)))
    expect(wrapper.find('[data-testid="mail-deposit-too-many"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="mail-deposit-send"]').attributes('disabled')).toBeDefined()
  })

  it('shows the server refusal of the whole deposit', async () => {
    vi.mocked(api.listDepositPoints).mockResolvedValue([point])
    vi.mocked(api.depositMail).mockRejectedValue({ response: { status: 403, data: { status: 403, detail: 'Access denied' } } })
    const wrapper = await mountView()
    await choose(wrapper, [pdf('a.pdf')])
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[data-testid="mail-deposit-error"]').text()).toContain('Access denied')
  })
})
