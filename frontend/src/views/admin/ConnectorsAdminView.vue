<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { createConnector, deleteConnector, listConnectors, updateConnector } from '@/api/admin'
import type { Connector } from '@/api/types'
import { toProblemDetail } from '@/api/http'

const connectors = ref<Connector[]>([])
const error = ref<string | null>(null)
const form = ref({
  name: '',
  connectorType: 'HTTP',
  enabled: true,
  configJson: '{}',
})

async function refresh() {
  connectors.value = await listConnectors()
}

onMounted(() => {
  void refresh().catch((err) => {
    error.value = toProblemDetail(err).detail ?? 'Chargement impossible'
  })
})

async function create() {
  try {
    const config = JSON.parse(form.value.configJson) as Record<string, unknown>
    await createConnector({
      name: form.value.name,
      connectorType: form.value.connectorType,
      enabled: form.value.enabled,
      config,
    })
    form.value = { name: '', connectorType: 'HTTP', enabled: true, configJson: '{}' }
    await refresh()
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? 'Création impossible'
  }
}

async function toggle(connector: Connector) {
  await updateConnector(connector.id, { enabled: !connector.enabled })
  await refresh()
}

async function remove(connector: Connector) {
  await deleteConnector(connector.id)
  await refresh()
}
</script>

<template>
  <section>
    <h1>Connecteurs</h1>
    <p v-if="error" class="error">{{ error }}</p>
    <form class="admin-form" @submit.prevent="create">
      <input v-model="form.name" placeholder="nom" required />
      <input v-model="form.connectorType" placeholder="type" required />
      <textarea v-model="form.configJson" rows="3" />
      <button type="submit">Créer</button>
    </form>
    <ul>
      <li v-for="c in connectors" :key="c.id">
        <div>
          <strong>{{ c.name }}</strong> ({{ c.connectorType }}) — {{ c.enabled ? 'actif' : 'inactif' }}
          <span v-if="Object.keys(c.secretConfigured || {}).length" class="secrets">
            secrets: {{ Object.keys(c.secretConfigured).join(', ') }}
          </span>
        </div>
        <div>
          <button type="button" @click="toggle(c)">Basculer</button>
          <button type="button" @click="remove(c)">Supprimer</button>
        </div>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.admin-form {
  display: grid;
  gap: 0.5rem;
  max-width: 520px;
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
.secrets {
  display: block;
  font-size: 0.85rem;
  opacity: 0.7;
}
.error {
  color: #c92a2a;
}
</style>
