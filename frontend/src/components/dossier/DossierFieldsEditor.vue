<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { FieldDefinition } from '@/api/types'
import DynamicFields from './DynamicFields.vue'
import { diffDossierFields } from '@/dossier/diffDossierFields'

const props = defineProps<{
  definitions: FieldDefinition[]
  fields: Record<string, unknown>
  canEdit: boolean
  locked: boolean
  saving?: boolean
}>()

const emit = defineEmits<{
  save: [Record<string, unknown>]
}>()

const draft = ref<Record<string, unknown>>({ ...props.fields })

watch(
  () => props.fields,
  (value) => {
    draft.value = { ...value }
  },
)

const editable = computed(() => props.canEdit && !props.locked)

function save() {
  const patch = diffDossierFields(props.fields, draft.value, props.definitions)
  if (Object.keys(patch).length === 0) return
  emit('save', patch)
}
</script>

<template>
  <section class="dossier-fields-editor">
    <h2>Champs</h2>
    <DynamicFields
      v-model="draft"
      :definitions="definitions"
      :readonly="!editable"
      :apply-defaults="false"
    />
    <button
      v-if="editable"
      type="button"
      :disabled="saving"
      @click="save"
    >
      Enregistrer les champs
    </button>
  </section>
</template>

<style scoped>
.dossier-fields-editor {
  margin-bottom: 1rem;
}
.dossier-fields-editor h2 {
  font-size: 1.05rem;
  margin: 0 0 0.5rem;
}
.dossier-fields-editor button {
  margin-top: 0.5rem;
  border: 1px solid #b7b7ba;
  background: white;
  padding: 0.4rem 0.7rem;
  cursor: pointer;
}
.dossier-fields-editor button:disabled {
  cursor: default;
  opacity: 0.6;
}
</style>
