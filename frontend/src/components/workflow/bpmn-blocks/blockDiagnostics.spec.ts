import { describe, expect, it } from 'vitest'
import { diagnoseBlock, diagnoseParameter, priorityHint, type DiagnosticContext } from './blockDiagnostics'
import { TEST_BLOCKS } from './testCatalog'
import type { Moddled } from './blockValues'

function bo(attrs: Record<string, unknown> = {}, signers: object[] = []): Moddled {
  return {
    $type: 'bpmn:ServiceTask',
    get: (name: string) => {
      if (name === 'extensionElements') {
        return { get: () => signers.map((s) => ({ $type: 'flowable:signer', ...s })) }
      }
      return attrs[name]
    },
  }
}

const ctx = (over: Partial<DiagnosticContext> = {}): DiagnosticContext => ({
  options: () => undefined,
  hasSignatureCompletedCatch: true,
  ...over,
})
const block = (id: string) => TEST_BLOCKS.find((b) => b.id === id)!

const mapFields = {
  id: 'map-fields',
  label: 'map',
  description: 'map',
  category: 'DOSSIER' as const,
  element: 'SERVICE_TASK' as const,
  marker: { delegate: 'fieldMappingTask', serviceTaskType: null, formKeyPrefix: null, portalRecipient: false },
  parameters: [
    {
      name: 'fieldMapping', label: 'Valeurs', type: 'TEXT' as const, binding: 'ATTRIBUTE' as const, required: true,
      valueSource: null, enumValues: [], maxLength: 8000, min: null, max: null, help: null, multiline: true,
    },
  ],
}

