<script setup lang="ts">
import type { PurgeOutboxCounts } from '@/api/purgeOutbox'

defineProps<{
  counts: PurgeOutboxCounts
}>()
</script>

<template>
  <ul class="purge-outbox-counts" data-testid="purge-outbox-counts">
    <li>PENDING : {{ counts.pending }}</li>
    <li>PROCESSING : {{ counts.processing }}</li>
    <li>FAILED : {{ counts.failed }}</li>
    <li>COMPLETED : {{ counts.completed }}</li>
  </ul>
</template>
