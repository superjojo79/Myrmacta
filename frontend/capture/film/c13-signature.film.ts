// TODO §148 (D-VIDEO-SIGN, D-VIDEO-SIGN-WEBHOOK, issue #59) — capsule courte C13 « La convention se signe en ligne », sur
// la scène signature (`scripts/video.sh start c13-signature`), bac à sable Yousign. La convention d'exemple est relue et
// validée ; Myrmacta la génère et l'envoie chez Yousign ; le lien du portail arrive dans Mailpit ; le fournisseur signe
// chez Yousign ; le webhook est rejoué en local (hors champ) ; le PDF signé et la preuve reviennent au dossier.
import { expect, test, type Page } from '@playwright/test'
import { assertMasked, maskPersonalData, replaySignatureWebhook, until } from './director'
import { api, en, opening, plan, scrollTo, shortCapsule, underTheHood } from './short'

const capsule = shortCapsule('c13-signature')
const TYPE = 'CONVENTION_FOURNISSEUR'
const MAILPIT = process.env.FILM_MAILPIT ?? 'http://localhost:8025'
const LABEL = en ? 'Agreement — Lambert Printing' : 'Convention — Imprimerie Lambert'
// À l'écran, l'adresse réelle du signataire (bac à sable) et le titulaire du compte Yousign cèdent la place à l'exemple.
const SCREEN = { email: 'claire.lambert@imprimerie-lambert.example', name: 'Myrmacta' }

type Dossier = { id: string; label: string; version: number }
type Connector = { id: string; connectorType: string }
type Signature = { id: string; status: string }

async function dossier(page: Page): Promise<Dossier> {
  return until(async () => {
    const list = await api<{ content: Dossier[] }>(page, 'GET', `/api/dossiers?dossierTypeCode=${TYPE}&size=20`)
    return list.content.find((d) => d.label === LABEL)
  })
}

async function signatures(page: Page, id: string): Promise<Signature[]> {
  return (await api<{ requests: Signature[] }>(page, 'GET', `/api/dossiers/${id}/signature-requests`)).requests
}

