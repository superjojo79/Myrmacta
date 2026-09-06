import axios from 'axios'
import type { FieldDefinition, TaskQuestionSchema, TaskQuestionSchemaRuntime } from './types'
import type { Page } from './page'

export async function listTaskQuestionSchemas(
  params: { page?: number; size?: number } = {},
): Promise<Page<TaskQuestionSchema>> {
  const query: Record<string, number> = {}
  if (params.page != null) query.page = params.page
  if (params.size != null) query.size = params.size
  const { data } = await axios.get<Page<TaskQuestionSchema>>('/api/task-question-schemas', { params: query })
  return data
}

export async function createTaskQuestionSchema(payload: {
  schemaKey: string
  label: string
  fieldDefinitions: FieldDefinition[]
}): Promise<TaskQuestionSchema> {
  const { data } = await axios.post<TaskQuestionSchema>('/api/task-question-schemas', payload)
  return data
}

export async function updateTaskQuestionSchema(
  schemaKey: string,
  payload: { label: string; fieldDefinitions: FieldDefinition[] },
): Promise<TaskQuestionSchema> {
  const { data } = await axios.put<TaskQuestionSchema>(`/api/task-question-schemas/${schemaKey}`, payload)
  return data
}

export async function deleteTaskQuestionSchema(schemaKey: string): Promise<void> {
  await axios.delete(`/api/task-question-schemas/${schemaKey}`)
}

/** D-USERQ-RUNTIME-SCOPE: taskId required — the server rejects a schemaKey the task doesn't reference. */
export async function getTaskQuestionSchemaForTask(
  schemaKey: string,
  taskId: string,
): Promise<TaskQuestionSchemaRuntime> {
  const { data } = await axios.get<TaskQuestionSchemaRuntime>(
    `/api/task-question-schemas/${schemaKey}/runtime`,
    { params: { taskId } },
  )
  return data
}
