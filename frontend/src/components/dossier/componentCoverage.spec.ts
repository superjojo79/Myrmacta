import { describe, expect, it, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import DynamicFields from './DynamicFields.vue'
import StartWorkflowDialog from '../workflow/StartWorkflowDialog.vue'
import * as workflowsApi from '@/api/workflows'

vi.mock('@/api/workflows')

describe('component coverage extras', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('DynamicFields boolean/select and StartWorkflow other config', async () => {
    setActivePinia(createPinia())
    const fields = mount(DynamicFields, {
      props: {
        definitions: [
          { name: 'ok', label: 'OK', type: 'BOOLEAN', required: false },
          {
            name: 'choice',
            label: 'Choice',
            type: 'SELECT',
            required: false,
            options: ['A', 'B'],
          },
        ],
        modelValue: { ok: false, choice: '' },
      },
    })
    await fields.get('input[type="checkbox"]').setValue(true)
    await fields.get('select').setValue('A')
    expect(fields.emitted('update:modelValue')?.length).toBeGreaterThan(0)

    vi.mocked(workflowsApi.listStartVariables).mockResolvedValue([])
    vi.mocked(workflowsApi.listAllWorkflowTypeConfigs).mockResolvedValue([
      {
        id: '1',
        tenantId: 't',
        workflowKey: 'other',
        label: 'Other',
        statusDefinitions: [],
        allowedDossierStatuses: [],
        onStartDossierStatus: null,
        observerUserIds: [],
      },
    ])
    const dialog = mount(StartWorkflowDialog, {
      props: { open: true, defaultWorkflowKey: 'default' },
    })
    await flushPromises()
    await dialog.get('input[type="checkbox"]').setValue(true)
    await dialog.get('select').setValue('other')
    await flushPromises()
    await dialog.get('button.primary').trigger('click')
    expect(dialog.emitted('start')?.[0]).toEqual(['other', {}])
  })
})
