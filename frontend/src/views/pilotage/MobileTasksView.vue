<script setup lang="ts">
import { onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { RouterLink } from 'vue-router'
import { useTasksStore } from '@/stores/tasks'
import { toProblemDetail } from '@/api/http'
import { ref } from 'vue'

const store = useTasksStore()
const { tasks, loading, error } = storeToRefs(store)
const actionError = ref<string | null>(null)

onMounted(() => {
  void store.refresh()
})

async function complete(taskId: string) {
  actionError.value = null
  try {
    await store.complete(taskId)
  } catch (err) {
    actionError.value = toProblemDetail(err).detail ?? 'Complétion impossible'
  }
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
          </span>
        </div>
        <div class="tasks-view__actions">
          <RouterLink v-if="task.dossierId" :to="`/dossiers/${task.dossierId}`">Dossier</RouterLink>
          <button type="button" @click="complete(task.taskId)">Compléter</button>
        </div>
      </li>
    </ul>
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
