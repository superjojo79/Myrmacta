<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { listAllDossierTypes } from '@/api/dossierTypes'
import { useDossierStore } from '@/stores/dossier'
import { fieldErrorsOf, toProblemDetail } from '@/api/http'
import type { DossierType, FieldError } from '@/api/types'
import DynamicFields from '@/components/dossier/DynamicFields.vue'
import { onMounted } from 'vue'
import { cleanStructuredFields } from '@/dossier/fieldValues'
import { describeDetachedFieldError } from '@/dossier/fieldErrors'

const { t } = useI18n()
const router = useRouter()
const store = useDossierStore()
const types = ref<DossierType[]>([])
const dossierTypeCode = ref('')
const label = ref('')
const fields = ref<Record<string, unknown>>({})
const error = ref<string | null>(null)
const serverErrors = ref<FieldError[]>([])
const submitting = ref(false)

const selectedType = () => types.value.find((t) => t.code === dossierTypeCode.value) ?? null

function onUndisplayed(errors: FieldError[]) {
  if (serverErrors.value.length === 0) return
  const labels = new Map((selectedType()?.fieldDefinitions ?? []).map((def) => [def.name, def.label]))
  error.value = errors.length > 0 ? errors.map((item) => describeDetachedFieldError(item, labels.get(item.field))).join(' ') : null
}

watch(dossierTypeCode, () => {
  error.value = null
  serverErrors.value = []
})

onMounted(async () => {
  types.value = (await listAllDossierTypes()).filter((t) => t.active)
  if (types.value[0]) dossierTypeCode.value = types.value[0].code
})

async function submit() {
  error.value = null
  serverErrors.value = []
  if (!dossierTypeCode.value || !label.value.trim()) {
    error.value = t('createDossier.errors.required')
    return
  }
  submitting.value = true
  try {
    const created = await store.create({
      dossierTypeCode: dossierTypeCode.value,
      label: label.value.trim(),
      fields: cleanStructuredFields(selectedType()?.fieldDefinitions ?? [], fields.value),
    })
    await router.push(`/dossiers/${created.id}`)
  } catch (err) {
    const problem = toProblemDetail(err)
    const parsed = fieldErrorsOf(problem)
    serverErrors.value = parsed
    if (parsed.length === 0) error.value = problem.detail ?? t('createDossier.errors.create')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="create-dossier">
    <h1>{{ t('createDossier.title') }}</h1>
    <form class="create-dossier__form" @submit.prevent="submit">
      <label>
        {{ t('createDossier.type') }}
        <select v-model="dossierTypeCode">
          <option v-for="type in types" :key="type.code" :value="type.code">{{ type.label }}</option>
        </select>
      </label>
      <label for="dossier-label">
        {{ t('createDossier.label') }}
        <input id="dossier-label" v-model="label" type="text" required />
      </label>
      <DynamicFields
        v-if="selectedType()"
        v-model="fields"
        :definitions="selectedType()!.fieldDefinitions"
        :type-code="selectedType()!.code"
        :server-errors="serverErrors"
        @undisplayed-errors="onUndisplayed"
      />
      <p v-if="error" class="create-dossier__error" role="alert">{{ error }}</p>
      <button type="submit" :disabled="submitting">{{ t('createDossier.create') }}</button>
    </form>
  </section>
</template>

<style scoped>
.create-dossier__form {
  display: grid;
  gap: 0.85rem;
  max-width: 520px;
}
label {
  display: grid;
  gap: 0.3rem;
}
input,
select,
button {
  padding: 0.45rem 0.6rem;
}
button {
  width: fit-content;
  background: var(--color-accent);
  color: white;
  border: 0;
  cursor: pointer;
}
.create-dossier__error {
  color: #c92a2a;
}
</style>
