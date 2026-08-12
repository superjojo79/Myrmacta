import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useDossierStore } from './dossier'
import { useAuthStore } from './auth'
import { useNotificationsStore } from './notifications'
import * as dossiersApi from '@/api/dossiers'
import * as typesApi from '@/api/dossierTypes'
import * as workflowsApi from '@/api/workflows'
import * as tasksApi from '@/api/tasks'
import axios from 'axios'

vi.mock('@/api/dossiers')
vi.mock('@/api/dossierTypes')
vi.mock('@/api/workflows')
vi.mock('@/api/tasks')
vi.mock('axios')

describe('stores remaining coverage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.resetAllMocks()
  })

  it('loadByRef complete and reassign', async () => {
    vi.mocked(dossiersApi.getDossierByRef).mockResolvedValue({
      id: 'd1',
      tenantId: 't',
      reference: 'DOS-1',
      dossierTypeCode: 'CLAIM',
      label: 'L',
      status: 'EN_COURS',
      fields: {},
      currentWorkflowLinkId: 'link-1',
      version: 1,
      createdBy: 'u',
      createdAt: '',
      updatedBy: 'u',
      updatedAt: '',
      closedBy: null,
      closedAt: null,
    })
    vi.mocked(typesApi.getDossierType).mockResolvedValue({
      id: 't1',
      tenantId: 't',
      code: 'CLAIM',
      label: 'Claim',
      workflowKey: 'wf',
      active: true,
      allowUnknownFields: false,
      fieldDefinitions: [],
    })
    vi.mocked(dossiersApi.listDossierWorkflows).mockResolvedValue([])
    vi.mocked(dossiersApi.listDossierElements).mockResolvedValue([])
    vi.mocked(workflowsApi.getWorkflowDiagram).mockResolvedValue({
      bpmnXml: '<xml/>',
      activeIds: ['a'],
      completedIds: ['b'],
      failedIds: ['c'],
    })
    vi.mocked(workflowsApi.getWorkflowHistory).mockResolvedValue([])
    vi.mocked(workflowsApi.getWorkflowTasks).mockResolvedValue([])
    vi.mocked(tasksApi.completeTask).mockResolvedValue(undefined)
    vi.mocked(workflowsApi.reassignWorkflowTask).mockResolvedValue(undefined)
    vi.mocked(dossiersApi.getDossierById).mockResolvedValue({
      id: 'd1',
      tenantId: 't',
      reference: 'DOS-1',
      dossierTypeCode: 'CLAIM',
      label: 'L',
      status: 'EN_COURS',
      fields: {},
      currentWorkflowLinkId: 'link-1',
      version: 2,
      createdBy: 'u',
      createdAt: '',
      updatedBy: 'u',
      updatedAt: '',
      closedBy: null,
      closedAt: null,
    })

    const store = useDossierStore()
    await store.loadByRef('DOS-1')
    expect(store.diagram?.activeIds).toEqual(['a'])
    await store.complete('task-1')
    expect(tasksApi.completeTask).toHaveBeenCalledWith('task-1', {})
    await store.reassign('link-1', 'task-1', 'u2')
    expect(workflowsApi.reassignWorkflowTask).toHaveBeenCalled()
  })

  it('auth logout and notifications message path', async () => {
    vi.mocked(axios.post).mockResolvedValue({})
    const location = { href: '' }
    vi.stubGlobal('window', { location })
    const auth = useAuthStore()
    auth.user = { id: 'u', name: 'Ada', email: null, roles: [] }
    await auth.logout()
    expect(location.href).toBe('/')

    let source: { onmessage: ((ev: MessageEvent) => void) | null; onerror: ((ev: Event) => void) | null } | null =
      null
    vi.stubGlobal(
      'EventSource',
      class {
        onopen: ((ev: Event) => void) | null = null
        onerror: ((ev: Event) => void) | null = null
        onmessage: ((ev: MessageEvent) => void) | null = null
        close = vi.fn()
        constructor() {
          // eslint-disable-next-line @typescript-eslint/no-this-alias
          source = this
        }
      },
    )
    const notif = useNotificationsStore()
    notif.connect()
    source?.onmessage?.({ data: JSON.stringify({ id: 'n1', title: 'T', body: 'B' }) } as MessageEvent)
    expect(notif.items[0].title).toBe('T')
    source?.onerror?.(new Event('error'))
    expect(notif.failed).toBe(true)
    notif.disconnect()
    vi.unstubAllGlobals()
  })
})
