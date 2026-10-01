<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getDossierById, updateDossierFields } from '@/api/dossiers'
import { getDossierType } from '@/api/dossierTypes'
import { toProblemDetail } from '@/api/http'
import DynamicFields from '@/components/dossier/DynamicFields.vue'
import { diffDossierFields } from '@/dossier/diffDossierFields'
import type { FieldDefinition } from '@/api/types'

// TODO §52 -- kind `dossier-fields` (taskFormRegistry.ts). Like DossierFieldsEditor.vue (the
// dossier-page editor), the PATCH body is a diff against the last known snapshot, built with the
// same diffDossierFields() -- not the full draft state. Submitting the full state was found in
// Phase 9 adversarial review to destroy a `sensitive` field the current actor can never see (it's
// stripped from `fields` by DossierFieldRedactor for anyone but TENANT_ADMIN): the redacted key
// renders as an empty input, and echoing that "" back as an explicit PATCH value wipes the real
// value the user never read. diffDossierFields treats "absent before" and "blank after" as no
// change at all, so an untouched (or touched-then-cleared) redacted field is never sent -- exactly
// the protection DossierFieldsEditor already relies on. Completing without any edit still works:
// an empty patch skips the network call entirely instead of failing a no-op PATCH.
const props = defineProps<{ dossierId: string }>()
const emit = defineEmits<{ ready: [Record<string, unknown>]; error: [string] }>()

const definitions = ref<FieldDefinition[]>([])
const typeCode = ref<string | undefined>(undefined)
const snapshot = ref<Record<string, unknown>>({})
const fields = ref<Record<string, unknown>>({})
const version = ref(0)
const loading = ref(true)
const loadError = ref<string | null>(null)
const submitting = ref(false)

onMounted(load)

async function load() {
  loading.value = true
  loadError.value = null
  try {
    const dossier = await getDossierById(props.dossierId)
    snapshot.value = dossier.fields
    fields.value = { ...dossier.fields }
    version.value = dossier.version
    const type = await getDossierType(dossier.dossierTypeCode)
    definitions.value = type.fieldDefinitions
    typeCode.value = type.code
  } catch (err) {
    loadError.value = toProblemDetail(err).detail ?? 'Impossible de charger le formulaire'
  } finally {
    loading.value = false
  }
}

async function submit() {
  const patch = diffDossierFields(snapshot.value, fields.value, definitions.value)
  if (Object.keys(patch).length === 0) {
    emit('ready', {})
    return
  }
  submitting.value = true
  try {
    const updated = await updateDossierFields(props.dossierId, patch, version.value)
    snapshot.value = updated.fields
    fields.value = { ...updated.fields }
    version.value = updated.version
    emit('ready', {})
  } catch (err) {
    emit('error', toProblemDetail(err).detail ?? 'Mise à jour des champs impossible')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="dossier-fields-task-form">
    <p v-if="loading">Chargement du formulaire…</p>
    <p v-else-if="loadError" class="error">{{ loadError }}</p>
    <template v-else>
      <DynamicFields v-model="fields" :definitions="definitions" :apply-defaults="false" :type-code="typeCode" />
      <button type="button" :disabled="submitting" @click="submit">Compléter</button>
    </template>
  </div>
</template>
