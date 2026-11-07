<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { CalendarEntry, CalendarSource } from '@/api/tenantCalendar'

/**
 * TODO §121 (D-CAL-ENTRY, D-CAL-OVERRIDE) — saisies manuelles (supprimables) et jours des sources (lecture seule),
 * triés par début. Un jour de source recouvert par un jour ouvré forcé est signalé.
 */
const props = defineProps<{ manual: CalendarEntry[]; sources: CalendarSource[] }>()
const emit = defineEmits<{ remove: [index: number] }>()

const { t } = useI18n()

interface Row {
  key: string
  entry: CalendarEntry
  origin: string
  manualIndex: number | null
  overridden: boolean
}

const forcedOpen = computed(() => props.manual.filter((entry) => entry.nature === 'OPEN'))

function overlapsForcedOpen(entry: CalendarEntry): boolean {
  return forcedOpen.value.some((open) => open.start <= entry.end && entry.start <= open.end)
}

const rows = computed<Row[]>(() => {
  const all: Row[] = props.manual.map((entry, index) => ({
    key: `m-${index}`,
    entry,
    origin: t('admin.types.calendar.entries.manual'),
    manualIndex: index,
    overridden: false,
  }))
  props.sources.forEach((source) =>
    source.entries.forEach((entry, index) =>
      all.push({ key: `s-${source.id}-${index}`, entry, origin: source.name, manualIndex: null, overridden: overlapsForcedOpen(entry) }),
    ),
  )
  return all.sort((a, b) => a.entry.start.localeCompare(b.entry.start) || a.entry.end.localeCompare(b.entry.end))
})

function period(entry: CalendarEntry): string {
  return entry.start === entry.end ? entry.start : `${entry.start} → ${entry.end}`
}
</script>

<template>
  <p v-if="rows.length === 0" class="hint" data-testid="calendar-entries-empty">{{ t('admin.types.calendar.entries.empty') }}</p>
  <table v-else class="admin-table" data-testid="calendar-entries">
    <thead>
      <tr>
        <th>{{ t('admin.types.calendar.entries.period') }}</th>
        <th>{{ t('admin.types.calendar.entries.nature') }}</th>
        <th>{{ t('admin.types.calendar.entries.label') }}</th>
        <th>{{ t('admin.types.calendar.entries.origin') }}</th>
        <th />
      </tr>
    </thead>
    <tbody>
      <tr v-for="row in rows" :key="row.key" :data-testid="row.manualIndex === null ? 'entry-row-source' : 'entry-row-manual'">
        <td>{{ period(row.entry) }}</td>
        <td>{{ row.entry.nature === 'OPEN' ? t('admin.types.calendar.entries.open') : t('admin.types.calendar.entries.closed') }}</td>
        <td>{{ row.entry.label ?? '' }}</td>
        <td>
          {{ row.origin }}
          <span v-if="row.overridden" class="hint" data-testid="entry-overridden">{{ t('admin.types.calendar.entries.overridden') }}</span>
        </td>
        <td>
          <button
            v-if="row.manualIndex !== null"
            type="button"
            data-testid="entry-remove"
            @click="emit('remove', row.manualIndex)"
          >
            {{ t('admin.types.calendar.entries.remove') }}
          </button>
        </td>
      </tr>
    </tbody>
  </table>
</template>
