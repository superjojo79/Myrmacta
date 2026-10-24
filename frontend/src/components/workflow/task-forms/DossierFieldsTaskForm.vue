<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { onMounted, ref } from 'vue'
import { getDossierById, updateDossierFields } from '@/api/dossiers'
import { getDossierType } from '@/api/dossierTypes'
import { fieldErrorsOf, toProblemDetail } from '@/api/http'
import DynamicFields from '@/components/dossier/DynamicFields.vue'
import { diffDossierFields } from '@/dossier/diffDossierFields'
import { describeDetachedFieldError } from '@/dossier/fieldErrors'
import type { FieldDefinition, FieldError } from '@/api/types'

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
const emit = defineEmits<{ ready: [Record<string, unknown>]; error: [string | null] }>()

const definitions = ref<FieldDefinition[]>([])
const typeCode = ref<string | undefined>(undefined)
const snapshot = ref<Record<string, unknown>>({})
const fields = ref<Record<string, unknown>>({})
const version = ref(0)
const loading = ref(true)
const loadError = ref<string | null>(null)
const serverErrors = ref<FieldError[]>([])
const banner = ref<string | null>(null)
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
    loadError.value = toProblemDetail(err).detail ?? t('workflow.taskForms.errors.form')
  } finally {
    loading.value = false
  }
}

function publish(message: string | null) {
  banner.value = message
  emit('error', message)
}

function onUndisplayed(errors: FieldError[]) {
  if (serverErrors.value.length === 0) return
  if (errors.length === 0) {
    publish(null)
    return
  }
  const labels = new Map(definitions.value.map((def) => [def.name, def.label]))
  publish(errors.map((item) => describeDetachedFieldError(item, labels.get(item.field))).join(' '))
}

async function submit() {
  const patch = diffDossierFields(snapshot.value, fields.value, definitions.value)
  if (Object.keys(patch).length === 0) {
    emit('ready', {})
    return
  }
  const previousErrors = serverErrors.value
  submitting.value = true
  try {
    const updated = await updateDossierFields(props.dossierId, patch, version.value)
    snapshot.value = updated.fields
    fields.value = { ...updated.fields }
    version.value = updated.version
    // Un champ présent dans le patch (y compris `null`, valeur retirée d'un champ masqué) n'est plus
    // en erreur. Les autres, hors schéma ou non touchées, restent. Un patch vide est sorti plus haut :
    // il ne rétracte pas la bannière.
    const kept = previousErrors.filter((error) => !Object.hasOwn(patch, error.field))
    serverErrors.value = kept
    if (kept.length === 0) publish(null)
    emit('ready', {})
  } catch (err) {
    const problem = toProblemDetail(err)
    const parsed = fieldErrorsOf(problem)
    serverErrors.value = parsed
    if (parsed.length === 0) publish(problem.detail ?? t('workflow.taskForms.errors.fields'))
  } finally {
    submitting.value = false
  }
}

const { t } = useI18n()
</script>

<template>
  <div class="dossier-fields-task-form">
    <p v-if="loading">{{ t('workflow.taskForms.loadingForm') }}</p>
    <p v-else-if="loadError" class="error">{{ loadError }}</p>
    <template v-else>
      <DynamicFields v-model="fields" :definitions="definitions" :apply-defaults="false" :type-code="typeCode" :server-errors="serverErrors" @undisplayed-errors="onUndisplayed" />
      <p v-if="banner" class="error" role="alert" data-testid="task-form-banner">{{ banner }}</p>
      <button type="button" :disabled="submitting" @click="submit">{{ t('workflow.taskForms.complete') }}</button>
    </template>
  </div>
</template>
