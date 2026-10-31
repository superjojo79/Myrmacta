<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ref } from 'vue'
import type { WorkflowTaskView } from '@/api/types'
import AdminPager from '@/components/admin/AdminPager.vue'
import { resolveTaskFormComponent } from '@/components/workflow/task-forms/taskFormRegistry'

const props = defineProps<{
  tasks: WorkflowTaskView[]
  dossierId?: string
  canReassign?: boolean
  /** D-ASSIGN-CLAIM : l'utilisateur peut modifier le dossier, donc prendre une tâche libre. */
  canClaim?: boolean
  currentUserId?: string | null
  /** Noms affichés par identifiant d'utilisateur (annuaire local), quand ils sont connus. */
  assigneeNames?: Record<string, string>
  totalPages?: number
  /** TODO §113 — groupes vers lesquels un éditeur peut réaffecter une tâche (code, libellé). */
  groupOptions?: { code: string; label: string }[]
}>()

const page = defineModel<number>('page', { default: 0 })

const emit = defineEmits<{
  complete: [taskId: string, variables?: Record<string, unknown>]
  reassign: [taskId: string]
  claim: [taskId: string]
  reassignGroup: [taskId: string, groupCode: string]
}>()

const { t } = useI18n()

const selectedGroup = ref<Record<string, string>>({})

function groupLabels(codes: string[] | undefined): string {
  return (codes ?? []).map((code) => props.groupOptions?.find((option) => option.code === code)?.label ?? code).join(', ')
}

function reassignGroup(taskId: string) {
  const code = selectedGroup.value[taskId]
  if (code) emit('reassignGroup', taskId, code)
}

function assigneeLabel(assignee: string | null): string {
  if (!assignee) return '—'
  if (assignee === props.currentUserId) return t('workflow.tasks.you')
  return props.assigneeNames?.[assignee] ?? assignee
}
</script>

<template>
  <section>
    <h2>{{ t('workflow.tasks.title') }}</h2>
    <p v-if="tasks.length === 0">{{ t('workflow.tasks.empty') }}</p>
    <ul v-else class="task-list">
      <li v-for="task in tasks" :key="task.taskId">
        <div>
          <strong>{{ task.name || task.taskId }}</strong>
          <span class="task-list__meta">{{ t('workflow.tasks.assignee', { name: assigneeLabel(task.assignee) }) }}</span>
          <span v-if="task.candidateGroups?.length" class="task-list__meta" data-testid="task-groups">
            {{ t('workflow.tasks.groups', { groups: groupLabels(task.candidateGroups) }) }}
          </span>
        </div>
        <div class="task-list__actions">
          <component
            :is="resolveTaskFormComponent(task.formKey)"
            v-if="resolveTaskFormComponent(task.formKey) && dossierId"
            :dossier-id="dossierId"
            :task-id="task.taskId"
            :form-key="task.formKey"
            @ready="emit('complete', task.taskId, $event)"
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
            v-if="canClaim && !task.assignee"
            type="button"
            data-testid="claim-task"
            @click="emit('claim', task.taskId)"
          >
            {{ t('workflow.tasks.claim') }}
          </button>
          <button
            v-if="canReassign"
            type="button"
            @click="emit('reassign', task.taskId)"
          >
            {{ t('workflow.tasks.reassign') }}
          </button>
          <template v-if="canClaim && groupOptions?.length">
            <select
              v-model="selectedGroup[task.taskId]"
              :aria-label="t('workflow.tasks.groupChoice')"
              data-testid="reassign-group-select"
            >
              <option value="">{{ t('workflow.tasks.groupChoice') }}</option>
              <option
                v-for="option in groupOptions.filter((o) => !(task.candidateGroups ?? []).includes(o.code))"
                :key="option.code"
                :value="option.code"
              >
                {{ option.label }}
              </option>
            </select>
            <button type="button" data-testid="reassign-group" @click="reassignGroup(task.taskId)">
              {{ t('workflow.tasks.reassignGroup') }}
            </button>
          </template>
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
  flex-wrap: wrap;
  gap: 0.4rem;
  align-items: center;
}
button {
  border: 1px solid #b7b7ba;
  background: white;
  padding: 0.35rem 0.65rem;
  cursor: pointer;
}
</style>
