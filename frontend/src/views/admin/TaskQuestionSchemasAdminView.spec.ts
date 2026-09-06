import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import TaskQuestionSchemasAdminView from './TaskQuestionSchemasAdminView.vue'
import * as api from '@/api/taskQuestionSchemas'
import { routes } from '@/app/routes'
import { asPage } from '@/api/page'
import type { Component } from 'vue'

vi.mock('@/api/taskQuestionSchemas')

const SAMPLE = {
  id: '1',
  schemaKey: 'orientation-dossier',
  label: 'Orientation',
  fieldDefinitions: [
    { name: 'decision', label: 'Decision', type: 'SELECT', required: true, options: ['approve', 'reject'] },
  ],
  createdBy: 'admin-1',
  updatedBy: 'admin-1',
}

async function mountView(component: Component, path = '/admin/task-question-schemas') {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(component, { global: { plugins: [router] } })
  return { wrapper, router }
}

describe('TaskQuestionSchemasAdminView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listTaskQuestionSchemas).mockResolvedValue(asPage([SAMPLE] as never))
  })

  it('lists existing schemas', async () => {
    const { wrapper } = await mountView(TaskQuestionSchemasAdminView)
    await flushPromises()

    expect(wrapper.text()).toContain('orientation-dossier')
    expect(wrapper.text()).toContain('Orientation')
  })

  it('creates a schema from the form', async () => {
    vi.mocked(api.createTaskQuestionSchema).mockResolvedValue(SAMPLE as never)
    const { wrapper } = await mountView(TaskQuestionSchemasAdminView)
    await flushPromises()

    const form = wrapper.get('[data-testid="schema-create-form"]')
    await wrapper.get('[data-testid="schema-key-input"]').setValue('new-schema')
    await wrapper.get('[data-testid="schema-label-input"]').setValue('New schema')
    await form.trigger('submit.prevent')
    await flushPromises()

    expect(api.createTaskQuestionSchema).toHaveBeenCalledWith(
      expect.objectContaining({ schemaKey: 'new-schema', label: 'New schema' }),
    )
  })

  it('edits a schema label and saves it', async () => {
    vi.mocked(api.updateTaskQuestionSchema).mockResolvedValue(SAMPLE as never)
    const { wrapper } = await mountView(TaskQuestionSchemasAdminView)
    await flushPromises()

    await wrapper.get('[data-testid="schema-edit"]').trigger('click')
    await wrapper.get('[data-testid="schema-edit-label"]').setValue('Orientation revised')
    await wrapper.get('[data-testid="schema-edit-save"]').trigger('click')
    await flushPromises()

    expect(api.updateTaskQuestionSchema).toHaveBeenCalledWith(
      'orientation-dossier',
      expect.objectContaining({ label: 'Orientation revised' }),
    )
  })

  it('deletes a schema', async () => {
    vi.mocked(api.deleteTaskQuestionSchema).mockResolvedValue(undefined)
    const { wrapper } = await mountView(TaskQuestionSchemasAdminView)
    await flushPromises()

    await wrapper.get('[data-testid="schema-delete"]').trigger('click')
    await flushPromises()

    expect(api.deleteTaskQuestionSchema).toHaveBeenCalledWith('orientation-dossier')
  })

  it('shows the server error message when creating a schema with a colliding field name', async () => {
    vi.mocked(api.createTaskQuestionSchema).mockRejectedValue({
      response: { data: { detail: "Field name 'documentType' collides with a reserved or already-used workflow variable name" } },
    })
    const { wrapper } = await mountView(TaskQuestionSchemasAdminView)
    await flushPromises()

    const form = wrapper.get('[data-testid="schema-create-form"]')
    await wrapper.get('[data-testid="schema-key-input"]').setValue('new-schema')
    await wrapper.get('[data-testid="schema-label-input"]').setValue('New schema')
    await form.trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('collides with a reserved')
  })

  it('shows the server error message when editing a schema fails', async () => {
    vi.mocked(api.updateTaskQuestionSchema).mockRejectedValue({
      response: { data: { detail: 'Unknown schema: orientation-dossier' } },
    })
    const { wrapper } = await mountView(TaskQuestionSchemasAdminView)
    await flushPromises()

    await wrapper.get('[data-testid="schema-edit"]').trigger('click')
    await wrapper.get('[data-testid="schema-edit-save"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Unknown schema: orientation-dossier')
  })
})
