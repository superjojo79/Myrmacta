import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import TemplatesAdminView from './TemplatesAdminView.vue'
import ConnectorsAdminView from './ConnectorsAdminView.vue'
import WorkflowConfigsAdminView from './WorkflowConfigsAdminView.vue'
import DossierTypesAdminView from './DossierTypesAdminView.vue'
import * as adminApi from '@/api/admin'
import * as workflowsApi from '@/api/workflows'
import * as typesApi from '@/api/dossierTypes'
import * as retentionApi from '@/api/retentionSettings'
import * as slaApi from '@/api/slaSettings'
import { routes } from '@/app/routes'
import { asPage } from '@/api/page'
import type { Component } from 'vue'

vi.mock('@/api/admin')
vi.mock('@/api/workflows')
vi.mock('@/api/dossierTypes')
vi.mock('@/api/retentionSettings')
vi.mock('@/api/slaSettings')
vi.mock('@/api/documents', () => ({
  previewHtml: vi.fn().mockResolvedValue('<p>ok</p>'),
  previewPdf: vi.fn().mockResolvedValue(new Blob()),
}))

async function mountView(component: Component, path: string) {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(path)
  await router.isReady()
  return mount(component, { global: { plugins: [router] } })
}

describe('admin actions coverage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.stubGlobal('prompt', vi.fn().mockReturnValue('Renamed'))
    vi.mocked(adminApi.listDocumentTemplates).mockResolvedValue(
      asPage([
        {
          id: 'tpl1',
          templateKey: 'k',
          variantKey: null,
          locale: null,
          label: 'Old',
          version: 1,
          status: 'DRAFT',
          htmlContent: null,
          createdAt: '',
          updatedAt: '',
          updatedBy: null,
        },
      ]),
    )
    vi.mocked(adminApi.listConnectors).mockResolvedValue(
      asPage([
        {
          id: 'c1',
          tenantId: 't',
          name: 'Hook',
          connectorType: 'HTTP',
          enabled: true,
          config: {},
          secretConfigured: {},
        },
      ]),
    )
    vi.mocked(adminApi.listConnectorDefinitions).mockResolvedValue([{ type: 'HTTP', label: 'HTTP' }])
    vi.mocked(workflowsApi.listWorkflowTypeConfigs).mockResolvedValue(
      asPage([
        {
          id: '1',
          tenantId: 't',
          workflowKey: 'wf',
          label: 'WF',
          statusDefinitions: [],
          allowedDossierStatuses: [],
          onStartDossierStatus: null,
          observerUserIds: [],
        },
      ]),
    )
    vi.mocked(typesApi.listDossierTypes).mockResolvedValue(
      asPage([
        {
          id: '1',
          tenantId: 't',
          code: 'CLAIM',
          label: 'Sinistre',
          workflowKey: 'wf',
          active: true,
          allowUnknownFields: false,
          fieldDefinitions: [],
          retentionDays: null,
          retentionNever: false,
        },
      ]),
    )
    vi.mocked(retentionApi.getRetentionSettings).mockRejectedValue({
      response: { status: 404, data: { detail: 'not found', status: 404 } },
    })
    vi.mocked(slaApi.getSlaSettings).mockRejectedValue({
      response: { status: 404, data: { detail: 'not found', status: 404 } },
    })
  })

  it('templates rename create and lifecycle actions', async () => {
    vi.mocked(adminApi.createDocumentTemplate).mockResolvedValue({} as never)
    vi.mocked(adminApi.updateDocumentTemplate).mockResolvedValue({} as never)
    vi.mocked(adminApi.activateDocumentTemplate).mockResolvedValue({} as never)
    vi.mocked(adminApi.archiveDocumentTemplate).mockResolvedValue({} as never)
    vi.mocked(adminApi.newDocumentTemplateVersion).mockResolvedValue({} as never)
    const wrapper = await mountView(TemplatesAdminView, '/admin/templates')
    await flushPromises()
    // par libellé : un brouillon porte aussi « Modifier le contenu » (éditeur visuel, LC4b)
    const button = (label: string) => wrapper.findAll('li button').find((b) => b.text() === label)!
    await button('Renommer').trigger('click')
    await button('Activer').trigger('click')
    await button('Archiver').trigger('click')
    await button('Nouvelle version').trigger('click')
    await flushPromises()
    expect(adminApi.updateDocumentTemplate).toHaveBeenCalled()
    expect(adminApi.activateDocumentTemplate).toHaveBeenCalled()
    expect(adminApi.archiveDocumentTemplate).toHaveBeenCalled()
    expect(adminApi.newDocumentTemplateVersion).toHaveBeenCalled()
    const inputs = wrapper.findAll('form input')
    await inputs[0].setValue('k2')
    await inputs[1].setValue('Label')
    await wrapper.get('form').trigger('submit.prevent')
    await flushPromises()
    expect(adminApi.createDocumentTemplate).toHaveBeenCalled()
  })

  it('loads the draft content from a later page and never opens an empty editor when it is missing (review LC4b)', async () => {
    const draft = (htmlContent: string | null) => ({
      id: 'tpl1', templateKey: 'k', variantKey: null, locale: null, label: 'Old', version: 1, status: 'DRAFT' as const,
      htmlContent, createdAt: '', updatedAt: '', updatedBy: null,
    })
    const other = { ...draft('<p>autre</p>'), id: 'other' }
    const pageOf = (content: unknown[], page: number) => ({ content, page, size: 100, totalElements: 101, totalPages: 2 }) as never
    // 1er appel : liste de la vue ; ensuite : chargement du contenu (page 0 sans le brouillon, page 1 avec).
    vi.mocked(adminApi.listDocumentTemplates).mockImplementation(async (_key, includeContent, query) => {
      if (!includeContent) return asPage([draft(null)])
      return query?.page === 1 ? pageOf([draft('<p>v1</p>')], 1) : pageOf([other], 0)
    })
    const wrapper = await mountView(TemplatesAdminView, '/admin/templates')
    await flushPromises()
    await wrapper.findAll('li button').find((b) => b.text() === 'Modifier le contenu')!.trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="template-edit"]').exists()).toBe(true)

    // Brouillon introuvable : message, pas d'éditeur (l'enregistrement écraserait le contenu).
    vi.mocked(adminApi.listDocumentTemplates).mockImplementation(async (_key, includeContent) =>
      includeContent ? pageOf([other], 0) : asPage([draft(null)]))
    const missing = await mountView(TemplatesAdminView, '/admin/templates')
    await flushPromises()
    await missing.findAll('li button').find((b) => b.text() === 'Modifier le contenu')!.trigger('click')
    await flushPromises()
    expect(missing.find('[data-testid="template-edit"]').exists()).toBe(false)
    expect(missing.text()).toContain('introuvable')
  })

  it('connectors toggle remove create', async () => {
    vi.mocked(adminApi.createConnector).mockResolvedValue({} as never)
    vi.mocked(adminApi.updateConnector).mockResolvedValue({} as never)
    vi.mocked(adminApi.deleteConnector).mockResolvedValue(undefined)
    const wrapper = await mountView(ConnectorsAdminView, '/admin/connectors')
    await flushPromises()
    const buttons = wrapper.findAll('li button')
    await buttons[0].trigger('click')
    await buttons[1].trigger('click')
    await flushPromises()
    expect(adminApi.updateConnector).toHaveBeenCalled()
    expect(adminApi.deleteConnector).toHaveBeenCalled()
    await wrapper.get('form input').setValue('N')
    await wrapper.get('form').trigger('submit.prevent')
    await flushPromises()
    expect(adminApi.createConnector).toHaveBeenCalled()
  })

  it('workflow configs rename create and types acl', async () => {
    vi.mocked(workflowsApi.createWorkflowTypeConfig).mockResolvedValue({} as never)
    vi.mocked(workflowsApi.updateWorkflowTypeConfig).mockResolvedValue({} as never)
    vi.mocked(typesApi.updateDossierType).mockResolvedValue({} as never)
    vi.mocked(typesApi.grantDossierTypeAcl).mockResolvedValue(undefined)
    vi.mocked(typesApi.revokeDossierTypeAcl).mockResolvedValue(undefined)
    const wf = await mountView(WorkflowConfigsAdminView, '/admin/workflow-types')
    await flushPromises()
    await wf.get('li button').trigger('click')
    await flushPromises()
    expect(workflowsApi.updateWorkflowTypeConfig).toHaveBeenCalled()
    await wf.get('form input').setValue('wf2')
    await wf.findAll('form input')[1].setValue('Label')
    await wf.get('form').trigger('submit.prevent')
    await flushPromises()
    expect(workflowsApi.createWorkflowTypeConfig).toHaveBeenCalled()

    const types = await mountView(DossierTypesAdminView, '/admin/dossier-types')
    await flushPromises()
    await types.get('td button').trigger('click')
    await flushPromises()
    expect(typesApi.updateDossierType).toHaveBeenCalled()
    const aclForm = types.get('[data-testid="acl-form"]')
    const aclInputs = aclForm.findAll('input')
    await aclInputs[0].setValue('CLAIM')
    await aclInputs[1].setValue('u1')
    await aclForm.trigger('submit.prevent')
    await flushPromises()
    expect(typesApi.grantDossierTypeAcl).toHaveBeenCalled()
    await aclForm.findAll('button')[1].trigger('click')
    await flushPromises()
    expect(typesApi.revokeDossierTypeAcl).toHaveBeenCalled()
  })
})
