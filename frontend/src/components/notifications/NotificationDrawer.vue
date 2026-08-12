<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useNotificationsStore } from '@/stores/notifications'

const store = useNotificationsStore()
const { items, unreadCount, failed } = storeToRefs(store)
const open = ref(false)

onMounted(() => store.connect())
onUnmounted(() => store.disconnect())
</script>

<template>
  <div class="notif">
    <button type="button" class="notif__bell" @click="open = !open">
      Notifications
      <span v-if="unreadCount" class="notif__badge">{{ unreadCount }}</span>
    </button>
    <p v-if="failed" class="notif__fail">Flux temps réel indisponible</p>
    <aside v-if="open" class="notif__drawer">
      <header>
        <strong>Notifications</strong>
        <button type="button" @click="store.markAllRead()">Tout lu</button>
      </header>
      <p v-if="items.length === 0">Aucune notification.</p>
      <ul>
        <li v-for="item in items" :key="item.id" :class="{ unread: !item.read }">
          <button type="button" @click="store.markRead(item.id)">
            <strong>{{ item.title }}</strong>
            <span>{{ item.body }}</span>
          </button>
        </li>
      </ul>
    </aside>
  </div>
</template>

<style scoped>
.notif {
  position: relative;
}
.notif__bell {
  border: 1px solid #b7b7ba;
  background: white;
  padding: 0.35rem 0.7rem;
  cursor: pointer;
}
.notif__badge {
  margin-left: 0.35rem;
  background: #c92a2a;
  color: white;
  border-radius: 999px;
  padding: 0 0.4rem;
  font-size: 0.75rem;
}
.notif__fail {
  position: absolute;
  top: 100%;
  right: 0;
  font-size: 0.75rem;
  color: #8a4b08;
  white-space: nowrap;
}
.notif__drawer {
  position: absolute;
  right: 0;
  top: calc(100% + 0.4rem);
  width: min(320px, 90vw);
  background: white;
  border: 1px solid #d0d0d3;
  border-radius: 6px;
  padding: 0.75rem;
  z-index: 30;
  max-height: 360px;
  overflow: auto;
}
.notif__drawer ul {
  list-style: none;
  padding: 0;
  margin: 0;
}
.notif__drawer li button {
  width: 100%;
  text-align: left;
  border: 0;
  background: transparent;
  padding: 0.45rem 0;
  cursor: pointer;
}
.notif__drawer li.unread strong {
  color: var(--color-accent, #5980a6);
}
.notif__drawer span {
  display: block;
  font-size: 0.85rem;
  opacity: 0.75;
}
header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 0.5rem;
}
</style>
