<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { computed, onMounted, ref } from 'vue'
import {
  addUserGroupMember,
  createUserGroup,
  deleteUserGroup,
  listUserDirectory,
  listUserGroups,
  removeUserGroupMember,
  updateUserGroup,
  type DirectoryUser,
  type UserGroup,
} from '@/api/userGroups'
import { toProblemDetail } from '@/api/http'

const { t } = useI18n()
const groups = ref<UserGroup[]>([])
const directory = ref<DirectoryUser[]>([])
const error = ref<string | null>(null)
const form = ref({ code: '', label: '', idpGroup: '' })
const edits = ref<Record<string, { label: string; idpGroup: string }>>({})
const selectedMember = ref<Record<string, string>>({})

const directoryById = computed(() => new Map(directory.value.map((user) => [user.userId, user])))

function candidates(group: UserGroup): DirectoryUser[] {
  const members = new Set(group.members.map((member) => member.userId))
  return directory.value.filter((user) => !members.has(user.userId))
}

function memberLabel(userId: string, displayName: string): string {
  const email = directoryById.value.get(userId)?.email
  return email ? `${displayName} (${email})` : displayName
}

function remember(group: UserGroup) {
  edits.value[group.code] = { label: group.label, idpGroup: group.idpGroup ?? '' }
}

function replace(updated: UserGroup) {
  groups.value = groups.value.map((group) => (group.code === updated.code ? updated : group))
  remember(updated)
}

async function refresh() {
  const [loadedGroups, loadedDirectory] = await Promise.all([listUserGroups(), listUserDirectory()])
  groups.value = loadedGroups
  directory.value = loadedDirectory
  edits.value = {}
  loadedGroups.forEach(remember)
}

async function run(action: () => Promise<void>, fallback: string) {
  error.value = null
  try {
    await action()
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? t(fallback)
  }
}

onMounted(() => {
  void run(refresh, 'admin.userGroups.errors.load')
})

function create() {
  return run(async () => {
    await createUserGroup({
      code: form.value.code.trim(),
      label: form.value.label.trim(),
      idpGroup: form.value.idpGroup.trim() || null,
    })
    form.value = { code: '', label: '', idpGroup: '' }
    await refresh()
  }, 'admin.userGroups.errors.create')
}

function save(group: UserGroup) {
  const edit = edits.value[group.code]
  return run(async () => {
    replace(await updateUserGroup(group.code, { label: edit.label.trim(), idpGroup: edit.idpGroup.trim() || null }))
  }, 'admin.userGroups.errors.save')
}

function remove(group: UserGroup) {
  return run(async () => {
    await deleteUserGroup(group.code)
    await refresh()
  }, 'admin.userGroups.errors.remove')
}

function addMember(group: UserGroup) {
  const userId = selectedMember.value[group.code]
  if (!userId) return Promise.resolve()
  return run(async () => {
    replace(await addUserGroupMember(group.code, userId))
    selectedMember.value[group.code] = ''
  }, 'admin.userGroups.errors.member')
}

function removeMember(group: UserGroup, userId: string) {
  return run(async () => {
    replace(await removeUserGroupMember(group.code, userId))
  }, 'admin.userGroups.errors.member')
}
</script>

<template>
  <section>
    <h1>{{ t('admin.userGroups.title') }}</h1>
    <p class="hint">{{ t('admin.userGroups.hint') }}</p>
    <p v-if="error" class="error" role="alert" data-testid="group-error">{{ error }}</p>
    <form class="admin-form" data-testid="group-create-form" @submit.prevent="create">
      <input v-model="form.code" :placeholder="t('admin.userGroups.codePlaceholder')" required data-testid="group-code" />
      <input v-model="form.label" :placeholder="t('admin.userGroups.labelPlaceholder')" required data-testid="group-label" />
      <input v-model="form.idpGroup" :placeholder="t('admin.userGroups.idpGroupPlaceholder')" data-testid="group-idp" />
      <button type="submit">{{ t('admin.userGroups.create') }}</button>
    </form>
    <p v-if="groups.length === 0" class="empty">{{ t('admin.userGroups.empty') }}</p>
    <ul data-testid="group-list">
      <li v-for="group in groups" :key="group.code" :data-testid="`group-${group.code}`">
        <div class="group-head">
          <strong>{{ group.code }}</strong>
          <template v-if="edits[group.code]">
            <input
              v-model="edits[group.code].label"
              :aria-label="t('admin.userGroups.labelPlaceholder')"
              :data-testid="`group-edit-label-${group.code}`"
            />
            <input
              v-model="edits[group.code].idpGroup"
              :placeholder="t('admin.userGroups.idpGroupPlaceholder')"
              :aria-label="t('admin.userGroups.idpGroupPlaceholder')"
              :data-testid="`group-edit-idp-${group.code}`"
            />
          </template>
          <button type="button" :data-testid="`group-save-${group.code}`" @click="save(group)">
            {{ t('admin.userGroups.save') }}
          </button>
          <button type="button" :data-testid="`group-remove-${group.code}`" @click="remove(group)">
            {{ t('admin.userGroups.remove') }}
          </button>
        </div>
        <p v-if="group.idpGroup" class="hint">{{ t('admin.userGroups.idpGroupNote', { group: group.idpGroup }) }}</p>
        <ul class="members">
          <li v-if="group.members.length === 0" class="empty">{{ t('admin.userGroups.noMember') }}</li>
          <li v-for="member in group.members" :key="member.userId">
            {{ memberLabel(member.userId, member.displayName) }}
            <button
              type="button"
              :data-testid="`group-member-remove-${group.code}-${member.userId}`"
              @click="removeMember(group, member.userId)"
            >
              {{ t('admin.userGroups.removeMember') }}
            </button>
          </li>
        </ul>
        <div class="add-member">
          <select
            v-model="selectedMember[group.code]"
            :aria-label="t('admin.userGroups.addMember')"
            :data-testid="`group-member-select-${group.code}`"
          >
            <option value="">{{ t('admin.userGroups.chooseMember') }}</option>
            <option v-for="user in candidates(group)" :key="user.userId" :value="user.userId">
              {{ memberLabel(user.userId, user.displayName) }}
            </option>
          </select>
          <button type="button" :data-testid="`group-member-add-${group.code}`" @click="addMember(group)">
            {{ t('admin.userGroups.addMember') }}
          </button>
        </div>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.admin-form,
.group-head,
.add-member {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: center;
}
.admin-form {
  margin: 1rem 0;
}
ul {
  list-style: none;
  padding: 0;
}
li {
  padding: 0.4rem 0;
}
[data-testid='group-list'] > li {
  border-bottom: 1px solid #ddd;
  padding: 0.75rem 0;
}
.members {
  margin: 0.4rem 0 0.4rem 1rem;
}
.hint,
.empty {
  font-size: 0.85rem;
  opacity: 0.75;
}
.error {
  color: #c92a2a;
}
</style>
