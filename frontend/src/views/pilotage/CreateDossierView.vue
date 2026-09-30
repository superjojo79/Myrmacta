<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { listAllDossierTypes } from '@/api/dossierTypes'
import { useDossierStore } from '@/stores/dossier'
import { toProblemDetail } from '@/api/http'
import type { DossierType } from '@/api/types'
import DynamicFields from '@/components/dossier/DynamicFields.vue'
import { onMounted } from 'vue'
import { cleanStructuredFields } from '@/dossier/fieldValues'

const router = useRouter()
const store = useDossierStore()
const types = ref<DossierType[]>([])
const dossierTypeCode = ref('')
const label = ref('')
const fields = ref<Record<string, unknown>>({})
const error = ref<string | null>(null)
const submitting = ref(false)

const selectedType = () => types.value.find((t) => t.code === dossierTypeCode.value) ?? null

onMounted(async () => {
  types.value = (await listAllDossierTypes()).filter((t) => t.active)
  if (types.value[0]) dossierTypeCode.value = types.value[0].code
})

async function submit() {
  error.value = null
  if (!dossierTypeCode.value || !label.value.trim()) {
    error.value = 'Type et libellé requis'
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
    error.value = toProblemDetail(err).detail ?? 'Création impossible'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="create-dossier">
    <h1>Nouveau dossier</h1>
    <form class="create-dossier__form" @submit.prevent="submit">
      <label>
        Type
        <select v-model="dossierTypeCode">
          <option v-for="type in types" :key="type.code" :value="type.code">{{ type.label }}</option>
        </select>
      </label>
      <label for="dossier-label">
        Libellé
        <input id="dossier-label" v-model="label" type="text" required />
      </label>
      <DynamicFields
        v-if="selectedType()"
        v-model="fields"
        :definitions="selectedType()!.fieldDefinitions"
      />
      <p v-if="error" class="create-dossier__error">{{ error }}</p>
      <button type="submit" :disabled="submitting">Créer</button>
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
