import type { RelationDirection, RelationType } from '@/api/types'

export function relationLabel(type: RelationType, direction: RelationDirection): string {
  switch (type) {
    case 'PARENT_CHILD':
      return direction === 'OUTGOING' ? 'Enfant' : 'Parent'
    case 'SUBSEQUENT':
      return direction === 'OUTGOING' ? 'Dossier de suite' : 'Précédent'
    case 'LINKED':
      return 'Lié'
    case 'DUPLICATE':
      return 'Doublon'
    case 'MERGED_INTO':
      return direction === 'OUTGOING' ? 'Fusionné vers' : 'Fusionné depuis'
    default:
      return type
  }
}
