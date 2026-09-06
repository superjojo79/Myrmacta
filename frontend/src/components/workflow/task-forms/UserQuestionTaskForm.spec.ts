import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import UserQuestionTaskForm from './UserQuestionTaskForm.vue'
import { getTaskQuestionSchemaForTask } from '@/api/taskQuestionSchemas'

vi.mock('@/api/taskQuestionSchemas', () => ({
  getTaskQuestionSchemaForTask: vi.fn(),
}))

const SCHEMA = {
  schemaKey: 'orientation-dossier',
  label: 'Orientation',
  fieldDefinitions: [
    {
      name: 'decision',
      label: 'Decision',
      type: 'SELECT',
      required: true,
      options: ['approve', 'reject'],
    },
  ],
}

describe('UserQuestionTaskForm', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('loads the schema referenced by the task formKey and renders its fields', async () => {
    vi.mocked(getTaskQuestionSchemaForTask).mockResolvedValue(SCHEMA as never)

    const wrapper = mount(UserQuestionTaskForm, {
      props: { taskId: 'task-1', formKey: 'user-question:orientation-dossier' },
    })
    await flushPromises()

    expect(getTaskQuestionSchemaForTask).toHaveBeenCalledWith('orientation-dossier', 'task-1')
    expect(wrapper.text()).toContain('Decision')
  })

  it('disables submission until every required field has a value, then emits ready with the answers', async () => {
    vi.mocked(getTaskQuestionSchemaForTask).mockResolvedValue(SCHEMA as never)

    const wrapper = mount(UserQuestionTaskForm, {
      props: { taskId: 'task-1', formKey: 'user-question:orientation-dossier' },
    })
    await flushPromises()

    expect(wrapper.get('button').attributes('disabled')).toBeDefined()

    await wrapper.get('select').setValue('approve')
    expect(wrapper.get('button').attributes('disabled')).toBeUndefined()

    await wrapper.get('button').trigger('click')

    expect(wrapper.emitted('ready')?.[0]).toEqual([{ decision: 'approve' }])
  })

  it('shows a load error when the runtime endpoint rejects', async () => {
    vi.mocked(getTaskQuestionSchemaForTask).mockRejectedValue({
      response: { data: { detail: 'Unknown schema' } },
    })

    const wrapper = mount(UserQuestionTaskForm, {
      props: { taskId: 'task-1', formKey: 'user-question:orientation-dossier' },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('Unknown schema')
    expect(wrapper.find('button').exists()).toBe(false)
  })

  it('shows an error and never calls the API when formKey does not carry a schemaKey', async () => {
    const wrapper = mount(UserQuestionTaskForm, { props: { taskId: 'task-1', formKey: null } })
    await flushPromises()

    expect(getTaskQuestionSchemaForTask).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('formKey invalide')
  })
})
