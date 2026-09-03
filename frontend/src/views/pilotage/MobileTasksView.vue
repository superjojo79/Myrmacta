<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { RouterLink } from 'vue-router'
import { useTasksStore } from '@/stores/tasks'
import { toProblemDetail } from '@/api/http'
import { useAdminListQuery } from '@/composables/useAdminListQuery'
import AdminPager from '@/components/admin/AdminPager.vue'
import { resolveTaskFormComponent } from '@/components/workflow/task-forms/taskFormRegistry'

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
    actionError.value = toProblemDetail(err).detail ?? 'Complétion impossible'
  }
}

function onFormError(message: string) {
  actionError.value = message
}
</script>

<template>
  <section class="tasks-view">
    <h1>Mes tâches</h1>
    <p v-if="loading">Chargement…</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <p v-else-if="tasks.length === 0" class="empty">Aucune tâche assignée.</p>
    <ul v-else class="tasks-view__list">
      <li v-for="task in tasks" :key="task.taskId">
        <div>
          <strong>{{ task.name || task.taskId }}</strong>
          <span>
            {{ task.dossierReference || task.dossierId }}
            <em v-if="task.slaRemainingHours != null" data-testid="task-sla">
              SLA {{ task.slaRemainingHours > 0 ? `dans ${task.slaRemainingHours} h` : `dépassée` }}
            </em>
          </span>
        </div>
        <div class="tasks-view__actions">
          <RouterLink v-if="task.dossierId" :to="`/dossiers/${task.dossierId}`">Dossier</RouterLink>
          <component
            :is="resolveTaskFormComponent(task.formKey)"
            v-if="resolveTaskFormComponent(task.formKey)"
            :dossier-id="task.dossierId"
            @ready="complete(task.taskId, $event)"
            @error="onFormError"
          />
          <button
            v-else
            type="button"
            data-testid="complete-task"
            @click="complete(task.taskId)"
          >
            Compléter
          </button>
        </div>
      </li>
    </ul>
    <AdminPager v-if="tasks.length > 0" v-model="page" :total-pages="totalPages" />
    <p v-if="actionError" class="error">{{ actionError }}</p>
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
