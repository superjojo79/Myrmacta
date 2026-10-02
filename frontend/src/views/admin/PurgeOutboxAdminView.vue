<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ref, shallowRef, watch } from 'vue'
import { toProblemDetail } from '@/api/http'
import { listPurgeOutbox, type PurgeOutboxList } from '@/api/purgeOutbox'
import PurgeOutboxCounts from '@/components/admin/PurgeOutboxCounts.vue'
import PurgeOutboxQueue from '@/components/admin/PurgeOutboxQueue.vue'
import AdminPager from '@/components/admin/AdminPager.vue'
import { useAdminListQuery } from '@/composables/useAdminListQuery'

const { page, size } = useAdminListQuery()
const loading = shallowRef(true)
const error = shallowRef<string | null>(null)
const list = ref<PurgeOutboxList | null>(null)

watch(
  [page, size],
  async () => {
    loading.value = true
    try {
      list.value = await listPurgeOutbox({ page: page.value, size: size.value })
    } catch (err) {
      error.value = toProblemDetail(err).detail ?? t('admin.purgeQueue.loadError')
    } finally {
      loading.value = false
    }
  },
  { immediate: true },
)

const { t } = useI18n()
</script>

<template>
  <section class="purge-outbox-admin">
    <h1>{{ t('admin.purgeQueue.title') }}</h1>
    <p v-if="loading">{{ t('common.loading') }}</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <template v-else-if="list">
      <PurgeOutboxCounts :counts="list.counts" />
      <PurgeOutboxQueue :items="list.content" />
      <AdminPager v-model="page" :total-pages="list.totalPages" />
    </template>
  </section>
</template>

<style scoped>
.error {
  color: #c92a2a;
}
</style>
