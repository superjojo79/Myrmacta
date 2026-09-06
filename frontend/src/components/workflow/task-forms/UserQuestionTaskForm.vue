<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getTaskQuestionSchemaForTask } from '@/api/taskQuestionSchemas'
import { toProblemDetail } from '@/api/http'
import { schemaKeyOf } from '@/workflow/taskQuestionFormKeys'
import DynamicFields from '@/components/dossier/DynamicFields.vue'
import type { FieldDefinition } from '@/api/types'

// TODO §55 -- kind `user-question` (taskFormRegistry.ts). Independent of the dossier: the answer
// leaves only as task-completion variables, never a dossier field -- this component never calls
// PATCH /api/dossiers/{id}/fields (unlike DossierFieldsTaskForm.vue). `dossierId` is received only
// because the registry's contract passes it to every kind (MobileTasksView.vue/TaskList.vue),
// unused here beyond that. `formKey` carries the schemaKey (D-USERQ-FORMKEY-PARSING convention,
// mirrored client-side by taskQuestionFormKeys.ts); the server independently re-derives it from
// `taskId` for the runtime read (D-USERQ-RUNTIME-SCOPE) rather than trusting this parse.
const props = defineProps<{ dossierId?: string; taskId: string; formKey?: string | null }>()
const emit = defineEmits<{ ready: [Record<string, unknown>]; error: [string] }>()

const definitions = ref<FieldDefinition[]>([])
const answers = ref<Record<string, unknown>>({})
const loading = ref(true)
const loadError = ref<string | null>(null)

const schemaKey = computed(() => schemaKeyOf(props.formKey))

onMounted(load)

async function load() {
  loading.value = true
  loadError.value = null
  if (!schemaKey.value) {
    loadError.value = 'formKey invalide pour le kind user-question'
    loading.value = false
    return
  }
  try {
    const schema = await getTaskQuestionSchemaForTask(schemaKey.value, props.taskId)
    definitions.value = schema.fieldDefinitions
  } catch (err) {
    loadError.value = toProblemDetail(err).detail ?? 'Impossible de charger le formulaire'
  } finally {
    loading.value = false
  }
}

const missingRequiredField = computed(() =>
  definitions.value.some((def) => {
    if (!def.required) return false
    const value = answers.value[def.name]
    return value == null || value === ''
  }),
)

function submit() {
  emit('ready', { ...answers.value })
}
</script>

<template>
  <div class="user-question-task-form">
    <p v-if="loading">Chargement du formulaire…</p>
    <p v-else-if="loadError" class="error">{{ loadError }}</p>
    <template v-else>
      <DynamicFields v-model="answers" :definitions="definitions" :apply-defaults="false" />
      <button type="button" :disabled="missingRequiredField" @click="submit">Compléter</button>
    </template>
  </div>
</template>
