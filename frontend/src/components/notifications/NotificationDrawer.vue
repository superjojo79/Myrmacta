<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { onMounted, onUnmounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import Button from 'openvue/button'
import OverlayBadge from 'openvue/overlaybadge'
import ScrollPanel from 'openvue/scrollpanel'
import { useNotificationsStore } from '@/stores/notifications'

const { t } = useI18n()
const store = useNotificationsStore()
const { items, unreadCount, hasMore, failed } = storeToRefs(store)
const open = ref(false)

onMounted(() => store.connect())
onUnmounted(() => store.disconnect())
</script>

<template>
  <div class="notification-drawer">
    <OverlayBadge v-if="unreadCount" :value="unreadCount" severity="danger">
      <Button
        icon="pi pi-bell"
        severity="secondary"
        text
        rounded
        :aria-label="t('common.notifications.bell')"
        data-testid="notification-bell"
        @click="open = !open"
      />
    </OverlayBadge>
    <Button
      v-else
      icon="pi pi-bell"
      severity="secondary"
      text
      rounded
      :aria-label="t('common.notifications.bell')"
      data-testid="notification-bell"
      @click="open = !open"
    />
    <p v-if="failed" class="notification-drawer__fail">{{ t('common.notifications.streamFailed') }}</p>
    <div v-if="open" class="notification-drawer__panel" data-testid="notification-panel">
      <header class="notification-drawer__header">
        <strong>{{ t('common.notifications.title') }}</strong>
        <Button
          :label="t('common.notifications.markAllRead')"
          text
          size="small"
          :disabled="unreadCount === 0"
          data-testid="mark-all-read"
          @click="store.markAllRead()"
        />
      </header>
      <p v-if="items.length === 0" data-testid="notification-empty">{{ t('common.notifications.empty') }}</p>
      <ScrollPanel v-else style="width: min(360px, 90vw); max-height: 360px">
        <ul class="notification-drawer__list">
          <li
            v-for="item in items"
            :key="item.id"
            :class="{ 'notification-drawer__item--unread': !item.read }"
          >
            <button
              type="button"
              class="notification-drawer__item"
              data-testid="notification-item"
              @click="store.markRead(item.id)"
            >
              <strong>{{ item.title }}</strong>
              <span>{{ item.body }}</span>
            </button>
          </li>
        </ul>
        <Button
          v-if="hasMore"
          :label="t('common.notifications.loadMore')"
          text
          size="small"
          data-testid="notification-load-more"
          @click="store.loadMore()"
        />
      </ScrollPanel>
    </div>
  </div>
</template>

<style scoped>
.notification-drawer {
  position: relative;
  display: inline-flex;
  align-items: center;
}
.notification-drawer__fail {
  position: absolute;
  top: 100%;
  right: 0;
  font-size: 0.75rem;
  color: var(--p-orange-600, #b45309);
  white-space: nowrap;
}
.notification-drawer__panel {
  position: absolute;
  right: 0;
  top: calc(100% + 0.4rem);
  z-index: 30;
  min-width: 320px;
  padding: 0.75rem;
  background: var(--p-content-background, #fff);
  border: 1px solid var(--p-content-border-color, #d0d0d3);
  border-radius: var(--p-content-border-radius, 6px);
  box-shadow: var(--p-overlay-popover-shadow, 0 2px 8px rgba(0, 0, 0, 0.15));
}
.notification-drawer__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.5rem;
}
.notification-drawer__list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.notification-drawer__item {
  width: 100%;
  text-align: left;
  border: 0;
  background: transparent;
  padding: 0.5rem 0.25rem;
  cursor: pointer;
}
.notification-drawer__item span {
  display: block;
  font-size: 0.85rem;
  opacity: 0.75;
}
.notification-drawer__item--unread strong {
  color: var(--p-primary-color, #5980a6);
}
</style>
