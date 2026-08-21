<script setup lang="ts">
import type { WorkflowLink } from '@/api/types'
import AdminPager from '@/components/admin/AdminPager.vue'

defineProps<{
  workflows: WorkflowLink[]
  totalPages?: number
}>()

const page = defineModel<number>('page', { default: 0 })
</script>

<template>
  <section class="workflow-links-list">
    <h2>Workflows</h2>
    <p v-if="workflows.length === 0" class="workflow-links-list__empty">Aucun workflow.</p>
    <ul v-else>
      <li v-for="link in workflows" :key="link.id">
        <div>
          <strong>{{ link.processDefinitionKey }}</strong>
          <span class="workflow-links-list__meta">
            #{{ link.sequence }} · {{ link.status }} · {{ link.startedAt }} → {{ link.endedAt || 'en cours' }}
          </span>
        </div>
      </li>
    </ul>
    <AdminPager v-if="(totalPages ?? 1) > 1" v-model="page" :total-pages="totalPages ?? 1" />
  </section>
</template>

<style scoped>
.workflow-links-list ul {
  list-style: none;
  padding: 0;
  margin: 0;
}
.workflow-links-list li {
  padding: 0.65rem 0;
  border-bottom: 1px solid #ddd;
}
.workflow-links-list__meta {
  display: block;
  font-size: 0.85rem;
  opacity: 0.7;
}
.workflow-links-list__empty {
  opacity: 0.7;
}
</style>
