<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ref, watch } from 'vue'
import {
  createTaskQuestionSchema,
  deleteTaskQuestionSchema,
  listTaskQuestionSchemas,
  updateTaskQuestionSchema,
} from '@/api/taskQuestionSchemas'
import type { FieldDefinition, TaskQuestionSchema } from '@/api/types'
import { toProblemDetail } from '@/api/http'
import AdminPager from '@/components/admin/AdminPager.vue'
import FormDesigner from '@/components/dossier/FormDesigner.vue'
import { validateFieldDefinitions } from '@/dossier/validateFieldDefinitions'
import { useAdminListQuery } from '@/composables/useAdminListQuery'

// TODO §55 -- kind `user-question` (taskFormRegistry.ts). FieldType.FILE is rejected server-side
// (TaskQuestionSchema constructor) but still offered by FieldDefinitionDefsEditor -- not worth
// forking/parameterizing a shared component just to hide one option; the server error surfaces
// clearly in `error`/`editFieldErrors` below if an admin picks it anyway.
const { page, size } = useAdminListQuery()
const schemas = ref<TaskQuestionSchema[]>([])
const totalPages = ref(0)
const error = ref<string | null>(null)
const form = ref({
  schemaKey: '',
  label: '',
  fieldDefinitions: [] as FieldDefinition[],
})

const editingKey = ref<string | null>(null)
const editDraft = ref<{ label: string; fieldDefinitions: FieldDefinition[] }>({
  label: '',
  fieldDefinitions: [],
})
const editFieldErrors = ref<string[]>([])

async function refresh() {
  const result = await listTaskQuestionSchemas({ page: page.value, size: size.value })
  schemas.value = result.content
  totalPages.value = result.totalPages
}

watch(
  [page, size],
  () => {
    void refresh().catch((err) => {
      error.value = toProblemDetail(err).detail ?? t('admin.schemas.errors.load')
    })
  },
  { immediate: true },
)

async function create() {
  error.value = null
  const fieldErrors = validateFieldDefinitions(form.value.fieldDefinitions)
  if (fieldErrors.length > 0) {
    error.value = fieldErrors.join(' ')
    return
  }
  try {
    await createTaskQuestionSchema({
      schemaKey: form.value.schemaKey,
      label: form.value.label,
      fieldDefinitions: form.value.fieldDefinitions,
    })
    form.value = { schemaKey: '', label: '', fieldDefinitions: [] }
    await refresh()
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? t('admin.schemas.errors.create')
  }
}

function startEdit(schema: TaskQuestionSchema) {
  editingKey.value = schema.schemaKey
  editDraft.value = { label: schema.label, fieldDefinitions: [...schema.fieldDefinitions] }
  editFieldErrors.value = []
}

function cancelEdit() {
  editingKey.value = null
  editFieldErrors.value = []
}

async function saveEdit() {
  const fieldErrors = validateFieldDefinitions(editDraft.value.fieldDefinitions)
  if (fieldErrors.length > 0) {
    editFieldErrors.value = fieldErrors
    return
  }
  try {
    await updateTaskQuestionSchema(editingKey.value as string, {
      label: editDraft.value.label,
      fieldDefinitions: editDraft.value.fieldDefinitions,
    })
    editingKey.value = null
    editFieldErrors.value = []
    await refresh()
  } catch (err) {
    editFieldErrors.value = [toProblemDetail(err).detail ?? t('admin.schemas.errors.save')]
  }
}

async function remove(schema: TaskQuestionSchema) {
  error.value = null
  try {
    await deleteTaskQuestionSchema(schema.schemaKey)
    await refresh()
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? t('admin.schemas.errors.remove')
  }
}

const { t } = useI18n()
</script>

<template>
  <section>
    <h1>{{ t('admin.schemas.title') }}</h1>
    <i18n-t keypath="admin.schemas.hint" tag="p" class="hint">
      <template #userTask><code>userTask</code></template>
      <template #formKey><code>formKey = "user-question:&lt;schemaKey&gt;"</code></template>
    </i18n-t>
    <p v-if="error" class="error">{{ error }}</p>

    <form class="admin-form" data-testid="schema-create-form" @submit.prevent="create">
      <input v-model="form.schemaKey" :placeholder="t('admin.schemas.keyPlaceholder')" required data-testid="schema-key-input" />
      <input v-model="form.label" :placeholder="t('admin.schemas.labelPlaceholder')" required data-testid="schema-label-input" />
      <FormDesigner v-model="form.fieldDefinitions" mode="task" />
      <button type="submit">{{ t('admin.schemas.create') }}</button>
    </form>

    <table>
      <thead>
        <tr>
          <th>{{ t('admin.schemas.keyPlaceholder') }}</th>
          <th>{{ t('admin.schemas.label') }}</th>
          <th>{{ t('admin.schemas.fields') }}</th>
          <th />
        </tr>
      </thead>
      <tbody>
        <tr v-for="schema in schemas" :key="schema.schemaKey">
          <td>{{ schema.schemaKey }}</td>
          <td>{{ schema.label }}</td>
          <td>{{ schema.fieldDefinitions.length }}</td>
          <td class="actions">
            <button type="button" data-testid="schema-edit" @click="startEdit(schema)">{{ t('admin.schemas.edit') }}</button>
            <button type="button" data-testid="schema-delete" @click="remove(schema)">{{ t('admin.schemas.remove') }}</button>
          </td>
        </tr>
      </tbody>
    </table>
    <AdminPager v-model="page" :total-pages="totalPages" />

    <section v-if="editingKey" class="edit-panel" data-testid="schema-edit-panel">
      <h2>{{ t('admin.schemas.editTitle', { key: editingKey }) }}</h2>
      <p v-if="editFieldErrors.length" class="error">{{ editFieldErrors.join(' ') }}</p>
      <label>
        {{ t('admin.schemas.label') }}
        <input v-model="editDraft.label" data-testid="schema-edit-label" />
      </label>
      <FormDesigner v-model="editDraft.fieldDefinitions" mode="task" />
      <div class="edit-panel__actions">
        <button type="button" data-testid="schema-edit-save" @click="saveEdit">{{ t('admin.schemas.save') }}</button>
        <button type="button" data-testid="schema-edit-cancel" @click="cancelEdit">{{ t('common.cancel') }}</button>
      </div>
    </section>
  </section>
</template>

<style scoped>
.admin-form {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin: 1rem 0;
  align-items: end;
}
.hint {
  color: #555;
  font-size: 0.95rem;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th,
td {
  border-bottom: 1px solid #ddd;
  text-align: left;
  padding: 0.45rem;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}
.error {
  color: #c92a2a;
}
.edit-panel {
  margin-top: 1.5rem;
  padding-top: 1rem;
  border-top: 1px solid #ddd;
}
.edit-panel__actions {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.5rem;
}
button,
input,
select {
  padding: 0.4rem 0.6rem;
}
label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.9rem;
}
</style>
