// TODO §68 (portail-externe) — page publique, build Vite séparé (D-PORTAL-UI-BUILD) : aucune
// dépendance au SPA interne (store d'auth, routeur, client axios) — règle ESLint dédiée.
import { createApp } from 'vue'
import './portal.css'
import PortalApp from './PortalApp.vue'
import { i18n } from '@/i18n'
import { takeLinkFromLocation } from './token'

const link = takeLinkFromLocation(window.location, window.history)

createApp(PortalApp, { token: link?.token ?? null, kind: link?.kind ?? 'task' }).use(i18n).mount('#portal')
