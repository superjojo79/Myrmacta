// TODO §127 (D-DEMO-CAPTURE) — réglages partagés des captures.

export type CaptureLang = 'fr' | 'en'

export function captureLang(): CaptureLang {
  return process.env.CAPTURE_LANG === 'en' ? 'en' : 'fr'
}

/** Répertoire de sortie d'une langue (non versionné). */
export function outDir(lang: CaptureLang = captureLang()): string {
  return `capture/out/${lang}`
}
