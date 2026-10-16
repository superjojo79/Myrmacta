import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import DossierDetailView from './DossierDetailView.vue'
import { routes } from '@/app/routes'
import * as dossiersApi from '@/api/dossiers'
import * as typesApi from '@/api/dossierTypes'
import * as workflowsApi from '@/api/workflows'
import * as tasksApi from '@/api/tasks'
import { useAuthStore } from '@/stores/auth'
import { asPage } from '@/api/page'

vi.mock('@/api/dossiers')
vi.mock('@/api/dossierTypes')
vi.mock('@/api/workflows', () => ({
  getWorkflowDiagram: vi.fn(),
  getWorkflowHistory: vi.fn().mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
  getWorkflowTasks: vi.fn().mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
  listAllWorkflowTypeConfigs: vi.fn().mockResolvedValue([]),
  reassignWorkflowTask: vi.fn().mockResolvedValue(undefined),
  claimWorkflowTask: vi.fn().mockResolvedValue(undefined),
  listAssignableUsers: vi.fn().mockResolvedValue([]),
}))
vi.mock('@/api/tasks')
vi.mock('@/api/purgeOutbox', () => ({
  enqueueDossierPurge: vi.fn().mockResolvedValue(undefined),
}))
vi.mock('bpmn-js/lib/NavigatedViewer', () => ({
  default: class {
    importXML = vi.fn().mockResolvedValue(undefined)
    destroy = vi.fn()
    get = () => ({ zoom: vi.fn(), addMarker: vi.fn() })
  },
}))

const baseDossier = {
  id: 'd1',
  tenantId: 't',
  reference: 'DOS-1',
  dossierTypeCode: 'CLAIM',
  label: 'Claim A',
  status: 'DRAFT' as const,
  fields: { priority: 'NORMAL' },
  currentWorkflowLinkId: 'link-1' as string | null,
  version: 1,
  createdBy: 'u',
  createdAt: '',
  updatedBy: 'u',
  updatedAt: '',
  closedBy: null,
  closedAt: null,
  canEdit: false,
}

