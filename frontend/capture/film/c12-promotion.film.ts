// TODO §140 (D-VIDEO-SHORT, issue #58) — capsule courte C12 « De la recette à la production sans recopier », sur la démo
// complète. La démo n'a qu'une instance : le film exporte sa configuration (la « recette »), le script
// scripts/video/recette_bundle.py la fait avancer hors champ (groupe ACHATS, champ « Bon de commande » sur FACTURE), puis le
// film importe ce paquet (la « production ») : plan à blanc, puis application.
import { execFileSync } from 'node:child_process'
import { mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { expect, test } from '@playwright/test'
import { filmDir, lang, REPO } from './director'
import { opening, plan, scrollTo, shortCapsule, underTheHood } from './short'

const capsule = shortCapsule('c12-promotion')
const work = () => {
  const dir = resolve(filmDir(), 'bundles')
  mkdirSync(dir, { recursive: true })
  return dir
}
const exported = () => resolve(work(), 'export.zip')
const recette = () => resolve(work(), 'recette.zip')

test('ouverture', async ({ page }) => {
  await opening(capsule, page)
})

test('export', async ({ page }) => {
  const { director: d, order } = plan(capsule, page, 'export')
  await d.goto('/admin/configuration')
  await d.beat(2)
  await page.getByTestId('source-label').pressSequentially(lang === 'en' ? 'staging' : 'recette', { delay: 35 })
  await d.beat(0.8)
  const download = page.waitForEvent('download')
  await page.getByTestId('export-button').click()
  await (await download).saveAs(exported())
  await d.beat(1.5)
  await d.holdForVoice(0.6)
  await d.wrap(order)
  // Hors champ : la recette avance (un groupe, un champ de plus).
  execFileSync('uv', ['run', 'scripts/video/recette_bundle.py', exported(), recette(), '--lang', lang], { cwd: REPO, stdio: 'inherit' })
})

test('import', async ({ page }) => {
  const { director: d, order } = plan(capsule, page, 'import')
  await d.goto('/admin/configuration')
  await d.beat(1)
  await page.getByTestId('bundle-file').setInputFiles(recette())
  await d.beat(0.8)
  await page.getByTestId('plan-button').click()
  const planTable = page.getByTestId('plan-table')
  await expect(planTable).toBeVisible()
  await expect(page.getByTestId('plan-USER_GROUP-ACHATS')).toContainText(/CREATE|Créer|Create/i)
  await scrollTo(page, page.getByTestId('plan-USER_GROUP-ACHATS'), 'center')
  await d.beat(2.5)
  await scrollTo(page, page.getByTestId('plan-DOSSIER_TYPE-FACTURE'), 'center')
  await d.beat(2.5)
  // La confirmation (fenêtre native, absente de la vidéo) est acceptée.
  page.once('dialog', (dialog) => dialog.accept())
  const apply = page.getByTestId('apply-button')
  await scrollTo(page, apply, 'center')
  await apply.click()
  const report = page.getByTestId('report-table')
  await expect(report).toBeVisible()
  await scrollTo(page, report, 'center')
  await d.holdForVoice(0.6)
  await d.wrap(order)
})

test('sous-le-capot', async ({ page }) => {
  await underTheHood(capsule, page)
})
