import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import * as workflowsApi from '@/api/workflows'
import { routes } from '@/app/routes'
import DmnEditorView from './DmnEditorView.vue'

vi.mock('@/api/workflows')

const importXML = vi.fn().mockResolvedValue({ warnings: [] })
const saveXML = vi.fn().mockResolvedValue({ xml: '<definitions><decision id="editedDecision"/></definitions>' })
const destroy = vi.fn()

vi.mock('dmn-js/lib/Modeler', () => ({
  default: class {
    importXML = importXML
    saveXML = saveXML
    destroy = destroy
    getActiveViewer = () => ({ on: vi.fn() })
    on = vi.fn()
  },
}))

async function mountView(path: string) {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(path)
  await router.isReady()
  return mount(DmnEditorView, { global: { plugins: [router] } })
}

describe('DmnEditorView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    importXML.mockResolvedValue({ warnings: [] })
    saveXML.mockResolvedValue({ xml: '<definitions><decision id="editedDecision"/></definitions>' })
  })

  it('shows a blank canvas when no decisionKey query param is given', async () => {
    await mountView('/admin/dmn-editor')
    await flushPromises()

    expect(workflowsApi.getDmnDraft).not.toHaveBeenCalled()
    expect(workflowsApi.getDeployedDmnXml).not.toHaveBeenCalled()
    expect(importXML).toHaveBeenCalledWith(expect.stringContaining('Decision_1'))
  })

  it('loads the existing draft when decisionKey is given and a draft exists', async () => {
    vi.mocked(workflowsApi.getDmnDraft).mockResolvedValue({
      decisionKey: 'grilleEligibilite',
      xml: '<definitions><decision id="grilleEligibilite"/></definitions>',
      updatedAt: '2026-09-11T00:00:00Z',
      updatedBy: 'admin',
    })

    await mountView('/admin/dmn-editor?decisionKey=grilleEligibilite')
    await flushPromises()

    expect(workflowsApi.getDmnDraft).toHaveBeenCalledWith('grilleEligibilite')
    expect(importXML).toHaveBeenCalledWith('<definitions><decision id="grilleEligibilite"/></definitions>')
  })

  it('falls back to the deployed table when no draft exists for the key', async () => {
    vi.mocked(workflowsApi.getDmnDraft).mockResolvedValue(null)
    vi.mocked(workflowsApi.getDeployedDmnXml).mockResolvedValue(
      '<definitions><decision id="grilleEligibilite"/></definitions>',
    )

    await mountView('/admin/dmn-editor?decisionKey=grilleEligibilite')
    await flushPromises()

    expect(workflowsApi.getDeployedDmnXml).toHaveBeenCalledWith('grilleEligibilite')
    expect(importXML).toHaveBeenCalledWith('<definitions><decision id="grilleEligibilite"/></definitions>')
  })

  it('shows an error message and still mounts a blank canvas when loading the draft fails', async () => {
    vi.mocked(workflowsApi.getDmnDraft).mockRejectedValue(new Error('network down'))

    const wrapper = await mountView('/admin/dmn-editor?decisionKey=grilleEligibilite')
    await flushPromises()

    expect(wrapper.get('.dmn-editor__error').text()).toContain('network down')
    expect(importXML).toHaveBeenCalledWith(expect.stringContaining('Decision_1'))
  })

  it('loads a locally selected file into the canvas', async () => {
    const wrapper = await mountView('/admin/dmn-editor')
    await flushPromises()
    importXML.mockClear()

    const file = new File(
      ['<definitions><decision id="importedDecision"/></definitions>'],
      'table.dmn',
      { type: 'application/xml' },
    )
    const fileInput = wrapper.get('input[type="file"]').element as HTMLInputElement
    Object.defineProperty(fileInput, 'files', { value: [file], configurable: true })
    await wrapper.get('input[type="file"]').trigger('change')
    await flushPromises()

    expect(importXML).toHaveBeenCalledWith('<definitions><decision id="importedDecision"/></definitions>')
  })

  it('the "Charger" button loads an existing table by key typed into the field (Phase 7 gap fix)', async () => {
    vi.mocked(workflowsApi.getDmnDraft).mockResolvedValue(null)
    vi.mocked(workflowsApi.getDeployedDmnXml).mockResolvedValue(
      '<definitions><decision id="manuallyLoaded"/></definitions>',
    )

    const wrapper = await mountView('/admin/dmn-editor')
    await flushPromises()
    importXML.mockClear()

    await wrapper.get('input[type="text"]').setValue('manuallyLoaded')
    await wrapper.get('button.dmn-editor__load').trigger('click')
    await flushPromises()

    expect(workflowsApi.getDmnDraft).toHaveBeenCalledWith('manuallyLoaded')
    expect(importXML).toHaveBeenCalledWith('<definitions><decision id="manuallyLoaded"/></definitions>')
  })

  it('the "Enregistrer le brouillon" button calls useDmnDraft.save with the canvas XML', async () => {
    vi.mocked(workflowsApi.saveDmnDraft).mockResolvedValue({
      decisionKey: 'editedDecision',
      xml: '<definitions><decision id="editedDecision"/></definitions>',
      updatedAt: '2026-09-11T00:00:00Z',
      updatedBy: 'admin',
    })

    const wrapper = await mountView('/admin/dmn-editor')
    await flushPromises()

    await wrapper.get('button.dmn-editor__save').trigger('click')
    await flushPromises()

    expect(workflowsApi.saveDmnDraft).toHaveBeenCalledWith(
      'editedDecision',
      '<definitions><decision id="editedDecision"/></definitions>',
    )
  })

  it('the "Déployer" button reuses useDmnDeployment (info then confirm)', async () => {
    vi.mocked(workflowsApi.getDecisionTableDeploymentInfo).mockResolvedValue({
      decisionKey: 'editedDecision',
      latestVersion: 1,
    })
    vi.mocked(workflowsApi.deployDecisionTable).mockResolvedValue({
      decisionKey: 'editedDecision',
      deployedVersion: 2,
      deploymentId: 'dep-2',
    })

    const wrapper = await mountView('/admin/dmn-editor')
    await flushPromises()

    await wrapper.get('button.dmn-editor__deploy').trigger('click')
    await flushPromises()

    expect(workflowsApi.getDecisionTableDeploymentInfo).toHaveBeenCalledWith('editedDecision')

    await wrapper.get('button.dmn-editor__confirm-deploy').trigger('click')
    await flushPromises()

    expect(workflowsApi.deployDecisionTable).toHaveBeenCalled()
    const deployedFile = vi.mocked(workflowsApi.deployDecisionTable).mock.calls[0][0]
    expect(deployedFile).toBeInstanceOf(File)
  })
})
