<script setup lang="ts">
import { ref, watch } from 'vue'
import {
  createNotificationWebhook,
  deleteNotificationWebhook,
  listNotificationWebhooks,
  updateNotificationWebhook,
  type NotificationWebhook,
} from '@/api/notificationWebhooks'
import { toProblemDetail } from '@/api/http'
import AdminPager from '@/components/admin/AdminPager.vue'
import { useAdminListQuery } from '@/composables/useAdminListQuery'

const { page, size } = useAdminListQuery()
const webhooks = ref<NotificationWebhook[]>([])
const totalPages = ref(0)
const error = ref<string | null>(null)
const form = ref({
  name: '',
  targetUrl: '',
  secret: '',
  enabled: true,
})

async function refresh() {
  const result = await listNotificationWebhooks({ page: page.value, size: size.value })
  webhooks.value = result.content
  totalPages.value = result.totalPages
}

watch(
  [page, size],
  () => {
    void refresh().catch((err) => {
      error.value = toProblemDetail(err).detail ?? 'Chargement impossible'
    })
  },
  { immediate: true },
)

async function create() {
  error.value = null
  try {
    await createNotificationWebhook({
      name: form.value.name,
      targetUrl: form.value.targetUrl,
      secret: form.value.secret || undefined,
      enabled: form.value.enabled,
    })
    form.value = { name: '', targetUrl: '', secret: '', enabled: true }
    await refresh()
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? 'Création impossible'
  }
}

async function toggle(webhook: NotificationWebhook) {
  await updateNotificationWebhook(webhook.id, { enabled: !webhook.enabled })
  await refresh()
}

async function remove(webhook: NotificationWebhook) {
  await deleteNotificationWebhook(webhook.id)
  await refresh()
}
</script>

<template>
  <section>
    <h1>Webhooks de notification</h1>
    <p v-if="error" class="error" data-testid="webhook-error">{{ error }}</p>
    <form class="admin-form" data-testid="webhook-create-form" @submit.prevent="create">
      <input v-model="form.name" placeholder="nom" required data-testid="webhook-name" />
      <input v-model="form.targetUrl" placeholder="https://…" required data-testid="webhook-url" />
      <input
        v-model="form.secret"
        type="password"
        placeholder="secret (écriture seule)"
        data-testid="webhook-secret"
        autocomplete="new-password"
      />
      <button type="submit">Créer</button>
    </form>
    <ul data-testid="webhook-list">
      <li v-for="hook in webhooks" :key="hook.id">
        <div>
          <strong>{{ hook.name }}</strong>
          — {{ hook.targetUrl }}
          — {{ hook.enabled ? 'actif' : 'inactif' }}
          <span v-if="hook.secretConfigured" class="secret-badge">secret configuré</span>
        </div>
        <div>
          <button type="button" @click="toggle(hook)">Basculer</button>
          <button type="button" @click="remove(hook)">Supprimer</button>
        </div>
      </li>
    </ul>
    <AdminPager v-model="page" :total-pages="totalPages" />
  </section>
</template>

<style scoped>
.admin-form {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin: 1rem 0;
}
ul {
  list-style: none;
  padding: 0;
}
li {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.6rem 0;
  border-bottom: 1px solid #ddd;
}
.secret-badge {
  font-size: 0.85rem;
  opacity: 0.7;
  margin-left: 0.35rem;
}
.error {
  color: #c92a2a;
}
</style>
