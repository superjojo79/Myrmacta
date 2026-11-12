// TODO §128 (D-VIDEO-PIPELINE, lot 4 de #56) — metteur en scène d'un chapitre filmé : tient le plan le temps de la voix
// off, note les attentes à couper au montage (relève IMAP) et les passages à accélérer (agent IA), pose le bandeau
// « Sous le capot », et range la vidéo du chapitre à côté de ses coupes.
import { spawn } from 'node:child_process'
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { createInterface } from 'node:readline'
import { expect, type Page } from '@playwright/test'
import { captureLang } from '../settings'

export const REPO = resolve(process.cwd(), '..')
export const lang = captureLang()

/** La capsule tournée (FILM_CAPSULE, défaut l1-mailroom). */
export const capsuleId = process.env.FILM_CAPSULE ?? 'l1-mailroom'

/** Répertoire de la capsule et de la langue : voix (voice.json), vidéos et coupes des chapitres (TODO §136). */
export function filmDir(): string {
  return resolve(process.env.FILM_OUT ?? `capture/out/film/${capsuleId}`, lang)
}

type Voice = { chapters: Record<string, { duration: number }> }

/** Durée de la voix d'un chapitre (scripts/video/voice.py, lancé avant le tournage). */
function voiceDuration(chapter: string): number {
  const voice = JSON.parse(readFileSync(resolve(filmDir(), 'voice.json'), 'utf-8')) as Voice
  const found = voice.chapters[chapter]
  if (!found) throw new Error(`voice.json has no chapter ${chapter}: run scripts/video/voice.py first`)
  return found.duration
}

export type Span = { start: number; end: number; kind: 'cut' | 'fast'; factor?: number }

export class Director {
  private readonly t0 = Date.now()
  private readonly spans: Span[] = []
  private bannerText: string | null = null
  private pipSelector: string | null = null

  constructor(
    readonly page: Page,
    readonly chapter: string,
  ) {}

  private now(): number {
    return (Date.now() - this.t0) / 1000
  }

  /** Durée qui restera à l'écran après montage (coupes retirées, passages accélérés comprimés). */
  private screenTime(): number {
    let removed = 0
    for (const span of this.spans) {
      const length = span.end - span.start
      removed += span.kind === 'cut' ? length : length - length / (span.factor ?? 1)
    }
    return this.now() - removed
  }

  /** Une attente hors champ (relève IMAP, routage) : filmée, puis retirée au montage. */
  async offscreen<T>(wait: () => Promise<T>): Promise<T> {
    const start = this.now()
    try {
      return await wait()
    } finally {
      this.spans.push({ start, end: this.now(), kind: 'cut' })
    }
  }

  /** Un passage accéléré au montage (l'agent IA qui travaille). */
  async fast<T>(factor: number, action: () => Promise<T>): Promise<T> {
    const start = this.now()
    try {
      return await action()
    } finally {
      this.spans.push({ start, end: this.now(), kind: 'fast', factor })
    }
  }

  /** Le plan reste à l'écran au moins `seconds` (temps à l'écran après montage). */
  async beat(seconds: number): Promise<void> {
    await this.page.waitForTimeout(seconds * 1000)
  }

  /** Tient le dernier plan jusqu'à la fin de la voix du chapitre, plus une respiration. */
  async holdForVoice(tail = 1.2): Promise<void> {
    const missing = voiceDuration(this.chapter) + tail - this.screenTime()
    if (missing > 0) await this.page.waitForTimeout(missing * 1000)
  }

  /** Bandeau « Sous le capot », reposé après chaque navigation. */
  async banner(text: string | null): Promise<void> {
    this.bannerText = text
    await this.applyBanner()
  }

  /**
   * TODO §136 (D-VIDEO-PIP) — vignette : l'élément `selector` (le diagramme de l'instance) est posé, réduit, dans le coin
   * inférieur droit de l'écran filmé, au-dessus du bandeau ; reposée après chaque navigation. `null` la retire.
   */
  async pip(selector: string | null): Promise<void> {
    const previous = this.pipSelector
    this.pipSelector = selector
    if (previous && !selector) await this.page.locator(previous).first().evaluate((el) => el.removeAttribute('style'))
    await this.applyPip()
  }

  async goto(url: string): Promise<void> {
    await this.page.goto(url)
    await this.settle()
    await this.applyBanner()
    await this.applyPip()
  }

  /** Recharge la page (l'interface ne se rafraîchit pas seule), puis repose bandeau et vignette. */
  async reload(): Promise<void> {
    await this.page.reload()
    await this.settle()
    await this.applyBanner()
    await this.applyPip()
  }

  async settle(): Promise<void> {
    await this.page.waitForLoadState('networkidle')
    await this.page.waitForTimeout(500)
  }

