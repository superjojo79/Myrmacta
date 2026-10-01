<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { SUPPORTED_LOCALES, setLocale, useLocale, type AppLocale } from '@/i18n'

// TODO §90 (D7) — sélecteur de langue partagé par le SPA et le portail public (aucune dépendance au store, au routeur ni à axios).
const { t } = useI18n()
const { locale } = useLocale()

function onChange(event: Event): void {
  setLocale((event.target as HTMLSelectElement).value as AppLocale)
}
</script>

<template>
  <label class="locale-switcher">
    <span class="locale-switcher__label">{{ t('common.language') }}</span>
    <select :value="locale" data-testid="locale-switcher" :aria-label="t('common.language')" @change="onChange">
      <option v-for="code in SUPPORTED_LOCALES" :key="code" :value="code">{{ t(`common.languages.${code}`) }}</option>
    </select>
  </label>
</template>

<style scoped>
.locale-switcher { display: inline-flex; align-items: center; gap: 0.4rem; font-size: 0.85rem; }
.locale-switcher__label { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
</style>
