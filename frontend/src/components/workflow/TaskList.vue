<script setup lang="ts">
import type { WorkflowTaskView } from '@/api/types'

defineProps<{
  tasks: WorkflowTaskView[]
  canReassign?: boolean
}>()

const emit = defineEmits<{
  complete: [taskId: string]
  reassign: [taskId: string]
}>()
</script>

<template>
  <section>
    <h2>Tâches du lien</h2>
    <p v-if="tasks.length === 0">Aucune tâche ouverte.</p>
    <ul v-else class="task-list">
      <li v-for="task in tasks" :key="task.taskId">
        <div>
          <strong>{{ task.name || task.taskId }}</strong>
          <span class="task-list__meta">Assignee : {{ task.assignee || '—' }}</span>
        </div>
        <div class="task-list__actions">
          <button type="button" @click="emit('complete', task.taskId)">Compléter</button>
          <button
            v-if="canReassign"
            type="button"
            @click="emit('reassign', task.taskId)"
          >
            Réassigner
          </button>
        </div>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.task-list {
  list-style: none;
  padding: 0;
}
.task-list li {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.6rem 0;
  border-bottom: 1px solid #ddd;
}
.task-list__meta {
  display: block;
  font-size: 0.85rem;
  opacity: 0.7;
}
.task-list__actions {
  display: flex;
  gap: 0.4rem;
}
button {
  border: 1px solid #b7b7ba;
  background: white;
  padding: 0.35rem 0.65rem;
  cursor: pointer;
}
</style>
