<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { computed, ref, watch } from 'vue'
import {
  createConnector,
  deleteConnector,
  listConnectorDefinitions,
  listConnectors,
  updateConnector,
  type ConnectorDefinition,
} from '@/api/admin'
import type { Connector } from '@/api/types'
import { apiContextPath, toProblemDetail } from '@/api/http'
import AdminPager from '@/components/admin/AdminPager.vue'
import ConnectorConfigForm from '@/components/connectors/ConnectorConfigForm.vue'
import ConnectorIngressUrl from '@/components/connectors/ConnectorIngressUrl.vue'
import {
  configFromFormValues,
  defaultsFromSchema,
  ingressUrlFor,
  schemaOf,
  type FormValues,
} from '@/components/connectors/connectorConfig'
import { useAdminListQuery } from '@/composables/useAdminListQuery'

const { page, size } = useAdminListQuery()
const connectors = ref<Connector[]>([])
const totalPages = ref(0)
const definitions = ref<ConnectorDefinition[]>([])
const error = ref<string | null>(null)
const form = ref({
  name: '',
  connectorType: '',
  enabled: true,
  configJson: '{}',
})
const configValues = ref<FormValues>({})

const catalogueLoaded = computed(() => definitions.value.length > 0)
const activeSchema = computed(() => schemaOf(definitions.value, form.value.connectorType))

watch(
  () => form.value.connectorType,
  (type) => {
    if (!catalogueLoaded.value) {
      return
    }
    configValues.value = defaultsFromSchema(schemaOf(definitions.value, type))
  },
)

async function refresh() {
  const [result, defs] = await Promise.all([
    listConnectors({ page: page.value, size: size.value }),
    listConnectorDefinitions(),
  ])
  connectors.value = result.content
  totalPages.value = result.totalPages
  definitions.value = defs
  if (!form.value.connectorType && definitions.value[0]) {
    form.value.connectorType = definitions.value[0].type
  }
}

watch(
  [page, size],
  () => {
    void refresh().catch((err) => {
      error.value = toProblemDetail(err).detail ?? t('admin.connectors.errors.load')
    })
  },
  { immediate: true },
)

function resetForm() {
  form.value = {
    name: '',
    connectorType: definitions.value[0]?.type ?? '',
    enabled: true,
    configJson: '{}',
  }
  configValues.value = defaultsFromSchema(schemaOf(definitions.value, form.value.connectorType))
}

async function create() {
  try {
    const config = catalogueLoaded.value
      ? configFromFormValues(activeSchema.value, configValues.value)
      : (JSON.parse(form.value.configJson) as Record<string, unknown>)
    await createConnector({
      name: form.value.name,
      connectorType: form.value.connectorType,
      enabled: form.value.enabled,
      config,
    })
    resetForm()
    await refresh()
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? t('admin.connectors.errors.create')
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

function ingressOf(connector: Connector) {
  return ingressUrlFor(connector, globalThis.location.origin, apiContextPath())
}

const listedConnectors = computed(() =>
  connectors.value.map((connector) => ({
    connector,
    ingress: ingressOf(connector),
  })),
)

const { t } = useI18n()
</script>

<template>
  <section>
    <h1>{{ t('admin.connectors.title') }}</h1>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="definitions.length" class="defs">
      {{ t('admin.connectors.catalogTypes') }}
      <span v-for="def in definitions" :key="def.type">{{ def.label || def.type }} </span>
    </p>
    <form class="admin-form" data-testid="connector-create-form" @submit.prevent="create">
      <input v-model="form.name" :placeholder="t('admin.connectors.namePlaceholder')" required />
      <select
        v-if="definitions.length"
        v-model="form.connectorType"
        data-testid="connector-type"
      >
        <option v-for="def in definitions" :key="def.type" :value="def.type">
          {{ def.label || def.type }}
        </option>
      </select>
      <input v-else v-model="form.connectorType" :placeholder="t('admin.connectors.typePlaceholder')" required />
      <ConnectorConfigForm
        v-if="catalogueLoaded"
        v-model="configValues"
        :schema="activeSchema"
      />
      <p
        v-if="catalogueLoaded && activeSchema.length === 0"
        data-testid="connector-config-empty"
        class="empty-config"
      >
        {{ t('admin.connectors.noConfig') }}
      </p>
      <textarea
        v-if="!catalogueLoaded"
        v-model="form.configJson"
        rows="3"
        data-testid="connector-config-json-fallback"
      />
      <button type="submit">{{ t('admin.connectors.create') }}</button>
    </form>
    <ul data-testid="connector-list">
      <li v-for="{ connector: c, ingress } in listedConnectors" :key="c.id">
        <div>
          <strong>{{ c.name }}</strong> ({{ c.connectorType }}) — {{ c.enabled ? t('admin.connectors.enabled') : t('admin.connectors.disabled') }}
          <span v-if="Object.keys(c.secretConfigured || {}).length" class="secrets">
            {{ t('admin.connectors.secrets', { names: Object.keys(c.secretConfigured).join(', ') }) }}
          </span>
          <ConnectorIngressUrl v-if="ingress" :kind="ingress.kind" :url="ingress.url" />
        </div>
        <div>
          <button type="button" @click="toggle(c)">{{ t('admin.connectors.toggle') }}</button>
          <button type="button" @click="remove(c)">{{ t('admin.connectors.remove') }}</button>
        </div>
      </li>
    </ul>
    <AdminPager v-model="page" :total-pages="totalPages" />
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
.defs {
  font-size: 0.9rem;
  opacity: 0.8;
}
.error {
  color: #c92a2a;
}
.empty-config {
  font-size: 0.9rem;
  opacity: 0.8;
  margin: 0;
}
</style>
