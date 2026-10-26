import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import PrecheckPanel from './PrecheckPanel.vue'
import * as api from '@/api/prechecks'

vi.mock('@/api/prechecks')

function finding(overrides: Partial<api.PrecheckFinding>): api.PrecheckFinding {
  return {
    id: 'f1',
    kind: 'MISSING',
    origin: 'DETERMINISTIC',
    sourceType: 'FIELD',
    sourceRef: 'telephone',
    message: 'Champ obligatoire manquant : Téléphone.',
    confidence: null,
    decision: null,
    decidedBy: null,
    decidedAt: null,
    ...overrides,
  }
}

function run(overrides: Partial<api.PrecheckRun>): api.PrecheckRun {
  return {
    id: 'r1',
    trigger: 'WORKFLOW',
    requestedBy: 'system',
    status: 'SUCCEEDED',
    failure: null,
    provider: 'openai-compatible',
    model: 'mistral-small',
    promptVersion: 'v1',
    summary: 'Adresse conforme ; téléphone absent.',
    tokensIn: 120,
    tokensOut: 30,
    discarded: 0,
    createdAt: '2026-10-26T09:00:00Z',
    finishedAt: '2026-10-26T09:00:05Z',
    findings: [
      finding({}),
      finding({
        id: 'f2',
        kind: 'INCONSISTENT',
        origin: 'MODEL',
        sourceType: 'QUESTION',
        sourceRef: 'adresse',
        message: "L'adresse de la preuve diffère de celle du formulaire.",
        confidence: 0.82,
      }),
    ],
    ...overrides,
  }
}

function mountPanel(props: Partial<{ canEdit: boolean; canExport: boolean }> = {}) {
  return mount(PrecheckPanel, { props: { dossierId: 'd1', ...props } })
}