describe('blockDiagnostics', () => {
  it('un paramètre multiligne du catalogue n\'est pas signalé pour ses retours à la ligne (D3b)', () => {
    const attrs = { 'flowable:fieldMapping': 'score = connectorResult.json.score\nville = connectorResult.json.city' }
    expect(diagnoseBlock(bo(attrs), mapFields, ctx())).toEqual([])
    const single = { ...mapFields, parameters: [{ ...mapFields.parameters[0], multiline: false }] }
    expect(diagnoseBlock(bo(attrs), single, ctx())).toEqual([expect.objectContaining({ kind: 'invalid' })])
  })

  it('repli sur le nom pour un catalogue qui ne publie pas encore multiline', () => {
    const legacy = { ...mapFields, parameters: [{ ...mapFields.parameters[0], multiline: undefined }] }
    const attrs = { 'flowable:fieldMapping': 'a = 1\nb = 2' }
    expect(diagnoseBlock(bo(attrs), legacy, ctx())).toEqual([])
  })

  it('signale un paramètre obligatoire vide, et plus rien une fois corrigé (S-DIAG-1)', () => {
    const b = block('set-dossier-status')
    expect(diagnoseBlock(bo(), b, ctx())).toEqual([
      expect.objectContaining({ kind: 'required', param: 'targetDossierStatus' }),
    ])
    expect(diagnoseBlock(bo({ 'flowable:targetDossierStatus': 'OUVERT' }), b, ctx())).toEqual([])
  })

  it('signale une référence absente de la source (S-DIAG-2)', () => {
    const b = block('generate-document')
    const attrs = { 'flowable:documentType': 'contrat' }
    const listed = ctx({ options: () => [{ value: 'autre', label: 'Autre' }] })
    expect(diagnoseBlock(bo(attrs), b, listed)).toEqual([expect.objectContaining({ kind: 'reference' })])
    const present = ctx({ options: () => [{ value: 'contrat', label: 'Contrat' }] })
    expect(diagnoseBlock(bo(attrs), b, present)).toEqual([])
  })

  it('n’affirme jamais « référence absente » si la source est indisponible ou non chargée (S-DIAG-5)', () => {
    const b = block('generate-document')
    const attrs = { 'flowable:documentType': 'contrat' }
    expect(diagnoseBlock(bo(attrs), b, ctx({ options: () => null }))).toEqual([])
    expect(diagnoseBlock(bo(attrs), b, ctx({ options: () => undefined }))).toEqual([])
  })

  it('refuse les expressions ${…} / #{…} (S-DIAG-3)', () => {
    const p = block('generate-document').parameters[0]
    for (const v of ['${x}', '#{y}', 'a${b}']) {
      expect(diagnoseParameter(bo({ 'flowable:documentType': v }), block('generate-document'), p, ctx())).toEqual([
        expect.objectContaining({ kind: 'literal' }),
      ])
    }
  })

  it('signature : compagnon signatureCompleted attendu en aval (S-DIAG-6)', () => {
    const b = block('request-signature')
    const attrs = { 'flowable:signatureDocument': 'variable:doc' }
    const signers = [{ source: 'variable:m', kind: 'EXTERNAL', required: 'true' }]
    expect(diagnoseBlock(bo(attrs, signers), b, ctx({ hasSignatureCompletedCatch: false }))).toEqual([
      expect.objectContaining({ kind: 'companion' }),
    ])
    expect(diagnoseBlock(bo(attrs, signers), b, ctx())).toEqual([])
    expect(diagnoseBlock(bo(attrs, []), b, ctx()).map((d) => d.param)).toEqual(['signers'])
  })

  it('tâche externe sans schéma valide : avertissement (S-DIAG-7)', () => {
    const b = block('external-task')
    const noKey = bo({ 'flowable:formKey': 'user-question:', 'flowable:portalRecipient': 'variable:m' })
    expect(diagnoseBlock(noKey, b, ctx())).toEqual([expect.objectContaining({ kind: 'required', param: 'schemaKey' })])
    const ok = bo({ 'flowable:formKey': 'user-question:demande', 'flowable:portalRecipient': 'variable:m' })
    expect(diagnoseBlock(ok, b, ctx())).toEqual([])
  })

  it('déconseille l’e-mail littéral et rappelle la priorité de la variable (S-DIAG-8)', () => {
    const b = block('notify')
    const attrs = { 'flowable:notificationChannels': 'EMAIL', 'flowable:notificationTo': 'jean@example.org' }
    expect(diagnoseBlock(bo(attrs), b, ctx())).toEqual([expect.objectContaining({ kind: 'email' })])
    expect(priorityHint(block('notify').parameters[1])).toContain('notificationTo')
    expect(priorityHint(block('request-signature').parameters[3])).toBeUndefined()
  })

  it('accepte une source de destinataire bien formée et refuse une source mal formée, comme le déploiement (TODO §93)', () => {
    const b = block('notify')
    for (const ok of ['dossierField:email', 'variable:adresse_demandeur']) {
      expect(diagnoseBlock(bo({ 'flowable:notificationChannels': 'EMAIL', 'flowable:notificationTo': ok }), b, ctx())).toEqual([])
    }
    for (const bad of ['dossierField:', 'dossierField:a.b', 'variable:1x']) {
      expect(diagnoseBlock(bo({ 'flowable:notificationChannels': 'EMAIL', 'flowable:notificationTo': bad }), b, ctx())).toEqual([
        expect.objectContaining({ kind: 'invalid', param: 'notificationTo' }),
      ])
    }
  })

  it('pièce jointe : source dossierField:/variable: exigée, nom de fichier sans chemin, comme le déploiement (issue #183)', () => {
    const b = block('notify')
    const base = { 'flowable:notificationChannels': 'EMAIL', 'flowable:notificationTo': 'dossierField:email' }
    const ok = { ...base, 'flowable:notificationAttachment': 'variable:generatedDocumentId', 'flowable:notificationAttachmentName': 'Décision (2026).pdf' }
    expect(diagnoseBlock(bo(ok), b, ctx())).toEqual([])
    for (const bad of ['tenant/doc.pdf', 'variable:', 'dossierField:a.b']) {
      expect(diagnoseBlock(bo({ ...base, 'flowable:notificationAttachment': bad }), b, ctx())).toEqual([
        expect.objectContaining({ kind: 'invalid', param: 'notificationAttachment' }),
      ])
    }
    for (const bad of ['../x.pdf', 'a/b.pdf', '.pdf', 'a"b']) {
      expect(diagnoseBlock(bo({ ...ok, 'flowable:notificationAttachmentName': bad }), b, ctx())).toEqual([
        expect.objectContaining({ kind: 'invalid', param: 'notificationAttachmentName' }),
      ])
    }
  })

  it('pièce jointe : destinataire et canal EMAIL exigés dans le BPMN, nom sans pièce jointe refusé (revue #183 M1, L5)', () => {
    const b = block('notify')
    const attachment = { 'flowable:notificationAttachment': 'variable:generatedDocumentId' }
    expect(diagnoseBlock(bo({ 'flowable:notificationChannels': 'EMAIL', ...attachment }), b, ctx())).toEqual([
      expect.objectContaining({ kind: 'invalid', param: 'notificationTo' }),
    ])
    expect(diagnoseBlock(bo({ 'flowable:notificationChannels': 'WEBHOOK', 'flowable:notificationTo': 'dossierField:email', ...attachment }), b, ctx())).toEqual([
      expect.objectContaining({ kind: 'invalid', param: 'notificationChannels' }),
    ])
    expect(diagnoseBlock(bo({ 'flowable:notificationChannels': 'EMAIL', 'flowable:notificationAttachmentName': 'a.pdf' }), b, ctx())).toEqual([
      expect.objectContaining({ kind: 'invalid', param: 'notificationAttachmentName' }),
    ])
  })

  it('l’avertissement sur l’e-mail littéral propose de lire l’adresse dans le dossier (TODO §93)', () => {
    const attrs = { 'flowable:notificationChannels': 'EMAIL', 'flowable:notificationTo': 'jean@example.org' }
    expect(diagnoseBlock(bo(attrs), block('notify'), ctx())[0]?.message).toContain('dossierField:')
  })

  it('signale une valeur d’énumération ou un format invalide, comme le déploiement (I4)', () => {
    const status = block('set-dossier-status')
    expect(diagnoseBlock(bo({ 'flowable:targetDossierStatus': 'CLOSED' }), status, ctx())).toEqual([
      expect.objectContaining({ kind: 'invalid', param: 'targetDossierStatus' }),
    ])
    const notify = block('notify')
    expect(diagnoseBlock(bo({ 'flowable:notificationChannels': 'email' }), notify, ctx())).toEqual([
      expect.objectContaining({ kind: 'invalid' }),
    ])
    expect(diagnoseBlock(bo({ 'flowable:notificationChannels': 'EMAIL,WEBHOOK' }), notify, ctx())).toEqual([])
    const connector = block('call-connector')
    expect(diagnoseBlock(bo({ 'flowable:connectorInstanceId': 'pas-un-uuid' }), connector, ctx())).toEqual([
      expect.objectContaining({ kind: 'invalid' }),
    ])
    const uuid = '3f2b8c1e-0a4d-4c55-9d1e-2b7a6f0c9e11'
    expect(diagnoseBlock(bo({ 'flowable:connectorInstanceId': uuid }), connector, ctx())).toEqual([])
  })

  it('signale les espaces de bord (I2)', () => {
    expect(
      diagnoseBlock(bo({ 'flowable:targetDossierStatus': ' CLOTURE' }), block('set-dossier-status'), ctx()),
    ).toEqual([expect.objectContaining({ kind: 'invalid' })])
  })

  it('signale un dossier enfant qui redémarre le même process (M4)', () => {
    const child = block('create-child-dossier')
    const attrs = { 'flowable:workflowProcessKey': 'claim' }
    expect(diagnoseBlock(bo(attrs), child, ctx({ processKey: 'claim' })).map((d) => d.kind)).toContain('recursion')
    expect(diagnoseBlock(bo(attrs), child, ctx({ processKey: 'autre' })).map((d) => d.kind)).not.toContain('recursion')
  })
})
