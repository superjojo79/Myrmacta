import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import OpenVue from 'openvue/config'
import Aura from '@openvue/themes/aura'
import 'primeicons/primeicons.css'
import '@/styles/main.css'
import App from './App.vue'
import { configureHttpClient, routerBase } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { routes } from '@/app/routes'
import { createRoleGuard } from '@/app/roleGuard'
import { i18n } from '@/i18n'

configureHttpClient()

const app = createApp(App)
const pinia = createPinia()
const router = createRouter({
  // import.meta.env.BASE_URL vaut './' (DD-BASE) : sous /tenant_a le routeur ne retirait pas le
  // préfixe et le perdait à chaque navigation.
  history: createWebHistory(routerBase()),
  routes,
})

// Un seul appel à /api/auth/me, partagé entre le démarrage et la garde de rôles.
let userLoaded: Promise<void> | null = null
function ensureUserLoaded(): Promise<void> {
  userLoaded ??= useAuthStore().init()
  return userLoaded
}

router.beforeEach(
  createRoleGuard(ensureUserLoaded, () => {
    const auth = useAuthStore()
    return auth.user ? auth.roles : null
  }),
)

app.use(pinia)
app.use(i18n)
app.use(router)
app.use(OpenVue, {
  theme: {
    preset: Aura,
    // Le CSS applicatif est clair uniquement : sans ça, Aura passe `color-scheme: dark` quand l'OS
    // est en mode sombre et les contrôles natifs deviennent blanc sur blanc.
    options: { darkModeSelector: 'none' },
  },
})
app.mount('#app')

void ensureUserLoaded()
