<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { LiquibaseMigrationStatus, ObservabilitySnapshot } from '@/api/observability'
import ObservabilityKpiCard from './ObservabilityKpiCard.vue'

const props = defineProps<{
  snapshot: ObservabilitySnapshot
}>()
const { t } = useI18n()

function pdfValue(snapshot: ObservabilitySnapshot): string {
  if (snapshot.pdfRender.count === 0) {
    return t('admin.observability.pdfNone')
  }
  const mean = snapshot.pdfRender.meanMs?.toFixed(0) ?? t('admin.observability.na')
  const max = snapshot.pdfRender.maxMs?.toFixed(0) ?? t('admin.observability.na')
  return t('admin.observability.pdfSummary', { count: snapshot.pdfRender.count, mean, max })
}

function migrationValue(m: LiquibaseMigrationStatus): string {
  if (m.appliedChangesetsCount === 0) {
    return t('admin.observability.migrationNone')
  }
  const id = m.lastAppliedChangesetId ?? t('admin.observability.na')
  return t('admin.observability.migrationSummary', { count: m.appliedChangesetsCount, id })
}
</script>

<template>
  <div class="kpi-grid" data-testid="observability-kpis">
    <ObservabilityKpiCard
      test-id="kpi-trigger-outbox-age"
      :label="t('admin.observability.triggerOutboxAge')"
      :value="`${props.snapshot.triggerOutboxOldestPendingAgeSeconds} s`"
      :severity="props.snapshot.severity.triggerOutboxOldestPendingAge"
    />
    <ObservabilityKpiCard
      test-id="kpi-notification-outbox-age"
      :label="t('admin.observability.notificationOutboxAge')"
      :value="`${props.snapshot.notificationOutboxOldestPendingAgeSeconds} s`"
      :severity="props.snapshot.severity.notificationOutboxOldestPendingAge"
    />
    <ObservabilityKpiCard
      test-id="kpi-purge-outbox-age"
      :label="t('admin.observability.purgeOutboxAge')"
      :value="`${props.snapshot.purgeOutboxOldestUnfinishedAgeSeconds} s`"
      :severity="props.snapshot.severity.purgeOutboxOldestUnfinishedAge"
    />
    <ObservabilityKpiCard
      test-id="kpi-flowable-dlq"
      :label="t('admin.observability.deadLetterJobs')"
      :value="String(props.snapshot.flowableDeadLetterJobCount)"
      :severity="props.snapshot.severity.flowableDeadLetterJobs"
    />
    <ObservabilityKpiCard
      test-id="kpi-flowable-exception"
      :label="t('admin.observability.exceptionJobs')"
      :value="String(props.snapshot.flowableExceptionJobCount)"
      :severity="props.snapshot.severity.flowableExceptionJobs"
    />
    <ObservabilityKpiCard
      test-id="kpi-pdf-render"
      :label="t('admin.observability.pdfRender')"
      :value="pdfValue(props.snapshot)"
      :severity="props.snapshot.severity.pdfRender"
    />
    <ObservabilityKpiCard
      test-id="kpi-webhook-dead"
      :label="t('admin.observability.deadWebhooks')"
      :value="String(props.snapshot.webhookDeadCount)"
      :severity="props.snapshot.severity.webhookDead"
    />
    <ObservabilityKpiCard
      test-id="kpi-sse-active"
      :label="t('admin.observability.activeSse')"
      :value="String(props.snapshot.sseActiveCount)"
      :severity="props.snapshot.severity.sseActive"
    />
    <ObservabilityKpiCard
      test-id="kpi-migration"
      :label="t('admin.observability.migration')"
      :value="migrationValue(props.snapshot.migrationStatus)"
      severity="ok"
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
