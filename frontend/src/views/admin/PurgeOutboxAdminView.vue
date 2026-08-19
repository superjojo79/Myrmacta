<script setup lang="ts">
import { onMounted, ref, shallowRef } from 'vue'
import { toProblemDetail } from '@/api/http'
import { listPurgeOutbox, type PurgeOutboxList } from '@/api/purgeOutbox'
import PurgeOutboxCounts from '@/components/admin/PurgeOutboxCounts.vue'
import PurgeOutboxQueue from '@/components/admin/PurgeOutboxQueue.vue'

const loading = shallowRef(true)
const error = shallowRef<string | null>(null)
const page = ref<PurgeOutboxList | null>(null)

onMounted(async () => {
  try {
    page.value = await listPurgeOutbox()
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? 'Impossible de charger la file de purge'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="purge-outbox-admin">
    <h1>File de purge</h1>
    <p v-if="loading">Chargement…</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <template v-else-if="page">
      <PurgeOutboxCounts :counts="page.counts" />
      <PurgeOutboxQueue :items="page.items" />
    </template>
  </section>
</template>

<style scoped>
.error {
  color: #c92a2a;
}
</style>
