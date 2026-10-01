<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { computed, ref } from 'vue'
import type { FieldDefinition } from '@/api/types'
import FieldDefinitionDefsEditor from './FieldDefinitionDefsEditor.vue'
import DynamicFields from './DynamicFields.vue'

/**
 * TODO §85 (LC4a) — concepteur de formulaire : l'éditeur de champs (sections, largeur, aide, ordre par glisser-déposer) à
 * côté d'un aperçu rendu par le moteur de saisie réel (`DynamicFields`, celui des tâches et des dossiers ; le portail
 * externe applique la même disposition). `fieldDefinitions` reste la seule source : aucun format propre au concepteur.
 */
const props = withDefaults(defineProps<{ modelValue: FieldDefinition[]; mode?: 'fields' | 'task' }>(), { mode: 'fields' })
const { t } = useI18n()
const emit = defineEmits<{ 'update:modelValue': [FieldDefinition[]] }>()

const preview = ref(true)
const sample = ref<Record<string, unknown>>({})
/** Un champ sans nom n'est pas encore un champ : l'aperçu l'ignore. */
const previewable = computed(() => props.modelValue.filter((def) => (def.name ?? '').trim() !== ''))
</script>

<template>
  <div class="form-designer" data-testid="form-designer">
    <label class="form-designer__toggle">
      <input v-model="preview" type="checkbox" data-testid="form-designer-preview-toggle" />
      {{ t('admin.formDesigner.toggle') }}
    </label>
    <div class="form-designer__panes" :class="{ 'form-designer__panes--split': preview }">
      <FieldDefinitionDefsEditor :model-value="modelValue" :mode="mode" @update:model-value="(v) => emit('update:modelValue', v)" />
      <aside v-if="preview" class="form-designer__preview" :aria-label="t('admin.formDesigner.aria')" data-testid="form-designer-preview">
        <p v-if="previewable.length === 0" class="form-designer__empty">{{ t('admin.formDesigner.empty') }}</p>
        <DynamicFields v-else v-model="sample" :definitions="previewable" />
      </aside>
    </div>
  </div>
</template>

<style scoped>
.form-designer__panes--split {
  display: grid;
  grid-template-columns: minmax(0, 3fr) minmax(0, 2fr);
  gap: 1rem;
  align-items: start;
}
.form-designer__preview {
  position: sticky;
  top: 0.5rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  padding: 0.75rem;
  background: #fafafb;
}
.form-designer__empty {
  color: #6b6b70;
}
@media (max-width: 900px) {
  .form-designer__panes--split {
    grid-template-columns: 1fr;
  }
}
</style>
