<script setup lang="ts">
import type { ObservabilitySnapshot } from '@/api/observability'
import ObservabilityKpiCard from './ObservabilityKpiCard.vue'

const props = defineProps<{
  snapshot: ObservabilitySnapshot
}>()

function pdfValue(snapshot: ObservabilitySnapshot): string {
  if (snapshot.pdfRender.count === 0) {
    return `0 rendus · n/a`
  }
  const mean = snapshot.pdfRender.meanMs?.toFixed(0) ?? 'n/a'
  const max = snapshot.pdfRender.maxMs?.toFixed(0) ?? 'n/a'
  return `${snapshot.pdfRender.count} rendus · moy. ${mean} ms · max ${max} ms`
}
</script>

<template>
  <div class="kpi-grid" data-testid="observability-kpis">
    <ObservabilityKpiCard
      test-id="kpi-trigger-outbox-age"
      label="Âge outbox déclenchement"
      :value="`${props.snapshot.triggerOutboxOldestPendingAgeSeconds} s`"
      :severity="props.snapshot.severity.triggerOutboxOldestPendingAge"
    />
    <ObservabilityKpiCard
      test-id="kpi-notification-outbox-age"
      label="Âge outbox notification"
      :value="`${props.snapshot.notificationOutboxOldestPendingAgeSeconds} s`"
      :severity="props.snapshot.severity.notificationOutboxOldestPendingAge"
    />
    <ObservabilityKpiCard
      test-id="kpi-purge-outbox-age"
      label="Âge outbox purge"
      :value="`${props.snapshot.purgeOutboxOldestUnfinishedAgeSeconds} s`"
      :severity="props.snapshot.severity.purgeOutboxOldestUnfinishedAge"
    />
    <ObservabilityKpiCard
      test-id="kpi-flowable-dlq"
      label="Jobs Flowable dead-letter"
      :value="String(props.snapshot.flowableDeadLetterJobCount)"
      :severity="props.snapshot.severity.flowableDeadLetterJobs"
    />
    <ObservabilityKpiCard
      test-id="kpi-flowable-exception"
      label="Jobs Flowable en exception"
      :value="String(props.snapshot.flowableExceptionJobCount)"
      :severity="props.snapshot.severity.flowableExceptionJobs"
    />
    <ObservabilityKpiCard
      test-id="kpi-pdf-render"
      label="Rendu PDF"
      :value="pdfValue(props.snapshot)"
      :severity="props.snapshot.severity.pdfRender"
    />
    <ObservabilityKpiCard
      test-id="kpi-webhook-dead"
      label="Webhooks DEAD"
      :value="String(props.snapshot.webhookDeadCount)"
      :severity="props.snapshot.severity.webhookDead"
    />
    <ObservabilityKpiCard
      test-id="kpi-sse-active"
      label="SSE actifs"
      :value="String(props.snapshot.sseActiveCount)"
      :severity="props.snapshot.severity.sseActive"
    />
  </div>
</template>

<style scoped>
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 0.75rem;
}
</style>
