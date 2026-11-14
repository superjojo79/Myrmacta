import type { Page, Request } from '@playwright/test'

// Issue #10, lot 3b — simulateurs des extensions de carte eID, injectés dans la page avant son chargement. Aucune vraie
// extension ni vraie carte : ils suivent les protocoles de Web eID 2.1.0 et de BeIDConnect 2.12 et gardent la trace de ce
// que la page leur envoie (dont le PIN, que seul BeIDConnect reçoit).

export const SIGNATURE = 'U0lHTkFUVVJF' // « SIGNATURE » en base64
export const CERTIFICATE = 'Q0VSVElGSUNBVEU=' // « CERTIFICATE » en base64

export interface WebEidLog {
  requests: Record<string, unknown>[]
}

export interface BeidLog {
  signCalls: unknown[][]
  certificateCalls: unknown[][]
}

/** Web eID : `postMessage` vers la page, réponses `…-ack` puis `…-success` (ou `…-failure` avec le code donné). */
export async function installWebEid(page: Page, opts: { failSignWith?: string } = {}) {
  await page.addInitScript(
    ({ certificate, signature, failSignWith }) => {
      const w = window as unknown as { __webEid: { requests: Record<string, unknown>[] } }
      w.__webEid = { requests: [] }
      window.addEventListener('message', (event) => {
        const data = event.data as { action?: string } | null
        if (event.source !== window || !data || typeof data.action !== 'string' || !data.action.startsWith('web-eid:')) return
        if (/-(ack|success|failure)$/.test(data.action)) return
        w.__webEid.requests.push(data as Record<string, unknown>)
        const name = data.action.slice('web-eid:'.length)
        const reply = (message: Record<string, unknown>) => window.postMessage(message, '*')
        reply({ action: `web-eid:${name}-ack` })
        setTimeout(() => {
          if (name === 'status') {
            reply({ action: 'web-eid:status-success', library: '2.1.0', extension: '2.1.0', nativeApp: '2.1.0' })
          } else if (name === 'get-signing-certificate') {
            reply({ action: `web-eid:${name}-success`, certificate, supportedSignatureAlgorithms: [] })
          } else if (failSignWith) {
            reply({ action: 'web-eid:sign-failure', error: { code: failSignWith, message: 'simulé' } })
          } else {
            reply({ action: 'web-eid:sign-success', signature, signatureAlgorithm: {} })
          }
        }, 20)
      })
    },
    { certificate: CERTIFICATE, signature: SIGNATURE, failSignWith: opts.failSignWith ?? null },
  )
}

export async function webEidLog(page: Page): Promise<WebEidLog> {
  return page.evaluate(() => (window as unknown as { __webEid: WebEidLog }).__webEid)
}

/** BeIDConnect : `window.EIDChromeExt`, lecteur ordinaire (PIN à demander à la page) ou lecteur à clavier. */
export async function installBeidConnect(page: Page, opts: { readerType?: 'standard' | 'pinpad'; signFailsWith?: string } = {}) {
  await page.addInitScript(
    ({ certificate, signature, readerType, signFailsWith }) => {
      const log = { signCalls: [] as unknown[][], certificateCalls: [] as unknown[][] }
      ;(window as unknown as { __beid: typeof log }).__beid = log
      ;(window as unknown as { EIDChromeExt: unknown }).EIDChromeExt = class {
        getVersion() {
          return Promise.resolve({ result: 'OK', version: '2.12' })
        }
        getUserCertificates(...args: unknown[]) {
          log.certificateCalls.push(args)
          return Promise.resolve({
            result: 'OK',
            Readers: [{ ReaderName: 'Lecteur simulé', ReaderType: readerType, cardType: 'BEID', certificates: [certificate] }],
          })
        }
        getUserCertificateChain() {
          return Promise.resolve({ result: 'OK', certificateChain: { rootCA: 'Uk9PVA==', subCA: ['SU5URVJNRURJQVRF'] } })
        }
        sign(...args: unknown[]) {
          log.signCalls.push(args)
          return signFailsWith ? Promise.reject({ message: signFailsWith, result: signFailsWith }) : Promise.resolve({ result: 'OK', signature })
        }
      }
    },
    { certificate: CERTIFICATE, signature: SIGNATURE, readerType: opts.readerType ?? 'standard', signFailsWith: opts.signFailsWith ?? null },
  )
}

export async function beidLog(page: Page): Promise<BeidLog> {
  return page.evaluate(() => (window as unknown as { __beid: BeidLog }).__beid)
}

/** Garde chaque requête réseau de la page (URL, en-têtes, corps) pour vérifier qu'un secret n'y figure jamais. */
export function recordRequests(page: Page): () => string {
  const seen: string[] = []
  page.on('request', (request: Request) => {
    seen.push(JSON.stringify({ url: request.url(), headers: request.headers(), body: request.postData() }))
  })
  return () => seen.join('\n')
}
