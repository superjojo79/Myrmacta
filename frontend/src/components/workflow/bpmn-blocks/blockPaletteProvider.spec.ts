import { beforeEach, describe, expect, it, vi } from 'vitest'
import { BlockPaletteProvider, createBlockShape } from './blockPaletteProvider'
import { setBlockCatalog } from './blockCatalogHolder'
import { recognizeBlock } from './recognizeBlock'
import { TEST_BLOCKS } from './testCatalog'

function fakeFactory() {
  return {
    createShape: vi.fn(({ type }: { type: string }) => {
      const attrs: Record<string, unknown> = {}
      return {
        businessObject: {
          $type: type,
          name: undefined as string | undefined,
          set: (k: string, v: unknown) => (attrs[k] = v),
          get: (k: string) => attrs[k],
        },
      }
    }),
  }
}

describe('blockPaletteProvider (S-UI-1)', () => {
  beforeEach(() => setBlockCatalog(TEST_BLOCKS))

  it('expose une entrée par brique dans un groupe dédié', () => {
    const palette = { registerProvider: vi.fn() }
    const provider = new BlockPaletteProvider(palette, { start: vi.fn() }, fakeFactory())
    expect(palette.registerProvider).toHaveBeenCalledWith(provider)
    const entries = provider.getPaletteEntries()
    expect(Object.keys(entries)).toHaveLength(TEST_BLOCKS.length)
    expect(new Set(Object.values(entries).map((e) => e.group))).toEqual(new Set(['business-blocks']))
    expect(entries['create-block-generate-document'].title).toContain('generate-document')
  })

  it('cliquer démarre la création de l’élément pré-configuré', () => {
    const create = { start: vi.fn() }
    const provider = new BlockPaletteProvider({ registerProvider: vi.fn() }, create, fakeFactory())
    provider.getPaletteEntries()['create-block-set-dossier-status'].action.click({ type: 'click' })
    expect(create.start).toHaveBeenCalledTimes(1)
    const shape = create.start.mock.calls[0][1]
    expect(shape.businessObject.$type).toBe('bpmn:ServiceTask')
    expect(shape.businessObject.get('flowable:delegateExpression')).toBe('${updateDossierStatusTask}')
  })

  it.each(TEST_BLOCKS.map((b) => b.id))('l’élément créé pour %s est reconnu comme cette brique', (id) => {
    const block = TEST_BLOCKS.find((b) => b.id === id)!
    const shape = createBlockShape(block, fakeFactory())
    expect(shape.businessObject.name).toBe(block.label)
    expect(recognizeBlock(shape.businessObject as never, TEST_BLOCKS)?.id).toBe(id)
  })

  it('crée asynchrone une brique dont le marqueur l\'exige, et elle seule (TODO §106)', () => {
    const precheck = {
      ...TEST_BLOCKS[0]!,
      id: 'ai-precheck',
      marker: { delegate: 'aiPrecheckTask', serviceTaskType: null, formKeyPrefix: null, portalRecipient: false, async: true },
    }
    expect(createBlockShape(precheck, fakeFactory()).businessObject.get('flowable:async')).toBe('true')
    expect(createBlockShape(TEST_BLOCKS[0]!, fakeFactory()).businessObject.get('flowable:async')).toBeUndefined()
  })

  it('déclare xmlns:flowable avant de créer une brique (I3)', () => {
    const attrs: Record<string, unknown> = {}
    const definitions = { $type: 'bpmn:Definitions', $attrs: attrs, get: () => undefined }
    const create = { start: vi.fn() }
    const provider = new BlockPaletteProvider(
      { registerProvider: vi.fn() },
      create,
      fakeFactory(),
      { getDefinitions: () => definitions },
    )
    provider.getPaletteEntries()['create-block-attach-document'].action.click({})
    expect(attrs['xmlns:flowable']).toBe('http://flowable.org/bpmn')
    expect(create.start).toHaveBeenCalled()
  })

  it('sans catalogue, aucune entrée (repli éditeur brut)', () => {
    setBlockCatalog([])
    const provider = new BlockPaletteProvider({ registerProvider: vi.fn() }, { start: vi.fn() }, fakeFactory())
    expect(provider.getPaletteEntries()).toEqual({})
  })
})
