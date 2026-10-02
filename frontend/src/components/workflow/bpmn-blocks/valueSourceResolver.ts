import axios from 'axios'
import { listAllDossierTypes } from '@/api/dossierTypes'
import { listAllWorkflowTypeConfigs, getWorkflowTypeConfig } from '@/api/workflows'
import { listAllPages } from '@/api/page'
import { listTaskQuestionSchemas } from '@/api/taskQuestionSchemas'
import type { ValueOption } from './types'

// Résout les sources de valeurs du catalogue (`valueSource`) en options {value,label}. Les sources
// réservées à l'administration, et les tables DMN (pas d'API REST de liste), passent par `/api/bpmn-building-blocks/references/{source}` (D-PALETTE-REFERENCES).
// `null` = source non listable (saisie libre : la vérification se fait au déploiement) ; un échec réseau
// renvoie aussi `null` sans lever (D-PALETTE-FALLBACK).

const SERVER_SOURCES = new Set(['documentTemplates', 'connectorInstances', 'notificationWebhooks', 'dmnTables'])

export interface ResolveContext {
  /** Clé du process ouvert dans l'éditeur (pour `workflowStatuses`). */
  processKey?: string | null
}

export async function resolveValueSource(source: string, context: ResolveContext = {}): Promise<ValueOption[] | null> {
  try {
    if (SERVER_SOURCES.has(source)) {
      const { data } = await axios.get<ValueOption[]>(`/api/bpmn-building-blocks/references/${source}`)
      return data
    }
    switch (source) {
      case 'dossierTypes':
        return (await listAllDossierTypes()).map((t) => ({ value: t.code, label: t.label }))
      case 'workflowTypes':
        return (await listAllWorkflowTypeConfigs()).map((c) => ({ value: c.workflowKey, label: c.workflowKey }))
      case 'workflowStatuses': {
        if (!context.processKey) return null
        const config = await getWorkflowTypeConfig(context.processKey)
        return (config.statusDefinitions ?? []).map((s) => ({ value: s.code, label: s.label || s.code }))
      }
      case 'questionSchemas':
        return (await listAllPages((page, size) => listTaskQuestionSchemas({ page, size }))).map((s) => ({
          value: s.schemaKey,
          label: s.label || s.schemaKey,
        }))
      default:
        // Source inconnue : saisie libre, existence vérifiée au déploiement.
        return null
    }
  } catch {
    return null
  }
}

// Cache de la session d'édition : les listes servent à chaque sélection d'élément. Les échecs (`null`) ne sont pas
// mémorisés (nouvel essai à la sélection suivante) ; vidé à l'ouverture de l'éditeur (setBlockCatalog).
const cache = new Map<string, Promise<ValueOption[] | null>>()
const resolved = new Map<string, ValueOption[]>()
const loadedAt = new Map<string, number>()
// Revue M7 : une ressource créée pendant la session (template, connecteur…) doit apparaître sans rouvrir l'éditeur.
const TTL_MS = 60_000

function cacheKey(source: string, context: ResolveContext): string {
  return `${source}|${source === 'workflowStatuses' ? (context.processKey ?? '') : ''}`
}

export function resolveValueSourceCached(source: string, context: ResolveContext = {}): Promise<ValueOption[] | null> {
  const key = cacheKey(source, context)
  let pending = cache.get(key)
  if (pending && Date.now() - (loadedAt.get(key) ?? 0) > TTL_MS) {
    cache.delete(key)
    pending = undefined
  }
  if (!pending) {
    loadedAt.set(key, Date.now())
    pending = resolveValueSource(source, context).then((options) => {
      if (options === null) cache.delete(key)
      else resolved.set(key, options)
      return options
    })
    cache.set(key, pending)
  }
  return pending
}

/** Liste déjà résolue (synchrone) ou `undefined` : sert aux diagnostics du canevas, qui ne peuvent pas attendre. */
export function peekValueSource(source: string, context: ResolveContext = {}): ValueOption[] | undefined {
  return resolved.get(cacheKey(source, context))
}

export function resetValueSourceCache(): void {
  cache.clear()
  resolved.clear()
  loadedAt.clear()
}
