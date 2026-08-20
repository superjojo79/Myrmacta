<script setup lang="ts">
import type { ObservabilitySeverity } from '@/api/observability'

defineProps<{
  testId: string
  label: string
  value: string
  severity: ObservabilitySeverity
}>()
</script>

<template>
  <article :class="['kpi-card', `kpi-card--${severity}`]" :data-testid="testId">
    <p class="kpi-card__label">{{ label }}</p>
    <p class="kpi-card__value">{{ value }}</p>
    <p class="kpi-card__severity">{{ severity }}</p>
  </article>
</template>

<style scoped>
.kpi-card {
  border: 1px solid #dee2e6;
  border-radius: 8px;
  padding: 0.75rem 1rem;
  background: #fff;
}
.kpi-card--ok {
  border-left: 4px solid #2b8a3e;
}
.kpi-card--warn {
  border-left: 4px solid #e67700;
}
.kpi-card--crit {
  border-left: 4px solid #c92a2a;
}
.kpi-card__label {
  margin: 0;
  color: #495057;
  font-size: 0.875rem;
}
.kpi-card__value {
  margin: 0.35rem 0 0;
  font-size: 1.25rem;
  font-weight: 600;
}
.kpi-card__severity {
  margin: 0.25rem 0 0;
  text-transform: uppercase;
  font-size: 0.75rem;
  color: #868e96;
}
</style>
