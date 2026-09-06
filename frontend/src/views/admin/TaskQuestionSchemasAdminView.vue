<script setup lang="ts">
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
import FieldDefinitionDefsEditor from '@/components/dossier/FieldDefinitionDefsEditor.vue'
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
      error.value = toProblemDetail(err).detail ?? 'Chargement impossible'
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
    error.value = toProblemDetail(err).detail ?? 'Création impossible'
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
    editFieldErrors.value = [toProblemDetail(err).detail ?? 'Enregistrement du schéma impossible']
  }
}

async function remove(schema: TaskQuestionSchema) {
  error.value = null
  try {
    await deleteTaskQuestionSchema(schema.schemaKey)
    await refresh()
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? 'Suppression impossible'
  }
}
</script>

<template>
  <section>
    <h1>Schémas de questions de tâche</h1>
    <p class="hint">
      Un <code>userTask</code> BPMN déclarant <code>formKey = "user-question:&lt;schemaKey&gt;"</code>
      affiche le mini-formulaire correspondant avant complétion — réponse indépendante du dossier.
    </p>
    <p v-if="error" class="error">{{ error }}</p>

    <form class="admin-form" data-testid="schema-create-form" @submit.prevent="create">
      <input v-model="form.schemaKey" placeholder="schemaKey" required data-testid="schema-key-input" />
      <input v-model="form.label" placeholder="libellé" required data-testid="schema-label-input" />
      <FieldDefinitionDefsEditor v-model="form.fieldDefinitions" />
      <button type="submit">Créer</button>
    </form>

    <table>
      <thead>
        <tr>
          <th>schemaKey</th>
          <th>Libellé</th>
          <th>Champs</th>
          <th />
        </tr>
      </thead>
      <tbody>
        <tr v-for="schema in schemas" :key="schema.schemaKey">
          <td>{{ schema.schemaKey }}</td>
          <td>{{ schema.label }}</td>
          <td>{{ schema.fieldDefinitions.length }}</td>
          <td class="actions">
            <button type="button" data-testid="schema-edit" @click="startEdit(schema)">Modifier</button>
            <button type="button" data-testid="schema-delete" @click="remove(schema)">Supprimer</button>
          </td>
        </tr>
      </tbody>
    </table>
    <AdminPager v-model="page" :total-pages="totalPages" />

    <section v-if="editingKey" class="edit-panel" data-testid="schema-edit-panel">
      <h2>Modifier — {{ editingKey }}</h2>
      <p v-if="editFieldErrors.length" class="error">{{ editFieldErrors.join(' ') }}</p>
      <label>
        Libellé
        <input v-model="editDraft.label" data-testid="schema-edit-label" />
      </label>
      <FieldDefinitionDefsEditor v-model="editDraft.fieldDefinitions" />
      <div class="edit-panel__actions">
        <button type="button" data-testid="schema-edit-save" @click="saveEdit">Enregistrer</button>
        <button type="button" data-testid="schema-edit-cancel" @click="cancelEdit">Annuler</button>
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
