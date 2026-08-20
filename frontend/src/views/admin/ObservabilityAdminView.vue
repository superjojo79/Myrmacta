<script setup lang="ts">
import ObservabilityKpiGrid from '@/components/admin/observability/ObservabilityKpiGrid.vue'
import { useObservabilitySnapshot } from '@/composables/useObservabilitySnapshot'

const { snapshot, loading, error, refresh } = useObservabilitySnapshot()
</script>

<template>
  <section class="observability-admin" data-testid="observability-page">
    <h1>Observabilité</h1>
    <button type="button" data-testid="observability-refresh" :disabled="loading" @click="refresh">
      Rafraîchir
    </button>
    <p v-if="loading">Chargement…</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <ObservabilityKpiGrid v-else-if="snapshot" :snapshot="snapshot" />
  </section>
</template>

<style scoped>
.observability-admin {
  display: grid;
  gap: 1rem;
}
.error {
  color: #c92a2a;
}
</style>
