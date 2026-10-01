import type { RelationDirection, RelationType } from '@/api/types'
import { i18n } from '@/i18n'

export function relationLabel(type: RelationType, direction: RelationDirection): string {
  const t = i18n.global.t
  const outgoing = direction === 'OUTGOING'
  switch (type) {
    case 'PARENT_CHILD':
      return outgoing ? t('dossier.relations.child') : t('dossier.relations.parent')
    case 'SUBSEQUENT':
      return outgoing ? t('dossier.relations.followUp') : t('dossier.relations.previous')
    case 'LINKED':
      return t('dossier.relations.linked')
    case 'DUPLICATE':
      return t('dossier.relations.duplicate')
    case 'MERGED_INTO':
      return outgoing ? t('dossier.relations.mergedInto') : t('dossier.relations.mergedFrom')
    default:
      return type
  }
}