/** Le dernier lien du portail reçu par le fournisseur (Mailpit). */
async function portalLink(): Promise<{ id: string; link: string }> {
  return until(async () => {
    const list = await (await fetch(`${MAILPIT}/api/v1/messages?limit=5`)).json() as { total: number; messages: { ID: string }[] }
    if (list.total === 0) return undefined
    const message = await (await fetch(`${MAILPIT}/api/v1/message/${list.messages[0]!.ID}`)).json() as { ID: string; Text: string }
    const link = message.Text.match(/https?:\/\/\S+\/portal\/\S*#\S+/)?.[0]
    return link ? { id: message.ID, link } : undefined
  }, 180_000)
}

test('ouverture', async ({ page }) => {
  await opening(capsule, page)
})

test('envoi', async ({ page }) => {
  const { director: d, order } = plan(capsule, page, 'envoi')
  await maskPersonalData(page, SCREEN)
  // Hors champ (coupé au montage) : la boîte de Mailpit est vidée, pour que le plan suivant ne montre que l'invitation ;
  // la fiche est chargée avant la première image.
  const found = await d.offscreen(async () => {
    await d.goto('/supervision')
    await fetch(`${MAILPIT}/api/v1/messages`, { method: 'DELETE' })
    const sample = await dossier(page)
    await d.goto(`/dossiers/${sample.id}`)
    return sample
  })
  await d.beat(1.5)
  const tasks = page.locator('.task-list').first()
  await scrollTo(page, tasks, 'center')
  await d.beat(1.2)
  const complete = tasks.getByRole('button', { name: /^(Compléter|Complete)$/ }).first()
  await complete.click()
  // Génération, envoi chez Yousign : l'attente est coupée au montage.
  await d.offscreen(async () => {
    await until(async () => (await signatures(page, found.id)).find((s) => s.status === 'PENDING'), 90_000)
    await d.reload()
  })
  await scrollTo(page, page.getByTestId('signature-requests-panel'), 'center')
  await d.holdForVoice(0.4)
  await assertMasked(page)
  await d.wrap(order)
})

test('signature', async ({ page }) => {
  const { director: d, order } = plan(capsule, page, 'signature')
  await maskPersonalData(page, SCREEN)
  // La vidéo de Playwright ne commence qu'à la première image peinte : chaque attente hors champ s'ouvre sur une page,
  // sinon ses bornes ne correspondraient plus au temps de la vidéo.
  const message = await d.offscreen(async () => {
    await d.goto(MAILPIT)
    const found = await portalLink()
    await d.goto(`${MAILPIT}/view/${found.id}`)
    return found
  })
  // L'invitation reste à l'écran le temps du premier sous-titre.
  await d.beat(3.3)
  // Hors champ (coupé au montage) : le portail redirige vers Yousign (lien demandé à l'ouverture, D-SIGNATURE-LINK), puis
  // le fournisseur relit la convention. Le plan reprend sur « Cliquer pour signer », sous le second sous-titre. Myrmacta
  // crée le signataire en français : la version anglaise passe la page en anglais par son menu de langue.
  await d.offscreen(async () => {
    await page.goto(message.link)
    await page.waitForURL(/yousign\.app/, { timeout: 60_000 })
    await page.getByRole('button', { name: /^Commencer$/i }).waitFor({ timeout: 60_000 })
    if (en) {
      // Le lien Yousign est à usage unique : la langue se change par le menu de la page, pas en rechargeant l'URL.
      await page.getByRole('button', { name: /^Sélection de la langue/ }).click()
      await page.getByRole('menuitem', { name: 'English' }).click()
      await page.getByRole('button', { name: /^Start$/i }).waitFor()
    }
    await readAtYousign(page)
    await d.settle()
  })
  await d.beat(0.8)
  await page.getByRole('button', { name: /^(Cliquer pour signer|Click to sign)$/i }).click()
  await expect(page).toHaveURL(/\/signed/, { timeout: 60_000 })
  await d.settle()
  await d.beat(2)
  await d.holdForVoice(0.4)
  await assertMasked(page)
  await d.wrap(order)
})

/**
 * La cérémonie du bac à sable Yousign, relevée le 2026-11-17 : « Commencer », le document à faire défiler jusqu'au bout,
 * « Continuer » ; vient ensuite « Cliquer pour signer » (signature simple, sans code : `no_otp`).
 */
async function readAtYousign(page: Page): Promise<void> {
  await page.getByRole('button', { name: /^(Commencer|Start)$/i }).click()
  const proceed = page.getByRole('button', { name: /^(Continuer|Continue)$/i })
  await proceed.waitFor()
  // Le bouton ne s'active qu'une fois le document lu jusqu'au bout ; le document occupe la droite de l'écran.
  const size = page.viewportSize() ?? { width: 1920, height: 1080 }
  await page.mouse.move(size.width * 0.6, size.height * 0.5)
  for (let i = 0; i < 10 && !(await proceed.isEnabled()); i++) {
    await page.mouse.wheel(0, 500)
    await page.waitForTimeout(500)
  }
  await proceed.click()
  await page.getByRole('button', { name: /^(Cliquer pour signer|Click to sign)$/i }).waitFor()
}

test('retour', async ({ page }) => {
  const { director: d, order } = plan(capsule, page, 'retour')
  await maskPersonalData(page, SCREEN)
  // Hors champ (coupé au montage) : le webhook de Yousign, rejoué en local ; le répartiteur télécharge le PDF signé et la
  // preuve chez Yousign, puis le processus reprend jusqu'à « Signée ». La fiche est chargée avant la première image.
  await d.offscreen(async () => {
    await d.goto('/supervision')
    const found = await dossier(page)
    const connectors = await api<{ content: Connector[] }>(page, 'GET', '/api/connectors?size=50')
    const instance = connectors.content.find((c) => c.connectorType === 'signature')
    expect(instance, 'signature connector of the scene').toBeTruthy()
    const request = (await signatures(page, found.id))[0]
    expect(request, 'signature request of the sample case').toBeTruthy()
    await replaySignatureWebhook(instance!.id, request!.id)
    await until(async () => {
      const links = await api<{ content: { status: string }[] }>(page, 'GET', `/api/dossiers/${found.id}/workflows?size=5`)
      return links.content.some((l) => l.status === 'SIGNEE')
    }, 180_000)
    await d.goto(`/dossiers/${found.id}`)
  })
  await d.beat(1.5)
  await scrollTo(page, page.getByRole('heading', { name: /^(Éléments|Elements)$/ }), 'start')
  await d.beat(3)
  await scrollTo(page, page.getByTestId('signature-requests-panel'), 'center')
  await d.holdForVoice(0.6)
  await assertMasked(page)
  await d.wrap(order)
})

test('sous-le-capot', async ({ page }) => {
  await underTheHood(capsule, page)
})
