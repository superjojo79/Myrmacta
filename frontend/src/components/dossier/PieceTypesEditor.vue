<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { PieceType } from '@/api/types'
import { MAX_PIECE_TYPES } from '@/dossier/validatePieceTypes'

// TODO §105 (issue #211, lot P) — types de pièces justificatives d'un type de dossier, écran Admin → Types.
const model = defineModel<PieceType[]>({ required: true })

function update(index: number, patch: Partial<PieceType>) {
  model.value = model.value.map((type, i) => (i === index ? { ...type, ...patch } : type))
}

function add() {
  model.value = [...model.value, { key: '', label: '', sensitive: false }]
}

function remove(index: number) {
  model.value = model.value.filter((_, i) => i !== index)
}

const { t } = useI18n()
</script>

<template>
  <div class="piece-types-editor" data-testid="piece-types-editor">
    <p class="piece-types-editor__help">{{ t('dossier.pieceTypes.help') }}</p>
    <p v-if="model.length === 0">{{ t('dossier.pieceTypes.none') }}</p>
    <div v-for="(type, index) in model" :key="index" class="piece-types-editor__row" data-testid="piece-type-row">
      <label>
        {{ t('dossier.pieceTypes.key') }}
        <input
          :value="type.key"
          type="text"
          maxlength="60"
          :data-testid="`piece-type-key-${index}`"
          @input="update(index, { key: ($event.target as HTMLInputElement).value.trim() })"
        />
      </label>
      <label>
        {{ t('dossier.pieceTypes.label') }}
        <input
          :value="type.label"
          type="text"
          maxlength="100"
          :data-testid="`piece-type-label-${index}`"
          @input="update(index, { label: ($event.target as HTMLInputElement).value })"
        />
      </label>
      <label class="piece-types-editor__sensitive">
        <input
          type="checkbox"
          :checked="type.sensitive"
          :data-testid="`piece-type-sensitive-${index}`"
          @change="update(index, { sensitive: ($event.target as HTMLInputElement).checked })"
        />
        {{ t('dossier.pieceTypes.sensitive') }}
      </label>
      <button
        type="button"
        :aria-label="t('dossier.pieceTypes.removeNamed', { label: type.label || type.key })"
        :data-testid="`piece-type-remove-${index}`"
        @click="remove(index)"
      >
        {{ t('dossier.pieceTypes.remove') }}
      </button>
    </div>
    <button type="button" :disabled="model.length >= MAX_PIECE_TYPES" data-testid="piece-type-add" @click="add">
      {{ t('dossier.pieceTypes.add') }}
    </button>
  </div>
</template>

<style scoped>
.piece-types-editor__row {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}
.piece-types-editor__help {
  font-size: 0.85rem;
  opacity: 0.8;
}
.piece-types-editor__sensitive {
  display: flex;
  align-items: center;
  gap: 0.3rem;
}
</style>