describe('PrecheckPanel (TODO §106, lot C)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('stays hidden when the feature is disabled, when nothing ran yet or when the API fails', async () => {
    vi.mocked(api.listPrechecks).mockResolvedValueOnce(null).mockResolvedValueOnce([]).mockRejectedValueOnce(new Error('500'))
    for (let i = 0; i < 3; i++) {
      const wrapper = mountPanel({ canEdit: true })
      await flushPromises()
      expect(wrapper.find('[data-testid="precheck-panel"]').exists()).toBe(false)
    }
  })

  it('marks the model findings and the summary as AI, with source and confidence', async () => {
    vi.mocked(api.listPrechecks).mockResolvedValue([run({})])
    const wrapper = mountPanel()
    await flushPromises()

    const panel = wrapper.get('[data-testid="precheck-panel"]')
    expect(panel.attributes('aria-label')).toBe('Pré-vérification')
    expect(panel.text()).toContain("Constats indicatifs : l'agent vérifie et décide.")
    expect(wrapper.get('[data-testid="precheck-status"]').text()).toContain('Terminée')
    expect(wrapper.get('[data-testid="precheck-status"]').text()).toContain('1 manquant(s), 1 incohérence(s), 0 à vérifier')
    expect(wrapper.get('[data-testid="precheck-summary"]').text()).toContain('IA')
    const [deterministic, model] = wrapper.findAll('[data-testid="precheck-finding"]')
    expect(deterministic!.text()).toContain('Manquant')
    expect(deterministic!.text()).toContain('Contrôle')
    expect(deterministic!.text()).toContain('Champ « telephone »')
    expect(deterministic!.text()).not.toContain('IA')
    expect(model!.attributes('data-origin')).toBe('MODEL')
    expect(model!.text()).toContain('Incohérence')
    expect(model!.text()).toContain('IA')
    expect(model!.text()).toContain('Question « adresse »')
    expect(model!.text()).toContain('confiance 82 %')
  })

  it('is read-only without EDIT', async () => {
    vi.mocked(api.listPrechecks).mockResolvedValue([run({})])
    const wrapper = mountPanel({ canEdit: false, canExport: false })
    await flushPromises()
    expect(wrapper.findAll('button')).toHaveLength(0)
  })

  it('records a decision, shows who decided, and can undo it', async () => {
    const decided = run({
      findings: [
        finding({}),
        finding({ id: 'f2', origin: 'MODEL', decision: 'DISMISSED', decidedBy: 'agent', decidedAt: '2026-10-26T09:10:00Z' }),
      ],
    })
    vi.mocked(api.listPrechecks).mockResolvedValueOnce([run({})]).mockResolvedValue([decided])
    vi.mocked(api.decidePrecheckFinding).mockResolvedValue(decided.findings[1]!)
    const wrapper = mountPanel({ canEdit: true })
    await flushPromises()

    await wrapper.get('button[aria-label^="Écarter le constat : L\'adresse"]').trigger('click')
    await flushPromises()

    expect(api.decidePrecheckFinding).toHaveBeenCalledWith('d1', 'r1', 'f2', 'DISMISSED')
    const model = wrapper.findAll('[data-testid="precheck-finding"]')[1]!
    expect(model.attributes('data-decision')).toBe('DISMISSED')
    expect(model.get('[data-testid="precheck-decision"]').text()).toContain('Écarté par agent')
    expect(model.findAll('button').map((b) => b.text())).toEqual(['Accepter', 'Annuler la décision'])

    await model.get('button[aria-label^="Annuler la décision"]').trigger('click')
    expect(api.decidePrecheckFinding).toHaveBeenLastCalledWith('d1', 'r1', 'f2', null)
  })

  it('reruns in the background and refreshes until the run is finished', async () => {
    vi.useFakeTimers()
    const pending = run({ id: 'r2', trigger: 'MANUAL', requestedBy: 'agent', status: 'PENDING', summary: null, findings: [], finishedAt: null })
    const done = run({ id: 'r2', trigger: 'MANUAL', requestedBy: 'agent' })
    vi.mocked(api.listPrechecks)
      .mockResolvedValueOnce([run({})])
      .mockResolvedValueOnce([pending, run({})])
      .mockResolvedValue([done, run({})])
    vi.mocked(api.rerunPrecheck).mockResolvedValue(pending)
    const wrapper = mountPanel({ canEdit: true })
    await flushPromises()

    await wrapper.get('button:not([aria-label])').trigger('click')
    await flushPromises()
    expect(api.rerunPrecheck).toHaveBeenCalledWith('d1')
    expect(wrapper.get('[data-testid="precheck-status"]').text()).toContain('En attente')
    expect(wrapper.get('button:not([aria-label])').attributes('disabled')).toBeDefined()
    expect(wrapper.find('button[aria-label^="Accepter"]').exists()).toBe(false) // pas de décision sur une exécution en cours

    await vi.advanceTimersByTimeAsync(5000)
    await flushPromises()
    expect(wrapper.get('[data-testid="precheck-status"]').text()).toContain('Terminée')
    expect(wrapper.get('[data-testid="precheck-status"]').text()).toContain('relance')
    const calls = vi.mocked(api.listPrechecks).mock.calls.length
    await vi.advanceTimersByTimeAsync(20000)
    expect(vi.mocked(api.listPrechecks).mock.calls.length).toBe(calls) // plus de rafraîchissement une fois terminé
  })

  it('shows the server reason when a rerun is refused', async () => {
    vi.mocked(api.listPrechecks).mockResolvedValue([run({})])
    vi.mocked(api.rerunPrecheck).mockRejectedValue({
      isAxiosError: true,
      response: { status: 409, data: { detail: 'a pre-check is already queued or running on this dossier' } },
    })
    const wrapper = mountPanel({ canEdit: true })
    await flushPromises()
    await wrapper.get('button:not([aria-label])').trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('already queued')
  })

  it('explains a failed run with a translated generic reason and keeps the deterministic findings', async () => {
    vi.mocked(api.listPrechecks).mockResolvedValue([
      run({ status: 'FAILED', failure: 'the language model provider is unavailable', summary: null, findings: [finding({})] }),
    ])
    const wrapper = mountPanel()
    await flushPromises()
    expect(wrapper.text()).toContain('Échec : fournisseur du modèle indisponible.')
    expect(wrapper.findAll('[data-testid="precheck-finding"]')).toHaveLength(1)
    expect(wrapper.find('[data-testid="precheck-summary"]').exists()).toBe(false)
  })

  it('shows an older run on demand and exports the displayed run for a tenant admin', async () => {
    const older = run({ id: 'r0', createdAt: '2026-10-20T09:00:00Z', summary: 'Ancienne synthèse.' })
    vi.mocked(api.listPrechecks).mockResolvedValue([run({}), older])
    vi.mocked(api.downloadPrecheckExport).mockResolvedValue()
    const wrapper = mountPanel({ canExport: true })
    await flushPromises()

    await wrapper.get('[data-testid="precheck-run-select"]').setValue('r0')
    expect(wrapper.get('[data-testid="precheck-summary"]').text()).toContain('Ancienne synthèse.')
    const exportButton = wrapper.findAll('button').find((b) => b.text() === 'Exporter le journal')!
    await exportButton.trigger('click')
    expect(api.downloadPrecheckExport).toHaveBeenCalledWith('d1', 'r0')
  })
})
