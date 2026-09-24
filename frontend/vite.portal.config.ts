import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// TODO §68 (portail-externe) — build séparé de la page publique (D-PORTAL-UI-BUILD) : sortie dans
// dist/portal/ (copiée par Maven vers static/portal/), aucun chunk partagé avec le SPA interne, donc
// seul /portal/** devient public. À lancer APRÈS le build principal (qui vide dist/).
export default defineConfig({
  root: fileURLToPath(new URL('./portal', import.meta.url)),
  base: './',
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    outDir: fileURLToPath(new URL('./dist/portal', import.meta.url)),
    emptyOutDir: true,
  },
})
