<script setup lang="ts">
import type { ActivityHistoryEntry } from '@/api/types'

defineProps<{
  history: ActivityHistoryEntry[]
}>()
</script>

<template>
  <section>
    <h2>Historique</h2>
    <ol class="history-timeline">
      <li v-for="entry in history" :key="entry.activityId + entry.startTime">
        <strong>{{ entry.name || entry.activityId }}</strong>
        <span>{{ entry.startTime }} → {{ entry.endTime || 'en cours' }}</span>
      </li>
    </ol>
    <p v-if="history.length === 0">Pas encore d’activité.</p>
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
