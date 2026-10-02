<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { WorkflowTaskView } from '@/api/types'
import AdminPager from '@/components/admin/AdminPager.vue'
import { resolveTaskFormComponent } from '@/components/workflow/task-forms/taskFormRegistry'

defineProps<{
  tasks: WorkflowTaskView[]
  dossierId?: string
  canReassign?: boolean
  totalPages?: number
}>()

const page = defineModel<number>('page', { default: 0 })

const emit = defineEmits<{
  complete: [taskId: string, variables?: Record<string, unknown>]
  reassign: [taskId: string]
  'form-error': [message: string]
}>()

const { t } = useI18n()
</script>

<template>
  <section>
    <h2>{{ t('workflow.tasks.title') }}</h2>
    <p v-if="tasks.length === 0">{{ t('workflow.tasks.empty') }}</p>
    <ul v-else class="task-list">
      <li v-for="task in tasks" :key="task.taskId">
        <div>
          <strong>{{ task.name || task.taskId }}</strong>
          <span class="task-list__meta">{{ t('workflow.tasks.assignee', { name: task.assignee || '—' }) }}</span>
        </div>
        <div class="task-list__actions">
          <component
            :is="resolveTaskFormComponent(task.formKey)"
            v-if="resolveTaskFormComponent(task.formKey) && dossierId"
            :dossier-id="dossierId"
            :task-id="task.taskId"
            :form-key="task.formKey"
            @ready="emit('complete', task.taskId, $event)"
            @error="emit('form-error', $event)"
          />
          <button
            v-else
            type="button"
            data-testid="complete-task"
            @click="emit('complete', task.taskId)"
          >
            {{ t('workflow.tasks.complete') }}
          </button>
          <button
            v-if="canReassign"
            type="button"
            @click="emit('reassign', task.taskId)"
          >
            {{ t('workflow.tasks.reassign') }}
          </button>
        </div>
      </li>
    </ul>
    <AdminPager v-if="(totalPages ?? 1) > 1" v-model="page" :total-pages="totalPages ?? 1" />
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
