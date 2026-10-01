// Tout composant monté dans un test dispose de vue-i18n (comme dans l'application) sans que chaque spec ait à l'installer.
import { config } from '@vue/test-utils'
import { afterEach } from 'vitest'
import { clearStoredLocale, i18n, setLocale } from '@/i18n'

config.global.plugins = [...(config.global.plugins ?? []), i18n]

// Un test qui change de langue ne doit pas contaminer le suivant.
afterEach(() => {
  clearStoredLocale()
  setLocale('fr')
  clearStoredLocale()
})
