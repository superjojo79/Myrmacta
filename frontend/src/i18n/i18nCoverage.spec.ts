import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'
import { describe, expect, it } from 'vitest'
import { rawTexts } from './rawText'

/**
 * TODO §90 (D7) — garde-fou de couverture : aucun template Vue n'écrit de texte visible en dur. Les exceptions sont des jetons
 * techniques (codes, exemples de syntaxe, noms de variables d'environnement, marque) : elles se déclarent ici, une par une.
 */
const TECHNICAL = new Set([
  'PENDING :', 'PROCESSING :', 'FAILED :', 'COMPLETED :', // codes d'état du serveur
  'EUR', 'https://…', 'POST', 'VIEW', 'EDIT', 'FlowForge', // code ISO, exemple d'URL, verbe HTTP, rôles d'ACL, marque
  'APP_ASSISTANT_ENABLED', 'APP_ASSISTANT_API_KEY', 'userTask', 'formKey = "user-question: schemaKey "', // vocabulaire technique cité
  'dossier.fields.nom_du_champ', 'dossier.fields.ht * (1 + dossier.fields.taux)', "dossier.fields.pays = 'FR'", 'dossier.fields.montant > 1000',
  'client.nom', 'client.pro', 'PRO', 'lignes', 'ligne', 'nom', // exemples de saisie
])
const CODE_FRAGMENT = /[?:]\s*t\(|\$\{|=>|\}\)/ // résidu d'une expression multi-lignes dans une interpolation

function sources(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name)
    if (statSync(path).isDirectory()) return name === 'node_modules' ? [] : sources(path)
    return name.endsWith('.vue') ? [path] : []
  })
}

describe('template text coverage', () => {
  it('no Vue template writes visible text in French by hand', () => {
    const root = join(__dirname, '..', '..')
    const offenders: string[] = []
    for (const file of [...sources(join(root, 'src')), ...sources(join(root, 'portal'))]) {
      for (const text of rawTexts(readFileSync(file, 'utf8'))) {
        if (!TECHNICAL.has(text) && !CODE_FRAGMENT.test(text)) offenders.push(`${relative(root, file)}: ${text}`)
      }
    }
    expect(offenders).toEqual([])
  })

  it('the detector sees a hard-coded text and ignores an interpolation', () => {
    expect(rawTexts('<template><p>Bonjour</p></template>')).toEqual(['Bonjour'])
    expect(rawTexts('<template><p>{{ t(\'a.b\') }}</p></template>')).toEqual([])
    expect(rawTexts('<template><input placeholder="Nom" /><input :placeholder="t(\'a\')" /></template>')).toEqual(['Nom'])
  })
})
