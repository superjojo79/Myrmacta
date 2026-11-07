<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { toProblemDetail } from '@/api/http'
import { addBelgianFederalHolidays, type CalendarSource } from '@/api/tenantCalendar'

/** TODO §121 (D-CAL-SOURCE, D-CAL-BE-SOURCE) — sources du calendrier ; les fériés fédéraux belges sont l'une d'elles. */
defineProps<{ sources: CalendarSource[] }>()
const emit = defineEmits<{ changed: [sources: CalendarSource[]] }>()

const { t } = useI18n()
const error = ref<string | null>(null)
const busy = ref(false)

async function belgian() {
  error.value = null
  busy.value = true
  try {
    emit('changed', (await addBelgianFederalHolidays()).sources)
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? t('admin.types.calendar.errors.save')
  } finally {
    busy.value = false
  }
}

function loaded(source: CalendarSource): string {
  if (!source.loadedAt) return ''
  return t('admin.types.calendar.sources.loaded', {
    date: new Date(source.loadedAt).toLocaleDateString(),
    by: source.loadedBy ?? '—',
  })
}
</script>

<template>
  <div data-testid="calendar-sources">
    <h3>{{ t('admin.types.calendar.sources.title') }}</h3>
    <p v-if="error" class="error" role="alert" data-testid="sources-error">{{ error }}</p>
    <p v-if="sources.length === 0" class="hint" data-testid="calendar-sources-empty">{{ t('admin.types.calendar.sources.empty') }}</p>
    <ul v-else class="source-list">
      <li v-for="source in sources" :key="source.id" data-testid="calendar-source">
        <strong>{{ source.name }}</strong>
        <span class="hint">
          {{ t(`admin.types.calendar.sources.kind.${source.kind}`) }} ·
          {{ t('admin.types.calendar.sources.count', { count: source.entryCount }) }} · {{ loaded(source) }}
        </span>
        <span v-if="source.lastError" class="error" data-testid="source-error">{{ source.lastError }}</span>
      </li>
    </ul>
    <button type="button" :disabled="busy" data-testid="calendar-belgian" @click="belgian">
      {{ t('admin.types.calendar.belgian') }}
    </button>
  </div>
</template>