describe('DossierDetailView phase 8', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.resetAllMocks()
    vi.mocked(dossiersApi.getDossierById).mockResolvedValue({ ...baseDossier })
    vi.mocked(typesApi.getDossierType).mockResolvedValue({
      id: 't1',
      tenantId: 't',
      code: 'CLAIM',
      label: 'Sinistre',
      workflowKey: 'wf',
      active: true,
      allowUnknownFields: false,
      fieldDefinitions: [
        { name: 'priority', label: 'Priority', type: 'STRING' as const, required: true },
      ],
    })
    vi.mocked(dossiersApi.listDossierWorkflows).mockResolvedValue(asPage([]))
    vi.mocked(dossiersApi.listDossierElements).mockResolvedValue(asPage([]))
    vi.mocked(dossiersApi.listDossierRelations).mockResolvedValue([])
    vi.mocked(workflowsApi.getWorkflowDiagram).mockResolvedValue({
      bpmnXml: '<xml/>',
      activeIds: [],
      completedIds: [],
      failedIds: [],
    })
    vi.mocked(workflowsApi.getWorkflowHistory).mockResolvedValue(asPage([]))
    vi.mocked(workflowsApi.getWorkflowTasks).mockResolvedValue(
      asPage([
        {
          taskId: 'task-1',
          name: 'Review',
          assignee: 'u',
          taskDefinitionKey: 'k',
          createTime: null,
        },
      ]),
    )
    vi.mocked(dossiersApi.startDossierWorkflow).mockResolvedValue({
      id: 'link-1',
      dossierId: 'd1',
      tenantId: 't',
      processInstanceId: 'pi',
      processDefinitionKey: 'wf',
      sequence: 1,
      status: 'ACTIVE',
      triggerRef: null,
      startedAt: null,
      endedAt: null,
      endReason: null,
      startedBy: 'u',
    })
    vi.mocked(tasksApi.completeTask).mockResolvedValue(undefined)
    vi.mocked(workflowsApi.claimWorkflowTask).mockResolvedValue(undefined)
    vi.mocked(workflowsApi.listAssignableUsers).mockResolvedValue([
      { userId: 'user-2', displayName: 'Grace Hopper', email: 'grace@example.org' },
    ])
  })

  async function mountDetail(roles: string[], canEdit = false) {
    const auth = useAuthStore()
    auth.user = { id: 'u', name: 'Ada', email: null, roles }
    vi.mocked(dossiersApi.getDossierById).mockResolvedValue({ ...baseDossier, canEdit })
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/dossiers/d1')
    await router.isReady()
    const wrapper = mount(DossierDetailView, { global: { plugins: [router] } })
    await flushPromises()
    return wrapper
  }

  it('starts workflow from dialog with default key', async () => {
    const wrapper = await mountDetail(['TENANT_ADMIN'])
    await wrapper.get('button.primary').trigger('click')
    await flushPromises()
    await wrapper.get('.dialog button.primary').trigger('click')
    await flushPromises()
    expect(dossiersApi.startDossierWorkflow).toHaveBeenCalled()
    const call = vi.mocked(dossiersApi.startDossierWorkflow).mock.calls[0]
    expect(call[0]).toBe('d1')
    expect(call[1]).toBe('wf')
    expect(call[2]).toEqual({})
    expect(call[3]).toBeTruthy()
  })

  it('transmet les variables de démarrage saisies dans le dialogue (TODO §93)', async () => {
    const wrapper = await mountDetail(['TENANT_ADMIN'])
    await wrapper.get('button.primary').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="start-variables-add"]').trigger('click')
    await wrapper.get('[data-testid="start-variable-name"]').setValue('priorite')
    await wrapper.get('[data-testid="start-variable-value"]').setValue('haute')
    await wrapper.get('.dialog button.primary').trigger('click')
    await flushPromises()
    const call = vi.mocked(dossiersApi.startDossierWorkflow).mock.calls[0]
    expect(call[1]).toBe('wf')
    expect(call[2]).toEqual({ priorite: 'haute' })
  })

  it('completes a task with empty variables', async () => {
    const wrapper = await mountDetail(['DOSSIER_MANAGER'])
    const completeBtn = wrapper.findAll('.task-list button').find((b) => b.text() === 'Compléter')
    expect(completeBtn).toBeTruthy()
    await completeBtn!.trigger('click')
    await flushPromises()
    expect(tasksApi.completeTask).toHaveBeenCalledWith('task-1', {})
  })

  it('shows reassign only for admin and confirms reassignment', async () => {
    const manager = await mountDetail(['DOSSIER_MANAGER'])
    expect(manager.text()).not.toContain('Réassigner')
    manager.unmount()

    const admin = await mountDetail(['WORKFLOW_ADMIN'])
    expect(admin.text()).toContain('Réassigner')
    const reassignBtn = admin.findAll('.task-list button').find((b) => b.text() === 'Réassigner')
    await reassignBtn!.trigger('click')
    await flushPromises()
    // D-ASSIGN-DIR : une liste des utilisateurs connus remplace la saisie d'un identifiant.
    const select = admin.get('[data-testid="reassign-user"]')
    expect(select.text()).toContain('Grace Hopper (grace@example.org)')
    await select.setValue('user-2')
    await admin.get('.reassign button').trigger('click')
    await flushPromises()
    expect(workflowsApi.reassignWorkflowTask).toHaveBeenCalledWith('link-1', 'task-1', 'user-2')
  })

  it('explains an empty user list instead of showing a bare select', async () => {
    vi.mocked(workflowsApi.listAssignableUsers).mockResolvedValue([])
    const admin = await mountDetail(['TENANT_ADMIN'])
    const reassignBtn = admin.findAll('.task-list button').find((b) => b.text() === 'Réassigner')
    await reassignBtn!.trigger('click')
    await flushPromises()
    expect(admin.text()).toContain('Un utilisateur apparaît ici après sa première connexion.')
  })

  it('lets a dossier editor take a free task with the claim button (D-ASSIGN-CLAIM)', async () => {
    vi.mocked(workflowsApi.getWorkflowTasks).mockResolvedValue(
      asPage([{ taskId: 'task-1', name: 'Review', assignee: null, taskDefinitionKey: 'k', createTime: null }]),
    )
    const viewer = await mountDetail(['DOSSIER_VIEWER'], false)
    expect(viewer.find('[data-testid="claim-task"]').exists()).toBe(false)
    viewer.unmount()

    const editor = await mountDetail(['DOSSIER_MANAGER'], true)
    await editor.get('[data-testid="claim-task"]').trigger('click')
    await flushPromises()
    expect(workflowsApi.claimWorkflowTask).toHaveBeenCalledWith('link-1', 'task-1')
    // Pas d'appel à l'annuaire pour un non-admin : il lui est refusé.
    expect(workflowsApi.listAssignableUsers).not.toHaveBeenCalled()
  })

  it('shows the assignee display name for admins and "vous" for oneself', async () => {
    vi.mocked(workflowsApi.getWorkflowTasks).mockResolvedValue(
      asPage([
        { taskId: 'task-1', name: 'Review', assignee: 'user-2', taskDefinitionKey: 'k', createTime: null },
        { taskId: 'task-2', name: 'Sign', assignee: 'u', taskDefinitionKey: 'k', createTime: null },
      ]),
    )
    const admin = await mountDetail(['WORKFLOW_ADMIN'])
    expect(admin.text()).toContain('Assignee : Grace Hopper')
    expect(admin.text()).toContain('Assignee : vous')
  })

  it('follows the server-computed canEdit flag rather than the role alone (TODO §36)', async () => {
    // A DOSSIER_MANAGER without an EDIT ACL entry on the dossier type sees canEdit=false from
    // the API — the frontend must not re-derive edit rights from the role, or it would show an
    // editable UI that then fails with 403 on save (the bug this closes).
    const manager = await mountDetail(['DOSSIER_MANAGER'], false)
    expect(manager.text()).not.toContain('Lier un dossier')
    expect(manager.text()).not.toContain('Enregistrer les champs')
    manager.unmount()

    // Conversely, a role with no special claim (VIEWER) but canEdit=true from the server must
    // see the editable UI.
    const viewerWithGrant = await mountDetail(['DOSSIER_VIEWER'], true)
    expect(viewerWithGrant.text()).toContain('Lier un dossier')
    expect(viewerWithGrant.text()).toContain('Enregistrer les champs')
  })

  it('shows link action for manager and hides it for viewer', async () => {
    const manager = await mountDetail(['DOSSIER_MANAGER'], true)
    expect(manager.text()).toContain('Dossiers liés')
    expect(manager.text()).toContain('Lier un dossier')
    manager.unmount()

    const viewer = await mountDetail(['DOSSIER_VIEWER'])
    expect(viewer.text()).toContain('Dossiers liés')
    expect(viewer.text()).not.toContain('Lier un dossier')
  })

  it('surfaces ProblemDetail when status patch fails', async () => {
    vi.mocked(dossiersApi.updateDossierStatus).mockRejectedValue({
      response: { status: 409, data: { detail: 'stale version', status: 409 } },
    })
    const wrapper = await mountDetail(['TENANT_ADMIN'])
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('stale version')
  })

  it('shows field save for manager and hides it for viewer', async () => {
    const manager = await mountDetail(['DOSSIER_MANAGER'], true)
    expect(manager.text()).toContain('Enregistrer les champs')
    manager.unmount()

    const viewer = await mountDetail(['DOSSIER_VIEWER'])
    expect(viewer.text()).not.toContain('Enregistrer les champs')
  })

  it('surfaces ProblemDetail when fields patch fails', async () => {
    vi.mocked(dossiersApi.updateDossierFields).mockRejectedValue({
      response: { status: 400, data: { detail: 'invalid field', status: 400 } },
    })
    const wrapper = await mountDetail(['TENANT_ADMIN'], true)
    await wrapper.get('.dossier-fields-editor input').setValue('HIGH')
    await wrapper.get('.dossier-fields-editor button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('invalid field')
  })

  it('shows purge request for tenant admin only', async () => {
    const admin = await mountDetail(['TENANT_ADMIN'])
    expect(admin.text()).toContain('Demander la purge')
    admin.unmount()

    const manager = await mountDetail(['DOSSIER_MANAGER'])
    expect(manager.text()).not.toContain('Demander la purge')
  })
})
