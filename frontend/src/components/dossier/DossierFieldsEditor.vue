<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { computed, ref, watch } from 'vue'
import type { FieldDefinition, FieldError } from '@/api/types'
import DynamicFields from './DynamicFields.vue'
import { diffDossierFields } from '@/dossier/diffDossierFields'
import { describeDetachedFieldError } from '@/dossier/fieldErrors'

const props = defineProps<{
  definitions: FieldDefinition[]
  /** TODO §80 : code du type (règles : champs calculés, visibilité, obligations — évaluées côté serveur pendant la saisie). */
  typeCode?: string
  fields: Record<string, unknown>
  canEdit: boolean
  locked: boolean
  saving?: boolean
  /** Erreurs `errors[]` du dernier enregistrement (TODO §102). */
  serverErrors?: FieldError[]
}>()

const emit = defineEmits<{
  save: [Record<string, unknown>]
  /** Bannière : texte des erreurs hors écran, ou null pour la retirer. */
  'field-banner': [string | null]
}>()

const draft = ref<Record<string, unknown>>({ ...props.fields })

watch(
  () => props.fields,
  (value) => {
    draft.value = { ...value }
  },
)

const editable = computed(() => props.canEdit && !props.locked)

function onUndisplayed(errors: FieldError[]) {
  if ((props.serverErrors ?? []).length === 0) return
  if (errors.length === 0) {
    emit('field-banner', null)
    return
  }
  const labels = new Map(props.definitions.map((def) => [def.name, def.label]))
  emit('field-banner', errors.map((item) => describeDetachedFieldError(item, labels.get(item.field))).join(' '))
}

function save() {
  const patch = diffDossierFields(props.fields, draft.value, props.definitions)
  if (Object.keys(patch).length === 0) return
  emit('save', patch)
}

const { t } = useI18n()
</script>

<template>
  <section class="dossier-fields-editor">
    <h2>{{ t('dossier.fieldsEditor.title') }}</h2>
    <DynamicFields
      v-model="draft"
      :definitions="definitions"
      :readonly="!editable"
      :apply-defaults="false"
      :type-code="typeCode"
      :server-errors="serverErrors"
      @undisplayed-errors="onUndisplayed"
    />
    <button
      v-if="editable"
      type="button"
      :disabled="saving"
      @click="save"
    >
      {{ t('dossier.fieldsEditor.save') }}
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
