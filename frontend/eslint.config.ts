import pluginVue from 'eslint-plugin-vue'
import { defineConfigWithVueTs, vueTsConfigs } from '@vue/eslint-config-typescript'

export default defineConfigWithVueTs(
  {
    name: 'app/files-to-lint',
    files: ['**/*.{ts,mts,tsx,vue}'],
  },
  {
    name: 'app/files-to-ignore',
    ignores: ['dist/**', 'coverage/**', 'node_modules/**', 'playwright-report/**', 'test-results/**', 'capture/out/**', 'reports/**', '.stryker-tmp/**', 'eslint.config.ts'],
  },
  pluginVue.configs['flat/essential'],
  vueTsConfigs.recommended,
  {
    // TODO §68 (portail-externe) V-PORTAL-5 — la page publique ne doit embarquer ni le store
    // d'authentification, ni le routeur, ni le client axios (XSRF/cookies BFF) du SPA interne.
    name: 'app/portal-isolation',
    files: ['portal/**/*.{ts,vue}'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            { group: ['@/stores/*', '@/app/*', '@/views/*'], message: 'Portail public : pas de code du SPA interne.' },
            { group: ['@/api/http', 'axios', 'vue-router', 'pinia'], message: 'Portail public : fetch sans cookies uniquement.' },
            { group: ['@/components/dossier/SensitiveValue.vue', '@/components/dossier/DynamicFields.vue'], message: 'Dépend du store d’authentification — utiliser DynamicFieldInput.vue.' },
          ],
        },
      ],
    },
  },
)
