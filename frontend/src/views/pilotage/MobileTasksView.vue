<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { RouterLink } from 'vue-router'
import { useTasksStore } from '@/stores/tasks'
import { toProblemDetail } from '@/api/http'
import { useAdminListQuery } from '@/composables/useAdminListQuery'
import AdminPager from '@/components/admin/AdminPager.vue'
import { resolveTaskFormComponent } from '@/components/workflow/task-forms/taskFormRegistry'

const { t } = useI18n()
const store = useTasksStore()
const { tasks, totalPages, loading, error } = storeToRefs(store)
const { page, size } = useAdminListQuery()
const actionError = ref<string | null>(null)

onMounted(() => {
  void store.refresh(page.value, size.value)
})

watch([page, size], ([nextPage, nextSize]) => {
  void store.refresh(nextPage, nextSize)
})

async function complete(taskId: string, variables: Record<string, unknown> = {}) {
  actionError.value = null
  try {
    await store.complete(taskId, variables)
  } catch (err) {
    actionError.value = toProblemDetail(err).detail ?? t('myTasks.errors.complete')
  }
}

</script>

<template>
  <section class="tasks-view">
    <h1>{{ t('myTasks.title') }}</h1>
    <p v-if="loading">{{ t('common.loading') }}</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <p v-else-if="tasks.length === 0" class="empty">{{ t('myTasks.empty') }}</p>
    <ul v-else class="tasks-view__list">
      <li v-for="task in tasks" :key="task.taskId">
        <div>
          <strong>{{ task.name || task.taskId }}</strong>
          <span>
            {{ task.dossierReference || task.dossierId }}
            <em v-if="task.slaRemainingHours != null" data-testid="task-sla">
              {{ t('myTasks.sla', { state: task.slaRemainingHours > 0 ? t('myTasks.slaIn', { n: task.slaRemainingHours }) : t('myTasks.slaOver') }) }}
            </em>
          </span>
        </div>
        <div class="tasks-view__actions">
          <RouterLink v-if="task.dossierId" :to="`/dossiers/${task.dossierId}`">{{ t('myTasks.dossier') }}</RouterLink>
          <component
            :is="resolveTaskFormComponent(task.formKey)"
            v-if="resolveTaskFormComponent(task.formKey)"
            :dossier-id="task.dossierId"
            :task-id="task.taskId"
            :form-key="task.formKey"
            @ready="complete(task.taskId, $event)"
          />
          <button
            v-else
            type="button"
            data-testid="complete-task"
            @click="complete(task.taskId)"
          >
            {{ t('myTasks.complete') }}
          </button>
        </div>
      </li>
    </ul>
    <AdminPager v-if="tasks.length > 0" v-model="page" :total-pages="totalPages" />
    <p v-if="actionError" class="error" role="alert">{{ actionError }}</p>
  </section>
</template>

<style scoped>
.tasks-view__list {
  list-style: none;
  padding: 0;
}
.tasks-view__list li {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.75rem 0;
  border-bottom: 1px solid #ddd;
  flex-wrap: wrap;
}
.tasks-view__list span {
  display: block;
  font-size: 0.85rem;
  opacity: 0.7;
}
.tasks-view__actions {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}
button {
  border: 1px solid #b7b7ba;
  background: white;
  padding: 0.35rem 0.65rem;
  cursor: pointer;
}
.empty,
.error {
  margin-top: 1rem;
}
.error {
  color: #c92a2a;
}

@media (max-width: 640px) {
  .tasks-view__list li {
    flex-direction: column;
  }
}
</style>
