import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import ConfigurationPromotionView from './ConfigurationPromotionView.vue'
import * as api from '@/api/configuration'
import type { ImportPlan } from '@/api/configuration'

vi.mock('@/api/configuration', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/configuration')>()
  return { ...actual, planImport: vi.fn(), applyImport: vi.fn(), listImports: vi.fn(), downloadExport: vi.fn() }
})

const plan: ImportPlan = {
  bundleSha256: 'sha-1',
  formatVersion: 1,
  sourceLabel: 'recette',
  entries: [
    { type: 'DOSSIER_TYPE', key: 'SIN', action: 'CREATE', reason: '', secretsRequired: [] },
    { type: 'CONNECTOR_INSTANCE', key: 'erp-prod', action: 'BLOCKED', reason: 'cannot be created: webhookSecret is required', secretsRequired: ['webhookSecret'], connectorName: 'ERP Prod' },
    { type: 'BPMN', key: 'flow', action: 'UNCHANGED', reason: '', secretsRequired: [] },
  ],
}

async function mountView() {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { template: '<div/>' } }] })
  await router.push('/')
  const wrapper = mount(ConfigurationPromotionView, { global: { plugins: [router], stubs: { RouterLink: { template: '<a><slot/></a>' } } } })
  await flushPromises()
  return wrapper
}

async function pickFile(wrapper: Awaited<ReturnType<typeof mountView>>) {
  const input = wrapper.get('[data-testid="bundle-file"]')
  Object.defineProperty(input.element, 'files', { value: [new File(['zip'], 'b.zip')], configurable: true })
  await input.trigger('change')
}

describe('ConfigurationPromotionView (TODO §83, D4)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listImports).mockResolvedValue([])
    vi.spyOn(window, 'confirm').mockReturnValue(true)
  })

  it('downloads the export through the API (not a bare link) with the environment name', async () => {
    const wrapper = await mountView()
    await wrapper.get('[data-testid="source-label"]').setValue('rec & co')
    await wrapper.get('[data-testid="export-button"]').trigger('click')
    await flushPromises()
    expect(api.downloadExport).toHaveBeenCalledWith('rec & co')
  })

  it('shows the server reason when the export fails', async () => {
    vi.mocked(api.downloadExport).mockRejectedValue({ isAxiosError: true, response: { status: 403, data: { detail: 'Access denied' } } })
    const wrapper = await mountView()
    await wrapper.get('[data-testid="export-button"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="promotion-error"]').text()).toContain('Access denied')
  })

  it('plans a dry run and shows each artifact with its action and reason, without applying anything', async () => {
    vi.mocked(api.planImport).mockResolvedValue(plan)
    const wrapper = await mountView()
    expect(wrapper.get('[data-testid="plan-button"]').attributes('disabled')).toBeDefined()
    await pickFile(wrapper)
    await wrapper.get('[data-testid="plan-button"]').trigger('submit')
    await wrapper.get('form.promotion__import').trigger('submit')
    await flushPromises()
    expect(api.planImport).toHaveBeenCalled()
    expect(wrapper.get('[data-testid="plan-DOSSIER_TYPE-SIN"]').text()).toContain('Créer')
    expect(wrapper.get('[data-testid="plan-CONNECTOR_INSTANCE-erp-prod"]').text()).toContain('webhookSecret is required')
    expect(api.applyImport).not.toHaveBeenCalled()
  })

  it('refuses invalid parameters before calling the server', async () => {
    const wrapper = await mountView()
    await pickFile(wrapper)
    await wrapper.get('[data-testid="parameters"]').setValue('["not","an","object"]')
    expect(wrapper.text()).toContain('objet JSON')
    expect(wrapper.get('[data-testid="plan-button"]').attributes('disabled')).toBeDefined()
  })

  it('asks for the secrets of connectors to create by name, applies the reviewed checksum and then forgets the secrets', async () => {
    vi.mocked(api.planImport).mockResolvedValue(plan)
    vi.mocked(api.applyImport).mockResolvedValue({
      bundleSha256: 'sha-1',
      performedAt: '2026-10-01T10:00:00Z',
      entries: [
        { type: 'DOSSIER_TYPE', key: 'SIN', outcome: 'APPLIED', message: 'created' },
        { type: 'CONNECTOR_INSTANCE', key: 'erp-prod', outcome: 'APPLIED', message: 'created disabled' },
      ],
    })
    const wrapper = await mountView()
    await pickFile(wrapper)
    await wrapper.get('form.promotion__import').trigger('submit')
    await flushPromises()
    await wrapper.get('[data-testid="secret-erp-prod-webhookSecret"]').setValue('s3cret-value')
    await wrapper.get('[data-testid="apply-button"]').trigger('click')
    await flushPromises()

    expect(api.applyImport).toHaveBeenCalledWith(expect.any(File), {
      parameters: {},
      // indexés par le NOM exact du connecteur, pas par sa clé de fichier (revue D4 H1)
      secrets: { 'ERP Prod': { webhookSecret: 's3cret-value' } },
      expectedBundleSha256: 'sha-1',
    })
    expect(wrapper.get('[data-testid="report-table"]').text()).toContain('created disabled')
    // le secret n'est plus dans la page
    expect(wrapper.html()).not.toContain('s3cret-value')
    expect((wrapper.find('[data-testid="secret-erp-prod-webhookSecret"]').element as HTMLInputElement | undefined)?.value ?? '').toBe('')
  })

  it('shows the server reason when the plan is refused', async () => {
    vi.mocked(api.planImport).mockRejectedValue({ isAxiosError: true, response: { status: 400, data: { detail: 'checksum mismatch for dmn/x.dmn' } } })
    const wrapper = await mountView()
    await pickFile(wrapper)
    await wrapper.get('form.promotion__import').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[data-testid="promotion-error"]').text()).toContain('checksum mismatch')
    expect(wrapper.find('[data-testid="plan-table"]').exists()).toBe(false)
  })

  it('does not apply when the confirmation is declined and shows the import journal', async () => {
    vi.mocked(api.planImport).mockResolvedValue(plan)
    vi.mocked(api.listImports).mockResolvedValue([
      { id: 'i1', performedBy: 'alice', performedAt: '2026-10-01T10:00:00Z', channel: 'ADMIN', bundleSha256: 'abcdef012345', sourceLabel: 'recette', applied: 2, unchanged: 1, skipped: 0, failed: 0 },
    ])
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    const wrapper = await mountView()
    expect(wrapper.get('[data-testid="import-log"]').text()).toContain('2 appliqué(s)')
    await pickFile(wrapper)
    await wrapper.get('form.promotion__import').trigger('submit')
    await flushPromises()
    await wrapper.get('[data-testid="apply-button"]').trigger('click')
    expect(api.applyImport).not.toHaveBeenCalled()
  })

  it('invalidates the reviewed plan when the parameters change (revue D4 L4)', async () => {
    vi.mocked(api.planImport).mockResolvedValue(plan)
    const wrapper = await mountView()
    await pickFile(wrapper)
    await wrapper.get('form.promotion__import').trigger('submit')
    await flushPromises()
    expect(wrapper.find('[data-testid="plan-table"]').exists()).toBe(true)
    await wrapper.get('[data-testid="parameters"]').setValue('{"pk":"other"}')
    expect(wrapper.find('[data-testid="plan-table"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="apply-button"]').exists()).toBe(false)
  })
})
