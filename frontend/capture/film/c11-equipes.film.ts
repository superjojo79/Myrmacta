// TODO §140 (D-VIDEO-SHORT, issue #58) — capsule courte C11 « La bonne tâche à la bonne équipe, même sur mobile », sur la
// démo complète. Un groupe est créé et reçoit un membre ; puis, à la taille d'un téléphone, la page « Mes tâches » montre
// les tâches des groupes du compte de démo, qui en prend une. Le plan mobile est centré sur un fond sombre au montage.
import { resolve } from 'node:path'
import { expect, test } from '@playwright/test'
import { filmDir } from './director'
import { en, opening, plan, scrollTo, shortCapsule, underTheHood } from './short'

const capsule = shortCapsule('c11-equipes')
const GROUP = 'SUPPORT_IT'
const PHONE = { width: 390, height: 844 }

test('ouverture', async ({ page }) => {
  await opening(capsule, page)
})

test('groupes', async ({ page }) => {
  const { director: d, order } = plan(capsule, page, 'groupes')
  await d.goto('/admin/user-groups')
  // Style posé au tournage seulement : une marge sous la page, pour remonter le nouveau groupe au-dessus des sous-titres.
  await page.addStyleTag({ content: 'main { padding-bottom: 360px !important; }' })
  await d.beat(1.5)
  const form = page.getByTestId('group-create-form')
  await form.getByTestId('group-code').pressSequentially(GROUP, { delay: 30 })
  await form.getByTestId('group-label').pressSequentially(en ? 'IT support' : 'Support informatique', { delay: 30 })
  await d.beat(0.6)
  await form.getByRole('button').click()
  const group = page.getByTestId(`group-${GROUP}`)
  await expect(group).toBeVisible()
  await scrollTo(page, group, 'center')
  await d.beat(1)
  const select = page.getByTestId(`group-member-select-${GROUP}`)
  await select.selectOption({ index: 1 })
  await d.beat(0.8)
  await page.getByTestId(`group-member-add-${GROUP}`).click()
  await expect(group).toContainText('demo@')
  await d.holdForVoice(0.6)
  await d.wrap(order)
})

// Filmé à la taille du téléphone (portrait), dans un contexte à part : la taille de la vidéo ne se règle pas par groupe de
// tests. Le montage centre ce plan sur un fond sombre, sans l'étirer.
test('mobile', async ({ browser, baseURL, locale, timezoneId, storageState }) => {
  const context = await browser.newContext({
    baseURL, locale, timezoneId, storageState, viewport: PHONE, isMobile: true, hasTouch: true,
    recordVideo: { dir: resolve(filmDir(), 'video', '.mobile'), size: PHONE },
  })
  const page = await context.newPage()
  const { director: d, order } = plan(capsule, page, 'mobile')
  await d.goto('/tasks')
  const take = page.getByTestId('take-task')
  await expect(take.first()).toBeVisible()
  await d.beat(3)
  await take.first().click()
  await d.beat(1)
  await d.holdForVoice(0.6)
  await d.wrap(order)
  await context.close()
})

test('sous-le-capot', async ({ page }) => {
  await underTheHood(capsule, page)
})
