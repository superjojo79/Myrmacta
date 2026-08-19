<script setup lang="ts">
import type { PurgeOutboxItem } from '@/api/purgeOutbox'

defineProps<{
  items: PurgeOutboxItem[]
}>()
</script>

<template>
  <table class="purge-outbox-queue" data-testid="purge-outbox-queue">
    <thead>
      <tr>
        <th>Dossier</th>
        <th>Statut</th>
        <th>Origine</th>
        <th>Créé</th>
        <th>Erreur</th>
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
