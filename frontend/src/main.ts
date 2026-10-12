import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import OpenVue from 'openvue/config'
import Aura from '@openvue/themes/aura'
import 'primeicons/primeicons.css'
import '@/styles/main.css'
import App from './App.vue'
import { configureHttpClient } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { routes } from '@/app/routes'
import { i18n } from '@/i18n'

configureHttpClient()

const app = createApp(App)
const pinia = createPinia()
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

router.beforeEach((to) => {
  const required = to.meta.roles as string[] | undefined
  if (!required?.length) return true
  const auth = useAuthStore()
  if (!auth.user) return true
  const ok = required.some((role) => auth.roles.includes(role))
  return ok ? true : { name: 'supervision' }
})

app.use(pinia)
app.use(i18n)
app.use(router)
app.use(OpenVue, {
  theme: {
    preset: Aura,
  },
})
app.mount('#app')

void useAuthStore().init()
