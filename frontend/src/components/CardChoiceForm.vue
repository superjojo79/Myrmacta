<script setup lang="ts">
import { onMounted, useTemplateRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatDate } from '@/i18n/format'
import type { CardChoice } from '@/lib/cardBridge'

// Lot 3c — choix du lecteur et du certificat de signature (BeIDConnect, plus d'un choix). Boutons radio d'un même
// groupe : étiquetés par un `fieldset`, navigables aux flèches, un choix présélectionné. Le libellé vient du certificat
// (nom du titulaire, fin de validité) et jamais du numéro de registre national.
const props = defineProps<{
  choices: CardChoice[]
  modelValue: string
  /** Préfixe i18n (`portal.signature.card` ou `mySignatures.card`). */
  prefix: string
  /** Distingue les groupes de plusieurs lignes d'une même page. */
  idBase: string
  /** Style du portail public (`portal__primary`/`portal__secondary`). */
  portal?: boolean
}>()
const emit = defineEmits<{ 'update:modelValue': [id: string]; submit: []; cancel: [] }>()
const { t } = useI18n()
const root = useTemplateRef<HTMLFormElement>('root')

onMounted(() => root.value?.querySelector<HTMLInputElement>('input:checked')?.focus())

function holderOf(choice: CardChoice, index: number): string {
  return choice.holder ?? t(`${props.prefix}.choice.fallback`, { n: index + 1 })
}
</script>

<template>
  <form ref="root" class="card-choice" data-testid="signature-choice-form" @submit.prevent="emit('submit')">
    <fieldset>
      <legend>{{ t(`${prefix}.choice.title`) }}</legend>
      <label v-for="(choice, index) in choices" :key="choice.id" class="card-choice__item">
        <input
          type="radio"
          :name="`${idBase}-choice`"
          :value="choice.id"
          :checked="choice.id === modelValue"
          data-testid="signature-choice"
          @change="emit('update:modelValue', choice.id)"
        />
        <span>
          <strong>{{ holderOf(choice, index) }}</strong>
          <span v-if="choice.notAfter"> — {{ t(`${prefix}.choice.validUntil`, { date: formatDate(choice.notAfter, { dateStyle: 'medium' }) }) }}</span>
          <span v-if="choice.readerName" class="card-choice__reader">{{ t(`${prefix}.choice.reader`, { reader: choice.readerName }) }}</span>
        </span>
      </label>
    </fieldset>
    <button type="submit" :class="{ portal__primary: portal }" data-testid="signature-choice-submit">
      {{ t(`${prefix}.choice.continue`) }}
    </button>
    <button type="button" :class="{ portal__secondary: portal }" data-testid="signature-choice-cancel" @click="emit('cancel')">
      {{ t(`${prefix}.choice.cancel`) }}
    </button>
  </form>
</template>

<style scoped>
.card-choice fieldset {
  border: 0;
  padding: 0;
  margin: 0 0 0.75rem;
}
.card-choice__item {
  display: flex;
  gap: 0.5rem;
  align-items: flex-start;
  margin: 0.4rem 0;
}
.card-choice__reader {
  display: block;
  font-size: 0.9rem;
  opacity: 0.8;
}
</style>
