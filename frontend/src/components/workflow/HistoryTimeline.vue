<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { ActivityHistoryEntry } from '@/api/types'
import AdminPager from '@/components/admin/AdminPager.vue'

defineProps<{
  history: ActivityHistoryEntry[]
  totalPages?: number
}>()

const page = defineModel<number>('page', { default: 0 })

const { t } = useI18n()
</script>

<template>
  <section>
    <h2>{{ t('workflow.history.title') }}</h2>
    <ol class="history-timeline">
      <li v-for="entry in history" :key="entry.activityId + entry.startTime">
        <strong>{{ entry.name || entry.activityId }}</strong>
        <span>{{ entry.startTime }} → {{ entry.endTime || t('workflow.history.ongoing') }}</span>
      </li>
    </ol>
    <p v-if="history.length === 0">{{ t('workflow.history.empty') }}</p>
    <AdminPager v-if="(totalPages ?? 1) > 1" v-model="page" :total-pages="totalPages ?? 1" />
  </section>
</template>

<style scoped>
.history-timeline {
  padding-left: 1.1rem;
}
.history-timeline li {
  margin-bottom: 0.55rem;
}
.history-timeline span {
  display: block;
  font-size: 0.85rem;
  opacity: 0.7;
}
</style>
