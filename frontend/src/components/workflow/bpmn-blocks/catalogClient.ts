import axios from 'axios'
import type { BuildingBlockCatalog } from './types'

// Catalogue mis en cache pour la session de l'éditeur ; `null` = indisponible (repli : éditeur brut,
// D-PALETTE-FALLBACK) — jamais d'exception vers l'éditeur.
let cached: Promise<BuildingBlockCatalog | null> | null = null

export function loadBlockCatalog(): Promise<BuildingBlockCatalog | null> {
  if (!cached) {
    cached = axios
      .get<BuildingBlockCatalog>('/api/bpmn-building-blocks')
      .then((response) => response.data)
      .catch(() => {
        cached = null // nouvelle tentative à la prochaine ouverture
        return null
      })
  }
  return cached
}

export function resetBlockCatalogCache(): void {
  cached = null
}
