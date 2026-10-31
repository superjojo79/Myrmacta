<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { onMounted, ref, watch } from 'vue'
import { clearDeadline, getDeadline, setDeadline } from '@/api/dossierTypes'
import { listUserGroupOptions, type UserGroupOption } from '@/api/userGroups'
import { toProblemDetail } from '@/api/http'

/** TODO §115 (D-DEADLINE-FIELD, D-DEADLINE-ESCALATE) — échéance du type édité : champ DATE, rappel J-n, groupe d'escalade. */
const props = defineProps<{
  typeCode: string
  /** Champs DATE du type, seuls candidats. */
  dateFields: string[]
}>()

const { t } = useI18n()
const fieldName = ref('')
const reminderDays = ref(0)
const escalationGroup = ref('')
const groups = ref<UserGroupOption[]>([])
const error = ref<string | null>(null)
const saved = ref(false)

async function load() {
  error.value = null
  saved.value = false
  try {
    const [current, options] = await Promise.all([getDeadline(props.typeCode), listUserGroupOptions()])
    groups.value = options
    fieldName.value = current?.fieldName ?? ''
    reminderDays.value = current?.reminderDays ?? 0
    escalationGroup.value = current?.escalationGroup ?? ''
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? t('admin.types.deadline.errors.load')
  }
}

onMounted(load)
watch(() => props.typeCode, load)

async function save() {
  error.value = null
  saved.value = false
  try {
    if (fieldName.value === '') {
      await clearDeadline(props.typeCode)
    } else {
      await setDeadline(props.typeCode, {
        fieldName: fieldName.value,
        reminderDays: Number(reminderDays.value) || 0,
        escalationGroup: escalationGroup.value || null,
      })
    }
    saved.value = true
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? t('admin.types.deadline.errors.save')
  }
}
</script>

<template>
  <fieldset class="deadline" data-testid="deadline-panel">
    <legend>{{ t('admin.types.deadline.legend') }}</legend>
    <p class="hint">{{ t('admin.types.deadline.hint') }}</p>
    <p v-if="error" class="error" role="alert" data-testid="deadline-error">{{ error }}</p>
    <label>
      {{ t('admin.types.deadline.field') }}
      <select v-model="fieldName" data-testid="deadline-field">
        <option value="">{{ t('admin.types.deadline.none') }}</option>
        <option v-for="name in dateFields" :key="name" :value="name">{{ name }}</option>
      </select>
    </label>
    <template v-if="fieldName">
      <label>
        {{ t('admin.types.deadline.reminderDays') }}
        <input v-model.number="reminderDays" type="number" min="0" max="365" data-testid="deadline-reminder-days" />
      </label>
      <label>
        {{ t('admin.types.deadline.escalationGroup') }}
        <select v-model="escalationGroup" data-testid="deadline-escalation-group">
          <option value="">{{ t('admin.types.deadline.noEscalation') }}</option>
          <option v-for="group in groups" :key="group.code" :value="group.code">{{ group.label }} ({{ group.code }})</option>
        </select>
      </label>
    </template>
    <p v-if="dateFields.length === 0" class="hint" data-testid="deadline-no-date-field">{{ t('admin.types.deadline.noDateField') }}</p>
    <button type="button" data-testid="deadline-save" @click="save">{{ t('admin.types.deadline.save') }}</button>
    <span v-if="saved" class="hint" data-testid="deadline-saved">{{ t('admin.types.deadline.saved') }}</span>
  </fieldset>
</template>

<style scoped>
.deadline {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  align-items: center;
}
.deadline legend {
  font-weight: 600;
}
.deadline input[type='number'] {
  width: 5rem;
}
.hint {
  font-size: 0.85rem;
  opacity: 0.75;
  width: 100%;
}
.error {
  color: #c92a2a;
  width: 100%;
}
</style>
