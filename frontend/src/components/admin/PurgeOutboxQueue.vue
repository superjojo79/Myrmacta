<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { PurgeOutboxItem } from '@/api/purgeOutbox'

defineProps<{
  items: PurgeOutboxItem[]
}>()

const { t } = useI18n()
</script>

<template>
  <table class="purge-outbox-queue" data-testid="purge-outbox-queue">
    <thead>
      <tr>
        <th>{{ t('admin.purgeQueue.dossier') }}</th>
        <th>{{ t('admin.purgeQueue.status') }}</th>
        <th>{{ t('admin.purgeQueue.origin') }}</th>
        <th>{{ t('admin.purgeQueue.created') }}</th>
        <th>{{ t('admin.purgeQueue.error') }}</th>
      </tr>
    </thead>
    <tbody>
      <tr v-for="item in items" :key="item.id">
        <td>{{ item.dossierId }}</td>
        <td>{{ item.status }}</td>
        <td>{{ item.origin }}</td>
        <td>{{ item.createdAt }}</td>
        <td>{{ item.lastError || '—' }}</td>
      </tr>
    </tbody>
  </table>
</template>