  private async applyPip(): Promise<void> {
    if (!this.pipSelector) return
    const target = this.page.locator(this.pipSelector).first()
    await target.waitFor()
    // Le diagramme garde le cadrage calculé à son affichage : on réduit l'élément entier plutôt que de le redimensionner.
    // Réduits, les contours du chemin parcouru (vert) et de l'étape en cours (bleu) s'effacent : on les remplit.
    await this.page.evaluate(() => {
      if (document.getElementById('film-pip-style')) return
      const style = document.createElement('style')
      style.id = 'film-pip-style'
      style.textContent = [
        '.highlight-completed:not(.djs-connection) .djs-visual > :nth-child(1)',
        '{ fill: #b2f2bb !important; stroke-width: 5px !important; }',
        '.highlight-active:not(.djs-connection) .djs-visual > :nth-child(1)',
        '{ fill: #a5d8ff !important; stroke-width: 6px !important; }',
        '.highlight-failed:not(.djs-connection) .djs-visual > :nth-child(1)',
        '{ fill: #ffc9c9 !important; stroke-width: 6px !important; }',
      ].join(' ')
      document.head.appendChild(style)
    })
    await target.evaluate((el) => {
      const width = (el as HTMLElement).getBoundingClientRect().width
      Object.assign((el as HTMLElement).style, {
        position: 'fixed', right: '24px', bottom: '110px', width: `${width}px`, margin: '0', zIndex: '2147483646',
        transform: 'scale(0.55)', transformOrigin: 'bottom right', background: '#ffffff', borderRadius: '14px',
        padding: '10px 16px', boxShadow: '0 12px 36px rgba(0,0,0,.35)', border: '3px solid #4cc9f0',
      })
    })
  }

  private async applyBanner(): Promise<void> {
    if (!this.bannerText) return
    await this.page.evaluate((text) => {
      document.getElementById('film-banner')?.remove()
      const el = document.createElement('div')
      el.id = 'film-banner'
      el.textContent = text
      Object.assign(el.style, {
        position: 'fixed', left: '32px', bottom: '28px', zIndex: '2147483647', maxWidth: '70%',
        padding: '10px 18px', borderRadius: '10px', background: 'rgba(13,27,42,.88)', color: '#e2e8f0',
        font: '500 20px/1.35 "Segoe UI", system-ui, sans-serif', boxShadow: '0 6px 24px rgba(0,0,0,.25)',
        borderLeft: '5px solid #4cc9f0', pointerEvents: 'none',
      })
      document.body.appendChild(el)
    }, this.bannerText)
  }

  /** Ferme la page et range la vidéo du chapitre (<chapitre>.webm) et ses passages (<chapitre>.spans.json). */
  async wrap(order: number): Promise<void> {
    const dir = resolve(filmDir(), 'video')
    mkdirSync(dir, { recursive: true })
    const name = `${String(order).padStart(2, '0')}-${this.chapter}`
    writeFileSync(resolve(dir, `${name}.spans.json`), JSON.stringify(this.spans, null, 2))
    const video = this.page.video()
    await this.page.close()
    await video!.saveAs(resolve(dir, `${name}.webm`))
  }
}

/** Interroge `probe` jusqu'à ce qu'il rende une valeur (relève IMAP : jusqu'à 45 s). */
export async function until<T>(probe: () => Promise<T | undefined | null | false>, timeoutMs = 120_000): Promise<T> {
  const deadline = Date.now() + timeoutMs
  for (;;) {
    const value = await probe()
    if (value) return value
    if (Date.now() > deadline) throw new Error('until: timed out')
    await new Promise((r) => setTimeout(r, 1500))
  }
}

/** Envoie un e-mail du tournage (scripts/video/send_mail.py) vers le GreenMail de la scène. */
export async function sendMail(scenario: 'facture' | 'inconnu' | 'reponse'): Promise<void> {
  await run('uv', ['run', 'scripts/video/send_mail.py', scenario, '--lang', lang])
}

function run(command: string, args: string[]): Promise<void> {
  return new Promise((ok, fail) => {
    const child = spawn(command, args, { cwd: REPO, stdio: 'inherit' })
    child.on('exit', (code) => (code === 0 ? ok() : fail(new Error(`${command} ${args.join(' ')} exited ${code}`))))
  })
}

/** Lance l'agent IA (scripts/video/agent.py) et passe chacune de ses étapes au panneau terminal ouvert dans `page`. */
export async function runAgent(page: Page): Promise<void> {
  const child = spawn('uv', ['run', 'scripts/video/agent.py', '--lang', lang], {
    cwd: REPO, stdio: ['ignore', 'pipe', 'inherit'],
  })
  const lines = createInterface({ input: child.stdout! })
  let done = false
  for await (const line of lines) {
    if (!line.trim().startsWith('{')) continue
    const event = JSON.parse(line) as { kind: string }
    await page.evaluate((e) => (window as unknown as { agentEvent: (x: unknown) => void }).agentEvent(e), event)
    if (event.kind === 'done') done = true
  }
  const code = await new Promise<number | null>((r) => child.on('close', r))
  expect(done, `agent ended without finishing (exit ${code})`).toBe(true)
}

/** Un PDF minimal mais bien formé (comme MailRoomDemoPack.pdf), pour le dépôt papier. */
export function pdf(text: string): Buffer {
  const safe = text.replace(/[\\()]/g, '')
  const content = `BT /F1 18 Tf 72 720 Td (${safe}) Tj ET`
  return Buffer.from(
    '%PDF-1.4\n'
      + '1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n'
      + '2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n'
      + '3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R '
      + '/Resources << /Font << /F1 5 0 R >> >> >> endobj\n'
      + `4 0 obj << /Length ${content.length} >> stream\n${content}\nendstream endobj\n`
      + '5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n'
      + 'trailer << /Root 1 0 R >>\n%%EOF\n',
    'latin1',
  )
}
